package com.example.data.local

import kotlinx.coroutines.flow.Flow

class HashCacheRepository(
    private val dao: FileHashCacheDao
) {
    val cachedCountFlow: Flow<Int> = dao.observeCachedCount()

    /**
     * Fetches cached entries in SQLite-safe chunks of 500 paths, returning a map keyed by path.
     */
    suspend fun getCacheMapForPaths(paths: List<String>): Map<String, FileHashCacheEntity> {
        if (paths.isEmpty()) return emptyMap()
        val result = HashMap<String, FileHashCacheEntity>(paths.size)
        for (chunk in paths.chunked(500)) {
            val entries = dao.getCachedEntriesForPaths(chunk)
            for (entry in entries) {
                result[entry.path] = entry
            }
        }
        return result
    }

    suspend fun upsertBatch(entries: List<FileHashCacheEntity>) {
        if (entries.isEmpty()) return
        for (chunk in entries.chunked(500)) {
            dao.upsertEntries(chunk)
        }
    }

    suspend fun removePaths(paths: List<String>) {
        if (paths.isEmpty()) return
        for (chunk in paths.chunked(500)) {
            dao.deleteEntriesByPaths(chunk)
        }
    }

    suspend fun clearAllCache() {
        dao.clearAll()
    }
}
