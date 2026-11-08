package com.example.domain

import androidx.compose.runtime.Immutable

@Immutable
enum class FileCategory(val labelIt: String, val extensions: Set<String>) {
    ALL("Tutti", emptySet()),
    IMAGES(
        "Immagini",
        setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "raw", "dng", "svg")
    ),
    VIDEOS(
        "Video",
        setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "ts", "m4v")
    ),
    AUDIO(
        "Audio",
        setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma")
    ),
    DOCUMENTS(
        "Documenti",
        setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml", "epub")
    ),
    ARCHIVES(
        "Archivi",
        setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk", "iso")
    );

    companion object {
        fun fromExtension(ext: String): FileCategory {
            val lower = ext.lowercase()
            return entries.firstOrNull { it != ALL && lower in it.extensions } ?: DOCUMENTS
        }
    }
}

@Immutable
data class DuplicateFileItem(
    val path: String,
    val name: String,
    val parentDir: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val category: FileCategory,
    val partialHash: Long,
    val fullHash: String,
    val fromCache: Boolean = false
)

@Immutable
data class DuplicateGroup(
    val hash: String, // Composite stable key: "${sizeBytes}_${fullHash}"
    val fullHashHex: String,
    val partialHashHex: String,
    val fileSizeBytes: Long,
    val files: List<DuplicateFileItem>,
    val category: FileCategory
) {
    val copyCount: Int get() = files.size
    val reclaimableBytes: Long get() = if (files.size > 1) fileSizeBytes * (files.size - 1) else 0L
}

@Immutable
enum class ScanStage(val code: String, val titleIt: String) {
    IDLE("IDLE", "PRONTO"),
    STAGE_1_SIZE_GROUPING("STG-1", "STAGE 1/3 · RAGGRUPPAMENTO DIMENSIONE"),
    STAGE_2_PARTIAL_HASH("STG-2", "STAGE 2/3 · PARTIAL HASH 8KB (xxHash64)"),
    STAGE_3_FULL_HASH("STG-3", "STAGE 3/3 · FULL HASH 64KB (XXH3-128)"),
    COMPLETED("DONE", "SCANSIONE COMPLETATA"),
    CANCELLED("STOP", "SCANSIONE INTERROTTA"),
    ERROR("ERR", "ERRORE DI SCANSIONE")
}

@Immutable
data class ScanProgress(
    val stage: ScanStage = ScanStage.IDLE,
    val isScanning: Boolean = false,
    val totalFilesWalked: Int = 0,
    val stage1CandidateFiles: Int = 0,
    val stage2CandidateFiles: Int = 0,
    val stage3VerifiedDuplicates: Int = 0,
    val processedInCurrentStage: Int = 0,
    val totalInCurrentStage: Int = 0,
    val reclaimableBytesFound: Long = 0L,
    val speedMegabytesPerSec: Double = 0.0,
    val totalBytesRead: Long = 0L,
    val cacheHits: Int = 0,
    val elapsedMillis: Long = 0L,
    val currentFilePath: String = "",
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalInCurrentStage > 0) {
            (processedInCurrentStage.toFloat() / totalInCurrentStage.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@Immutable
data class ExclusionConfig(
    val excludedPathSegments: Set<String> = DEFAULT_EXCLUDED_SEGMENTS,
    val skipHiddenFiles: Boolean = true,
    val minFileSizeBytes: Long = 1L
) {
    companion object {
        val DEFAULT_EXCLUDED_SEGMENTS = setOf(
            "/Android/data",
            "/Android/obb",
            ".thumbnails",
            ".git",
            "/proc",
            "/sys",
            "/dev"
        )
    }
}

@Immutable
sealed interface SmartSelectionStrategy {
    data object KeepOldest : SmartSelectionStrategy
    data object KeepNewest : SmartSelectionStrategy
    data class SelectByFolder(val folderPath: String) : SmartSelectionStrategy
    data object DeselectAll : SmartSelectionStrategy
}

@Immutable
data class DeletionResult(
    val deletedCount: Int,
    val failedCount: Int,
    val bytesReclaimed: Long,
    val failedPaths: List<String>,
    val mediaStoreUpdated: Boolean
)
