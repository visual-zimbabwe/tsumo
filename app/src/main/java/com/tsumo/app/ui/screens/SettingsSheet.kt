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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.OpenMode
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun SettingsSheetOverlay(
    state: TsumoUiState,
    onDismiss: () -> Unit,
    onUpdateReaderSettings: (OpenMode, Int) -> Unit,
    onSaveDelivery: (to: String, from: String, host: String, port: Int, pass: String) -> Unit,
) {
    var deliveryTo by remember(state.status.deliveryTo) { mutableStateOf(state.status.deliveryTo) }
    var deliveryFrom by remember(state.status.deliveryFrom) { mutableStateOf(state.status.deliveryFrom) }
    var smtpHost by remember(state.status.deliverySmtpHost) { mutableStateOf(state.status.deliverySmtpHost) }
    var smtpPort by remember(state.status.deliverySmtpPort) { mutableStateOf(state.status.deliverySmtpPort.toString()) }
    var smtpPassword by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = state.settingsOpen,
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
                visible = state.settingsOpen,
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
                            text = "Preferences",
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
                        text = "${state.status.feedCount} feeds · ${state.status.unreadCount} unread · ${state.status.archivedCount} archived",
                        style = MaterialTheme.typography.bodySmall,
                        color = TsumoColors.OnSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "DEFAULT ARTICLE TAP ACTION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            color = TsumoColors.Outline,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OpenMode.entries.forEach { mode ->
                            val selected = state.status.openMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) TsumoColors.Primary else TsumoColors.SurfaceContainerLow)
                                    .border(0.5.dp, if (selected) TsumoColors.Primary else TsumoColors.OutlineVariant)
                                    .clickable { onUpdateReaderSettings(mode, state.status.keepReadDays) }
                                    .padding(vertical = 9.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) TsumoColors.OnPrimary else TsumoColors.OnSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "KEEP READ ARTICLES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            color = TsumoColors.Outline,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "Forever").forEach { (days, label) ->
                            val selected = state.status.keepReadDays == days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) TsumoColors.Primary else TsumoColors.SurfaceContainerLow)
                                    .border(0.5.dp, if (selected) TsumoColors.Primary else TsumoColors.OutlineVariant)
                                    .clickable { onUpdateReaderSettings(state.status.openMode, days) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (selected) TsumoColors.OnPrimary else TsumoColors.OnSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HairlineRule()
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "LOCAL STORAGE & EMAIL DELIVERY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            color = TsumoColors.Outline,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Articles export as .md & .pdf to Downloads/Tsumo. Optionally configure SMTP below to email exported files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TsumoColors.OnSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    EditorialInputField(
                        value = deliveryTo,
                        onValueChange = { deliveryTo = it },
                        placeholder = "recipient@example.com",
                        label = "Recipient Email",
                        keyboardType = KeyboardType.Email,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    EditorialInputField(
                        value = deliveryFrom,
                        onValueChange = { deliveryFrom = it },
                        placeholder = "sender@gmail.com",
                        label = "Sender Email",
                        keyboardType = KeyboardType.Email,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(2f)) {
                            EditorialInputField(
                                value = smtpHost,
                                onValueChange = { smtpHost = it },
                                placeholder = "smtp.gmail.com",
                                label = "SMTP Host",
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            EditorialInputField(
                                value = smtpPort,
                                onValueChange = { smtpPort = it },
                                placeholder = "587",
                                label = "Port",
                                keyboardType = KeyboardType.Number,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    EditorialInputField(
                        value = smtpPassword,
                        onValueChange = { smtpPassword = it },
                        placeholder = if (state.status.hasSmtpPassword) "Saved (leave blank to keep)" else "App password",
                        label = "SMTP Password",
                        isPassword = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    PrimaryTypographicAction(
                        label = "Save email delivery settings",
                        trailing = if (state.status.deliveryReady) "Ready" else "Optional",
                        onClick = {
                            onSaveDelivery(
                                deliveryTo,
                                deliveryFrom,
                                smtpHost,
                                smtpPort.toIntOrNull() ?: 587,
                                smtpPassword,
                            )
                        },
                    )
                }
            }
        }
    }
}
