package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["feedId"]),
        Index(value = ["pubDate"]),
        Index(value = ["isBookmarked"]),
        Index(value = ["isRead"]),
        Index(value = ["link"], unique = true)
    ]
)
data class ArticleEntity(
    @PrimaryKey
    val link: String,
    val feedId: Long,
    val feedName: String,
    val feedCategory: String,
    val title: String,
    val summary: String,
    val content: String? = null,
    val imageUrl: String? = null,
    val pubDate: Long = System.currentTimeMillis(),
    val pubDateFormatted: String = "",
    val isBookmarked: Boolean = false,
    val bookmarkedAt: Long? = null,
    val isRead: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)
