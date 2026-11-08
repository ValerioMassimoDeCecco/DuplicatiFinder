package com.example.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0

        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(mbPerSec: Double): String {
        if (mbPerSec <= 0.0) return "0.0 MB/s"
        return String.format(Locale.US, "%.1f MB/s", mbPerSec)
    }

    fun formatTimestamp(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        return synchronized(dateFormat) {
            dateFormat.format(Date(epochMillis))
        }
    }

    fun formatDurationMs(elapsedMs: Long): String {
        if (elapsedMs < 1000L) return "${elapsedMs}ms"
        val sec = elapsedMs / 1000.0
        return String.format(Locale.US, "%.2fs", sec)
    }
}
