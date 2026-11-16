package com.tsumo.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.tsumo.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportStore(private val context: Context) {
    private val exportsIndexFile = File(context.filesDir, "exports_v2.json")
    private val legacyShelfFile = File(context.filesDir, "shelves_v1.json")
    val exportsDir: File = File(context.filesDir, "exports").apply { mkdirs() }

    suspend fun loadExports(): List<LocalExportItem> = withContext(Dispatchers.IO) {
        val current = readExportsJson(exportsIndexFile)
        val legacy = readLegacyBooksJson(legacyShelfFile)
        (current + legacy)
            .distinctBy { it.filePath }
            .sortedByDescending { it.savedAt }
    }

    suspend fun saveMarkdownExport(
        article: Article,
        markdown: String,
        filename: String,
    ): LocalExportItem = withContext(Dispatchers.IO) {
        val target = File(exportsDir, uniqueFilename(filename))
        target.writeText(markdown, Charsets.UTF_8)
        val publicUri = exportToPublicDownloads(target, "md") ?: ""
        val item = buildItem(article, target, "MD", publicUri)
        persistExport(item)
        item
    }

    suspend fun savePdfExport(
        article: Article,
        paragraphs: List<String>,
        baseFilename: String,
    ): LocalExportItem = withContext(Dispatchers.IO) {
        val pdfName = baseFilename.removeSuffix(".md").removeSuffix(".pdf") + ".pdf"
        val target = File(exportsDir, uniqueFilename(pdfName))
        renderArticlePdf(article, paragraphs, target)
        val publicUri = exportToPublicDownloads(target, "pdf") ?: ""
        val item = buildItem(article, target, "PDF", publicUri)
        persistExport(item)
        item
    }

    suspend fun removeExport(entry: LocalExportItem) = withContext(Dispatchers.IO) {
        runCatching { File(entry.filePath).delete() }
        val updatedExports = readExportsJson(exportsIndexFile).filterNot { it.filePath == entry.filePath }
        writeExportsJson(updatedExports)

        if (legacyShelfFile.exists()) {
            val legacyUpdated = readLegacyBooksJson(legacyShelfFile).filterNot { it.filePath == entry.filePath }
            writeLegacyJson(legacyUpdated)
        }
    }

    fun openFileIntent(filePath: String): Intent {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = mimeTypeForExtension(file.extension)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(viewIntent, "Open ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareFileIntent(filePath: String): Intent {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = mimeTypeForExtension(file.extension)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(sendIntent, "Share ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun renderArticlePdf(article: Article, paragraphs: List<String>, outFile: File) {
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 52f
        val marginTop = 56f
        val marginBottom = 52f
        val contentWidth = (pageWidth - marginX * 2).toInt()

        val serifBold = ResourcesCompat.getFont(context, R.font.eb_garamond_semibold) ?: Typeface.SERIF
        val serifRegular = ResourcesCompat.getFont(context, R.font.eb_garamond_regular) ?: Typeface.SERIF
        val sansRegular = ResourcesCompat.getFont(context, R.font.manrope_regular) ?: Typeface.SANS_SERIF
        val sansBold = ResourcesCompat.getFont(context, R.font.manrope_semibold) ?: Typeface.SANS_SERIF

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = serifBold
            textSize = 22f
            color = Color.parseColor("#1B1C1A")
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansBold
            textSize = 9.5f
            color = Color.parseColor("#7E562E")
        }
        val linkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansRegular
            textSize = 8.5f
            color = Color.parseColor("#50453D")
        }
        val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = serifBold
            textSize = 14f
            color = Color.parseColor("#291200")
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = serifRegular
            textSize = 11.5f
            color = Color.parseColor("#1B1C1A")
        }
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(serifRegular, Typeface.ITALIC)
            textSize = 11.5f
            color = Color.parseColor("#50453D")
        }
        val rulePaint = Paint().apply {
            color = Color.parseColor("#D4C3B9")
            strokeWidth = 0.8f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansRegular
            textSize = 8.5f
            color = Color.parseColor("#82756B")
        }

        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas: Canvas = page.canvas
        var y = marginTop

        fun finishPage() {
            val footerText = "TSUMO · PAGE $pageNum"
            canvas.drawText(footerText, marginX, pageHeight - 28f, footerPaint)
            doc.finishPage(page)
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - marginBottom) {
                finishPage()
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = marginTop
            }
        }

        fun drawBlock(text: String, paint: TextPaint, indent: Float = 0f, spaceAfter: Float = 10f) {
            if (text.isBlank()) return
            val width = (contentWidth - indent).toInt().coerceAtLeast(120)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.18f)
                .setIncludePad(false)
                .build()
            ensureSpace(layout.height.toFloat())
            canvas.save()
            canvas.translate(marginX + indent, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + spaceAfter
        }

        val dateStr = if (article.published > 0L) {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(article.published * 1000L))
        } else ""
        val metaLine = listOf(article.feedTitle, article.author, dateStr)
            .filter { it.isNotBlank() }
            .joinToString("  ·  ")
            .uppercase(Locale.US)
        if (metaLine.isNotBlank()) {
            drawBlock(metaLine, metaPaint, spaceAfter = 6f)
        }
        drawBlock(article.title.ifBlank { "Untitled Article" }, titlePaint, spaceAfter = 6f)
        if (article.url.isNotBlank()) {
            drawBlock(article.url, linkPaint, spaceAfter = 10f)
        }
        canvas.drawLine(marginX, y, pageWidth - marginX, y, rulePaint)
        y += 16f

        val blocks = paragraphs.ifEmpty { listOf(article.summary.ifBlank { "No article body available." }) }
        for (rawBlock in blocks) {
            val trimmed = rawBlock.trim()
            when {
                trimmed.startsWith("## ") -> {
                    y += 4f
                    drawBlock(trimmed.removePrefix("## ").trim(), h2Paint, spaceAfter = 7f)
                }
                trimmed.startsWith("> ") -> {
                    drawBlock(trimmed.removePrefix("> ").trim(), quotePaint, indent = 14f, spaceAfter = 10f)
                }
                else -> {
                    drawBlock(trimmed, bodyPaint, spaceAfter = 10f)
                }
            }
        }

        finishPage()
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
    }

    private fun buildItem(article: Article, file: File, ext: String, publicUri: String): LocalExportItem {
        val dateLabel = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())
        return LocalExportItem(
            key = "${article.id}_$ext",
            title = article.title,
            source = article.feedTitle,
            dateLabel = dateLabel,
            extension = ext,
            sizeLabel = formatBytes(file.length()),
            filePath = file.absolutePath,
            publicUriString = publicUri,
            savedAt = System.currentTimeMillis(),
        )
    }

    private fun persistExport(entry: LocalExportItem) {
        val current = readExportsJson(exportsIndexFile).filterNot { it.filePath == entry.filePath }
        writeExportsJson(listOf(entry) + current)
    }

    private fun readExportsJson(file: File): List<LocalExportItem> {
        if (!file.exists()) return emptyList()
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("filePath")
                if (path.isNotBlank() && File(path).exists()) {
                    add(
                        LocalExportItem(
                            key = obj.optString("key"),
                            title = obj.optString("title"),
                            source = obj.optString("source"),
                            dateLabel = obj.optString("dateLabel"),
                            extension = obj.optString("extension"),
                            sizeLabel = obj.optString("sizeLabel"),
                            filePath = path,
                            publicUriString = obj.optString("publicUriString"),
                            savedAt = obj.optLong("savedAt", 0L),
                        )
                    )
                }
            }
        }
    }

    private fun writeExportsJson(items: List<LocalExportItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("key", item.key)
                    .put("title", item.title)
                    .put("source", item.source)
                    .put("dateLabel", item.dateLabel)
                    .put("extension", item.extension)
                    .put("sizeLabel", item.sizeLabel)
                    .put("filePath", item.filePath)
                    .put("publicUriString", item.publicUriString)
                    .put("savedAt", item.savedAt)
            )
        }
        exportsIndexFile.writeText(arr.toString())
    }

    private fun readLegacyBooksJson(file: File): List<LocalExportItem> {
        if (!file.exists()) return emptyList()
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("filePath")
                if (path.isNotBlank() && File(path).exists()) {
                    add(
                        LocalExportItem(
                            key = obj.optString("key"),
                            title = obj.optString("title"),
                            source = obj.optString("authors"),
                            dateLabel = obj.optString("year"),
                            extension = obj.optString("extension"),
                            sizeLabel = obj.optString("sizeLabel"),
                            filePath = path,
                            publicUriString = obj.optString("publicUriString"),
                            savedAt = obj.optLong("downloadedAt", 0L),
                        )
                    )
                }
            }
        }
    }

    private fun writeLegacyJson(items: List<LocalExportItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("key", item.key)
                    .put("title", item.title)
                    .put("authors", item.source)
                    .put("year", item.dateLabel)
                    .put("extension", item.extension)
                    .put("sizeLabel", item.sizeLabel)
                    .put("cover", "")
                    .put("filePath", item.filePath)
                    .put("publicUriString", item.publicUriString)
                    .put("downloadedAt", item.savedAt)
            )
        }
        legacyShelfFile.writeText(arr.toString())
    }

    private fun uniqueFilename(name: String): String {
        val candidate = File(exportsDir, name)
        if (!candidate.exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        return "${stem}-${System.currentTimeMillis() % 10000}$ext"
    }

    private fun exportToPublicDownloads(file: File, ext: String): String? {
        if (!file.exists()) return null
        val mime = mimeTypeForExtension(ext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Tsumo")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return uri.toString()
                }
            }
        }
        return null
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "md", "markdown" -> "text/markdown"
        "epub" -> "application/epub+zip"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
        else String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
