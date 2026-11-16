package com.tsumo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.tsumo.app.data.TsumoTab
import com.tsumo.app.ui.components.CanvasToast
import com.tsumo.app.ui.components.TsumoBottomBar
import com.tsumo.app.ui.components.TsumoTopBar
import com.tsumo.app.ui.screens.AddFeedSheetOverlay
import com.tsumo.app.ui.screens.ArticleDrawerOverlay
import com.tsumo.app.ui.screens.FeedsScreen
import com.tsumo.app.ui.screens.LibraryScreen
import com.tsumo.app.ui.screens.ReadScreen
import com.tsumo.app.ui.screens.ReaderViewOverlay
import com.tsumo.app.ui.screens.SearchOverlay
import com.tsumo.app.ui.screens.SettingsSheetOverlay
import com.tsumo.app.ui.theme.TsumoColors
import com.tsumo.app.ui.theme.TsumoTheme

class MainActivity : ComponentActivity() {
    private val vm: TsumoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val surfaceArgb = TsumoColors.Surface.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(surfaceArgb, surfaceArgb),
            navigationBarStyle = SystemBarStyle.light(surfaceArgb, surfaceArgb),
        )

        vm.handleIncomingShareIntent(intent)

        setContent {
            TsumoTheme {
                val state by vm.uiState.collectAsState()

                BackHandler(
                    enabled = state.readerOpen ||
                        state.selectedArticle != null ||
                        state.addFeedOpen ||
                        state.settingsOpen ||
                        state.searchActive
                ) {
                    when {
                        state.readerOpen -> vm.closeReader()
                        state.settingsOpen -> vm.closeSettingsSheet()
                        state.addFeedOpen -> vm.closeAddFeedSheet()
                        state.selectedArticle != null -> vm.closeArticleDrawer()
                        state.searchActive -> vm.setSearchActive(false)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TsumoColors.Surface),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TsumoTopBar(
                            currentTab = state.currentTab,
                            unreadCount = state.totalUnread,
                            onSearchClick = { vm.setSearchActive(true) },
                            onAddFeedClick = { vm.openAddFeedSheet() },
                            onSettingsClick = vm::openSettingsSheet,
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (state.currentTab) {
                                TsumoTab.READ -> ReadScreen(
                                    state = state,
                                    onSelectChannel = vm::selectChannel,
                                    onStepChannel = vm::stepChannel,
                                    onRefresh = { vm.refreshFeeds(silent = false) },
                                    onMarkAllRead = vm::markCurrentChannelRead,
                                    onOpenSearch = { vm.setSearchActive(true) },
                                    onArticleTap = vm::onArticleTapped,
                                    onArticleBrowser = vm::openInCustomTab,
                                    onToggleArchive = vm::toggleArchive,
                                    onToggleRead = vm::toggleRead,
                                    onShareArticle = vm::shareArticle,
                                )

                                TsumoTab.FEEDS -> FeedsScreen(
                                    state = state,
                                    onFilterChange = vm::setFeedsFilterQuery,
                                    onOpenFeed = vm::openFeedInRead,
                                    onOpenAddFeed = { vm.openAddFeedSheet() },
                                    onRemoveFeed = vm::removeFeedWithUndo,
                                    onUndoRemove = vm::undoRemoveFeed,
                                    onImportContent = vm::importFeedsContent,
                                    onExportFeeds = vm::exportFeeds,
                                )

                                TsumoTab.LIBRARY -> LibraryScreen(
                                    state = state,
                                    onSelectSection = vm::setLibrarySection,
                                    onArticleTap = vm::onArticleTapped,
                                    onArticleBrowser = vm::openInCustomTab,
                                    onToggleArchive = vm::toggleArchive,
                                    onShareArticle = vm::shareArticle,
                                    onHistoryQueryChange = vm::setHistoryQuery,
                                    onRemoveHistoryEntry = vm::removeHistoryEntry,
                                    onClearHistory = vm::clearHistory,
                                    onOpenLocalExport = vm::openLocalExport,
                                    onShareLocalExport = vm::shareLocalExport,
                                    onSendExportViaEmail = vm::sendExportViaEmail,
                                    onRemoveLocalExport = vm::removeLocalExport,
                                )
                            }
                        }

                        TsumoBottomBar(
                            currentTab = state.currentTab,
                            onSelectTab = vm::selectTab,
                        )
                    }

                    SearchOverlay(
                        state = state,
                        onDismiss = { vm.setSearchActive(false) },
                        onQueryChange = vm::setSearchQuery,
                        onScopeChange = vm::setSearchScope,
                        onOpenFeed = vm::openFeedInRead,
                        onArticleTap = vm::onArticleTapped,
                        onArticleBrowser = vm::openInCustomTab,
                        onToggleArchive = vm::toggleArchive,
                        onShareArticle = vm::shareArticle,
                    )

                    ArticleDrawerOverlay(
                        state = state,
                        onDismiss = vm::closeArticleDrawer,
                        onOpenReader = vm::openInReader,
                        onOpenCustomTab = vm::openInCustomTab,
                        onOpenExternalBrowser = vm::openInExternalBrowser,
                        onShare = vm::shareArticle,
                        onToggleArchive = vm::toggleArchive,
                        onToggleRead = vm::toggleRead,
                        onExportMarkdown = vm::exportMarkdown,
                        onExportPdf = vm::exportPdf,
                    )

                    ReaderViewOverlay(
                        state = state,
                        onClose = vm.let { { it.closeReader() } },
                        onOpenCustomTab = vm::openInCustomTab,
                        onShare = vm::shareArticle,
                        onToggleArchive = vm::toggleArchive,
                        onExportMarkdown = vm::exportMarkdown,
                        onExportPdf = vm::exportPdf,
                        onAdjustTextScale = vm::adjustReaderTextScale,
                    )

                    AddFeedSheetOverlay(
                        state = state,
                        onDismiss = vm::closeAddFeedSheet,
                        onUrlChange = vm::setAddFeedUrl,
                        onTagsChange = vm::setAddFeedTags,
                        onCustomTitleChange = vm::setAddFeedCustomTitle,
                        onDiscover = vm::discoverFeed,
                        onConfirmAdd = vm::confirmAddFeed,
                    )

                    SettingsSheetOverlay(
                        state = state,
                        onDismiss = vm::closeSettingsSheet,
                        onUpdateReaderSettings = vm::updateReaderSettings,
                        onSaveDelivery = vm::saveDeliveryConfig,
                    )

                    CanvasToast(
                        message = state.toastMessage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 82.dp),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleIncomingShareIntent(intent)
    }
}
