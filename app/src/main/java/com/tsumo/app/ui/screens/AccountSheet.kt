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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.FilterTabItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.components.SecondaryTypographicAction
import com.tsumo.app.ui.components.SharpSquareCheckbox
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun AccountSheetOverlay(
    state: TsumoUiState,
    onDismiss: () -> Unit,
    onLogin: (email: String, password: String, domain: String, useEapi: Boolean) -> Unit,
    onImportCookies: (cookiesText: String, domain: String, useEapi: Boolean) -> Unit,
    onLogout: () -> Unit,
    onSwitchDomain: (domain: String, mode: String) -> Unit,
    onRefreshProfile: () -> Unit,
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
        var section by remember { mutableStateOf(0) } // 0=Session, 1=Doctor, 2=Kindle

        var email by remember(state.status.lastEmail) { mutableStateOf(state.status.lastEmail) }
        var password by remember { mutableStateOf("") }
        var domain by remember(state.status.domain) { mutableStateOf(state.status.domain) }
        var useEapi by remember(state.status.mode) { mutableStateOf(state.status.mode != "html") }
        var showCookieImport by remember { mutableStateOf(false) }
        var cookiesText by remember { mutableStateOf("") }

        var kindleTo by remember(state.status.kindleTo) { mutableStateOf(state.status.kindleTo) }
        var kindleFrom by remember(state.status.kindleFrom) { mutableStateOf(state.status.kindleFrom) }
        var kindleHost by remember(state.status.kindleSmtpHost) { mutableStateOf(state.status.kindleSmtpHost) }
        var kindlePort by remember(state.status.kindleSmtpPort) { mutableStateOf(state.status.kindleSmtpPort.toString()) }
        var kindlePass by remember { mutableStateOf("") }

        LaunchedEffect(state.status.domain) {
            if (state.status.domain.isNotBlank()) domain = state.status.domain
        }

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
                FilterTabItem(label = "Session", selected = section == 0, onClick = { section = 0 })
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
                        if (state.status.loggedIn) {
                            // Profile & Daily Quota
                            Text(
                                text = "DAILY QUOTA",
                                style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                            )
                            val limits = state.profile
                            if (state.profileLoading) {
                                Text(
                                    text = "Reading account quota…",
                                    fontFamily = EBGaramondFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 18.sp,
                                    color = TsumoColors.Secondary,
                                )
                            } else if (limits != null) {
                                val frac = if (limits.dailyAllowed > 0) {
                                    (limits.dailyAmount.toFloat() / limits.dailyAllowed.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom,
                                    ) {
                                        Text(
                                            text = "${limits.dailyAmount} / ${limits.dailyAllowed}",
                                            fontFamily = EBGaramondFamily,
                                            fontSize = 28.sp,
                                            color = TsumoColors.Primary,
                                        )
                                        Text(
                                            text = "REMAINING ${limits.dailyRemaining}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Secondary),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(TsumoColors.Primary.copy(alpha = 0.12f)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(frac)
                                                .height(2.dp)
                                                .background(TsumoColors.Primary),
                                        )
                                    }
                                    if (limits.dailyReset.isNotBlank()) {
                                        Text(
                                            text = "Resets in ${limits.dailyReset}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Outline),
                                        )
                                    }
                                }
                            } else if (state.profileError != null) {
                                Text(
                                    text = state.profileError,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Error),
                                )
                            }

                            SecondaryTypographicAction(
                                label = "Refresh quota",
                                trailing = state.status.domain.removePrefix("https://"),
                                onClick = onRefreshProfile,
                            )

                            HairlineRule()

                            // Active Mirror & Transport Mode
                            Text(
                                text = "ACTIVE ENDPOINT",
                                style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                            )
                            EditorialInputField(
                                value = domain,
                                onValueChange = { domain = it },
                                placeholder = "https://z-lib.gd",
                                label = "Mirror Domain",
                            )
                            SharpSquareCheckbox(
                                checked = useEapi,
                                label = "Use EAPI transport (recommended)",
                                onToggle = { useEapi = !useEapi },
                            )
                            PrimaryTypographicAction(
                                label = "Apply endpoint",
                                trailing = if (useEapi) "EAPI" else "HTML",
                                onClick = { onSwitchDomain(domain, if (useEapi) "eapi" else "html") },
                            )

                            HairlineRule()

                            SecondaryTypographicAction(
                                label = "Sign out of Z-Library",
                                isError = true,
                                onClick = onLogout,
                            )
                        } else {
                            // Login Form
                            Text(
                                text = "Sign in to Z-Library",
                                fontFamily = EBGaramondFamily,
                                fontStyle = FontStyle.Italic,
                                fontSize = 22.sp,
                                color = TsumoColors.Primary,
                            )

                            EditorialInputField(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = "reader@example.com",
                                label = "Email",
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            )

                            EditorialInputField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = "Password",
                                label = "Password",
                                isPassword = true,
                                imeAction = ImeAction.Done,
                                onImeAction = { onLogin(email, password, domain, useEapi) },
                            )

                            EditorialInputField(
                                value = domain,
                                onValueChange = { domain = it },
                                placeholder = "https://z-lib.gd",
                                label = "Domain",
                            )

                            SharpSquareCheckbox(
                                checked = useEapi,
                                label = "Use EAPI mode (fast mobile API)",
                                onToggle = { useEapi = !useEapi },
                            )

                            if (state.authError != null) {
                                Text(
                                    text = state.authError,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Error),
                                )
                            }

                            PrimaryTypographicAction(
                                label = if (state.authLoading) "Authenticating…" else "Sign in",
                                trailing = if (useEapi) "EAPI" else "HTML",
                                enabled = !state.authLoading,
                                onClick = { onLogin(email, password, domain, useEapi) },
                            )

                            HairlineRule()

                            SecondaryTypographicAction(
                                label = if (showCookieImport) "Hide cookie import" else "Import cookies instead",
                                trailing = "remix_userid / remix_userkey",
                                onClick = { showCookieImport = !showCookieImport },
                            )

                            AnimatedVisibility(visible = showCookieImport) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    EditorialInputField(
                                        value = cookiesText,
                                        onValueChange = { cookiesText = it },
                                        placeholder = "remix_userid=...; remix_userkey=... or cookies.txt",
                                        label = "Cookie String / Netscape / JSON",
                                        singleLine = false,
                                    )
                                    PrimaryTypographicAction(
                                        label = if (state.authLoading) "Importing…" else "Import session cookies",
                                        enabled = !state.authLoading,
                                        onClick = { onImportCookies(cookiesText, domain, useEapi) },
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // Mirror Doctor
                        Text(
                            text = "Probe all known Z-Library mirrors concurrently to identify reachable HTML and EAPI endpoints.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.OnSurfaceVariant),
                        )
                        PrimaryTypographicAction(
                            label = if (state.doctorLoading) "Probing mirrors…" else "Run mirror doctor",
                            trailing = "${state.doctorResults.size} known",
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
                            val usable = item.status == "healthy" || item.status == "challenged" || item.eapiOk
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = usable) {
                                        val mode = if (item.eapiOk) "eapi" else "html"
                                        domain = item.domain
                                        useEapi = item.eapiOk
                                        onSwitchDomain(item.domain, mode)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.domain.removePrefix("https://"),
                                        fontFamily = EBGaramondFamily,
                                        fontSize = 18.sp,
                                        color = if (usable) TsumoColors.Primary else TsumoColors.Outline,
                                    )
                                    Text(
                                        text = if (item.eapiOk) "EAPI OK" else item.status.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                item.eapiOk || item.status == "healthy" -> TsumoColors.Primary
                                                item.status == "challenged" -> TsumoColors.Secondary
                                                else -> TsumoColors.Error.copy(alpha = 0.75f)
                                            },
                                        ),
                                    )
                                }
                                Text(
                                    text = buildString {
                                        append("HTML: ${item.status}")
                                        append(" · EAPI: ${item.eapiStatus}")
                                        if (item.finalUrl.isNotBlank() && !item.finalUrl.startsWith(item.domain)) {
                                            append(" → ${item.finalUrl}")
                                        }
                                    },
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
                        // Kindle SMTP Setup
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
