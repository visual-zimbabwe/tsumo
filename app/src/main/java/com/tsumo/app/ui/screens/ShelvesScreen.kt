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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.BookItem
import com.tsumo.app.data.LocalShelfBook
import com.tsumo.app.ui.components.ArchivalCoverPlate
import com.tsumo.app.ui.components.FilterTabItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun ShelvesScreen(
    state: TsumoUiState,
    onSelectSection: (Int) -> Unit,
    onOpenLocalBook: (String) -> Unit,
    onShareLocalBook: (String) -> Unit,
    onSendToKindle: (LocalShelfBook) -> Unit,
    onRemoveLocalBook: (LocalShelfBook) -> Unit,
    onSelectBookmark: (BookItem) -> Unit,
    onRemoveBookmark: (BookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TsumoColors.Surface)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterTabItem(
                label = "Downloaded (${state.downloadedBooks.size})",
                selected = state.shelvesSection == 0,
                onClick = { onSelectSection(0) },
            )
            FilterTabItem(
                label = "Saved (${state.bookmarkedBooks.size})",
                selected = state.shelvesSection == 1,
                onClick = { onSelectSection(1) },
            )
        }

        if (state.shelvesSection == 0) {
            if (state.downloadedBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No editions on device",
                            fontFamily = EBGaramondFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 20.sp,
                            color = TsumoColors.Secondary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloaded books are stored locally and exported to Downloads/Tsumo",
                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Outline),
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.downloadedBooks,
                        key = { it.filePath },
                    ) { entry ->
                        val sending = state.sendingToKindleKey == entry.key
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenLocalBook(entry.filePath) }
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Box(modifier = Modifier.width(68.dp)) {
                                    ArchivalCoverPlate(
                                        coverUrl = entry.cover,
                                        title = entry.title,
                                        authors = entry.authors,
                                        extension = entry.extension,
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = entry.title,
                                        fontFamily = EBGaramondFamily,
                                        fontSize = 19.sp,
                                        lineHeight = 23.sp,
                                        color = TsumoColors.Primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (entry.authors.isNotBlank()) {
                                        Text(
                                            text = entry.authors,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TsumoColors.Secondary,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = listOf(entry.extension, entry.sizeLabel, entry.year)
                                            .filter { it.isNotBlank() && it != "—" }
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TsumoColors.Outline,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "OPEN",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TsumoColors.Primary,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            modifier = Modifier
                                                .clickable { onOpenLocalBook(entry.filePath) }
                                                .padding(vertical = 4.dp),
                                        )
                                        Text(
                                            text = if (sending) "SENDING…" else "KINDLE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TsumoColors.Secondary,
                                            ),
                                            modifier = Modifier
                                                .clickable(enabled = !sending) { onSendToKindle(entry) }
                                                .padding(vertical = 4.dp),
                                        )
                                        Text(
                                            text = "SHARE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TsumoColors.Secondary,
                                            ),
                                            modifier = Modifier
                                                .clickable { onShareLocalBook(entry.filePath) }
                                                .padding(vertical = 4.dp),
                                        )
                                        Text(
                                            text = "REMOVE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TsumoColors.Error.copy(alpha = 0.8f),
                                            ),
                                            modifier = Modifier
                                                .clickable { onRemoveLocalBook(entry) }
                                                .padding(vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            HairlineRule()
                        }
                    }
                }
            }
        } else {
            if (state.bookmarkedBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No saved entries on shelf",
                        fontFamily = EBGaramondFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 20.sp,
                        color = TsumoColors.Secondary,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.bookmarkedBooks,
                        key = { it.effectiveId.ifBlank { it.name } },
                    ) { book ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBookmark(book) }
                                .padding(vertical = 15.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = book.name,
                                fontFamily = EBGaramondFamily,
                                fontSize = 19.sp,
                                color = TsumoColors.Primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = listOf(book.authorsDisplay, book.yearDisplay, book.packageDisplay)
                                        .filter { it.isNotBlank() && it != "—" }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Secondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "REMOVE",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                                    modifier = Modifier
                                        .clickable { onRemoveBookmark(book) }
                                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                                )
                            }
                        }
                        HairlineRule()
                    }
                }
            }
        }
    }
}
