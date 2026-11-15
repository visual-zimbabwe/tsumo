package com.tsumo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.BookItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.components.SecondaryTypographicAction
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun BookDrawerOverlay(
    state: TsumoUiState,
    onDismiss: () -> Unit,
    onDownload: (sendToKindleAfter: Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenLocal: (String) -> Unit,
    onToggleBookmark: (BookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = state.selectedBook
    val visible = book != null

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TsumoColors.OnSurface.copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (book != null) {
                val localMatch = state.downloadedBooks.firstOrNull {
                    it.key == book.effectiveId && book.effectiveId.isNotBlank()
                }
                val isBookmarked = state.bookmarkedBooks.any {
                    it.effectiveId == book.effectiveId && it.name == book.name
                }
                val dl = state.activeDownload
                val isDownloading = dl != null && (dl.state == "fetching" || dl.state == "downloading")
                val sendingKindle = state.sendingToKindleKey != null

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .background(TsumoColors.Surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.name,
                                fontFamily = EBGaramondFamily,
                                fontSize = 24.sp,
                                lineHeight = 29.sp,
                                color = TsumoColors.Primary,
                            )
                            if (book.authorsDisplay.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = book.authorsDisplay,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TsumoColors.Secondary,
                                    ),
                                )
                            }
                        }
                        Text(
                            text = "CLOSE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.4.sp,
                                color = TsumoColors.Secondary,
                            ),
                            modifier = Modifier
                                .clickable(onClick = onDismiss)
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    HairlineRule()

                    // Detail Specs Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RELEASED",
                                style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = book.yearDisplay,
                                style = MaterialTheme.typography.headlineSmall.copy(color = TsumoColors.Primary),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FILE PACKAGE",
                                style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = book.packageDisplay,
                                style = MaterialTheme.typography.headlineSmall.copy(color = TsumoColors.Primary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (book.publisher.isNotBlank() || book.language.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (book.publisher.isNotBlank()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PUBLISHER",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = book.publisher,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Primary),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (book.language.isNotBlank()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "LANGUAGE",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = book.language,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Primary),
                                    )
                                }
                            }
                        }
                    }

                    if (state.detailLoading) {
                        Text(
                            text = "Fetching archive metadata…",
                            fontFamily = EBGaramondFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp,
                            color = TsumoColors.Secondary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    } else if (book.description.isNotBlank()) {
                        Text(
                            text = book.description
                                .replace(Regex("<[^>]*>"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TsumoColors.OnSurfaceVariant,
                                lineHeight = 19.sp,
                            ),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }

                    if (dl != null && (dl.state == "fetching" || dl.state == "downloading")) {
                        val progressText = when {
                            dl.state == "fetching" -> "Resolving edition…"
                            dl.total > 0 -> {
                                val pct = ((dl.written.toDouble() / dl.total.toDouble()) * 100).toInt().coerceIn(0, 100)
                                val wMb = dl.written / 1048576.0
                                val tMb = dl.total / 1048576.0
                                String.format("%d%% · %.1f / %.1f MB", pct, wMb, tMb)
                            }
                            else -> String.format("%.1f MB", dl.written / 1048576.0)
                        }
                        val fraction = if (dl.total > 0) {
                            (dl.written.toFloat() / dl.total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0.25f
                        }

                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(TsumoColors.Primary.copy(alpha = 0.12f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(2.dp)
                                        .background(TsumoColors.Primary),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Primary),
                                )
                                Text(
                                    text = "CANCEL",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Error),
                                    modifier = Modifier.clickable(onClick = onCancelDownload),
                                )
                            }
                        }
                    }

                    HairlineRule()
                    Spacer(modifier = Modifier.height(10.dp))

                    // Pure Typographic Actions
                    val extLabel = book.extDisplay.ifBlank { "BOOK" }
                    if (localMatch != null && !isDownloading) {
                        PrimaryTypographicAction(
                            label = "Open $extLabel in reader",
                            trailing = "Saved on device",
                            onClick = { onOpenLocal(localMatch.filePath) },
                        )
                    } else {
                        PrimaryTypographicAction(
                            label = if (isDownloading) "Downloading $extLabel…" else "Download $extLabel",
                            trailing = when {
                                isDownloading -> "In progress"
                                state.detailLoading -> "Syncing record"
                                else -> "Ready to sync"
                            },
                            enabled = !isDownloading,
                            onClick = { onDownload(false) },
                        )
                    }

                    SecondaryTypographicAction(
                        label = if (sendingKindle) "Sending to Kindle…" else "Send to Kindle",
                        trailing = if (state.status.kindleReady) state.status.kindleTo else "Configure SMTP",
                        enabled = !isDownloading && !sendingKindle,
                        onClick = { onDownload(true) },
                    )

                    SecondaryTypographicAction(
                        label = if (isBookmarked) "Remove from shelf" else "Save to shelf",
                        trailing = if (isBookmarked) "Bookmarked" else "Later",
                        onClick = { onToggleBookmark(book) },
                    )
                }
            }
        }
    }
}
