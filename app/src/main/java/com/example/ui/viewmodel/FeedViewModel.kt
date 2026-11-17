package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ArticleEntity
import com.example.data.local.CleanFeedDatabase
import com.example.data.local.FeedEntity
import com.example.data.parser.FeedExportFormat
import android.app.Activity
import com.example.auth.GoogleAuthClient
import com.example.data.repository.FeedRepository
import com.example.data.repository.NepaliPortalsCatalog
import com.example.data.sync.FirestoreSyncManager
import com.example.data.sync.SyncResult
import com.google.firebase.auth.FirebaseUser
import com.example.ui.components.AppDestination
import com.example.ui.theme.AppSettings
import com.example.ui.theme.SettingsStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeViewMode {
    GROUPED_BY_FEED,
    COMBINED_FEED
}

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CleanFeedDatabase.getDatabase(application)
    private val repository = FeedRepository(database.feedDao(), database.articleDao(), application)
    val googleAuthClient = GoogleAuthClient(application)
    val syncManager = FirestoreSyncManager(application, database.feedDao(), repository)

    val currentUser: StateFlow<FirebaseUser?> = googleAuthClient.authStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), googleAuthClient.currentUser)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val allFeeds: StateFlow<List<FeedEntity>> = repository.allFeeds
        .map { feeds -> NepaliPortalsCatalog.sortFeeds(feeds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allArticles: StateFlow<List<ArticleEntity>> = repository.allArticles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedArticles: StateFlow<List<ArticleEntity>> = repository.bookmarkedArticles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = repository.categories
        .map { cats -> NepaliPortalsCatalog.sortCategories(cats) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = repository.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _settings = MutableStateFlow(SettingsStorage.loadSettings(application))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _currentDestination = MutableStateFlow<AppDestination>(AppDestination.Home)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isAddingFeed = MutableStateFlow(false)
    val isAddingFeed: StateFlow<Boolean> = _isAddingFeed.asStateFlow()

    private val _addFeedError = MutableStateFlow<String?>(null)
    val addFeedError: StateFlow<String?> = _addFeedError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val _selectedArticleForSheet = MutableStateFlow<ArticleEntity?>(null)
    val selectedArticleForSheet: StateFlow<ArticleEntity?> = _selectedArticleForSheet.asStateFlow()

    private val _inAppBrowserUrl = MutableStateFlow<String?>(null)
    val inAppBrowserUrl: StateFlow<String?> = _inAppBrowserUrl.asStateFlow()

    private val _inAppBrowserTitle = MutableStateFlow("")
    val inAppBrowserTitle: StateFlow<String> = _inAppBrowserTitle.asStateFlow()

    private val _homeViewMode = MutableStateFlow(HomeViewMode.GROUPED_BY_FEED)
    val homeViewMode: StateFlow<HomeViewMode> = _homeViewMode.asStateFlow()

    /**
     * Home page articles: strictly up to latest 5 news items from each feed!
     * Respects showUnreadOnly setting and search query.
     */
    val homeFeedArticles: StateFlow<Map<FeedEntity, List<ArticleEntity>>> = combine(
        allFeeds,
        allArticles,
        searchQuery,
        settings
    ) { feeds, articles, query, currentSettings ->
        val articlesByFeed = articles.groupBy { it.feedId }
        val result = mutableMapOf<FeedEntity, List<ArticleEntity>>()

        val orderedFeeds = NepaliPortalsCatalog.sortFeeds(feeds.filter { it.isEnabled })
        for (feed in orderedFeeds) {
            var feedArticles = articlesByFeed[feed.id] ?: emptyList()
            if (currentSettings.showUnreadOnly) {
                feedArticles = feedArticles.filter { !it.isRead }
            }

            val filtered = if (query.isBlank()) {
                feedArticles.take(5) // Latest 5 only on home page!
            } else {
                feedArticles.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.summary.contains(query, ignoreCase = true)
                }.take(5)
            }
            if (filtered.isNotEmpty() || query.isBlank()) {
                result[feed] = filtered
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            repository.initializeDefaultFeedsIfEmpty()
        }
        googleAuthClient.attemptAutoSignIn(
            scope = viewModelScope,
            onAuthSuccess = { user ->
                syncFeeds()
            }
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        SettingsStorage.saveSettings(getApplication(), newSettings)
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setHomeViewMode(mode: HomeViewMode) {
        _homeViewMode.value = mode
    }

    fun openArticleSheet(article: ArticleEntity) {
        _selectedArticleForSheet.value = article
        // Mark as read when opening reader
        markArticleAsRead(article.link, true)
    }

    fun closeArticleSheet() {
        _selectedArticleForSheet.value = null
    }

    fun openInAppBrowser(url: String, title: String) {
        _inAppBrowserUrl.value = url
        _inAppBrowserTitle.value = title
        markArticleAsRead(url, true)
    }

    fun closeInAppBrowser() {
        _inAppBrowserUrl.value = null
        _inAppBrowserTitle.value = ""
    }

    fun markArticleAsRead(link: String, isRead: Boolean = true) {
        viewModelScope.launch {
            repository.setArticleRead(link, isRead)
            // Update sheet state if current
            if (_selectedArticleForSheet.value?.link == link) {
                _selectedArticleForSheet.value = _selectedArticleForSheet.value?.copy(isRead = isRead)
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
            _userMessage.emit("Marked all articles as read")
        }
    }

    fun markAllAsReadForFeed(feedId: Long) {
        viewModelScope.launch {
            repository.markAllAsReadForFeed(feedId)
            _userMessage.emit("Marked feed articles as read")
        }
    }

    fun refreshAllFeeds() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.refreshAllFeeds()
            _isRefreshing.value = false
            if (result.isSuccess) {
                _userMessage.emit("Updated ${result.getOrNull() ?: 0} feeds")
            } else {
                _userMessage.emit("Failed to refresh feeds")
            }
        }
    }

    fun refreshSingleFeed(feedId: Long) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.refreshFeed(feedId)
            _isRefreshing.value = false
            if (result.isSuccess) {
                _userMessage.emit("Feed updated successfully")
            } else {
                _userMessage.emit("Error updating feed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun addFeed(name: String, url: String, category: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAddingFeed.value = true
            _addFeedError.value = null
            val result = repository.addFeed(name, url, category)
            _isAddingFeed.value = false
            if (result.isSuccess) {
                val inserted = result.getOrNull()
                val user = googleAuthClient.currentUser
                if (user != null && inserted != null) {
                    syncManager.saveFeedToCloud(user.uid, inserted)
                }
                _userMessage.emit("Added feed: ${inserted?.name}")
                onSuccess()
            } else {
                _addFeedError.value = result.exceptionOrNull()?.message ?: "Failed to find or add feed"
            }
        }
    }

    fun addAllNepaliPortals() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.addMissingNepaliPortals()
            _isRefreshing.value = false
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                if (count > 0) {
                    val user = googleAuthClient.currentUser
                    if (user != null) {
                        syncFeeds()
                    }
                    _userMessage.emit("Added $count Nepali news portals & fetched latest articles")
                } else {
                    _userMessage.emit("All Nepali news portals are already subscribed")
                }
            } else {
                _userMessage.emit("Could not add Nepali portals: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun updateFeed(feed: FeedEntity, oldUrl: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateFeed(feed, oldUrl)
            val user = googleAuthClient.currentUser
            if (user != null) {
                syncManager.saveFeedToCloud(user.uid, feed)
                if (oldUrl != null && oldUrl.trim() != feed.url.trim()) {
                    syncManager.deleteFeedFromCloud(user.uid, oldUrl)
                }
            }
            _userMessage.emit("Updated '${feed.name}'")
            onSuccess()
        }
    }

    fun deleteFeed(feed: FeedEntity) {
        viewModelScope.launch {
            repository.deleteFeed(feed)
            val user = googleAuthClient.currentUser
            if (user != null) {
                syncManager.deleteFeedFromCloud(user.uid, feed.url)
            }
            if (_currentDestination.value is AppDestination.FeedDetail &&
                (_currentDestination.value as AppDestination.FeedDetail).feedId == feed.id) {
                _currentDestination.value = AppDestination.Home
            }
            _userMessage.emit("Deleted ${feed.name}")
        }
    }

    fun signInWithGoogle(activity: Activity) {
        googleAuthClient.signInWithGoogle(
            activity = activity,
            scope = viewModelScope,
            onSuccess = { user ->
                viewModelScope.launch {
                    _userMessage.emit("Signed in as ${user.displayName ?: user.email}")
                    syncFeeds()
                }
            },
            onError = { error ->
                viewModelScope.launch {
                    _userMessage.emit("Sign in error: $error")
                }
            }
        )
    }

    fun signOut() {
        googleAuthClient.signOut(viewModelScope) {
            viewModelScope.launch {
                _userMessage.emit("Signed out of Google account")
                _syncMessage.value = null
            }
        }
    }

    fun syncFeeds() {
        val user = googleAuthClient.currentUser
        if (user == null) {
            viewModelScope.launch {
                _userMessage.emit("Sign in with Google to sync feeds")
            }
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Synchronizing feeds..."
            val result = syncManager.syncFeeds(user.uid)
            _isSyncing.value = false
            if (result.isSuccess) {
                val syncResult = result.getOrNull()
                _lastSyncTime.value = System.currentTimeMillis()
                _syncMessage.value = syncResult?.message ?: "Sync complete"
                _userMessage.emit(syncResult?.message ?: "Feeds synced successfully")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Sync failed"
                _syncMessage.value = "Sync failed: $err"
                _userMessage.emit("Sync failed: $err")
            }
        }
    }

    fun toggleBookmark(article: ArticleEntity) {
        viewModelScope.launch {
            val newStatus = !article.isBookmarked
            repository.setBookmark(article.link, newStatus)
            if (_selectedArticleForSheet.value?.link == article.link) {
                _selectedArticleForSheet.value = article.copy(isBookmarked = newStatus)
            }
            val msg = if (newStatus) "Saved to bookmarks" else "Removed from bookmarks"
            _userMessage.emit(msg)
        }
    }

    fun importFeeds(content: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = repository.importFeeds(content)
            _isImporting.value = false
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _userMessage.emit("Successfully imported $count new feeds")
                onFinished()
            } else {
                _userMessage.emit("Import failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    suspend fun exportFeeds(format: FeedExportFormat): String {
        return repository.exportFeeds(format)
    }
}
