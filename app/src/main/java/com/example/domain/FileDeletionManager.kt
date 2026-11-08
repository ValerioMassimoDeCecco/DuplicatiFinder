package com.example.domain

import android.content.Context
import android.media.MediaScannerConnection
import com.example.data.local.HashCacheRepository
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Batch file deletion manager with Room cache eviction and MediaStore synchronization
 * via MediaScannerConnection to prevent ghost thumbnails in Gallery/Files apps.
 */
class FileDeletionManager(
    private val context: Context,
    private val hashCacheRepository: HashCacheRepository
) {

    suspend fun deleteFilesBatch(
        filesToDelete: List<DuplicateFileItem>,
        onProgress: (deletedSoFar: Int, total: Int) -> Unit = { _, _ -> }
    ): DeletionResult = withContext(Dispatchers.IO) {
        if (filesToDelete.isEmpty()) {
            return@withContext DeletionResult(
                deletedCount = 0,
                failedCount = 0,
                bytesReclaimed = 0L,
                failedPaths = emptyList(),
                mediaStoreUpdated = true
            )
        }

        val deletedPaths = ArrayList<String>(filesToDelete.size)
        val failedPaths = ArrayList<String>()
        var bytesReclaimed = 0L

        filesToDelete.forEachIndexed { index, item ->
            val file = File(item.path)
            val actualSize = if (file.exists()) file.length() else item.sizeBytes
            val deleted = runCatching {
                !file.exists() || file.delete()
            }.getOrDefault(false)

            if (deleted) {
                deletedPaths.add(item.path)
                bytesReclaimed += actualSize
            } else {
                failedPaths.add(item.path)
            }

            onProgress(index + 1, filesToDelete.size)
        }

        // Remove deleted file paths from Room hash cache
        if (deletedPaths.isNotEmpty()) {
            hashCacheRepository.removePaths(deletedPaths)
        }

        // Notify Android MediaStore so deleted images/videos/audio disappear from Gallery immediately
        val mediaStoreSynced = if (deletedPaths.isNotEmpty()) {
            notifyMediaScanner(deletedPaths)
        } else {
            true
        }

        DeletionResult(
            deletedCount = deletedPaths.size,
            failedCount = failedPaths.size,
            bytesReclaimed = bytesReclaimed,
            failedPaths = failedPaths,
            mediaStoreUpdated = mediaStoreSynced
        )
    }

    private suspend fun notifyMediaScanner(paths: List<String>): Boolean {
        if (paths.isEmpty()) return true
        // Wait up to 2.5 seconds for MediaScannerConnection callbacks so UI stays snappy
        val completed = withTimeoutOrNull(2500L) {
            suspendCancellableCoroutine { continuation ->
                var remaining = paths.size
                val lock = Any()
                try {
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        paths.toTypedArray(),
                        null
                    ) { _, _ ->
                        synchronized(lock) {
                            remaining--
                            if (remaining <= 0 && continuation.isActive) {
                                continuation.resume(true)
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
        }
        return completed ?: true
    }
}
