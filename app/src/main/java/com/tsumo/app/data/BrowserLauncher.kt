package com.tsumo.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.tsumo.app.ui.theme.TsumoColors

object BrowserLauncher {
    fun openCustomTab(context: Context, url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank()) return false
        return runCatching {
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(TsumoColors.Surface.toArgb())
                .setNavigationBarColor(TsumoColors.Surface.toArgb())
                .build()
            val intent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.launchUrl(context, Uri.parse(clean))
            true
        }.getOrElse {
            openExternalBrowser(context, clean)
        }
    }

    fun openExternalBrowser(context: Context, url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank()) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun shareArticle(context: Context, article: Article) {
        val text = buildString {
            append(article.title.trim())
            if (article.feedTitle.isNotBlank()) {
                append(" — ")
                append(article.feedTitle.trim())
            }
            if (article.url.isNotBlank()) {
                append("\n")
                append(article.url.trim())
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, article.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "Share article").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
