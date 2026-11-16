package com.tsumo.app.data

data class Feed(
    val id: Long,
    val url: String,
    val title: String,
    val siteUrl: String = "",
    val tags: List<String> = emptyList(),
    val customTitle: String = "",
    val lastFetch: Long = 0L,
    val error: String = "",
    val position: Int = 0,
    val unreadCount: Int = 0,
) {
    val displayTitle: String
        get() = customTitle.trim().ifBlank { title.trim() }.ifBlank { url }
}

data class DiscoveredFeed(
    val url: String,
    val title: String,
    val siteUrl: String = "",
    val count: Int = 0,
)

data class Article(
    val id: Long,
    val feedId: Long,
    val feedTitle: String,
    val guid: String = "",
    val url: String = "",
    val title: String,
    val author: String = "",
    val summary: String = "",
    val content: String = "",
    val fullText: String = "",
    val snippet: String = "",
    val published: Long = 0L,
    val unread: Boolean = true,
    val archived: Boolean = false,
)

data class HistoryEntry(
    val historyId: Long,
    val openedAt: Long,
    val via: String, // reader | custom_tab | browser
    val article: Article,
)

enum class OpenMode(val wireValue: String, val label: String) {
    READER("reader", "In-App Reader"),
    CUSTOM_TAB("custom_tab", "In-App Browser"),
    BROWSER("browser", "Phone Browser");

    companion object {
        fun fromWire(value: String): OpenMode =
            entries.firstOrNull { it.wireValue == value } ?: READER
    }
}

data class EngineStatus(
    val openMode: OpenMode = OpenMode.READER,
    val keepReadDays: Int = 30,
    val feedCount: Int = 0,
    val unreadCount: Int = 0,
    val archivedCount: Int = 0,
    val deliveryReady: Boolean = false,
    val deliveryTo: String = "",
    val deliveryFrom: String = "",
    val deliverySmtpHost: String = "smtp.gmail.com",
    val deliverySmtpPort: Int = 587,
    val hasSmtpPassword: Boolean = false,
)

data class RefreshSummary(
    val refreshed: Int = 0,
    val newItems: Int = 0,
    val errorCount: Int = 0,
    val firstError: String = "",
)

data class LocalExportItem(
    val key: String,
    val title: String,
    val source: String,
    val dateLabel: String,
    val extension: String, // MD, PDF, EPUB
    val sizeLabel: String,
    val filePath: String,
    val publicUriString: String = "",
    val savedAt: Long,
)

enum class TsumoTab(val label: String) {
    READ("READ"),
    FEEDS("FEEDS"),
    LIBRARY("LIBRARY"),
}

sealed class ReadChannel {
    data object All : ReadChannel()
    data object Unread : ReadChannel()
    data object Archived : ReadChannel()
    data class Tag(val tag: String) : ReadChannel()
    data class FeedChannel(val feed: Feed) : ReadChannel()

    val key: String
        get() = when (this) {
            All -> "all"
            Unread -> "unread"
            Archived -> "archived"
            is Tag -> "tag:$tag"
            is FeedChannel -> "feed:${feed.id}"
        }

    val label: String
        get() = when (this) {
            All -> "All"
            Unread -> "Unread"
            Archived -> "Saved ★"
            is Tag -> "#$tag"
            is FeedChannel -> feed.displayTitle
        }
}
