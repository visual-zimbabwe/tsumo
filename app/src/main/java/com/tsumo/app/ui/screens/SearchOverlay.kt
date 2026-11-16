package com.tsumo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.Article
import com.tsumo.app.data.Feed
import com.tsumo.app.ui.components.ArticleListRow
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.FilterTabItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun SearchOverlay(
    state: TsumoUiState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onOpenFeed: (Feed) -> Unit,
    onArticleTap: (Article) -> Unit,
    onArticleBrowser: (Article) -> Unit,
    onToggleArchive: (Article) -> Unit,
    onShareArticle: (Article) -> Unit,
) {
    AnimatedVisibility(
        visible = state.searchActive,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TsumoColors.Surface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    EditorialInputField(
                        value = state.searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = "Instant search across feeds & articles…",
                    )
                }
                Text(
                    text = "CLOSE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        color = TsumoColors.Secondary,
                    ),
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FilterTabItem(
                    label = "All",
                    selected = state.searchScope == "all",
                    onClick = { onScopeChange("all") },
                )
                FilterTabItem(
                    label = "Archived ★",
                    selected = state.searchScope == "archived",
                    onClick = { onScopeChange("archived") },
                )
                FilterTabItem(
                    label = "History",
                    selected = state.searchScope == "history",
                    onClick = { onScopeChange("history") },
                )
            }

            HairlineRule()

            if (state.searchFeeds.isNotEmpty()) {
                Text(
                    text = "MATCHING FEEDS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.3.sp,
                        color = TsumoColors.Outline,
                    ),
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.searchFeeds, key = { it.id }) { feed ->
                        Box(
                            modifier = Modifier
                                .background(TsumoColors.SurfaceContainerLow)
                                .border(0.5.dp, TsumoColors.Primary.copy(alpha = 0.2f))
                                .clickable { onOpenFeed(feed) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = if (feed.unreadCount > 0) "${feed.displayTitle} (${feed.unreadCount})" else feed.displayTitle,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = TsumoColors.Primary,
                                ),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                HairlineRule()
            }

            if (state.searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Type any keyword, author, tag, or feed title — indexed locally via SQLite FTS5.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TsumoColors.OnSurfaceVariant,
                    )
                }
            } else if (state.searchArticles.isEmpty() && state.searchFeeds.isEmpty() && !state.isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No matches for “${state.searchQuery}”.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TsumoColors.Primary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.searchArticles, key = { it.id }) { article ->
                        ArticleListRow(
                            article = article,
                            onClick = { onArticleTap(article) },
                            onOpenBrowser = { onArticleBrowser(article) },
                            onToggleArchive = { onToggleArchive(article) },
                            onShare = { onShareArticle(article) },
                        )
                        HairlineRule(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}
