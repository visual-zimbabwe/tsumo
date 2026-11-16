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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.components.SecondaryTypographicAction
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun AddFeedSheetOverlay(
    state: TsumoUiState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onCustomTitleChange: (String) -> Unit,
    onDiscover: (String?) -> Unit,
    onConfirmAdd: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var clipboardUrl by remember { mutableStateOf("") }

    LaunchedEffect(state.addFeedOpen) {
        if (state.addFeedOpen) {
            val clip = clipboard.getText()?.text?.trim().orEmpty()
            clipboardUrl = if (clip.startsWith("http://") || clip.startsWith("https://")) clip else ""
        }
    }

    AnimatedVisibility(
        visible = state.addFeedOpen,
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
                visible = state.addFeedOpen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
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
                        .imePadding()
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
                            text = "Add Feed",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TsumoColors.Primary,
                        )
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter any RSS/Atom URL or website domain — Tsumo auto-discovers the feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TsumoColors.OnSurfaceVariant,
                    )

                    if (clipboardUrl.isNotBlank() && clipboardUrl != state.addFeedUrl) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TsumoColors.SurfaceContainerLow)
                                .border(0.5.dp, TsumoColors.OutlineVariant)
                                .clickable {
                                    onUrlChange(clipboardUrl)
                                    onDiscover(clipboardUrl)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "PASTE FROM CLIPBOARD: $clipboardUrl",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TsumoColors.Primary,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    EditorialInputField(
                        value = state.addFeedUrl,
                        onValueChange = onUrlChange,
                        placeholder = "e.g. quantamagazine.org or https://.../feed",
                        label = "Site or Feed URL",
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Search,
                        onImeAction = { onDiscover(null) },
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    SecondaryTypographicAction(
                        label = if (state.isDiscovering) "Discovering feed…" else "Discover & preview feed",
                        enabled = !state.isDiscovering && state.addFeedUrl.isNotBlank(),
                        onClick = { onDiscover(null) },
                    )

                    state.discoveredFeed?.let { disc ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TsumoColors.SurfaceContainerLow)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = disc.title.ifBlank { disc.url },
                                style = MaterialTheme.typography.titleMedium,
                                color = TsumoColors.Primary,
                            )
                            Text(
                                text = "${disc.count} recent articles · ${disc.url}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TsumoColors.OnSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    EditorialInputField(
                        value = state.addFeedTagsText,
                        onValueChange = onTagsChange,
                        placeholder = "History, Science, Essays",
                        label = "Tags (comma-separated, optional)",
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    EditorialInputField(
                        value = state.addFeedCustomTitle,
                        onValueChange = onCustomTitleChange,
                        placeholder = "Leave blank to use feed title",
                        label = "Custom Title (optional)",
                        imeAction = ImeAction.Done,
                        onImeAction = onConfirmAdd,
                    )

                    if (!state.addFeedError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.addFeedError,
                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Error),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HairlineRule()
                    Spacer(modifier = Modifier.height(6.dp))

                    PrimaryTypographicAction(
                        label = if (state.isAddingFeed) "Subscribing…" else "Subscribe & fetch articles",
                        trailing = if (state.discoveredFeed != null) "${state.discoveredFeed.count} items" else "",
                        enabled = !state.isAddingFeed && state.addFeedUrl.isNotBlank(),
                        onClick = onConfirmAdd,
                    )
                }
            }
        }
    }
}
