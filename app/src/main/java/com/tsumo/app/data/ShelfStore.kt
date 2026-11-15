package com.tsumo.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ShelfStore(private val context: Context) {
    private val shelfFile = File(context.filesDir, "shelves_v1.json")
    private val bookmarksFile = File(context.filesDir, "bookmarks_v1.json")
    val booksDir: File = File(context.filesDir, "books").apply { mkdirs() }

    suspend fun loadDownloaded(): List<LocalShelfBook> = withContext(Dispatchers.IO) {
        if (!shelfFile.exists()) return@withContext emptyList()
        val arr = runCatching { JSONArray(shelfFile.readText()) }.getOrNull() ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("filePath")
                if (path.isNotBlank() && File(path).exists()) {
                    add(
                        LocalShelfBook(
                            key = obj.optString("key"),
                            title = obj.optString("title"),
                            authors = obj.optString("authors"),
                            year = obj.optString("year"),
                            extension = obj.optString("extension"),
                            sizeLabel = obj.optString("sizeLabel"),
                            cover = obj.optString("cover"),
                            filePath = path,
                            publicUriString = obj.optString("publicUriString"),
                            downloadedAt = obj.optLong("downloadedAt", 0L),
                        )
                    )
                }
            }
        }.sortedByDescending { it.downloadedAt }
    }

    suspend fun saveDownloadedEntry(book: BookItem, filePath: String, byteSize: Long): LocalShelfBook =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            val ext = file.extension.ifBlank { book.extension.lowercase() }.ifBlank { "epub" }
            val publicUri = exportToPublicDownloads(file, ext) ?: ""
            val sizeLabel = formatBytes(byteSize).ifBlank { book.size }
            val entry = LocalShelfBook(
                key = book.effectiveId.ifBlank { file.name },
                title = book.name.ifBlank { file.nameWithoutExtension },
                authors = book.authorsDisplay,
                year = book.yearDisplay,
                extension = ext.uppercase(),
                sizeLabel = sizeLabel,
                cover = book.cover,
                filePath = filePath,
                publicUriString = publicUri,
                downloadedAt = System.currentTimeMillis(),
            )
            val current = loadDownloaded().filterNot { it.filePath == filePath || it.key == entry.key }
            persistDownloaded(listOf(entry) + current)
            entry
        }

    suspend fun removeDownloaded(entry: LocalShelfBook) = withContext(Dispatchers.IO) {
        runCatching { File(entry.filePath).delete() }
        val updated = loadDownloaded().filterNot { it.filePath == entry.filePath }
        persistDownloaded(updated)
    }

    private fun persistDownloaded(items: List<LocalShelfBook>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("key", item.key)
                    .put("title", item.title)
                    .put("authors", item.authors)
                    .put("year", item.year)
                    .put("extension", item.extension)
                    .put("sizeLabel", item.sizeLabel)
                    .put("cover", item.cover)
                    .put("filePath", item.filePath)
                    .put("publicUriString", item.publicUriString)
                    .put("downloadedAt", item.downloadedAt)
            )
        }
        shelfFile.writeText(arr.toString())
    }

    suspend fun loadBookmarks(): List<BookItem> = withContext(Dispatchers.IO) {
        if (!bookmarksFile.exists()) return@withContext emptyList()
        val arr = runCatching { JSONArray(bookmarksFile.readText()) }.getOrNull() ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                add(bookFromJson(arr.getJSONObject(i)))
            }
        }
    }

    suspend fun toggleBookmark(book: BookItem): List<BookItem> = withContext(Dispatchers.IO) {
        val current = loadBookmarks()
        val key = book.effectiveId.ifBlank { book.name }
        val exists = current.any { it.effectiveId.ifBlank { it.name } == key }
        val updated = if (exists) {
            current.filterNot { it.effectiveId.ifBlank { it.name } == key }
        } else {
            listOf(book) + current
        }
        val arr = JSONArray()
        updated.forEach { arr.put(bookToJson(it)) }
        bookmarksFile.writeText(arr.toString())
        updated
    }

    private fun exportToPublicDownloads(file: File, ext: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val mime = mimeTypeForExtension(ext)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Tsumo")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        }.getOrNull()
    }

    fun openBookIntent(filePath: String): Intent {
        val file = File(filePath)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = mimeTypeForExtension(file.extension)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareBookIntent(filePath: String): Intent {
        val file = File(filePath)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = mimeTypeForExtension(file.extension)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun mimeTypeForExtension(ext: String): String {
        val clean = ext.lowercase().trimStart('.')
        return when (clean) {
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            "mobi" -> "application/x-mobipocket-ebook"
            "azw3", "azw" -> "application/vnd.amazon.ebook"
            "fb2" -> "application/x-fictionbook+xml"
            "txt" -> "text/plain"
            "rtf" -> "application/rtf"
            "djvu", "djv" -> "image/vnd.djvu"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(clean) ?: "application/octet-stream"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", kb)
    }

    private fun bookToJson(b: BookItem): JSONObject = JSONObject()
        .put("id", b.id)
        .put("commandId", b.commandId)
        .put("hash", b.hash)
        .put("isbn", b.isbn)
        .put("url", b.url)
        .put("cover", b.cover)
        .put("name", b.name)
        .put("authors", JSONArray(b.authors))
        .put("publisher", b.publisher)
        .put("year", b.year)
        .put("language", b.language)
        .put("extension", b.extension)
        .put("size", b.size)
        .put("rating", b.rating)
        .put("quality", b.quality)
        .put("description", b.description)
        .put("categories", b.categories)
        .put("edition", b.edition)
        .put("downloadUrl", b.downloadUrl)

    private fun bookFromJson(obj: JSONObject): BookItem {
        val arr = obj.optJSONArray("authors")
        val authors = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
        }
        return BookItem(
            id = obj.optString("id"),
            commandId = obj.optString("commandId"),
            hash = obj.optString("hash"),
            isbn = obj.optString("isbn"),
            url = obj.optString("url"),
            cover = obj.optString("cover"),
            name = obj.optString("name"),
            authors = authors,
            publisher = obj.optString("publisher"),
            year = obj.optString("year"),
            language = obj.optString("language"),
            extension = obj.optString("extension"),
            size = obj.optString("size"),
            rating = obj.optString("rating"),
            quality = obj.optString("quality"),
            description = obj.optString("description"),
            categories = obj.optString("categories"),
            edition = obj.optString("edition"),
            downloadUrl = obj.optString("downloadUrl"),
        )
    }
}
