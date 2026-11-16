package com.tsumo.app.engine

import com.tsumo.app.data.Article
import com.tsumo.app.data.DiscoveredFeed
import com.tsumo.app.data.EngineStatus
import com.tsumo.app.data.Feed
import com.tsumo.app.data.HistoryEntry
import com.tsumo.app.data.OpenMode
import com.tsumo.app.data.RefreshSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class FeedsSnapshot(
    val feeds: List<Feed>,
    val totalUnread: Int,
    val tagUnreads: Map<String, Int>,
)

data class SearchResults(
    val items: List<Article>,
    val feeds: List<Feed>,
)

data class ArticleContentData(
    val html: String,
    val paragraphs: List<String>,
)

data class MarkdownExportData(
    val markdown: String,
    val filename: String,
)

object FeedEngine {
    init {
        System.loadLibrary("tsumo")
    }

    private external fun nativeCall(action: ByteArray, payload: ByteArray): ByteArray

    private fun rawCall(action: String, payload: JSONObject = JSONObject()): JSONObject {
        val out = nativeCall(
            action.toByteArray(Charsets.UTF_8),
            payload.toString().toByteArray(Charsets.UTF_8),
        )
        val json = JSONObject(out.toString(Charsets.UTF_8))
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "Request failed"))
        }
        return json
    }

    suspend fun init(dataDir: String): EngineStatus = withContext(Dispatchers.IO) {
        parseStatus(rawCall("init", JSONObject().put("dataDir", dataDir)))
    }

    suspend fun status(): EngineStatus = withContext(Dispatchers.IO) {
        parseStatus(rawCall("status"))
    }

    suspend fun saveSettings(openMode: OpenMode, keepReadDays: Int): EngineStatus = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("open_mode", openMode.wireValue)
            .put("keep_read_days", keepReadDays)
        parseStatus(rawCall("saveSettings", payload))
    }

    suspend fun discoverFeed(url: String): DiscoveredFeed = withContext(Dispatchers.IO) {
        val json = rawCall("discoverFeed", JSONObject().put("url", url))
        val f = json.getJSONObject("feed")
        DiscoveredFeed(
            url = f.optString("url"),
            title = f.optString("title"),
            siteUrl = f.optString("site_url"),
            count = f.optInt("count", 0),
        )
    }

    suspend fun addFeed(url: String, customTitle: String = "", tags: List<String> = emptyList()): Feed =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("url", url)
                .put("custom_title", customTitle)
                .put("tags", JSONArray(tags))
            val json = rawCall("addFeed", payload)
            parseFeed(json.getJSONObject("feed"))
        }

    suspend fun removeFeed(id: Long) = withContext(Dispatchers.IO) {
        rawCall("removeFeed", JSONObject().put("id", id))
        Unit
    }

    suspend fun updateFeed(id: Long, customTitle: String, tags: List<String>): Feed =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("id", id)
                .put("custom_title", customTitle)
                .put("tags", JSONArray(tags))
            val json = rawCall("updateFeed", payload)
            parseFeed(json.getJSONObject("feed"))
        }

    suspend fun reorderFeeds(ids: List<Long>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        rawCall("reorderFeeds", JSONObject().put("ids", arr))
        Unit
    }

    suspend fun listFeeds(): FeedsSnapshot = withContext(Dispatchers.IO) {
        val json = rawCall("listFeeds")
        val arr = json.optJSONArray("feeds") ?: JSONArray()
        val feeds = buildList {
            for (i in 0 until arr.length()) {
                add(parseFeed(arr.getJSONObject(i)))
            }
        }
        val tagObj = json.optJSONObject("tag_unreads") ?: JSONObject()
        val tagUnreads = buildMap {
            val keys = tagObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, tagObj.optInt(k, 0))
            }
        }
        FeedsSnapshot(
            feeds = feeds,
            totalUnread = json.optInt("total_unread", 0),
            tagUnreads = tagUnreads,
        )
    }

    suspend fun refresh(feedIds: List<Long> = emptyList()): RefreshSummary = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        if (feedIds.isNotEmpty()) {
            val arr = JSONArray()
            feedIds.forEach { arr.put(it) }
            payload.put("feed_ids", arr)
        }
        val json = rawCall("refresh", payload)
        val errors = json.optJSONArray("errors") ?: JSONArray()
        val firstErr = if (errors.length() > 0) {
            val e = errors.getJSONObject(0)
            "${e.optString("title")}: ${e.optString("error")}"
        } else ""
        RefreshSummary(
            refreshed = json.optInt("refreshed", 0),
            newItems = json.optInt("new_items", 0),
            errorCount = errors.length(),
            firstError = firstErr,
        )
    }

    suspend fun listItems(
        feedId: Long = 0L,
        tag: String = "",
        filter: String = "all",
        offset: Int = 0,
        limit: Int = 100,
    ): List<Article> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("feed_id", feedId)
            .put("tag", tag)
            .put("filter", filter)
            .put("offset", offset)
            .put("limit", limit)
        val json = rawCall("listItems", payload)
        parseArticles(json.optJSONArray("items"))
    }

    suspend fun getArticle(id: Long): Article = withContext(Dispatchers.IO) {
        val json = rawCall("getArticle", JSONObject().put("id", id))
        parseArticle(json.getJSONObject("article"))
    }

    suspend fun articleContent(id: Long): ArticleContentData = withContext(Dispatchers.IO) {
        val json = rawCall("articleContent", JSONObject().put("id", id))
        val pArr = json.optJSONArray("paragraphs") ?: JSONArray()
        val paragraphs = buildList {
            for (i in 0 until pArr.length()) {
                add(pArr.optString(i))
            }
        }
        ArticleContentData(
            html = json.optString("html"),
            paragraphs = paragraphs,
        )
    }

    suspend fun exportMarkdown(id: Long): MarkdownExportData = withContext(Dispatchers.IO) {
        val json = rawCall("exportMarkdown", JSONObject().put("id", id))
        MarkdownExportData(
            markdown = json.optString("markdown"),
            filename = json.optString("filename", "tsumo-article.md"),
        )
    }

    suspend fun search(query: String, scope: String = "all", limit: Int = 50): SearchResults =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("query", query)
                .put("scope", scope)
                .put("limit", limit)
            val json = rawCall("search", payload)
            val fArr = json.optJSONArray("feeds") ?: JSONArray()
            val feeds = buildList {
                for (i in 0 until fArr.length()) {
                    add(parseFeed(fArr.getJSONObject(i)))
                }
            }
            SearchResults(
                items = parseArticles(json.optJSONArray("items")),
                feeds = feeds,
            )
        }

    suspend fun markRead(ids: List<Long>, read: Boolean) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        rawCall("markRead", JSONObject().put("ids", arr).put("read", read))
        Unit
    }

    suspend fun markFeedRead(feedId: Long = 0L) = withContext(Dispatchers.IO) {
        rawCall("markFeedRead", JSONObject().put("feed_id", feedId))
        Unit
    }

    suspend fun setArchived(id: Long, archived: Boolean) = withContext(Dispatchers.IO) {
        rawCall("setArchived", JSONObject().put("id", id).put("archived", archived))
        Unit
    }

    suspend fun recordOpen(id: Long, via: String) = withContext(Dispatchers.IO) {
        rawCall("recordOpen", JSONObject().put("id", id).put("via", via))
        Unit
    }

    suspend fun history(query: String = "", offset: Int = 0, limit: Int = 100): List<HistoryEntry> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("query", query)
                .put("offset", offset)
                .put("limit", limit)
            val json = rawCall("history", payload)
            val arr = json.optJSONArray("entries") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        HistoryEntry(
                            historyId = obj.optLong("history_id"),
                            openedAt = obj.optLong("opened_at"),
                            via = obj.optString("via", "reader"),
                            article = parseArticle(obj.getJSONObject("article")),
                        )
                    )
                }
            }
        }

    suspend fun removeHistory(historyId: Long) = withContext(Dispatchers.IO) {
        rawCall("removeHistory", JSONObject().put("history_id", historyId))
        Unit
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        rawCall("clearHistory")
        Unit
    }

    suspend fun importUrls(content: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val json = rawCall("importUrls", JSONObject().put("content", content))
        json.optInt("imported", 0) to json.optInt("skipped", 0)
    }

    suspend fun exportUrls(): String = withContext(Dispatchers.IO) {
        rawCall("exportUrls").optString("content")
    }

    suspend fun importOpml(content: String): Int = withContext(Dispatchers.IO) {
        rawCall("importOpml", JSONObject().put("content", content)).optInt("imported", 0)
    }

    suspend fun exportOpml(): String = withContext(Dispatchers.IO) {
        rawCall("exportOpml").optString("content")
    }

    suspend fun saveDelivery(
        to: String,
        from: String,
        smtpHost: String,
        smtpPort: Int,
        password: String,
    ): EngineStatus = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("to", to)
            .put("from", from)
            .put("smtp_host", smtpHost)
            .put("smtp_port", smtpPort)
            .put("password", password)
        parseStatus(rawCall("saveDelivery", payload))
    }

    suspend fun sendDelivery(filePath: String): String = withContext(Dispatchers.IO) {
        rawCall("sendDelivery", JSONObject().put("filePath", filePath)).optString("sent_to")
    }

    private fun parseStatus(json: JSONObject): EngineStatus = EngineStatus(
        openMode = OpenMode.fromWire(json.optString("open_mode", "reader")),
        keepReadDays = json.optInt("keep_read_days", 30),
        feedCount = json.optInt("feed_count", 0),
        unreadCount = json.optInt("unread_count", 0),
        archivedCount = json.optInt("archived_count", 0),
        deliveryReady = json.optBoolean("delivery_ready", false),
        deliveryTo = json.optString("delivery_to"),
        deliveryFrom = json.optString("delivery_from"),
        deliverySmtpHost = json.optString("delivery_smtp_host", "smtp.gmail.com"),
        deliverySmtpPort = json.optInt("delivery_smtp_port", 587),
        hasSmtpPassword = json.optBoolean("has_smtp_password", false),
    )

    private fun parseFeed(obj: JSONObject): Feed {
        val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i).trim()
                if (t.isNotBlank()) add(t)
            }
        }
        return Feed(
            id = obj.optLong("id"),
            url = obj.optString("url"),
            title = obj.optString("title"),
            siteUrl = obj.optString("site_url"),
            tags = tags,
            customTitle = obj.optString("custom_title"),
            lastFetch = obj.optLong("last_fetch", 0L),
            error = obj.optString("error"),
            position = obj.optInt("position", 0),
            unreadCount = obj.optInt("unread_count", 0),
        )
    }

    private fun parseArticles(arr: JSONArray?): List<Article> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                add(parseArticle(arr.getJSONObject(i)))
            }
        }
    }

    private fun parseArticle(obj: JSONObject): Article = Article(
        id = obj.optLong("id"),
        feedId = obj.optLong("feed_id"),
        feedTitle = obj.optString("feed_title"),
        guid = obj.optString("guid"),
        url = obj.optString("url"),
        title = obj.optString("title"),
        author = obj.optString("author"),
        summary = obj.optString("summary"),
        content = obj.optString("content"),
        fullText = obj.optString("full_text"),
        snippet = obj.optString("snippet"),
        published = obj.optLong("published", 0L),
        unread = obj.optBoolean("unread", true),
        archived = obj.optBoolean("archived", false),
    )
}
