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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.FilterTabItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.components.SharpSquareCheckbox
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun AccountSheetOverlay(
    state: TsumoUiState,
    onDismiss: () -> Unit,
    onUpdateSources: (enableGutenberg: Boolean, enableInternetArchive: Boolean) -> Unit,
    onRunDoctor: () -> Unit,
    onSaveKindle: (to: String, from: String, host: String, port: Int, pass: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.accountOpen,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 }),
        modifier = modifier.fillMaxSize(),
    ) {
        var section by remember { mutableStateOf(0) } // 0=Sources, 1=Doctor, 2=Kindle

        var enableGutenberg by remember(state.status.enableGutenberg) { mutableStateOf(state.status.enableGutenberg) }
        var enableIA by remember(state.status.enableInternetArchive) { mutableStateOf(state.status.enableInternetArchive) }

        var kindleTo by remember(state.status.kindleTo) { mutableStateOf(state.status.kindleTo) }
        var kindleFrom by remember(state.status.kindleFrom) { mutableStateOf(state.status.kindleFrom) }
        var kindleHost by remember(state.status.kindleSmtpHost) { mutableStateOf(state.status.kindleSmtpHost) }
        var kindlePort by remember(state.status.kindleSmtpPort) { mutableStateOf(state.status.kindleSmtpPort.toString()) }
        var kindlePass by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TsumoColors.Surface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 24.dp),
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Archive Ledger",
                    fontFamily = EBGaramondFamily,
                    fontSize = 26.sp,
                    color = TsumoColors.Primary,
                )
                Text(
                    text = "CLOSE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.4.sp,
                        color = TsumoColors.Secondary,
                    ),
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                FilterTabItem(label = "Sources", selected = section == 0, onClick = { section = 0 })
                FilterTabItem(label = "Doctor", selected = section == 1, onClick = { section = 1 })
                FilterTabItem(label = "Kindle", selected = section == 2, onClick = { section = 2 })
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (section) {
                    0 -> {
                        Text(
                            text = "OPEN ARCHIVE PROVIDERS",
                            style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                        )
                        Text(
                            text = "Tsumo queries free, open-access public libraries with no account or login required.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.OnSurfaceVariant),
                        )

                        SharpSquareCheckbox(
                            checked = enableGutenberg,
                            label = "Project Gutenberg (OPDS · EPUB & MOBI)",
                            onToggle = { enableGutenberg = !enableGutenberg },
                        )
                        SharpSquareCheckbox(
                            checked = enableIA,
                            label = "Internet Archive & Open Library (EPUB & PDF)",
                            onToggle = { enableIA = !enableIA },
                        )

                        PrimaryTypographicAction(
                            label = if (state.sourcesSaving) "Saving…" else "Apply archive sources",
                            trailing = listOfNotNull(
                                if (enableGutenberg) "GUTENBERG" else null,
                                if (enableIA) "IA / OL" else null,
                            ).joinToString(" + ").ifBlank { "NONE" },
                            enabled = !state.sourcesSaving,
                            onClick = { onUpdateSources(enableGutenberg, enableIA) },
                        )
                    }

                    1 -> {
                        Text(
                            text = "Probe Project Gutenberg, Internet Archive, and Open Library concurrently to verify live connectivity.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.OnSurfaceVariant),
                        )
                        PrimaryTypographicAction(
                            label = if (state.doctorLoading) "Checking sources…" else "Run source check",
                            trailing = "${state.doctorResults.size} checked",
                            enabled = !state.doctorLoading,
                            onClick = onRunDoctor,
                        )

                        if (state.doctorError != null) {
                            Text(
                                text = state.doctorError,
                                style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Error),
                            )
                        }

                        HairlineRule()

                        state.doctorResults.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.name,
                                        fontFamily = EBGaramondFamily,
                                        fontSize = 18.sp,
                                        color = if (item.ok) TsumoColors.Primary else TsumoColors.Outline,
                                    )
                                    Text(
                                        text = item.status.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.ok) TsumoColors.Primary else TsumoColors.Error,
                                        ),
                                    )
                                }
                                Text(
                                    text = "${item.domain.removePrefix("https://")} · ${item.detail}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = TsumoColors.Outline,
                                    ),
                                )
                            }
                            HairlineRule()
                        }
                    }

                    2 -> {
                        Text(
                            text = "Configure SMTP credentials to dispatch downloaded EPUB/PDF editions directly to your Kindle address.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.OnSurfaceVariant),
                        )

                        EditorialInputField(
                            value = kindleTo,
                            onValueChange = { kindleTo = it },
                            placeholder = "reader@kindle.com",
                            label = "Send-to-Kindle Email",
                            keyboardType = KeyboardType.Email,
                        )
                        EditorialInputField(
                            value = kindleFrom,
                            onValueChange = { kindleFrom = it },
                            placeholder = "sender@gmail.com",
                            label = "Approved Sender Email",
                            keyboardType = KeyboardType.Email,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            EditorialInputField(
                                value = kindleHost,
                                onValueChange = { kindleHost = it },
                                placeholder = "smtp.gmail.com",
                                label = "SMTP Host",
                                modifier = Modifier.weight(1.6f),
                            )
                            EditorialInputField(
                                value = kindlePort,
                                onValueChange = { kindlePort = it },
                                placeholder = "587",
                                label = "Port",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(0.7f),
                            )
                        }
                        EditorialInputField(
                            value = kindlePass,
                            onValueChange = { kindlePass = it },
                            placeholder = if (state.status.hasSmtpPassword) "Saved (enter to replace)" else "App Password",
                            label = "SMTP Password",
                            isPassword = true,
                        )

                        PrimaryTypographicAction(
                            label = if (state.kindleSaving) "Saving…" else "Save Kindle configuration",
                            trailing = if (state.status.kindleReady) "Configured" else "Incomplete",
                            enabled = !state.kindleSaving,
                            onClick = {
                                onSaveKindle(
                                    kindleTo,
                                    kindleFrom,
                                    kindleHost,
                                    kindlePort.toIntOrNull() ?: 587,
                                    kindlePass,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
