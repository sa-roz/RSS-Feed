package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY pubDate DESC")
    fun getAllArticlesFlow(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY pubDate DESC")
    fun getArticlesForFeedFlow(feedId: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY pubDate DESC")
    suspend fun getArticlesForFeed(feedId: Long): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE feedCategory = :category ORDER BY pubDate DESC")
    fun getArticlesForCategoryFlow(category: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY bookmarkedAt DESC, pubDate DESC")
    fun getBookmarkedArticlesFlow(): Flow<List<ArticleEntity>>

    @Query("SELECT link FROM articles WHERE isBookmarked = 1")
    suspend fun getBookmarkedLinks(): List<String>

    @Query("SELECT link FROM articles WHERE isRead = 1")
    suspend fun getReadLinks(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isBookmarked = :isBookmarked, bookmarkedAt = :bookmarkedAt WHERE link = :link")
    suspend fun updateBookmarkStatus(link: String, isBookmarked: Boolean, bookmarkedAt: Long?)

    @Query("UPDATE articles SET isRead = :isRead WHERE link = :link")
    suspend fun updateReadStatus(link: String, isRead: Boolean)

    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllArticlesAsRead()

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId")
    suspend fun markAllArticlesAsReadForFeed(feedId: Long)

    @Query("UPDATE articles SET feedName = :feedName, feedCategory = :feedCategory WHERE feedId = :feedId")
    suspend fun updateFeedMetadata(feedId: Long, feedName: String, feedCategory: String)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isBookmarked = 0")
    suspend fun deleteUnbookmarkedArticlesForFeed(feedId: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteAllArticlesForFeed(feedId: Long)

    @Query("SELECT * FROM articles WHERE link = :link LIMIT 1")
    suspend fun getArticleByLink(link: String): ArticleEntity?

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Transaction
    suspend fun insertOrUpdatePreservingBookmarks(newArticles: List<ArticleEntity>) {
        if (newArticles.isEmpty()) return
        val bookmarkedLinks = getBookmarkedLinks().toSet()
        val readLinks = getReadLinks().toSet()
        val prepared = newArticles.map { article ->
            val isBm = bookmarkedLinks.contains(article.link)
            val isRd = readLinks.contains(article.link)
            if (isBm || isRd) {
                val existing = getArticleByLink(article.link)
                article.copy(
                    isBookmarked = isBm,
                    bookmarkedAt = if (isBm) (existing?.bookmarkedAt ?: System.currentTimeMillis()) else null,
                    isRead = isRd
                )
            } else {
                article
            }
        }
        insertArticles(prepared)
    }
}
