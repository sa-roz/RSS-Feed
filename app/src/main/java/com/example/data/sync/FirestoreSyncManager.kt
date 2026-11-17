package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.FeedDao
import com.example.data.local.FeedEntity
import com.example.data.repository.FeedRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class SyncResult(
    val uploadedCount: Int,
    val downloadedCount: Int,
    val totalFeeds: Int,
    val message: String
)

class FirestoreSyncManager(
    private val db: FirebaseFirestore,
    private val feedDao: FeedDao,
    private val feedRepository: FeedRepository
) {
    // Secondary constructor resolving the mandatory custom database ID
    constructor(
        context: Context,
        feedDao: FeedDao,
        feedRepository: FeedRepository
    ) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        feedDao,
        feedRepository
    )

    private fun getDocIdForUrl(url: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(url.trim().lowercase().toByteArray())
        return "feed_" + digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Backs up and synchronizes local feeds with the cloud user collection.
     */
    suspend fun syncFeeds(userId: String): Result<SyncResult> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("User ID must not be blank"))
        }

        try {
            val userFeedsCollection = db.collection("users")
                .document(userId)
                .collection("feeds")

            // 1. Fetch remote feeds from Firestore
            val remoteSnapshot = userFeedsCollection.get().await()
            val remoteFeedsMap = mutableMapOf<String, Map<String, Any?>>()
            for (doc in remoteSnapshot.documents) {
                val url = doc.getString("url")
                if (!url.isNullOrBlank()) {
                    remoteFeedsMap[url.trim().lowercase()] = doc.data ?: emptyMap()
                }
            }

            // 2. Fetch local feeds from Room
            val localFeeds = feedDao.getAllFeeds()
            val localUrlsMap = localFeeds.associateBy { it.url.trim().lowercase() }

            var uploadedCount = 0
            var downloadedCount = 0

            // 3. Upload local feeds to cloud if not present or needs sync
            for (localFeed in localFeeds) {
                val docId = getDocIdForUrl(localFeed.url)
                val payload = mapOf(
                    "id" to docId,
                    "name" to localFeed.name,
                    "url" to localFeed.url,
                    "category" to localFeed.category.ifBlank { "General" },
                    "siteUrl" to (localFeed.siteUrl ?: ""),
                    "isEnabled" to localFeed.isEnabled,
                    "userId" to userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userFeedsCollection.document(docId).set(payload).await()
                uploadedCount++
            }

            // 4. Download remote feeds missing from local database
            for ((remoteUrlKey, data) in remoteFeedsMap) {
                if (!localUrlsMap.containsKey(remoteUrlKey)) {
                    val name = data["name"] as? String ?: "Feed"
                    val url = data["url"] as? String ?: continue
                    val category = data["category"] as? String ?: "General"
                    val siteUrl = data["siteUrl"] as? String
                    val isEnabled = data["isEnabled"] as? Boolean ?: true

                    val newEntity = FeedEntity(
                        name = name,
                        url = url,
                        category = category,
                        siteUrl = siteUrl,
                        isEnabled = isEnabled,
                        lastUpdated = 0L
                    )
                    val newId = feedDao.insertFeed(newEntity)
                    if (newId > 0) {
                        downloadedCount++
                        // Asynchronously trigger refresh for new feed
                        try {
                            feedRepository.refreshFeed(newId)
                        } catch (e: Exception) {
                            Log.w("Sync", "Initial refresh failed for synced feed $name: ${e.message}")
                        }
                    }
                }
            }

            val total = feedDao.getFeedCount()
            Result.success(
                SyncResult(
                    uploadedCount = uploadedCount,
                    downloadedCount = downloadedCount,
                    totalFeeds = total,
                    message = "Synced $total feeds ($uploadedCount uploaded, $downloadedCount downloaded)"
                )
            )
        } catch (e: Exception) {
            Log.e("Sync", "Cloud sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads or updates a single feed in Firestore cloud storage.
     */
    suspend fun saveFeedToCloud(userId: String, feed: FeedEntity) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docId = getDocIdForUrl(feed.url)
            val payload = mapOf(
                "id" to docId,
                "name" to feed.name,
                "url" to feed.url,
                "category" to feed.category.ifBlank { "General" },
                "siteUrl" to (feed.siteUrl ?: ""),
                "isEnabled" to feed.isEnabled,
                "userId" to userId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users")
                .document(userId)
                .collection("feeds")
                .document(docId)
                .set(payload)
                .await()
        } catch (e: Exception) {
            Log.e("Sync", "Failed to save feed to cloud: ${e.message}", e)
        }
    }

    /**
     * Deletes a feed from Firestore when removed by the user.
     */
    suspend fun deleteFeedFromCloud(userId: String, feedUrl: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docId = getDocIdForUrl(feedUrl)
            db.collection("users")
                .document(userId)
                .collection("feeds")
                .document(docId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("Sync", "Failed to delete feed from cloud: ${e.message}", e)
        }
    }
}
