package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val url: String,
    val category: String,
    val siteUrl: String? = null,
    val iconUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true
)
