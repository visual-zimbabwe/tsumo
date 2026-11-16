package com.tsumo.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.Article
import com.tsumo.app.data.HistoryEntry
import com.tsumo.app.data.LocalExportItem
import com.tsumo.app.ui.components.ArticleListRow
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.FilterTabItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.theme.TsumoColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    state: TsumoUiState,
    onSelectSection: (Int) -> Unit,
    onArticleTap: (Article) -> Unit,
    onArticleBrowser: (Article) -> Unit,
    onToggleArchive: (Article) -> Unit,
    onShareArticle: (Article) -> Unit,
    onHistoryQueryChange: (String) -> Unit,
    onRemoveHistoryEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onOpenLocalExport: (LocalExportItem) -> Unit,
    onShareLocalExport: (LocalExportItem) -> Unit,
    onSendExportViaEmail: (LocalExportItem) -> Unit,
    onRemoveLocalExport: (LocalExportItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TsumoColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FilterTabItem(
                label = "Saved ★ (${state.archivedArticles.size})",
                selected = state.librarySection == 0,
                onClick = { onSelectSection(0) },
            )
            FilterTabItem(
                label = "History (${state.historyEntries.size})",
                selected = state.librarySection == 1,
                onClick = { onSelectSection(1) },
            )
            FilterTabItem(
                label = "Files (${state.localExports.size})",
                selected = state.librarySection == 2,
                onClick = { onSelectSection(2) },
            )
        }
        HairlineRule()

        when (state.librarySection) {
            0 -> ArchivedSection(
                articles = state.archivedArticles,
                onArticleTap = onArticleTap,
                onArticleBrowser = onArticleBrowser,
                onToggleArchive = onToggleArchive,
                onShareArticle = onShareArticle,
            )

            1 -> HistorySection(
                entries = state.historyEntries,
                query = state.historyQuery,
                onQueryChange = onHistoryQueryChange,
                onOpenArticle = onArticleTap,
                onRemoveEntry = onRemoveHistoryEntry,
                onClearHistory = onClearHistory,
            )

            else -> LocalFilesSection(
                exports = state.localExports,
                emailSendingKey = state.emailSendingKey,
                onOpen = onOpenLocalExport,
                onShare = onShareLocalExport,
                onEmailDelivery = onSendExportViaEmail,
                onRemove = onRemoveLocalExport,
            )
        }
    }
}

@Composable
private fun ArchivedSection(
    articles: List<Article>,
    onArticleTap: (Article) -> Unit,
    onArticleBrowser: (Article) -> Unit,
    onToggleArchive: (Article) -> Unit,
    onShareArticle: (Article) -> Unit,
) {
    if (articles.isEmpty()) {
        EmptyLibraryMessage(
            title = "No archived articles yet",
            subtitle = "Tap ☆ on any article to save its full readable text offline.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(articles, key = { it.id }) { article ->
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

@Composable
private fun HistorySection(
    entries: List<HistoryEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenArticle: (Article) -> Unit,
    onRemoveEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
) {
    val grouped = remember(entries) { groupHistoryByDay(entries) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                EditorialInputField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search reading history…",
                )
            }
            if (entries.isNotEmpty()) {
                Text(
                    text = "CLEAR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.0.sp,
                        color = TsumoColors.Error,
                    ),
                    modifier = Modifier
                        .clickable(onClick = onClearHistory)
                        .padding(vertical = 6.dp),
                )
            }
        }

        if (entries.isEmpty()) {
            EmptyLibraryMessage(
                title = "No reading history",
                subtitle = "Articles opened in Reader, In-App Browser, or Phone Browser appear here.",
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            grouped.forEach { (bucket, list) ->
                item(key = "hdr_$bucket") {
                    Text(
                        text = bucket,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.4.sp,
                            color = TsumoColors.Secondary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TsumoColors.SurfaceContainerLow)
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                    )
                }
                items(list, key = { it.historyId }) { entry ->
                    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.US) }
                    val timeLabel = timeFmt.format(Date(entry.openedAt * 1000L))
                    val viaLabel = when (entry.via) {
                        "custom_tab" -> "IN-APP BROWSER"
                        "browser" -> "PHONE BROWSER"
                        else -> "READER"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArticle(entry.article) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${entry.article.feedTitle.uppercase()} · $viaLabel · $timeLabel",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 0.8.sp,
                                    color = TsumoColors.Outline,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.article.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "×",
                            fontSize = 20.sp,
                            color = TsumoColors.Outline,
                            modifier = Modifier
                                .clickable { onRemoveEntry(entry.historyId) }
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                    HairlineRule(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun LocalFilesSection(
    exports: List<LocalExportItem>,
    emailSendingKey: String?,
    onOpen: (LocalExportItem) -> Unit,
    onShare: (LocalExportItem) -> Unit,
    onEmailDelivery: (LocalExportItem) -> Unit,
    onRemove: (LocalExportItem) -> Unit,
) {
    if (exports.isEmpty()) {
        EmptyLibraryMessage(
            title = "No local exports yet",
            subtitle = "Export any article as .md or .pdf to Downloads/Tsumo from its drawer.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(exports, key = { it.filePath }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = listOf(item.extension, item.sizeLabel, item.dateLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.0.sp,
                            color = TsumoColors.Secondary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = "DOWNLOADS/TSUMO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            color = TsumoColors.Outline,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.source.isNotBlank()) {
                    Text(
                        text = item.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = TsumoColors.OnSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "OPEN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.0.sp,
                            color = TsumoColors.Primary,
                        ),
                        modifier = Modifier.clickable { onOpen(item) },
                    )
                    Text(
                        text = "SHARE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.0.sp,
                            color = TsumoColors.Secondary,
                        ),
                        modifier = Modifier.clickable { onShare(item) },
                    )
                    Text(
                        text = if (emailSendingKey == item.key) "SENDING…" else "EMAIL FILE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.0.sp,
                            color = TsumoColors.Secondary,
                        ),
                        modifier = Modifier.clickable(enabled = emailSendingKey == null) {
                            onEmailDelivery(item)
                        },
                    )
                    Text(
                        text = "DELETE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.0.sp,
                            color = TsumoColors.Error,
                        ),
                        modifier = Modifier.clickable { onRemove(item) },
                    )
                }
            }
            HairlineRule(modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun EmptyLibraryMessage(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TsumoColors.Primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TsumoColors.OnSurfaceVariant,
            )
        }
    }
}

private fun groupHistoryByDay(entries: List<HistoryEntry>): List<Pair<String, List<HistoryEntry>>> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startToday = cal.timeInMillis / 1000L
    val startYesterday = startToday - 86400L
    val startWeek = startToday - 6L * 86400L

    val buckets = linkedMapOf<String, MutableList<HistoryEntry>>()
    entries.forEach { entry ->
        val label = when {
            entry.openedAt >= startToday -> "TODAY"
            entry.openedAt >= startYesterday -> "YESTERDAY"
            entry.openedAt >= startWeek -> "THIS WEEK"
            else -> "EARLIER"
        }
        buckets.getOrPut(label) { mutableListOf() }.add(entry)
    }
    return buckets.entries.map { it.key to it.value }
}
