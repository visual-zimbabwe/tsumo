package com.tsumo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.data.Article
import com.tsumo.app.data.TsumoTab
import com.tsumo.app.ui.theme.AlexBrushFamily
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.ManropeFamily
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun TsumoTopBar(
    currentTab: TsumoTab,
    unreadCount: Int,
    onSearchClick: () -> Unit,
    onAddFeedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TsumoColors.Surface.copy(alpha = 0.96f))
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Tsumo",
                fontFamily = AlexBrushFamily,
                fontSize = 31.sp,
                lineHeight = 32.sp,
                color = TsumoColors.PrimaryContainer,
            )
            Text(
                text = currentTab.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.6.sp,
                    color = TsumoColors.Secondary.copy(alpha = 0.68f),
                ),
                modifier = Modifier.padding(bottom = 5.dp),
            )
            if (unreadCount > 0) {
                Text(
                    text = "· $unreadCount",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.8.sp,
                        color = TsumoColors.Outline,
                    ),
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search feeds and articles",
                    tint = TsumoColors.Primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onAddFeedClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add feed",
                    tint = TsumoColors.Primary,
                    modifier = Modifier.size(21.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TsumoColors.Primary)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Settings",
                    tint = TsumoColors.OnPrimary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
fun TsumoBottomBar(
    currentTab: TsumoTab,
    onSelectTab: (TsumoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TsumoColors.Surface.copy(alpha = 0.96f))
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = TsumoColors.Primary.copy(alpha = 0.08f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TsumoTab.entries.forEach { tab ->
                val active = tab == currentTab
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectTab(tab) },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        fontFamily = ManropeFamily,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 1.0.sp,
                        color = if (active) TsumoColors.PrimaryContainer else TsumoColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun FilterTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.4.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) TsumoColors.Primary else TsumoColors.Secondary,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (selected) TsumoColors.Primary else TsumoColors.Surface)
        )
    }
}

@Composable
fun ArticleListRow(
    article: Article,
    onClick: () -> Unit,
    onOpenBrowser: () -> Unit,
    onToggleArchive: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (article.unread) {
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(color = TsumoColors.Secondary)
                    }
                }
                Text(
                    text = article.feedTitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.1.sp,
                        color = if (article.unread) TsumoColors.Secondary else TsumoColors.Outline,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val timeStr = formatRelativeTime(article.published)
                if (timeStr.isNotBlank()) {
                    Text(
                        text = "· $timeStr",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.6.sp,
                            color = TsumoColors.Outline,
                        ),
                        maxLines = 1,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (article.archived) "★" else "☆",
                    fontSize = 16.sp,
                    color = if (article.archived) TsumoColors.Secondary else TsumoColors.Outline,
                    modifier = Modifier
                        .clickable(onClick = onToggleArchive)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                )
                if (article.url.isNotBlank()) {
                    Text(
                        text = "↗",
                        fontSize = 15.sp,
                        color = TsumoColors.Outline,
                        modifier = Modifier
                            .clickable(onClick = onOpenBrowser)
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = "SHARE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        letterSpacing = 0.9.sp,
                        color = TsumoColors.Outline,
                    ),
                    modifier = Modifier
                        .clickable(onClick = onShare)
                        .padding(vertical = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = article.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (article.unread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (article.unread) TsumoColors.OnSurface else TsumoColors.OnSurfaceVariant,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (article.summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TsumoColors.OnSurfaceVariant.copy(alpha = if (article.unread) 0.9f else 0.7f),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun formatRelativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val diffSec = (System.currentTimeMillis() / 1000L) - epochSeconds
    return when {
        diffSec < 60L -> "now"
        diffSec < 3600L -> "${diffSec / 60L}m"
        diffSec < 86400L -> "${diffSec / 3600L}h"
        diffSec < 604800L -> "${diffSec / 86400L}d"
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
            fmt.format(java.util.Date(epochSeconds * 1000L))
        }
    }
}

@Composable
fun SharpSquareCheckbox(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(1.dp, TsumoColors.Primary)
                .background(if (checked) TsumoColors.Primary else TsumoColors.Surface),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TsumoColors.OnSurface,
            ),
        )
    }
}

@Composable
fun PrimaryTypographicAction(
    label: String,
    trailing: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.1.sp,
                    color = if (enabled) TsumoColors.Primary else TsumoColors.Outline,
                ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (enabled) TsumoColors.Primary else TsumoColors.OutlineVariant)
            )
        }
        if (trailing.isNotBlank()) {
            Text(
                text = trailing.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.0.sp,
                    color = TsumoColors.Secondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
fun SecondaryTypographicAction(
    label: String,
    trailing: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.0.sp,
                color = when {
                    !enabled -> TsumoColors.OutlineVariant
                    isError -> TsumoColors.Error
                    else -> TsumoColors.Secondary
                },
            ),
        )
        if (trailing.isNotBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TsumoColors.Secondary.copy(alpha = 0.65f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
fun EditorialInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String = "",
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TsumoColors.Outline,
                    letterSpacing = 1.2.sp,
                ),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            cursorBrush = SolidColor(TsumoColors.Primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onImeAction() },
                onDone = { onImeAction() },
                onGo = { onImeAction() },
            ),
            textStyle = TextStyle(
                fontFamily = ManropeFamily,
                fontSize = 15.sp,
                color = TsumoColors.Primary,
            ),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(vertical = 6.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = EBGaramondFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 17.sp,
                            color = TsumoColors.Secondary.copy(alpha = 0.55f),
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 1.dp else 0.5.dp)
                .background(
                    if (focused) TsumoColors.Primary else TsumoColors.Primary.copy(alpha = 0.16f)
                )
        )
    }
}

@Composable
fun CanvasToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(TsumoColors.Primary)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (message ?: "").uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.1.sp,
                    color = TsumoColors.Surface,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun HairlineRule(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
    ) {
        drawLine(
            color = TsumoColors.Primary.copy(alpha = 0.12f),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
        )
    }
}
