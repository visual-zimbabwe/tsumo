package com.tsumo.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.Article
import com.tsumo.app.data.BrowserLauncher
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.theme.TsumoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderViewOverlay(
    state: TsumoUiState,
    onClose: () -> Unit,
    onOpenCustomTab: (Article) -> Unit,
    onShare: (Article) -> Unit,
    onToggleArchive: (Article) -> Unit,
    onExportMarkdown: (Article) -> Unit,
    onExportPdf: (Article) -> Unit,
    onAdjustTextScale: (Float) -> Unit,
) {
    val article = state.selectedArticle
    var darkPaper by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state.readerOpen && article != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (article != null) {
            val context = LocalContext.current
            val bgColor = if (darkPaper) "#171614" else "#FAF9F5"
            val fgColor = if (darkPaper) "#E9E4DC" else "#1B1C1A"
            val mutedColor = if (darkPaper) "#A89F94" else "#50453D"
            val accentColor = if (darkPaper) "#C99F76" else "#7E562E"
            val borderColor = if (darkPaper) "#332F2B" else "#D4C3B9"

            val dateStr = remember(article.published) {
                if (article.published > 0L) {
                    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(article.published * 1000L))
                } else ""
            }
            val fontSizePx = (18 * state.readerTextScale).toInt()
            val fullDocument = remember(article.id, state.readerHtml, darkPaper, state.readerTextScale) {
                buildReaderDocument(
                    article = article,
                    bodyHtml = state.readerHtml,
                    dateStr = dateStr,
                    bgColor = bgColor,
                    fgColor = fgColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    borderColor = borderColor,
                    fontSizePx = fontSizePx,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (darkPaper) androidx.compose.ui.graphics.Color(0xFF171614) else TsumoColors.Surface)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "← BACK",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.1.sp,
                            color = if (darkPaper) androidx.compose.ui.graphics.Color(0xFFE9E4DC) else TsumoColors.Primary,
                        ),
                        modifier = Modifier
                            .clickable(onClick = onClose)
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    )
                    Text(
                        text = article.feedTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (darkPaper) androidx.compose.ui.graphics.Color(0xFFA89F94) else TsumoColors.Secondary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "A-",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (darkPaper) androidx.compose.ui.graphics.Color(0xFFE9E4DC) else TsumoColors.Primary,
                            ),
                            modifier = Modifier.clickable { onAdjustTextScale(-0.1f) },
                        )
                        Text(
                            text = "A+",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (darkPaper) androidx.compose.ui.graphics.Color(0xFFE9E4DC) else TsumoColors.Primary,
                            ),
                            modifier = Modifier.clickable { onAdjustTextScale(0.1f) },
                        )
                        Text(
                            text = if (darkPaper) "DAY" else "NIGHT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.9.sp,
                                color = if (darkPaper) androidx.compose.ui.graphics.Color(0xFFC99F76) else TsumoColors.Secondary,
                            ),
                            modifier = Modifier.clickable { darkPaper = !darkPaper },
                        )
                    }
                }

                if (state.readerLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = TsumoColors.Primary,
                    )
                } else {
                    HairlineRule()
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.loadsImagesAutomatically = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        BrowserLauncher.openCustomTab(context, url)
                                        return true
                                    }
                                }
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(
                                article.url.ifBlank { null },
                                fullDocument,
                                "text/html",
                                "utf-8",
                                null,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                HairlineRule()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val actionColor = if (darkPaper) {
                        androidx.compose.ui.graphics.Color(0xFFE9E4DC)
                    } else TsumoColors.Primary
                    Text(
                        text = if (article.archived) "★ ARCHIVED" else "☆ ARCHIVE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp, color = actionColor),
                        modifier = Modifier.clickable { onToggleArchive(article) },
                    )
                    Text(
                        text = "BROWSER",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp, color = actionColor),
                        modifier = Modifier.clickable { onOpenCustomTab(article) },
                    )
                    Text(
                        text = "SHARE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp, color = actionColor),
                        modifier = Modifier.clickable { onShare(article) },
                    )
                    Text(
                        text = if (state.exportingType == "MD") "MD…" else "↓ MD",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp, color = actionColor),
                        modifier = Modifier.clickable(enabled = state.exportingType == null) { onExportMarkdown(article) },
                    )
                    Text(
                        text = if (state.exportingType == "PDF") "PDF…" else "↓ PDF",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp, color = actionColor),
                        modifier = Modifier.clickable(enabled = state.exportingType == null) { onExportPdf(article) },
                    )
                }
            }
        }
    }
}

private fun buildReaderDocument(
    article: Article,
    bodyHtml: String,
    dateStr: String,
    bgColor: String,
    fgColor: String,
    mutedColor: String,
    accentColor: String,
    borderColor: String,
    fontSizePx: Int,
): String {
    val meta = listOf(article.feedTitle, article.author, dateStr)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .replace("<", "&lt;")
    val titleEsc = article.title.replace("<", "&lt;").replace(">", "&gt;")
    return """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  body {
    background: $bgColor;
    color: $fgColor;
    font-family: Georgia, 'Times New Roman', serif;
    font-size: ${fontSizePx}px;
    line-height: 1.62;
    margin: 0;
    padding: 20px 20px 44px 20px;
    word-wrap: break-word;
  }
  .meta {
    font-family: sans-serif;
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: $accentColor;
    margin-bottom: 8px;
  }
  h1.title {
    font-size: 1.55em;
    line-height: 1.22;
    font-weight: 600;
    margin: 0 0 14px 0;
  }
  hr {
    border: none;
    border-top: 1px solid $borderColor;
    margin: 0 0 20px 0;
  }
  img { max-width: 100%; height: auto; display: block; margin: 16px auto; }
  a { color: $accentColor; }
  blockquote {
    border-left: 2px solid $accentColor;
    margin: 14px 0;
    padding-left: 14px;
    color: $mutedColor;
    font-style: italic;
  }
  pre, code {
    font-family: monospace;
    font-size: 0.85em;
    background: rgba(128,128,128,0.12);
    padding: 2px 4px;
    overflow-x: auto;
  }
  pre { padding: 12px; }
</style>
</head>
<body>
  <div class="meta">$meta</div>
  <h1 class="title">$titleEsc</h1>
  <hr />
  <div class="content">$bodyHtml</div>
</body>
</html>
    """.trimIndent()
}
