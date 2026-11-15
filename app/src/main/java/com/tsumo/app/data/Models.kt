package com.tsumo.app.data

data class BookItem(
    val id: String,
    val commandId: String,
    val hash: String = "",
    val isbn: String = "",
    val url: String = "",
    val cover: String = "",
    val name: String,
    val authors: List<String> = emptyList(),
    val publisher: String = "",
    val year: String = "",
    val language: String = "",
    val extension: String = "",
    val size: String = "",
    val rating: String = "",
    val quality: String = "",
    val description: String = "",
    val categories: String = "",
    val edition: String = "",
    val downloadUrl: String = "",
) {
    val effectiveId: String
        get() = commandId.ifBlank {
            if (id.isNotBlank() && hash.isNotBlank()) "$id:$hash" else id
        }

    val authorsDisplay: String
        get() = authors.filter { it.isNotBlank() }.joinToString(", ")

    val yearDisplay: String
        get() = year.takeIf { it.isNotBlank() && it != "0" } ?: "—"

    val extDisplay: String
        get() = extension.trim().uppercase()

    val packageDisplay: String
        get() = listOf(extDisplay, size.trim()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" }
}

data class HistoryItem(
    val id: String = "",
    val commandId: String = "",
    val hash: String = "",
    val name: String,
    val url: String = "",
    val downloadUrl: String = "",
    val extension: String = "",
    val size: String = "",
    val date: String = "",
) {
    fun toBookItem(): BookItem = BookItem(
        id = id,
        commandId = commandId,
        hash = hash,
        url = url,
        name = name,
        extension = extension,
        size = size,
        downloadUrl = downloadUrl,
    )
}

data class DoctorCheck(
    val domain: String,
    val status: String,
    val httpStatus: Int = 0,
    val finalUrl: String = "",
    val detail: String = "",
    val eapiStatus: String = "",
    val eapiOk: Boolean = false,
)

data class EngineStatus(
    val loggedIn: Boolean = false,
    val loggedOutManually: Boolean = false,
    val domain: String = "https://z-lib.gd",
    val mode: String = "eapi",
    val lastEmail: String = "",
    val kindleReady: Boolean = false,
    val kindleTo: String = "",
    val kindleFrom: String = "",
    val kindleSmtpHost: String = "smtp.gmail.com",
    val kindleSmtpPort: Int = 587,
    val hasSmtpPassword: Boolean = false,
)

data class ProfileLimits(
    val dailyAmount: Int = 0,
    val dailyAllowed: Int = 0,
    val dailyRemaining: Int = 0,
    val dailyReset: String = "",
)

data class DownloadProgress(
    val id: String,
    val state: String, // fetching, downloading, complete, error, cancelled
    val written: Long = 0L,
    val total: Long = 0L,
    val filePath: String = "",
    val size: Long = 0L,
    val error: String = "",
)

data class LocalShelfBook(
    val key: String,
    val title: String,
    val authors: String,
    val year: String,
    val extension: String,
    val sizeLabel: String,
    val cover: String,
    val filePath: String,
    val publicUriString: String = "",
    val downloadedAt: Long,
)

enum class TsumoTab(val label: String) {
    DISCOVER("DISCOVER"),
    CATALOG("CATALOG"),
    SHELVES("SHELVES"),
}
