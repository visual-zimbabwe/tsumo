package com.tsumo.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tsumo.app.data.BookItem
import com.tsumo.app.data.DownloadProgress
import com.tsumo.app.data.EngineStatus
import com.tsumo.app.data.HistoryItem
import com.tsumo.app.data.LocalShelfBook
import com.tsumo.app.data.ShelfStore
import com.tsumo.app.data.SourceCheck
import com.tsumo.app.data.TsumoTab
import com.tsumo.app.engine.ArchiveEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class TsumoUiState(
    val initialized: Boolean = false,
    val currentTab: TsumoTab = TsumoTab.DISCOVER,
    val status: EngineStatus = EngineStatus(),

    // Discover
    val searchQuery: String = "",
    val activeSearchQuery: String = "",
    val selectedExtFilter: String = "ALL",
    val discoverLoading: Boolean = false,
    val discoverError: String? = null,
    val discoverBooks: List<BookItem> = emptyList(),
    val discoverPage: Int = 1,
    val discoverTotalPages: Int = 1,

    // Advanced Search Filters (shared)
    val filterLanguage: String = "",
    val filterFromYear: String = "",
    val filterToYear: String = "",
    val filterExact: Boolean = false,

    // Catalog (History & Full-Text)
    val catalogSection: Int = 0, // 0 = History, 1 = Full-Text
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val historyItems: List<HistoryItem> = emptyList(),
    val historyPage: Int = 1,
    val historyTotalPages: Int = 1,
    val fullTextQuery: String = "",
    val fullTextPhrase: Boolean = false,
    val fullTextLoading: Boolean = false,
    val fullTextError: String? = null,
    val fullTextBooks: List<BookItem> = emptyList(),
    val fullTextPage: Int = 1,
    val fullTextTotalPages: Int = 1,

    // Shelves
    val shelvesSection: Int = 0, // 0 = Downloaded, 1 = Saved
    val downloadedBooks: List<LocalShelfBook> = emptyList(),
    val bookmarkedBooks: List<BookItem> = emptyList(),
    val sendingToKindleKey: String? = null,

    // Drawer
    val selectedBook: BookItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val activeDownload: DownloadProgress? = null,

    // Settings Sheet (Sources, Doctor, Kindle)
    val accountOpen: Boolean = false,
    val sourcesSaving: Boolean = false,
    val doctorLoading: Boolean = false,
    val doctorResults: List<SourceCheck> = emptyList(),
    val doctorError: String? = null,
    val kindleSaving: Boolean = false,

    // Toast
    val toastMessage: String? = null,
)

class TsumoViewModel(application: Application) : AndroidViewModel(application) {
    private val shelfStore = ShelfStore(application.applicationContext)
    private val _uiState = MutableStateFlow(TsumoUiState())
    val uiState: StateFlow<TsumoUiState> = _uiState.asStateFlow()

    private var toastJob: Job? = null
    private var downloadPollJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                val status = ArchiveEngine.init(getApplication<Application>().filesDir.absolutePath)
                val downloaded = shelfStore.loadDownloaded()
                val bookmarks = shelfStore.loadBookmarks()
                _uiState.update {
                    it.copy(
                        initialized = true,
                        status = status,
                        downloadedBooks = downloaded,
                        bookmarkedBooks = bookmarks,
                    )
                }
                loadHistory(1)
            }.onFailure { err ->
                _uiState.update { it.copy(initialized = true) }
                showToast(err.message ?: "Engine initialization failed")
            }
        }
    }

    fun selectTab(tab: TsumoTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == TsumoTab.CATALOG && _uiState.value.historyItems.isEmpty()) {
            loadHistory(1)
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun selectExtFilter(ext: String) {
        _uiState.update { it.copy(selectedExtFilter = ext) }
        val activeQ = _uiState.value.activeSearchQuery.ifBlank { _uiState.value.searchQuery.trim() }
        if (activeQ.isNotBlank()) {
            searchBooks(activeQ, 1)
        }
    }

    fun updateAdvancedFilters(language: String, fromYear: String, toYear: String, exact: Boolean) {
        _uiState.update {
            it.copy(
                filterLanguage = language,
                filterFromYear = fromYear,
                filterToYear = toYear,
                filterExact = exact,
            )
        }
    }

    fun searchBooks(queryOverride: String? = null, page: Int = 1) {
        val q = (queryOverride ?: _uiState.value.searchQuery).trim()
        if (q.isBlank()) {
            _uiState.update {
                it.copy(
                    searchQuery = "",
                    activeSearchQuery = "",
                    discoverBooks = emptyList(),
                    discoverError = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                searchQuery = q,
                activeSearchQuery = q,
                discoverLoading = true,
                discoverError = null,
            )
        }

        val state = _uiState.value
        val exts = if (state.selectedExtFilter == "ALL") emptyList() else listOf(state.selectedExtFilter)
        val langs = if (state.filterLanguage.isBlank()) emptyList() else listOf(state.filterLanguage)
        val fromY = state.filterFromYear.toIntOrNull() ?: 0
        val toY = state.filterToYear.toIntOrNull() ?: 0

        viewModelScope.launch {
            runCatching {
                ArchiveEngine.search(
                    query = q,
                    page = page,
                    count = 24,
                    extensions = exts,
                    languages = langs,
                    fromYear = fromY,
                    toYear = toY,
                    exact = state.filterExact,
                )
            }.onSuccess { res ->
                _uiState.update {
                    it.copy(
                        discoverLoading = false,
                        discoverBooks = res.books,
                        discoverPage = res.page,
                        discoverTotalPages = res.totalPages.coerceAtLeast(1),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        discoverLoading = false,
                        discoverError = err.message ?: "Search failed",
                    )
                }
            }
        }
    }

    fun setCatalogSection(section: Int) {
        _uiState.update { it.copy(catalogSection = section) }
        if (section == 0 && _uiState.value.historyItems.isEmpty()) {
            loadHistory(1)
        }
    }

    fun loadHistory(page: Int = 1) {
        _uiState.update { it.copy(historyLoading = true, historyError = null) }
        viewModelScope.launch {
            runCatching { ArchiveEngine.history(page) }
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            historyLoading = false,
                            historyItems = res.items,
                            historyPage = res.page,
                            historyTotalPages = res.totalPages.coerceAtLeast(1),
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            historyLoading = false,
                            historyError = err.message ?: "Failed to load history",
                        )
                    }
                }
        }
    }

    fun setFullTextQuery(q: String) {
        _uiState.update { it.copy(fullTextQuery = q) }
    }

    fun toggleFullTextPhrase() {
        _uiState.update { it.copy(fullTextPhrase = !it.fullTextPhrase) }
    }

    fun searchFullText(page: Int = 1) {
        val q = _uiState.value.fullTextQuery.trim()
        if (q.isBlank()) return

        val phrase = _uiState.value.fullTextPhrase
        val state = _uiState.value
        val exts = if (state.selectedExtFilter == "ALL") emptyList() else listOf(state.selectedExtFilter)
        val langs = if (state.filterLanguage.isBlank()) emptyList() else listOf(state.filterLanguage)

        _uiState.update { it.copy(fullTextLoading = true, fullTextError = null) }
        viewModelScope.launch {
            runCatching {
                ArchiveEngine.search(
                    query = q,
                    page = page,
                    count = 20,
                    extensions = exts,
                    languages = langs,
                    fromYear = state.filterFromYear.toIntOrNull() ?: 0,
                    toYear = state.filterToYear.toIntOrNull() ?: 0,
                    exact = state.filterExact,
                    fullText = true,
                    phrase = phrase,
                )
            }.onSuccess { res ->
                _uiState.update {
                    it.copy(
                        fullTextLoading = false,
                        fullTextBooks = res.books,
                        fullTextPage = res.page,
                        fullTextTotalPages = res.totalPages.coerceAtLeast(1),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        fullTextLoading = false,
                        fullTextError = err.message ?: "Full-text search failed",
                    )
                }
            }
        }
    }

    fun setShelvesSection(section: Int) {
        _uiState.update { it.copy(shelvesSection = section) }
    }

    fun openBookDrawer(book: BookItem) {
        _uiState.update {
            it.copy(
                selectedBook = book,
                detailLoading = book.effectiveId.isNotBlank(),
                detailError = null,
            )
        }
        val ref = book.effectiveId
        if (ref.isBlank()) {
            _uiState.update { it.copy(detailLoading = false) }
            return
        }
        viewModelScope.launch {
            runCatching { ArchiveEngine.bookDetail(ref) }
                .onSuccess { detailed ->
                    _uiState.update { state ->
                        if (state.selectedBook?.effectiveId == ref || state.selectedBook?.name == book.name) {
                            val merged = detailed.copy(
                                id = detailed.id.ifBlank { book.id },
                                commandId = detailed.commandId.ifBlank { book.commandId },
                                cover = detailed.cover.ifBlank { book.cover },
                                authors = detailed.authors.ifEmpty { book.authors },
                                publisher = detailed.publisher.ifBlank { book.publisher },
                                year = detailed.year.ifBlank { book.year },
                                language = detailed.language.ifBlank { book.language },
                                extension = detailed.extension.ifBlank { book.extension },
                                size = detailed.size.ifBlank { book.size },
                                description = detailed.description.ifBlank { book.description },
                                categories = detailed.categories.ifBlank { book.categories },
                                downloadUrl = detailed.downloadUrl.ifBlank { book.downloadUrl },
                                source = detailed.source.ifBlank { book.source },
                            )
                            state.copy(selectedBook = merged, detailLoading = false)
                        } else {
                            state
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            detailLoading = false,
                            detailError = err.message,
                        )
                    }
                }
        }
    }

    fun closeBookDrawer() {
        _uiState.update { it.copy(selectedBook = null, detailLoading = false, detailError = null) }
    }

    fun downloadSelectedBook(sendToKindleAfter: Boolean = false) {
        val book = _uiState.value.selectedBook ?: return
        if (sendToKindleAfter && !_uiState.value.status.kindleReady) {
            _uiState.update { it.copy(accountOpen = true) }
            showToast("Configure Kindle SMTP first")
            return
        }

        val existing = _uiState.value.downloadedBooks.firstOrNull {
            it.key == book.effectiveId && book.effectiveId.isNotBlank()
        }
        if (existing != null && sendToKindleAfter) {
            sendFileToKindle(existing.filePath, existing.key)
            return
        }

        val dlId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                activeDownload = DownloadProgress(id = dlId, state = "fetching")
            )
        }

        downloadPollJob?.cancel()
        downloadPollJob = viewModelScope.launch {
            runCatching {
                ArchiveEngine.startDownload(
                    downloadId = dlId,
                    bookId = book.effectiveId,
                    directUrl = book.downloadUrl,
                    destDir = shelfStore.booksDir.absolutePath,
                    title = book.name,
                    extension = book.extension,
                )
            }.onFailure { err ->
                _uiState.update { it.copy(activeDownload = DownloadProgress(dlId, "error", error = err.message ?: "Failed")) }
                showToast(err.message ?: "Download failed")
                return@launch
            }

            while (isActive) {
                delay(180)
                val prog = runCatching { ArchiveEngine.downloadProgress(dlId) }.getOrNull() ?: continue
                _uiState.update { it.copy(activeDownload = prog) }
                when (prog.state) {
                    "complete" -> {
                        val saved = shelfStore.saveDownloadedEntry(book, prog.filePath, prog.size)
                        val all = shelfStore.loadDownloaded()
                        _uiState.update { it.copy(downloadedBooks = all) }
                        loadHistory(1)
                        if (sendToKindleAfter) {
                            sendFileToKindle(saved.filePath, saved.key)
                        } else {
                            showToast("Downloaded to storage")
                        }
                        break
                    }
                    "error" -> {
                        showToast(prog.error.ifBlank { "Download failed" })
                        break
                    }
                    "cancelled" -> {
                        showToast("Download cancelled")
                        break
                    }
                }
            }
        }
    }

    fun cancelActiveDownload() {
        val dlId = _uiState.value.activeDownload?.id ?: return
        viewModelScope.launch {
            runCatching { ArchiveEngine.cancelDownload(dlId) }
            downloadPollJob?.cancel()
            _uiState.update { it.copy(activeDownload = null) }
            showToast("Download cancelled")
        }
    }

    fun toggleBookmark(book: BookItem) {
        viewModelScope.launch {
            val updated = shelfStore.toggleBookmark(book)
            val savedNow = updated.any { it.effectiveId == book.effectiveId && it.name == book.name }
            _uiState.update { it.copy(bookmarkedBooks = updated) }
            showToast(if (savedNow) "Saved to shelf" else "Removed from shelf")
        }
    }

    fun openLocalBook(filePath: String) {
        val app = getApplication<Application>()
        runCatching {
            val intent = shelfStore.openBookIntent(filePath)
            app.startActivity(intent)
        }.onFailure {
            showToast("No reader app found for this file")
        }
    }

    fun shareLocalBook(filePath: String) {
        val app = getApplication<Application>()
        runCatching {
            val chooser = Intent.createChooser(shelfStore.shareBookIntent(filePath), "Share book")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(chooser)
        }.onFailure {
            showToast("Could not share file")
        }
    }

    fun removeLocalBook(entry: LocalShelfBook) {
        viewModelScope.launch {
            shelfStore.removeDownloaded(entry)
            val updated = shelfStore.loadDownloaded()
            _uiState.update { it.copy(downloadedBooks = updated) }
            showToast("Removed from device")
        }
    }

    fun sendFileToKindle(filePath: String, key: String = filePath) {
        if (!_uiState.value.status.kindleReady) {
            _uiState.update { it.copy(accountOpen = true) }
            showToast("Configure Kindle SMTP first")
            return
        }
        _uiState.update { it.copy(sendingToKindleKey = key) }
        viewModelScope.launch {
            runCatching { ArchiveEngine.sendToKindle(filePath) }
                .onSuccess { toAddr ->
                    _uiState.update { it.copy(sendingToKindleKey = null) }
                    showToast("Sent to $toAddr")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(sendingToKindleKey = null) }
                    showToast(err.message ?: "Kindle delivery failed")
                }
        }
    }

    // Sources, Doctor & Kindle Settings
    fun openAccountSheet() {
        _uiState.update { it.copy(accountOpen = true) }
    }

    fun closeAccountSheet() {
        _uiState.update { it.copy(accountOpen = false) }
    }

    fun updateSources(enableGutenberg: Boolean, enableInternetArchive: Boolean) {
        _uiState.update { it.copy(sourcesSaving = true) }
        viewModelScope.launch {
            runCatching { ArchiveEngine.setSources(enableGutenberg, enableInternetArchive) }
                .onSuccess { status ->
                    _uiState.update { it.copy(sourcesSaving = false, status = status) }
                    showToast("Archive sources updated")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(sourcesSaving = false) }
                    showToast(err.message ?: "Could not update sources")
                }
        }
    }

    fun runDoctor() {
        _uiState.update { it.copy(doctorLoading = true, doctorError = null) }
        viewModelScope.launch {
            runCatching { ArchiveEngine.doctor() }
                .onSuccess { results ->
                    _uiState.update { it.copy(doctorLoading = false, doctorResults = results) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            doctorLoading = false,
                            doctorError = err.message ?: "Source check failed",
                        )
                    }
                }
        }
    }

    fun saveKindleConfig(to: String, from: String, smtpHost: String, smtpPort: Int, password: String) {
        _uiState.update { it.copy(kindleSaving = true) }
        viewModelScope.launch {
            runCatching { ArchiveEngine.saveKindle(to, from, smtpHost, smtpPort, password) }
                .onSuccess { status ->
                    _uiState.update { it.copy(kindleSaving = false, status = status) }
                    showToast("Kindle configuration saved")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(kindleSaving = false) }
                    showToast(err.message ?: "Invalid Kindle settings")
                }
        }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2600)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }
}
