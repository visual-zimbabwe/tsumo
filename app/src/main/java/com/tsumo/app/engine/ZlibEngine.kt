package com.tsumo.app.engine

import com.tsumo.app.data.BookItem
import com.tsumo.app.data.DoctorCheck
import com.tsumo.app.data.DownloadProgress
import com.tsumo.app.data.EngineStatus
import com.tsumo.app.data.HistoryItem
import com.tsumo.app.data.ProfileLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SearchResponse(
    val books: List<BookItem>,
    val page: Int,
    val totalPages: Int,
)

data class HistoryResponse(
    val items: List<HistoryItem>,
    val page: Int,
    val totalPages: Int,
)

object ZlibEngine {
    init {
        System.loadLibrary("tsumo")
    }

    private external fun nativeCall(action: ByteArray, payload: ByteArray): ByteArray

    private fun rawCall(action: String, payload: JSONObject = JSONObject()): JSONObject {
        val out = nativeCall(
            action.toByteArray(Charsets.UTF_8),
            payload.toString().toByteArray(Charsets.UTF_8)
        )
        val text = out.toString(Charsets.UTF_8)
        val json = JSONObject(text)
        if (!json.optBoolean("ok", false)) {
            val err = json.optString("error", "Request failed")
            throw IllegalStateException(err)
        }
        return json
    }

    suspend fun init(dataDir: String): EngineStatus = withContext(Dispatchers.IO) {
        parseStatus(rawCall("init", JSONObject().put("dataDir", dataDir)))
    }

    suspend fun status(): EngineStatus = withContext(Dispatchers.IO) {
        parseStatus(rawCall("status"))
    }

    suspend fun login(email: String, password: String, domain: String, useEapi: Boolean): EngineStatus =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("domain", domain)
                .put("useEapi", useEapi)
            parseStatus(rawCall("login", payload))
        }

    suspend fun autoLogin(): EngineStatus = withContext(Dispatchers.IO) {
        parseStatus(rawCall("autoLogin"))
    }

    suspend fun importCookies(cookiesText: String, domain: String, useEapi: Boolean): EngineStatus =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("cookiesText", cookiesText)
                .put("domain", domain)
                .put("useEapi", useEapi)
            parseStatus(rawCall("importCookies", payload))
        }

    suspend fun logout(): EngineStatus = withContext(Dispatchers.IO) {
        parseStatus(rawCall("logout"))
    }

    suspend fun setDomain(domain: String, mode: String): EngineStatus = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("domain", domain)
            .put("mode", mode)
        parseStatus(rawCall("setDomain", payload))
    }

    suspend fun doctor(): List<DoctorCheck> = withContext(Dispatchers.IO) {
        val json = rawCall("doctor")
        val arr = json.optJSONArray("checks") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    DoctorCheck(
                        domain = obj.optString("domain"),
                        status = obj.optString("status"),
                        httpStatus = obj.optInt("http_status", 0),
                        finalUrl = obj.optString("final_url"),
                        detail = obj.optString("detail"),
                        eapiStatus = obj.optString("eapi_status"),
                        eapiOk = obj.optBoolean("eapi_ok", false),
                    )
                )
            }
        }
    }

    suspend fun profile(): ProfileLimits = withContext(Dispatchers.IO) {
        val json = rawCall("profile")
        ProfileLimits(
            dailyAmount = json.optInt("daily_amount", 0),
            dailyAllowed = json.optInt("daily_allowed", 0),
            dailyRemaining = json.optInt("daily_remaining", 0),
            dailyReset = json.optString("daily_reset"),
        )
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        count: Int = 24,
        extensions: List<String> = emptyList(),
        languages: List<String> = emptyList(),
        fromYear: Int = 0,
        toYear: Int = 0,
        exact: Boolean = false,
        fullText: Boolean = false,
        phrase: Boolean = false,
    ): SearchResponse = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("query", query)
            .put("page", page)
            .put("count", count)
            .put("extensions", JSONArray(extensions))
            .put("languages", JSONArray(languages))
            .put("fromYear", fromYear)
            .put("toYear", toYear)
            .put("exact", exact)
            .put("fullText", fullText)
            .put("phrase", phrase)

        val json = rawCall("search", payload)
        val arr = json.optJSONArray("books") ?: JSONArray()
        val books = buildList {
            for (i in 0 until arr.length()) {
                add(parseBook(arr.getJSONObject(i)))
            }
        }
        SearchResponse(
            books = books,
            page = json.optInt("page", page),
            totalPages = json.optInt("total_pages", 1),
        )
    }

    suspend fun bookDetail(bookId: String): BookItem = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("bookId", bookId)
        val json = rawCall("bookDetail", payload)
        parseBook(json.getJSONObject("book"))
    }

    suspend fun startDownload(
        downloadId: String,
        bookId: String,
        directUrl: String,
        destDir: String,
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("downloadId", downloadId)
            .put("bookId", bookId)
            .put("directUrl", directUrl)
            .put("destDir", destDir)
        rawCall("startDownload", payload)
        Unit
    }

    suspend fun downloadProgress(downloadId: String): DownloadProgress = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("downloadId", downloadId)
        val json = rawCall("downloadProgress", payload)
        DownloadProgress(
            id = json.optString("id"),
            state = json.optString("state"),
            written = json.optLong("written", 0L),
            total = json.optLong("total", 0L),
            filePath = json.optString("file_path"),
            size = json.optLong("size", 0L),
            error = json.optString("error"),
        )
    }

    suspend fun cancelDownload(downloadId: String) = withContext(Dispatchers.IO) {
        rawCall("cancelDownload", JSONObject().put("downloadId", downloadId))
        Unit
    }

    suspend fun history(page: Int = 1): HistoryResponse = withContext(Dispatchers.IO) {
        val json = rawCall("history", JSONObject().put("page", page))
        val arr = json.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    HistoryItem(
                        id = obj.optString("id"),
                        commandId = obj.optString("command_id"),
                        hash = obj.optString("hash"),
                        name = obj.optString("name"),
                        url = obj.optString("url"),
                        downloadUrl = obj.optString("download_url"),
                        extension = obj.optString("extension"),
                        size = obj.optString("size"),
                        date = obj.optString("date"),
                    )
                )
            }
        }
        HistoryResponse(
            items = items,
            page = json.optInt("page", page),
            totalPages = json.optInt("total_pages", 1),
        )
    }

    suspend fun saveKindle(
        to: String,
        from: String,
        smtpHost: String,
        smtpPort: Int,
        smtpPassword: String,
    ): EngineStatus = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("to", to)
            .put("from", from)
            .put("smtpHost", smtpHost)
            .put("smtpPort", smtpPort)
            .put("smtpPassword", smtpPassword)
        parseStatus(rawCall("saveKindle", payload))
    }

    suspend fun sendToKindle(filePath: String): String = withContext(Dispatchers.IO) {
        val json = rawCall("sendToKindle", JSONObject().put("filePath", filePath))
        json.optString("to")
    }

    private fun parseStatus(json: JSONObject): EngineStatus = EngineStatus(
        loggedIn = json.optBoolean("loggedIn", false),
        loggedOutManually = json.optBoolean("loggedOutManually", false),
        domain = json.optString("domain", "https://z-lib.gd"),
        mode = json.optString("mode", "eapi"),
        lastEmail = json.optString("lastEmail"),
        kindleReady = json.optBoolean("kindleReady", false),
        kindleTo = json.optString("kindleTo"),
        kindleFrom = json.optString("kindleFrom"),
        kindleSmtpHost = json.optString("kindleSmtpHost").ifBlank { "smtp.gmail.com" },
        kindleSmtpPort = json.optInt("kindleSmtpPort", 587).let { if (it <= 0) 587 else it },
        hasSmtpPassword = json.optBoolean("hasSmtpPassword", false),
    )

    private fun parseBook(obj: JSONObject): BookItem {
        val authorsArr = obj.optJSONArray("authors")
        val authors = buildList {
            if (authorsArr != null) {
                for (i in 0 until authorsArr.length()) {
                    val a = authorsArr.optString(i)
                    if (a.isNotBlank()) add(a)
                }
            }
        }
        return BookItem(
            id = obj.optString("id"),
            commandId = obj.optString("command_id"),
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
            downloadUrl = obj.optString("download_url"),
        )
    }
}
