package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ArticleEntity
import com.example.ui.components.AddFeedDialog
import com.example.ui.components.AppDestination
import com.example.ui.components.AppNavigationDrawer
import com.example.ui.components.ArticleDetailSheet
import com.example.ui.components.ImportExportDialog
import com.example.ui.components.InAppBrowserModal
import com.example.ui.screens.FeedArticlesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ManageFeedsScreen
import com.example.ui.screens.SavedArticlesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FeedViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FeedViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            MyApplicationTheme(settings = settings) {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                // State from ViewModel
                val allFeeds by viewModel.allFeeds.collectAsStateWithLifecycle()
                val allArticles by viewModel.allArticles.collectAsStateWithLifecycle()
                val bookmarkedArticles by viewModel.bookmarkedArticles.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
                val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
                val homeFeedArticles by viewModel.homeFeedArticles.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                val isAddingFeed by viewModel.isAddingFeed.collectAsStateWithLifecycle()
                val addFeedError by viewModel.addFeedError.collectAsStateWithLifecycle()
                val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
                val selectedArticle by viewModel.selectedArticleForSheet.collectAsStateWithLifecycle()
                val inAppBrowserUrl by viewModel.inAppBrowserUrl.collectAsStateWithLifecycle()
                val inAppBrowserTitle by viewModel.inAppBrowserTitle.collectAsStateWithLifecycle()
                val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
                val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

                // Dialog states
                var showAddFeedDialog by remember { mutableStateOf(false) }
                var showImportExportDialog by remember { mutableStateOf(false) }
                var initialImportExportTab by remember { mutableIntStateOf(0) }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                fun handleOpenLink(url: String, title: String = "") {
                    if (settings.openInAppBrowser) {
                        viewModel.openInAppBrowser(url, title)
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Cannot open link: ${e.message}")
                            }
                        }
                    }
                }

                // Observe snackbar messages
                LaunchedEffect(Unit) {
                    viewModel.userMessage.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                // Handle back press to close drawer if open
                BackHandler(enabled = drawerState.isOpen) {
                    coroutineScope.launch { drawerState.close() }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppNavigationDrawer(
                            currentDestination = currentDestination,
                            feeds = allFeeds,
                            categories = categories,
                            savedCount = bookmarkedArticles.size,
                            unreadCount = unreadCount,
                            isRefreshing = isRefreshing,
                            onNavigate = { destination ->
                                viewModel.navigateTo(destination)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onRefreshAll = {
                                viewModel.refreshAllFeeds()
                                coroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        when (val destination = currentDestination) {
                            is AppDestination.Home -> {
                                HomeScreen(
                                    feedArticles = homeFeedArticles,
                                    allFeeds = allFeeds,
                                    categories = categories,
                                    selectedCategory = null,
                                    searchQuery = searchQuery,
                                    unreadCount = unreadCount,
                                    isRefreshing = isRefreshing,
                                    showUnreadOnly = settings.showUnreadOnly,
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onCategorySelect = { cat ->
                                        if (cat == null) viewModel.navigateTo(AppDestination.Home)
                                        else viewModel.navigateTo(AppDestination.CategoryFilter(cat))
                                    },
                                    onToggleUnreadOnly = { unreadOnly ->
                                        viewModel.updateSettings(settings.copy(showUnreadOnly = unreadOnly))
                                    },
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onRefresh = { viewModel.refreshAllFeeds() },
                                    onAddFeedClick = { showAddFeedDialog = true },
                                    onImportClick = {
                                        initialImportExportTab = 0
                                        showImportExportDialog = true
                                    },
                                    onSettingsClick = { viewModel.navigateTo(AppDestination.Settings) },
                                    onMarkAllAsRead = { viewModel.markAllAsRead() },
                                    onArticleClick = { article ->
                                        viewModel.openArticleSheet(article)
                                    },
                                    onBookmarkToggle = { viewModel.toggleBookmark(it) },
                                    onToggleRead = { article ->
                                        viewModel.markArticleAsRead(article.link, !article.isRead)
                                    },
                                    onOpenBrowser = { url ->
                                        handleOpenLink(url)
                                    },
                                    onViewAllForFeed = { feedId ->
                                        viewModel.navigateTo(AppDestination.FeedDetail(feedId))
                                    }
                                )
                            }

                            is AppDestination.CategoryFilter -> {
                                HomeScreen(
                                    feedArticles = homeFeedArticles,
                                    allFeeds = allFeeds,
                                    categories = categories,
                                    selectedCategory = destination.category,
                                    searchQuery = searchQuery,
                                    unreadCount = unreadCount,
                                    isRefreshing = isRefreshing,
                                    showUnreadOnly = settings.showUnreadOnly,
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onCategorySelect = { cat ->
                                        if (cat == null) viewModel.navigateTo(AppDestination.Home)
                                        else viewModel.navigateTo(AppDestination.CategoryFilter(cat))
                                    },
                                    onToggleUnreadOnly = { unreadOnly ->
                                        viewModel.updateSettings(settings.copy(showUnreadOnly = unreadOnly))
                                    },
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onRefresh = { viewModel.refreshAllFeeds() },
                                    onAddFeedClick = { showAddFeedDialog = true },
                                    onImportClick = {
                                        initialImportExportTab = 0
                                        showImportExportDialog = true
                                    },
                                    onSettingsClick = { viewModel.navigateTo(AppDestination.Settings) },
                                    onMarkAllAsRead = { viewModel.markAllAsRead() },
                                    onArticleClick = { article ->
                                        viewModel.openArticleSheet(article)
                                    },
                                    onBookmarkToggle = { viewModel.toggleBookmark(it) },
                                    onToggleRead = { article ->
                                        viewModel.markArticleAsRead(article.link, !article.isRead)
                                    },
                                    onOpenBrowser = { url ->
                                        handleOpenLink(url)
                                    },
                                    onViewAllForFeed = { feedId ->
                                        viewModel.navigateTo(AppDestination.FeedDetail(feedId))
                                    }
                                )
                            }

                            is AppDestination.FeedDetail -> {
                                val selectedFeed = allFeeds.find { it.id == destination.feedId }
                                if (selectedFeed != null) {
                                    val feedArticles = allArticles.filter { it.feedId == destination.feedId }
                                    FeedArticlesScreen(
                                        feed = selectedFeed,
                                        articles = feedArticles,
                                        isRefreshing = isRefreshing,
                                        onBack = { viewModel.navigateTo(AppDestination.Home) },
                                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                        onRefreshFeed = { viewModel.refreshSingleFeed(destination.feedId) },
                                        onMarkAllAsRead = { viewModel.markAllAsReadForFeed(destination.feedId) },
                                        onArticleClick = { article ->
                                            viewModel.openArticleSheet(article)
                                        },
                                        onBookmarkToggle = { viewModel.toggleBookmark(it) },
                                        onToggleRead = { article ->
                                            viewModel.markArticleAsRead(article.link, !article.isRead)
                                        },
                                        onOpenBrowser = { url ->
                                            handleOpenLink(url, selectedFeed.name)
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        viewModel.navigateTo(AppDestination.Home)
                                    }
                                }
                            }

                            is AppDestination.SavedArticles -> {
                                SavedArticlesScreen(
                                    savedArticles = bookmarkedArticles,
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onBack = { viewModel.navigateTo(AppDestination.Home) },
                                    onArticleClick = { article ->
                                        viewModel.openArticleSheet(article)
                                    },
                                    onBookmarkToggle = { viewModel.toggleBookmark(it) },
                                    onToggleRead = { article ->
                                        viewModel.markArticleAsRead(article.link, !article.isRead)
                                    },
                                    onOpenBrowser = { url ->
                                        handleOpenLink(url)
                                    },
                                    onViewAllForFeed = { feedId ->
                                        viewModel.navigateTo(AppDestination.FeedDetail(feedId))
                                    }
                                )
                            }

                            is AppDestination.ManageFeeds -> {
                                ManageFeedsScreen(
                                    feeds = allFeeds,
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onBack = { viewModel.navigateTo(AppDestination.Home) },
                                    onAddFeedClick = { showAddFeedDialog = true },
                                    onImportClick = {
                                        initialImportExportTab = 0
                                        showImportExportDialog = true
                                    },
                                    onExportClick = {
                                        initialImportExportTab = 1
                                        showImportExportDialog = true
                                    },
                                    onToggleFeedEnabled = { feed ->
                                        viewModel.updateFeed(feed.copy(isEnabled = !feed.isEnabled))
                                    },
                                    onDeleteFeed = { feed ->
                                        viewModel.deleteFeed(feed)
                                    },
                                    onRefreshFeed = { feedId ->
                                        viewModel.refreshSingleFeed(feedId)
                                    },
                                    onViewFeedArticles = { feedId ->
                                        viewModel.navigateTo(AppDestination.FeedDetail(feedId))
                                    },
                                    onUpdateFeed = { feed, oldUrl ->
                                        viewModel.updateFeed(feed, oldUrl)
                                    },
                                    onAddNepaliPortals = {
                                        viewModel.addAllNepaliPortals()
                                    }
                                )
                            }

                            is AppDestination.Settings -> {
                                SettingsScreen(
                                    settings = settings,
                                    totalFeeds = allFeeds.size,
                                    unreadCount = unreadCount,
                                    currentUser = currentUser,
                                    isSyncing = isSyncing,
                                    lastSyncTime = lastSyncTime,
                                    syncMessage = syncMessage,
                                    onSettingsChange = { newSettings ->
                                        viewModel.updateSettings(newSettings)
                                    },
                                    onMarkAllAsRead = { viewModel.markAllAsRead() },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onBack = { viewModel.navigateTo(AppDestination.Home) },
                                    onImportExportClick = {
                                        initialImportExportTab = 0
                                        showImportExportDialog = true
                                    },
                                    onSignInWithGoogle = { viewModel.signInWithGoogle(this@MainActivity) },
                                    onSignOut = { viewModel.signOut() },
                                    onSyncNow = { viewModel.syncFeeds() }
                                )
                            }
                        }
                    }

                    // Reader Sheet
                    if (selectedArticle != null) {
                        ArticleDetailSheet(
                            article = selectedArticle,
                            sheetState = sheetState,
                            onDismiss = { viewModel.closeArticleSheet() },
                            onBookmarkToggle = {
                                selectedArticle?.let { viewModel.toggleBookmark(it) }
                            },
                            onOpenInAppBrowser = { url ->
                                handleOpenLink(url, selectedArticle?.title ?: "")
                            }
                        )
                    }

                    // In-App Browser Modal
                    inAppBrowserUrl?.let { url ->
                        InAppBrowserModal(
                            url = url,
                            title = inAppBrowserTitle,
                            onDismiss = { viewModel.closeInAppBrowser() }
                        )
                    }

                    // Add Feed Dialog
                    if (showAddFeedDialog) {
                        AddFeedDialog(
                            isLoading = isAddingFeed,
                            errorMessage = addFeedError,
                            onDismiss = { showAddFeedDialog = false },
                            onAddFeed = { name, url, category ->
                                viewModel.addFeed(name, url, category) {
                                    showAddFeedDialog = false
                                }
                            }
                        )
                    }

                    // Import / Export Dialog
                    if (showImportExportDialog) {
                        ImportExportDialog(
                            initialTab = initialImportExportTab,
                            isImporting = isImporting,
                            onDismiss = { showImportExportDialog = false },
                            onImportText = { text ->
                                viewModel.importFeeds(text) {
                                    showImportExportDialog = false
                                }
                            },
                            onExportRequested = { format ->
                                viewModel.exportFeeds(format)
                            }
                        )
                    }
                }
            }
        }
    }
}
