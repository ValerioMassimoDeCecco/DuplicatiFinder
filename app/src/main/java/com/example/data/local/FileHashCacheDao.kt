package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileHashCacheDao {

    @Query("SELECT * FROM file_hash_cache WHERE path IN (:paths)")
    suspend fun getCachedEntriesForPaths(paths: List<String>): List<FileHashCacheEntity>

    @Query("SELECT * FROM file_hash_cache WHERE path = :path LIMIT 1")
    suspend fun getCachedEntry(path: String): FileHashCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: FileHashCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<FileHashCacheEntity>)

    @Query("DELETE FROM file_hash_cache WHERE path IN (:paths)")
    suspend fun deleteEntriesByPaths(paths: List<String>)

    @Query("DELETE FROM file_hash_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM file_hash_cache")
    fun observeCachedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_hash_cache")
    suspend fun getCachedCount(): Int
}
