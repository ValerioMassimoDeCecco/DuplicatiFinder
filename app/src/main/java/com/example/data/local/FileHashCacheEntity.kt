package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "file_hash_cache",
    indices = [
        Index(value = ["size"]),
        Index(value = ["size", "partialHash"])
    ]
)
data class FileHashCacheEntity(
    @PrimaryKey
    val path: String,
    val size: Long,
    val lastModified: Long,
    val partialHash: Long?,
    val fullHash: String?
)
