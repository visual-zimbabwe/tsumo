package com.tsumo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tsumo.app.data.TsumoTab
import com.tsumo.app.ui.components.CanvasToast
import com.tsumo.app.ui.components.TsumoBottomBar
import com.tsumo.app.ui.components.TsumoTopBar
import com.tsumo.app.ui.screens.AccountSheetOverlay
import com.tsumo.app.ui.screens.BookDrawerOverlay
import com.tsumo.app.ui.screens.CatalogScreen
import com.tsumo.app.ui.screens.DiscoverScreen
import com.tsumo.app.ui.screens.ShelvesScreen
import com.tsumo.app.ui.theme.TsumoColors
import com.tsumo.app.ui.theme.TsumoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val surfaceArgb = TsumoColors.Surface.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(surfaceArgb, surfaceArgb),
            navigationBarStyle = SystemBarStyle.light(surfaceArgb, surfaceArgb),
        )

        setContent {
            TsumoTheme {
                val vm: TsumoViewModel = viewModel()
                val state by vm.uiState.collectAsState()

                BackHandler(enabled = state.selectedBook != null || state.accountOpen) {
                    when {
                        state.accountOpen -> vm.closeAccountSheet()
                        state.selectedBook != null -> vm.closeBookDrawer()
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
                            loggedIn = state.status.loggedIn,
                            onAccountClick = vm::openAccountSheet,
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (state.currentTab) {
                                TsumoTab.DISCOVER -> DiscoverScreen(
                                    state = state,
                                    onQueryChange = vm::setSearchQuery,
                                    onSearch = vm::searchBooks,
                                    onSelectExt = vm::selectExtFilter,
                                    onUpdateAdvancedFilters = vm::updateAdvancedFilters,
                                    onSelectBook = vm::openBookDrawer,
                                    onOpenAccount = vm::openAccountSheet,
                                )

                                TsumoTab.CATALOG -> CatalogScreen(
                                    state = state,
                                    onSelectSection = vm::setCatalogSection,
                                    onLoadHistory = vm::loadHistory,
                                    onFullTextQueryChange = vm::setFullTextQuery,
                                    onTogglePhrase = vm::toggleFullTextPhrase,
                                    onSearchFullText = vm::searchFullText,
                                    onSelectBook = vm::openBookDrawer,
                                    onOpenAccount = vm::openAccountSheet,
                                )

                                TsumoTab.SHELVES -> ShelvesScreen(
                                    state = state,
                                    onSelectSection = vm::setShelvesSection,
                                    onOpenLocalBook = vm::openLocalBook,
                                    onShareLocalBook = vm::shareLocalBook,
                                    onSendToKindle = { entry -> vm.sendFileToKindle(entry.filePath, entry.key) },
                                    onRemoveLocalBook = vm::removeLocalBook,
                                    onSelectBookmark = vm::openBookDrawer,
                                    onRemoveBookmark = vm::toggleBookmark,
                                )
                            }
                        }

                        TsumoBottomBar(
                            currentTab = state.currentTab,
                            onSelectTab = vm::selectTab,
                        )
                    }

                    BookDrawerOverlay(
                        state = state,
                        onDismiss = vm::closeBookDrawer,
                        onDownload = vm::downloadSelectedBook,
                        onCancelDownload = vm::cancelActiveDownload,
                        onOpenLocal = vm::openLocalBook,
                        onToggleBookmark = vm::toggleBookmark,
                    )

                    AccountSheetOverlay(
                        state = state,
                        onDismiss = vm::closeAccountSheet,
                        onLogin = vm::login,
                        onImportCookies = vm::importCookies,
                        onLogout = vm::logout,
                        onSwitchDomain = vm::switchDomain,
                        onRefreshProfile = vm::refreshProfile,
                        onRunDoctor = vm::runDoctor,
                        onSaveKindle = vm::saveKindleConfig,
                    )

                    CanvasToast(
                        message = state.toastMessage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 86.dp),
                    )
                }
            }
        }
    }
}
