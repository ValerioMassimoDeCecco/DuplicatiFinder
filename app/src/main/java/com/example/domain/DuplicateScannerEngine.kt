package com.example.domain

import com.example.data.local.FileHashCacheEntity
import com.example.data.local.HashCacheRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 3-Stage High-Performance Concurrent Duplicate File Scanner Engine.
 *
 * Pipeline stages:
 * - Stage 1 (Size Grouping): Direct FileTreeWalk traversal grouping files by length() > 0.
 *   Discards all size buckets with count == 1 immediately (zero disk read overhead).
 * - Stage 2 (8 KB Partial Hash): Reads first 4096B + last 4096B via RandomAccessFile/FileChannel
 *   and computes 64-bit xxHash64 (with Room cache lookup). Discards unique partial hashes.
 * - Stage 3 (Full Hash): Streams remaining collision candidates in 64 KB buffers computing
 *   128-bit XXH3-128 / dual-lane xxHash (with Room cache lookup & persistence).
 */
class DuplicateScannerEngine(
    private val hashCacheRepository: HashCacheRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioLimitedDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(6)

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    @Volatile
    private var cancelRequested = false

    fun requestCancel() {
        cancelRequested = true
    }

    suspend fun scanDirectories(
        rootDirectories: List<File>,
        exclusionConfig: ExclusionConfig = ExclusionConfig()
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        cancelRequested = false
        val startTimeNs = System.nanoTime()
        val totalBytesRead = AtomicLong(0L)
        val cacheHits = AtomicInteger(0)

        fun elapsedMs(): Long = (System.nanoTime() - startTimeNs) / 1_000_000L

        fun currentSpeedMbPerSec(): Double {
            val sec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
            if (sec <= 0.005) return 0.0
            val mb = totalBytesRead.get().toDouble() / (1024.0 * 1024.0)
            return mb / sec
        }

        try {
            // =========================================================================
            // STAGE 1: FILE SYSTEM TRAVERSAL & SIZE GROUPING
            // =========================================================================
            _scanProgress.value = ScanProgress(
                stage = ScanStage.STAGE_1_SIZE_GROUPING,
                isScanning = true,
                elapsedMillis = 0L
            )

            val sizeMap = HashMap<Long, MutableList<File>>(4096)
            val seenCanonicalPaths = HashSet<String>(4096)
            var walkedCount = 0

            val distinctRoots = rootDirectories
                .filter { it.exists() && it.canRead() }
                .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

            for (root in distinctRoots) {
                ensureActive()
                if (cancelRequested) break

                val walk = root.walk(FileWalkDirection.TOP_DOWN)
                    .onEnter { dir ->
                        !cancelRequested && !shouldExcludeDirectory(dir, exclusionConfig)
                    }

                for (file in walk) {
                    if (cancelRequested) break
                    if (!file.isFile) continue
                    if (exclusionConfig.skipHiddenFiles && file.name.startsWith(".")) continue
                    if (shouldExcludeFile(file, exclusionConfig)) continue

                    val length = file.length()
                    if (length < exclusionConfig.minFileSizeBytes || length <= 0L) continue

                    val absPath = file.absolutePath
                    if (!seenCanonicalPaths.add(absPath)) continue

                    walkedCount++
                    sizeMap.getOrPut(length) { ArrayList(2) }.add(file)

                    if (walkedCount % 64 == 0) {
                        ensureActive()
                        _scanProgress.value = _scanProgress.value.copy(
                            totalFilesWalked = walkedCount,
                            processedInCurrentStage = walkedCount,
                            totalInCurrentStage = walkedCount,
                            currentFilePath = absPath,
                            elapsedMillis = elapsedMs()
                        )
                    }
                }
            }

            if (cancelRequested) {
                _scanProgress.value = _scanProgress.value.copy(
                    stage = ScanStage.CANCELLED,
                    isScanning = false,
                    elapsedMillis = elapsedMs()
                )
                return@withContext emptyList()
            }

            // Immediately discard all entries where list.size == 1
            val stage1Survivors = ArrayList<Pair<Long, List<File>>>()
            var stage1CandidateFileCount = 0
            for ((size, fileList) in sizeMap) {
                if (fileList.size >= 2) {
                    stage1Survivors.add(size to fileList)
                    stage1CandidateFileCount += fileList.size
                }
            }
            sizeMap.clear()

            if (stage1Survivors.isEmpty()) {
                _scanProgress.value = ScanProgress(
                    stage = ScanStage.COMPLETED,
                    isScanning = false,
                    totalFilesWalked = walkedCount,
                    stage1CandidateFiles = 0,
                    stage2CandidateFiles = 0,
                    stage3VerifiedDuplicates = 0,
                    elapsedMillis = elapsedMs()
                )
                return@withContext emptyList()
            }

            // Prefetch Room cache for all Stage 1 survivor paths
            val allCandidatePaths = stage1Survivors.flatMap { (_, files) ->
                files.map { it.absolutePath }
            }
            val cachedMap = hashCacheRepository.getCacheMapForPaths(allCandidatePaths)
            val updatedCacheEntries = ConcurrentHashMap<String, FileHashCacheEntity>()

            // =========================================================================
            // STAGE 2: PARTIAL HASH (8 KB: 4096B HEAD + 4096B TAIL via xxHash64)
            // =========================================================================
            val stage2Processed = AtomicInteger(0)
            _scanProgress.value = _scanProgress.value.copy(
                stage = ScanStage.STAGE_2_PARTIAL_HASH,
                totalFilesWalked = walkedCount,
                stage1CandidateFiles = stage1CandidateFileCount,
                processedInCurrentStage = 0,
                totalInCurrentStage = stage1CandidateFileCount,
                elapsedMillis = elapsedMs()
            )

            data class PartialHashedFile(
                val file: File,
                val size: Long,
                val lastModified: Long,
                val partialHash: Long,
                val cachedFullHash: String?,
                val fromCache: Boolean
            )

            val partialResults: List<PartialHashedFile> = coroutineScope {
                stage1Survivors.flatMap { (size, files) ->
                    files.map { file ->
                        async(ioLimitedDispatcher) {
                            if (cancelRequested) return@async null
                            ensureActive()
                            val path = file.absolutePath
                            val lastMod = file.lastModified()
                            val cached = cachedMap[path]

                            val result = if (
                                cached != null &&
                                cached.size == size &&
                                cached.lastModified == lastMod &&
                                cached.partialHash != null
                            ) {
                                cacheHits.incrementAndGet()
                                PartialHashedFile(
                                    file = file,
                                    size = size,
                                    lastModified = lastMod,
                                    partialHash = cached.partialHash,
                                    cachedFullHash = cached.fullHash,
                                    fromCache = true
                                )
                            } else {
                                val pHash = runCatching {
                                    HashUtils.computePartialHash8KB(file, size)
                                }.getOrNull() ?: return@async null

                                val bytesReadNow = minOf(size, HashUtils.PARTIAL_CHUNK_SIZE * 2L)
                                totalBytesRead.addAndGet(bytesReadNow)

                                val entity = FileHashCacheEntity(
                                    path = path,
                                    size = size,
                                    lastModified = lastMod,
                                    partialHash = pHash,
                                    fullHash = null
                                )
                                updatedCacheEntries[path] = entity

                                PartialHashedFile(
                                    file = file,
                                    size = size,
                                    lastModified = lastMod,
                                    partialHash = pHash,
                                    cachedFullHash = null,
                                    fromCache = false
                                )
                            }

                            val done = stage2Processed.incrementAndGet()
                            if (done % 8 == 0 || done == stage1CandidateFileCount) {
                                _scanProgress.value = _scanProgress.value.copy(
                                    processedInCurrentStage = done,
                                    totalInCurrentStage = stage1CandidateFileCount,
                                    speedMegabytesPerSec = currentSpeedMbPerSec(),
                                    totalBytesRead = totalBytesRead.get(),
                                    cacheHits = cacheHits.get(),
                                    currentFilePath = path,
                                    elapsedMillis = elapsedMs()
                                )
                            }
                            result
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (cancelRequested) {
                hashCacheRepository.upsertBatch(updatedCacheEntries.values.toList())
                _scanProgress.value = _scanProgress.value.copy(
                    stage = ScanStage.CANCELLED,
                    isScanning = false,
                    elapsedMillis = elapsedMs()
                )
                return@withContext emptyList()
            }

            // Group by (size, partialHash) and discard unique partial hashes
            val stage2Buckets = partialResults
                .groupBy { it.size to it.partialHash }
                .filterValues { it.size >= 2 }

            val stage2Survivors = stage2Buckets.values.flatten()
            val stage2CandidateCount = stage2Survivors.size

            if (stage2Survivors.isEmpty()) {
                hashCacheRepository.upsertBatch(updatedCacheEntries.values.toList())
                _scanProgress.value = _scanProgress.value.copy(
                    stage = ScanStage.COMPLETED,
                    isScanning = false,
                    stage2CandidateFiles = 0,
                    stage3VerifiedDuplicates = 0,
                    speedMegabytesPerSec = currentSpeedMbPerSec(),
                    totalBytesRead = totalBytesRead.get(),
                    cacheHits = cacheHits.get(),
                    elapsedMillis = elapsedMs()
                )
                return@withContext emptyList()
            }

            // =========================================================================
            // STAGE 3: FULL HASH (64 KB BUFFER STREAMING via XXH3-128)
            // =========================================================================
            val stage3Processed = AtomicInteger(0)
            _scanProgress.value = _scanProgress.value.copy(
                stage = ScanStage.STAGE_3_FULL_HASH,
                stage2CandidateFiles = stage2CandidateCount,
                processedInCurrentStage = 0,
                totalInCurrentStage = stage2CandidateCount,
                elapsedMillis = elapsedMs()
            )

            val fullHashedItems: List<DuplicateFileItem> = coroutineScope {
                stage2Survivors.map { candidate ->
                    async(ioLimitedDispatcher) {
                        if (cancelRequested) return@async null
                        ensureActive()
                        val file = candidate.file
                        val path = file.absolutePath

                        val fullHashHex: String
                        val wasCached: Boolean

                        if (!candidate.cachedFullHash.isNullOrEmpty()) {
                            fullHashHex = candidate.cachedFullHash
                            wasCached = true
                        } else {
                            wasCached = false
                            fullHashHex = runCatching {
                                HashUtils.computeFullHash128(
                                    file = file,
                                    fileSize = candidate.size,
                                    isCancelled = { cancelRequested },
                                    onBytesRead = { delta ->
                                        totalBytesRead.addAndGet(delta)
                                    }
                                )
                            }.getOrDefault("")

                            if (fullHashHex.isEmpty() || cancelRequested) return@async null

                            updatedCacheEntries[path] = FileHashCacheEntity(
                                path = path,
                                size = candidate.size,
                                lastModified = candidate.lastModified,
                                partialHash = candidate.partialHash,
                                fullHash = fullHashHex
                            )
                        }

                        val ext = file.extension.lowercase()
                        val item = DuplicateFileItem(
                            path = path,
                            name = file.name,
                            parentDir = file.parent ?: "/",
                            sizeBytes = candidate.size,
                            lastModified = candidate.lastModified,
                            extension = ext,
                            category = FileCategory.fromExtension(ext),
                            partialHash = candidate.partialHash,
                            fullHash = fullHashHex,
                            fromCache = wasCached
                        )

                        val done = stage3Processed.incrementAndGet()
                        if (done % 4 == 0 || done == stage2CandidateCount) {
                            _scanProgress.value = _scanProgress.value.copy(
                                processedInCurrentStage = done,
                                totalInCurrentStage = stage2CandidateCount,
                                speedMegabytesPerSec = currentSpeedMbPerSec(),
                                totalBytesRead = totalBytesRead.get(),
                                cacheHits = cacheHits.get(),
                                currentFilePath = path,
                                elapsedMillis = elapsedMs()
                            )
                        }
                        item
                    }
                }.awaitAll().filterNotNull()
            }

            // Persist all newly computed partial and full hashes to Room
            hashCacheRepository.upsertBatch(updatedCacheEntries.values.toList())

            if (cancelRequested) {
                _scanProgress.value = _scanProgress.value.copy(
                    stage = ScanStage.CANCELLED,
                    isScanning = false,
                    elapsedMillis = elapsedMs()
                )
                return@withContext emptyList()
            }

            // Build final DuplicateGroups sorted by reclaimable space descending
            val duplicateGroups = fullHashedItems
                .groupBy { it.sizeBytes to it.fullHash }
                .filterValues { it.size >= 2 }
                .map { (key, items) ->
                    val (sizeBytes, fullHash) = key
                    val sortedFiles = items.sortedWith(
                        compareBy<DuplicateFileItem> { it.lastModified }.thenBy { it.path }
                    )
                    val first = sortedFiles.first()
                    val partialHex = first.partialHash.toULong().toString(16).padStart(16, '0').uppercase()
                    DuplicateGroup(
                        hash = "${sizeBytes}_$fullHash",
                        fullHashHex = fullHash,
                        partialHashHex = partialHex,
                        fileSizeBytes = sizeBytes,
                        files = sortedFiles,
                        category = first.category
                    )
                }
                .sortedByDescending { it.reclaimableBytes }

            val totalReclaimable = duplicateGroups.sumOf { it.reclaimableBytes }
            val totalDuplicateFiles = duplicateGroups.sumOf { it.files.size }

            _scanProgress.value = _scanProgress.value.copy(
                stage = ScanStage.COMPLETED,
                isScanning = false,
                totalFilesWalked = walkedCount,
                stage1CandidateFiles = stage1CandidateFileCount,
                stage2CandidateFiles = stage2CandidateCount,
                stage3VerifiedDuplicates = totalDuplicateFiles,
                processedInCurrentStage = stage2CandidateCount,
                totalInCurrentStage = stage2CandidateCount,
                reclaimableBytesFound = totalReclaimable,
                speedMegabytesPerSec = currentSpeedMbPerSec(),
                totalBytesRead = totalBytesRead.get(),
                cacheHits = cacheHits.get(),
                currentFilePath = "",
                elapsedMillis = elapsedMs()
            )

            duplicateGroups
        } catch (e: Exception) {
            _scanProgress.value = _scanProgress.value.copy(
                stage = ScanStage.ERROR,
                isScanning = false,
                errorMessage = e.localizedMessage ?: "Errore imprevisto durante la scansione I/O",
                elapsedMillis = elapsedMs()
            )
            emptyList()
        }
    }

    private fun shouldExcludeDirectory(dir: File, config: ExclusionConfig): Boolean {
        if (config.skipHiddenFiles && dir.name.startsWith(".") && dir.name.length > 1) {
            return true
        }
        val normalizedPath = dir.absolutePath.replace('\\', '/')
        return config.excludedPathSegments.any { segment ->
            normalizedPath.contains(segment, ignoreCase = true)
        }
    }

    private fun shouldExcludeFile(file: File, config: ExclusionConfig): Boolean {
        val normalizedPath = file.absolutePath.replace('\\', '/')
        // Never scan our own Room SQLite database files
        if (normalizedPath.contains("duphash_cache.db")) return true
        return config.excludedPathSegments.any { segment ->
            normalizedPath.contains(segment, ignoreCase = true)
        }
    }
}
