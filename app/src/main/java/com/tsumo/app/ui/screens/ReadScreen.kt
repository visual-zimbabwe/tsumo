package com.tsumo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.Article
import com.tsumo.app.data.ReadChannel
import com.tsumo.app.ui.components.ArticleListRow
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.TsumoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    state: TsumoUiState,
    onSelectChannel: (ReadChannel) -> Unit,
    onStepChannel: (Int) -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenSearch: () -> Unit,
    onArticleTap: (Article) -> Unit,
    onArticleBrowser: (Article) -> Unit,
    onToggleArchive: (Article) -> Unit,
    onToggleRead: (Article) -> Unit,
    onShareArticle: (Article) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railState = rememberLazyListState()
    val selectedIdx = state.channels.indexOfFirst { it.key == state.selectedChannelKey }.coerceAtLeast(0)

    LaunchedEffect(selectedIdx) {
        if (selectedIdx >= 0 && selectedIdx < state.channels.size) {
            railState.animateScrollToItem(selectedIdx)
        }
    }

    var dragAccum by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TsumoColors.Surface),
    ) {
        // Split-second search bar trigger + Refresh / Mark Read
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(TsumoColors.SurfaceContainerLow)
                    .border(0.5.dp, TsumoColors.Primary.copy(alpha = 0.14f))
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Search feeds & articles instantly…",
                    fontFamily = EBGaramondFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 15.sp,
                    color = TsumoColors.Secondary.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.padding(start = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.isRefreshing) "SYNCING" else "REFRESH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.0.sp,
                        color = TsumoColors.Primary,
                    ),
                    modifier = Modifier
                        .clickable(enabled = !state.isRefreshing, onClick = onRefresh)
                        .padding(vertical = 6.dp),
                )
                Text(
                    text = "READ ALL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.0.sp,
                        color = TsumoColors.Secondary,
                    ),
                    modifier = Modifier
                        .clickable(onClick = onMarkAllRead)
                        .padding(vertical = 6.dp),
                )
            }
        }

        // Feed & Tag navigation rail
        LazyRow(
            state = railState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.channels, key = { it.key }) { channel ->
                val selected = channel.key == state.selectedChannelKey
                val count = when (channel) {
                    ReadChannel.All, ReadChannel.Unread -> state.totalUnread
                    ReadChannel.Archived -> state.status.archivedCount
                    is ReadChannel.Tag -> state.tagUnreads[channel.tag] ?: 0
                    is ReadChannel.FeedChannel -> channel.feed.unreadCount
                }
                val labelText = if (count > 0) "${channel.label} ($count)" else channel.label
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) TsumoColors.Primary else TsumoColors.SurfaceContainerLow
                        )
                        .border(
                            0.5.dp,
                            if (selected) TsumoColors.Primary else TsumoColors.OutlineVariant,
                        )
                        .clickable { onSelectChannel(channel) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) TsumoColors.OnPrimary else TsumoColors.OnSurfaceVariant,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        if (state.isRefreshing || state.isLoadingArticles) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp),
                color = TsumoColors.Primary,
                trackColor = TsumoColors.SurfaceContainer,
            )
        } else {
            HairlineRule()
        }

        // Article list with pull-to-refresh & horizontal swipe to switch feeds
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(state.selectedChannelKey) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
                        onDragEnd = {
                            if (dragAccum > 140f) onStepChannel(-1)
                            else if (dragAccum < -140f) onStepChannel(1)
                            dragAccum = 0f
                        },
                    )
                },
        ) {
            if (state.articles.isEmpty() && !state.isRefreshing && !state.isLoadingArticles) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.selectedChannel is ReadChannel.Archived)
                                "No archived articles yet."
                            else
                                "No articles in ${state.selectedChannel.label}.",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TsumoColors.Primary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pull down to refresh feeds or swipe left/right to switch channels.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TsumoColors.OnSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.articles, key = { it.id }) { article ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    onToggleRead(article)
                                }
                                false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromEndToStart = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(TsumoColors.SurfaceContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = if (article.unread) "MARK READ" else "MARK UNREAD",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.2.sp,
                                            color = TsumoColors.Primary,
                                        ),
                                    )
                                }
                            },
                        ) {
                            Column(modifier = Modifier.background(TsumoColors.Surface)) {
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
    }
}
