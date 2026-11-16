package com.tsumo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.Article
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.components.SecondaryTypographicAction
import com.tsumo.app.ui.theme.TsumoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArticleDrawerOverlay(
    state: TsumoUiState,
    onDismiss: () -> Unit,
    onOpenReader: (Article) -> Unit,
    onOpenCustomTab: (Article) -> Unit,
    onOpenExternalBrowser: (Article) -> Unit,
    onShare: (Article) -> Unit,
    onToggleArchive: (Article) -> Unit,
    onToggleRead: (Article) -> Unit,
    onExportMarkdown: (Article) -> Unit,
    onExportPdf: (Article) -> Unit,
) {
    val article = state.selectedArticle
    val visible = article != null && !state.readerOpen

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                if (article != null) {
                    val dateStr = remember(article.published) {
                        if (article.published > 0L) {
                            SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US)
                                .format(Date(article.published * 1000L))
                        } else ""
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TsumoColors.Surface)
                            .border(0.5.dp, TsumoColors.Primary.copy(alpha = 0.14f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = listOf(article.feedTitle, dateStr)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.0.sp,
                                    color = TsumoColors.Secondary,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "CLOSE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    color = TsumoColors.Outline,
                                ),
                                modifier = Modifier
                                    .clickable(onClick = onDismiss)
                                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = TsumoColors.Primary,
                        )
                        if (article.author.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "By ${article.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TsumoColors.OnSurfaceVariant,
                            )
                        }
                        if (article.summary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = article.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TsumoColors.OnSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HairlineRule()
                        Spacer(modifier = Modifier.height(6.dp))

                        PrimaryTypographicAction(
                            label = "Read in Tsumo",
                            trailing = if (article.archived) "Offline ready" else "Clean view",
                            onClick = { onOpenReader(article) },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ActionChip(
                                label = "IN-APP BROWSER",
                                enabled = article.url.isNotBlank(),
                                onClick = { onOpenCustomTab(article) },
                                modifier = Modifier.weight(1f),
                            )
                            ActionChip(
                                label = "PHONE BROWSER",
                                enabled = article.url.isNotBlank(),
                                onClick = { onOpenExternalBrowser(article) },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionChip(
                                label = if (article.archived) "★ ARCHIVED" else "☆ ARCHIVE",
                                onClick = { onToggleArchive(article) },
                                modifier = Modifier.weight(1f),
                            )
                            ActionChip(
                                label = "SHARE",
                                onClick = { onShare(article) },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionChip(
                                label = if (state.exportingType == "MD") "SAVING MD…" else "↓ MARKDOWN",
                                enabled = state.exportingType == null,
                                onClick = { onExportMarkdown(article) },
                                modifier = Modifier.weight(1f),
                            )
                            ActionChip(
                                label = if (state.exportingType == "PDF") "RENDERING PDF…" else "↓ PDF",
                                enabled = state.exportingType == null,
                                onClick = { onExportPdf(article) },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        SecondaryTypographicAction(
                            label = if (article.unread) "Mark as read" else "Mark as unread",
                            onClick = { onToggleRead(article) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(TsumoColors.SurfaceContainerLow)
            .border(0.5.dp, TsumoColors.OutlineVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.9.sp,
                color = if (enabled) TsumoColors.Primary else TsumoColors.Outline,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
