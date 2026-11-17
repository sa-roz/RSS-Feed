package com.example.data.repository

import android.content.Context
import com.example.data.local.ArticleDao
import com.example.data.local.ArticleEntity
import com.example.data.local.FeedDao
import com.example.data.local.FeedEntity
import com.example.data.parser.FeedExportFormat
import com.example.data.parser.OpmlParser
import com.example.data.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FeedRepository(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val context: Context? = null
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "CleanFeed/1.0 (Android RSS Reader; +https://github.com)")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                .build()
            chain.proceed(request)
        }
        .build()

    val allFeeds: Flow<List<FeedEntity>> = feedDao.getAllFeedsFlow()
    val allArticles: Flow<List<ArticleEntity>> = articleDao.getAllArticlesFlow()
    val bookmarkedArticles: Flow<List<ArticleEntity>> = articleDao.getBookmarkedArticlesFlow()
    val categories: Flow<List<String>> = feedDao.getAllCategoriesFlow()

    fun getArticlesForFeed(feedId: Long): Flow<List<ArticleEntity>> =
        articleDao.getArticlesForFeedFlow(feedId)

    suspend fun initializeDefaultFeedsIfEmpty() = withContext(Dispatchers.IO) {
        val prefs = context?.getSharedPreferences("cleanfeed_seed_prefs", Context.MODE_PRIVATE)
        val alreadySeededNepali = prefs?.getBoolean("nepali_portals_seeded_v1", false) ?: false

        if (feedDao.getFeedCount() == 0 || !alreadySeededNepali) {
            val inserted = insertMissingNepaliPortalsInternal()
            prefs?.edit()?.putBoolean("nepali_portals_seeded_v1", true)?.apply()
            if (inserted.isNotEmpty() || feedDao.getFeedCount() > 0) {
                refreshAllFeeds()
            }
        }
    }

    suspend fun addMissingNepaliPortals(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val insertedFeeds = insertMissingNepaliPortalsInternal()
            context?.getSharedPreferences("cleanfeed_seed_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean("nepali_portals_seeded_v1", true)
                ?.apply()

            if (insertedFeeds.isNotEmpty()) {
                refreshFeedsList(insertedFeeds)
            }
            Result.success(insertedFeeds.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun insertMissingNepaliPortalsInternal(): List<FeedEntity> {
        val existingFeeds = feedDao.getAllFeeds()
        val existingKeys = existingFeeds.map { NepaliPortalsCatalog.normalizeUrlKey(it.url) }.toMutableSet()
        val inserted = mutableListOf<FeedEntity>()

        for (portal in NepaliPortalsCatalog.portals) {
            val key = NepaliPortalsCatalog.normalizeUrlKey(portal.url)
            if (!existingKeys.contains(key)) {
                val id = feedDao.insertFeed(portal)
                if (id > 0) {
                    existingKeys.add(key)
                    inserted.add(portal.copy(id = id))
                }
            }
        }
        return inserted
    }

    suspend fun refreshAllFeeds(): Result<Int> = withContext(Dispatchers.IO) {
        val feeds = feedDao.getAllFeeds().filter { it.isEnabled }
        val successCount = refreshFeedsList(feeds)
        Result.success(successCount)
    }

    private suspend fun refreshFeedsList(feeds: List<FeedEntity>): Int = coroutineScope {
        val semaphore = Semaphore(6)
        val successCount = AtomicInteger(0)
        feeds.map { feed ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        fetchAndStoreArticlesForFeed(feed)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }.awaitAll()
        successCount.get()
    }

    suspend fun refreshFeed(feedId: Long): Result<Int> = withContext(Dispatchers.IO) {
        val feed = feedDao.getFeedById(feedId) ?: return@withContext Result.failure(Exception("Feed not found"))
        try {
            val count = fetchAndStoreArticlesForFeed(feed)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAndStoreArticlesForFeed(feed: FeedEntity): Int {
        val request = Request.Builder().url(feed.url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw Exception("Empty body")
            val parsedChannel = RssParser.parse(body.byteStream())

            // Update feed metadata if siteUrl was empty
            if (feed.siteUrl.isNullOrBlank() && parsedChannel.siteUrl.isNotBlank()) {
                feedDao.updateFeed(feed.copy(siteUrl = parsedChannel.siteUrl, lastUpdated = System.currentTimeMillis()))
            } else {
                feedDao.updateFeed(feed.copy(lastUpdated = System.currentTimeMillis()))
            }

            val articleEntities = parsedChannel.items.map { item ->
                ArticleEntity(
                    link = item.link.ifBlank { "${feed.url}#${item.title.hashCode()}" },
                    feedId = feed.id,
                    feedName = feed.name,
                    feedCategory = feed.category,
                    title = item.title,
                    summary = item.description,
                    content = item.content,
                    imageUrl = item.imageUrl,
                    pubDate = item.pubDate,
                    pubDateFormatted = item.pubDateFormatted
                )
            }

            articleDao.insertOrUpdatePreservingBookmarks(articleEntities)
            return articleEntities.size
        }
    }

    val unreadCount: Flow<Int> = articleDao.getUnreadCountFlow()

    suspend fun addFeed(name: String, url: String, category: String): Result<FeedEntity> = withContext(Dispatchers.IO) {
        try {
            val discovery = com.example.data.parser.RssDiscovery(httpClient)
            val discovered = discovery.discoverFeed(url)

            val feedName = name.trim().ifBlank { discovered.title.ifBlank { "New Feed" } }
            val feedCategory = category.trim().ifBlank { "General" }
            val siteUrl = discovered.siteUrl.ifBlank { null }
            val feedUrl = discovered.feedUrl

            // Check if already subscribed
            val existing = feedDao.getFeedByUrl(feedUrl)
            if (existing != null) {
                return@withContext Result.failure(Exception("You are already subscribed to '$feedName' ($feedUrl)"))
            }

            val feedEntity = FeedEntity(
                name = feedName,
                url = feedUrl,
                category = feedCategory,
                siteUrl = siteUrl,
                lastUpdated = System.currentTimeMillis()
            )

            val newId = feedDao.insertFeed(feedEntity)
            val insertedFeed = feedEntity.copy(id = newId)

            // Save parsed articles from discovery
            val articles = discovered.channel.items.map { item ->
                ArticleEntity(
                    link = item.link.ifBlank { "${feedUrl}#${item.title.hashCode()}" },
                    feedId = newId,
                    feedName = feedName,
                    feedCategory = feedCategory,
                    title = item.title,
                    summary = item.description,
                    content = item.content,
                    imageUrl = item.imageUrl,
                    pubDate = item.pubDate,
                    pubDateFormatted = item.pubDateFormatted,
                    isRead = false
                )
            }
            articleDao.insertOrUpdatePreservingBookmarks(articles)

            Result.success(insertedFeed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFeed(feed: FeedEntity, oldUrl: String? = null) = withContext(Dispatchers.IO) {
        feedDao.updateFeed(feed)
        articleDao.updateFeedMetadata(feed.id, feed.name, feed.category)
        if (oldUrl != null && oldUrl.trim() != feed.url.trim()) {
            articleDao.deleteUnbookmarkedArticlesForFeed(feed.id)
            refreshFeed(feed.id)
        }
    }

    suspend fun deleteFeed(feed: FeedEntity) = withContext(Dispatchers.IO) {
        articleDao.deleteAllArticlesForFeed(feed.id)
        feedDao.deleteFeed(feed)
    }

    suspend fun setBookmark(link: String, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        val timestamp = if (isBookmarked) System.currentTimeMillis() else null
        articleDao.updateBookmarkStatus(link, isBookmarked, timestamp)
    }

    suspend fun setArticleRead(link: String, isRead: Boolean) = withContext(Dispatchers.IO) {
        articleDao.updateReadStatus(link, isRead)
    }

    suspend fun markAllAsRead() = withContext(Dispatchers.IO) {
        articleDao.markAllArticlesAsRead()
    }

    suspend fun markAllAsReadForFeed(feedId: Long) = withContext(Dispatchers.IO) {
        articleDao.markAllArticlesAsReadForFeed(feedId)
    }

    suspend fun importFeeds(content: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val items = OpmlParser.importFeeds(content)
            if (items.isEmpty()) {
                return@withContext Result.failure(Exception("No valid feeds found in provided content"))
            }

            var addedCount = 0
            for (item in items) {
                val existing = feedDao.getFeedByUrl(item.url)
                if (existing == null) {
                    val feedEntity = FeedEntity(
                        name = item.name,
                        url = item.url,
                        category = item.category.ifBlank { "General" },
                        siteUrl = item.siteUrl,
                        lastUpdated = 0L
                    )
                    val id = feedDao.insertFeed(feedEntity)
                    if (id > 0) {
                        addedCount++
                    }
                }
            }

            // Trigger background refresh for newly added feeds
            if (addedCount > 0) {
                refreshAllFeeds()
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportFeeds(format: FeedExportFormat): String = withContext(Dispatchers.IO) {
        val feeds = feedDao.getAllFeeds()
        OpmlParser.exportFeeds(feeds, format)
    }
}
