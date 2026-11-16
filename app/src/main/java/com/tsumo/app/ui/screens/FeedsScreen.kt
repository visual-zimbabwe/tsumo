package com.tsumo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.Feed
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun FeedsScreen(
    state: TsumoUiState,
    onFilterChange: (String) -> Unit,
    onOpenFeed: (Feed) -> Unit,
    onOpenAddFeed: () -> Unit,
    onRemoveFeed: (Feed) -> Unit,
    onUndoRemove: () -> Unit,
    onImportContent: (content: String, fileName: String) -> Unit,
    onExportFeeds: (asOpml: Boolean, onReady: (content: String, suggestedName: String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedTag by remember { mutableStateOf("") }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                val name = uri.lastPathSegment ?: "urls"
                if (content.isNotBlank()) onImportContent(content, name)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val allTags = remember(state.visibleFeeds) {
        state.visibleFeeds.flatMap { it.tags }.distinct().sorted()
    }

    val filteredFeeds = remember(state.visibleFeeds, state.feedsFilterQuery, selectedTag) {
        val q = state.feedsFilterQuery.trim().lowercase()
        state.visibleFeeds.filter { f ->
            val tagOk = selectedTag.isBlank() || f.tags.any { it.equals(selectedTag, ignoreCase = true) }
            val queryOk = q.isBlank() ||
                f.displayTitle.lowercase().contains(q) ||
                f.url.lowercase().contains(q) ||
                f.tags.any { it.lowercase().contains(q) }
            tagOk && queryOk
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TsumoColors.Surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                EditorialInputField(
                    value = state.feedsFilterQuery,
                    onValueChange = onFilterChange,
                    placeholder = "Filter ${state.visibleFeeds.size} feeds or tags…",
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(TsumoColors.Primary)
                            .clickable(onClick = onOpenAddFeed)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "+ ADD FEED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.1.sp,
                                color = TsumoColors.OnPrimary,
                            ),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "IMPORT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.0.sp,
                                color = TsumoColors.Secondary,
                            ),
                            modifier = Modifier
                                .clickable { importLauncher.launch(arrayOf("*/*")) }
                                .padding(vertical = 6.dp),
                        )
                        Text(
                            text = "EXPORT OPML",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.0.sp,
                                color = TsumoColors.Secondary,
                            ),
                            modifier = Modifier
                                .clickable {
                                    onExportFeeds(true) { content, name ->
                                        pendingExportContent = content
                                        exportLauncher.launch(name)
                                    }
                                }
                                .padding(vertical = 6.dp),
                        )
                        Text(
                            text = "URLS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.0.sp,
                                color = TsumoColors.Secondary,
                            ),
                            modifier = Modifier
                                .clickable {
                                    onExportFeeds(false) { content, name ->
                                        pendingExportContent = content
                                        exportLauncher.launch(name)
                                    }
                                }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }

            if (allTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        TagPill(
                            label = "All",
                            selected = selectedTag.isBlank(),
                            onClick = { selectedTag = "" },
                        )
                    }
                    items(allTags) { tag ->
                        TagPill(
                            label = "#$tag",
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) "" else tag },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            HairlineRule()

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 72.dp),
            ) {
                items(filteredFeeds, key = { it.id }) { feed ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onRemoveFeed(feed)
                                true
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(TsumoColors.ErrorContainer)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text(
                                    text = "REMOVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.2.sp,
                                        color = TsumoColors.OnErrorContainer,
                                    ),
                                )
                            }
                        },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TsumoColors.Surface)
                                .clickable { onOpenFeed(feed) }
                                .padding(horizontal = 20.dp, vertical = 13.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = feed.displayTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (feed.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (feed.unreadCount > 0) {
                                        Text(
                                            text = "${feed.unreadCount}",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = TsumoColors.Primary,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        )
                                    }
                                    Text(
                                        text = "REMOVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            letterSpacing = 0.8.sp,
                                            color = TsumoColors.Error,
                                        ),
                                        modifier = Modifier
                                            .clickable { onRemoveFeed(feed) }
                                            .padding(vertical = 2.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = buildString {
                                    if (feed.tags.isNotEmpty()) {
                                        append(feed.tags.joinToString(" · ") { "#$it" })
                                        append("  —  ")
                                    }
                                    append(feed.url)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TsumoColors.OnSurfaceVariant,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (feed.error.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Last sync error: ${feed.error}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Error),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    HairlineRule(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }

        if (state.undoFeed != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .background(TsumoColors.Primary)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Removed ${state.undoFeed.displayTitle}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.OnPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "UNDO",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        color = TsumoColors.OnPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier
                        .clickable(onClick = onUndoRemove)
                        .padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun TagPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(if (selected) TsumoColors.Primary else TsumoColors.SurfaceContainerLow)
            .border(0.5.dp, if (selected) TsumoColors.Primary else TsumoColors.OutlineVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) TsumoColors.OnPrimary else TsumoColors.OnSurfaceVariant,
            ),
        )
    }
}
