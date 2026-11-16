package com.tsumo.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tsumo.app.data.Article
import com.tsumo.app.data.BrowserLauncher
import com.tsumo.app.data.DiscoveredFeed
import com.tsumo.app.data.EngineStatus
import com.tsumo.app.data.ExportStore
import com.tsumo.app.data.Feed
import com.tsumo.app.data.HistoryEntry
import com.tsumo.app.data.LocalExportItem
import com.tsumo.app.data.OpenMode
import com.tsumo.app.data.ReadChannel
import com.tsumo.app.data.TsumoTab
import com.tsumo.app.engine.FeedEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TsumoUiState(
    val initialized: Boolean = false,
    val currentTab: TsumoTab = TsumoTab.READ,
    val status: EngineStatus = EngineStatus(),

    // Feeds & navigation
    val feeds: List<Feed> = emptyList(),
    val pendingDeleteIds: Set<Long> = emptySet(),
    val undoFeed: Feed? = null,
    val totalUnread: Int = 0,
    val tagUnreads: Map<String, Int> = emptyMap(),
    val channels: List<ReadChannel> = listOf(ReadChannel.All, ReadChannel.Unread, ReadChannel.Archived),
    val selectedChannelKey: String = "all",
    val feedsFilterQuery: String = "",

    // Articles on Read tab
    val articles: List<Article> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingArticles: Boolean = false,
    val readError: String? = null,

    // Split-second search
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchScope: String = "all", // all | archived | history
    val searchArticles: List<Article> = emptyList(),
    val searchFeeds: List<Feed> = emptyList(),
    val isSearching: Boolean = false,

    // Article drawer & In-App Reader
    val selectedArticle: Article? = null,
    val readerOpen: Boolean = false,
    val readerHtml: String = "",
    val readerLoading: Boolean = false,
    val readerTextScale: Float = 1.0f,
    val exportingType: String? = null, // "MD" | "PDF" | null

    // Library tab (0 = Archived, 1 = History, 2 = Local Files)
    val librarySection: Int = 0,
    val archivedArticles: List<Article> = emptyList(),
    val historyEntries: List<HistoryEntry> = emptyList(),
    val historyQuery: String = "",
    val localExports: List<LocalExportItem> = emptyList(),
    val emailSendingKey: String? = null,

    // Add Feed sheet
    val addFeedOpen: Boolean = false,
    val addFeedUrl: String = "",
    val addFeedTagsText: String = "",
    val addFeedCustomTitle: String = "",
    val discoveredFeed: DiscoveredFeed? = null,
    val isDiscovering: Boolean = false,
    val isAddingFeed: Boolean = false,
    val addFeedError: String? = null,

    // Settings / Account sheet
    val settingsOpen: Boolean = false,
    val toastMessage: String? = null,
) {
    val visibleFeeds: List<Feed>
        get() = feeds.filterNot { it.id in pendingDeleteIds }

    val selectedChannel: ReadChannel
        get() = channels.firstOrNull { it.key == selectedChannelKey } ?: ReadChannel.All
}

class TsumoViewModel(application: Application) : AndroidViewModel(application) {
    private val exportStore = ExportStore(application.applicationContext)
    private val _uiState = MutableStateFlow(TsumoUiState())
    val uiState: StateFlow<TsumoUiState> = _uiState.asStateFlow()

    private var toastJob: Job? = null
    private var searchJob: Job? = null
    private var undoDeleteJob: Job? = null
    private var pendingSharedUrl: String? = null

    init {
        viewModelScope.launch {
            runCatching {
                val appCtx = getApplication<Application>()
                val status = FeedEngine.init(appCtx.filesDir.absolutePath)
                _uiState.update { it.copy(initialized = true, status = status) }
                reloadAllLocal()
                pendingSharedUrl?.let { url ->
                    pendingSharedUrl = null
                    openAddFeedSheet(prefillUrl = url)
                }
                refreshFeeds(silent = true)
            }.onFailure { err ->
                _uiState.update { it.copy(initialized = true, readError = err.message ?: "Engine failed") }
            }
        }
    }

    private suspend fun reloadAllLocal() {
        val snap = FeedEngine.listFeeds()
        val status = FeedEngine.status()
        val exports = exportStore.loadExports()
        val archived = FeedEngine.listItems(filter = "archived", limit = 200)
        val history = FeedEngine.history(query = _uiState.value.historyQuery, limit = 150)

        val channels = buildChannels(snap.feeds, snap.tagUnreads)
        _uiState.update { s ->
            val validKey = if (channels.any { it.key == s.selectedChannelKey }) s.selectedChannelKey else "all"
            s.copy(
                status = status,
                feeds = snap.feeds,
                totalUnread = snap.totalUnread,
                tagUnreads = snap.tagUnreads,
                channels = channels,
                selectedChannelKey = validKey,
                localExports = exports,
                archivedArticles = archived,
                historyEntries = history,
            )
        }
        loadArticlesForChannel(_uiState.value.selectedChannel)
    }

    private fun buildChannels(feeds: List<Feed>, tagUnreads: Map<String, Int>): List<ReadChannel> {
        val list = mutableListOf<ReadChannel>(ReadChannel.All, ReadChannel.Unread, ReadChannel.Archived)
        val tags = feeds.flatMap { it.tags }.distinct().sortedByDescending { tagUnreads[it] ?: 0 }
        tags.forEach { list.add(ReadChannel.Tag(it)) }
        feeds.filterNot { it.id in _uiState.value.pendingDeleteIds }.forEach {
            list.add(ReadChannel.FeedChannel(it))
        }
        return list
    }

    fun selectTab(tab: TsumoTab) {
        _uiState.update { it.copy(currentTab = tab, searchActive = false) }
        if (tab == TsumoTab.LIBRARY) {
            viewModelScope.launch { reloadLibrary() }
        }
    }

    fun selectChannel(channel: ReadChannel) {
        if (_uiState.value.selectedChannelKey == channel.key) return
        _uiState.update { it.copy(selectedChannelKey = channel.key, isLoadingArticles = true) }
        viewModelScope.launch { loadArticlesForChannel(channel) }
    }

    fun stepChannel(delta: Int) {
        val channels = _uiState.value.channels
        if (channels.isEmpty()) return
        val idx = channels.indexOfFirst { it.key == _uiState.value.selectedChannelKey }.coerceAtLeast(0)
        val next = (idx + delta).coerceIn(0, channels.lastIndex)
        if (next != idx) {
            selectChannel(channels[next])
        }
    }

    fun openFeedInRead(feed: Feed) {
        val ch = ReadChannel.FeedChannel(feed)
        _uiState.update {
            it.copy(
                currentTab = TsumoTab.READ,
                searchActive = false,
                selectedChannelKey = ch.key,
                isLoadingArticles = true,
            )
        }
        viewModelScope.launch { loadArticlesForChannel(ch) }
    }

    private suspend fun loadArticlesForChannel(channel: ReadChannel) {
        runCatching {
            val items = when (channel) {
                ReadChannel.All -> FeedEngine.listItems(filter = "all", limit = 150)
                ReadChannel.Unread -> FeedEngine.listItems(filter = "unread", limit = 150)
                ReadChannel.Archived -> FeedEngine.listItems(filter = "archived", limit = 150)
                is ReadChannel.Tag -> FeedEngine.listItems(tag = channel.tag, filter = "all", limit = 150)
                is ReadChannel.FeedChannel -> FeedEngine.listItems(feedId = channel.feed.id, filter = "all", limit = 150)
            }
            _uiState.update { it.copy(articles = items, isLoadingArticles = false, readError = null) }
        }.onFailure { err ->
            _uiState.update { it.copy(isLoadingArticles = false, readError = err.message) }
        }
    }

    fun refreshFeeds(silent: Boolean = false) {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, readError = null) }
        viewModelScope.launch {
            runCatching {
                val ch = _uiState.value.selectedChannel
                val targetIds = if (ch is ReadChannel.FeedChannel) listOf(ch.feed.id) else emptyList()
                val summary = FeedEngine.refresh(targetIds)
                reloadAllLocal()
                if (!silent) {
                    val msg = buildString {
                        append("Refreshed ${summary.refreshed} feeds")
                        if (summary.newItems > 0) append(" · ${summary.newItems} new")
                        if (summary.errorCount > 0) append(" · ${summary.errorCount} failed")
                    }
                    showToast(msg)
                }
            }.onFailure { err ->
                if (!silent) showToast(err.message ?: "Refresh failed")
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // --- Split-second FTS5 search ---

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            if (active) it.copy(searchActive = true)
            else it.copy(searchActive = false, searchQuery = "", searchArticles = emptyList(), searchFeeds = emptyList())
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q, searchActive = true) }
        triggerSearch(q, _uiState.value.searchScope)
    }

    fun setSearchScope(scope: String) {
        _uiState.update { it.copy(searchScope = scope) }
        triggerSearch(_uiState.value.searchQuery, scope)
    }

    private fun triggerSearch(query: String, scope: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchArticles = emptyList(), searchFeeds = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(45)
            _uiState.update { it.copy(isSearching = true) }
            runCatching {
                FeedEngine.search(trimmed, scope = scope, limit = 50)
            }.onSuccess { res ->
                _uiState.update {
                    it.copy(searchArticles = res.items, searchFeeds = res.feeds, isSearching = false)
                }
            }.onFailure {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    // --- Article actions: Reader, Custom Tab, External Browser, Share, Archive, MD/PDF ---

    fun onArticleTapped(article: Article) {
        when (_uiState.value.status.openMode) {
            OpenMode.READER -> openArticleDrawer(article)
            OpenMode.CUSTOM_TAB -> openInCustomTab(article)
            OpenMode.BROWSER -> openInExternalBrowser(article)
        }
    }

    fun openArticleDrawer(article: Article) {
        _uiState.update { it.copy(selectedArticle = article) }
    }

    fun closeArticleDrawer() {
        _uiState.update { it.copy(selectedArticle = null) }
    }

    fun openInReader(article: Article) {
        _uiState.update {
            it.copy(
                selectedArticle = article,
                readerOpen = true,
                readerLoading = true,
                readerHtml = "",
            )
        }
        viewModelScope.launch {
            recordOpenAndRefreshCounts(article.id, "reader")
            runCatching {
                FeedEngine.articleContent(article.id)
            }.onSuccess { data ->
                _uiState.update { it.copy(readerHtml = data.html, readerLoading = false) }
            }.onFailure {
                val fallback = "<p>${article.summary.ifBlank { "Unable to fetch article body." }}</p>"
                _uiState.update { it.copy(readerHtml = fallback, readerLoading = false) }
            }
        }
    }

    fun closeReader() {
        _uiState.update { it.copy(readerOpen = false) }
    }

    fun adjustReaderTextScale(delta: Float) {
        _uiState.update {
            it.copy(readerTextScale = (it.readerTextScale + delta).coerceIn(0.8f, 1.6f))
        }
    }

    fun openInCustomTab(article: Article) {
        val ctx = getApplication<Application>()
        if (BrowserLauncher.openCustomTab(ctx, article.url)) {
            viewModelScope.launch { recordOpenAndRefreshCounts(article.id, "custom_tab") }
        } else {
            showToast("Article has no web URL")
        }
    }

    fun openInExternalBrowser(article: Article) {
        val ctx = getApplication<Application>()
        if (BrowserLauncher.openExternalBrowser(ctx, article.url)) {
            viewModelScope.launch { recordOpenAndRefreshCounts(article.id, "browser") }
        } else {
            showToast("Article has no web URL")
        }
    }

    private suspend fun recordOpenAndRefreshCounts(articleId: Long, via: String) {
        runCatching {
            FeedEngine.recordOpen(articleId, via)
            updateArticleFlagsLocally(articleId) { it.copy(unread = false) }
            val snap = FeedEngine.listFeeds()
            val history = FeedEngine.history(query = _uiState.value.historyQuery, limit = 150)
            _uiState.update {
                it.copy(
                    feeds = snap.feeds,
                    totalUnread = snap.totalUnread,
                    tagUnreads = snap.tagUnreads,
                    channels = buildChannels(snap.feeds, snap.tagUnreads),
                    historyEntries = history,
                )
            }
        }
    }

    fun shareArticle(article: Article) {
        BrowserLauncher.shareArticle(getApplication(), article)
    }

    fun toggleRead(article: Article) {
        val nextUnread = !article.unread
        viewModelScope.launch {
            runCatching {
                FeedEngine.markRead(listOf(article.id), read = !nextUnread)
                updateArticleFlagsLocally(article.id) { it.copy(unread = nextUnread) }
                val snap = FeedEngine.listFeeds()
                _uiState.update {
                    it.copy(
                        feeds = snap.feeds,
                        totalUnread = snap.totalUnread,
                        tagUnreads = snap.tagUnreads,
                        channels = buildChannels(snap.feeds, snap.tagUnreads),
                    )
                }
            }
        }
    }

    fun markCurrentChannelRead() {
        val ch = _uiState.value.selectedChannel
        viewModelScope.launch {
            runCatching {
                when (ch) {
                    is ReadChannel.FeedChannel -> FeedEngine.markFeedRead(ch.feed.id)
                    else -> {
                        val ids = _uiState.value.articles.filter { it.unread }.map { it.id }
                        if (ids.isNotEmpty()) FeedEngine.markRead(ids, read = true)
                    }
                }
                reloadAllLocal()
                showToast("Marked all read")
            }
        }
    }

    fun toggleArchive(article: Article) {
        val nextArchived = !article.archived
        viewModelScope.launch {
            runCatching {
                FeedEngine.setArchived(article.id, nextArchived)
                updateArticleFlagsLocally(article.id) { it.copy(archived = nextArchived) }
                val archived = FeedEngine.listItems(filter = "archived", limit = 200)
                val status = FeedEngine.status()
                _uiState.update { it.copy(archivedArticles = archived, status = status) }
                showToast(if (nextArchived) "Archived for offline reading" else "Removed from archive")
            }.onFailure { err ->
                showToast(err.message ?: "Archive update failed")
            }
        }
    }

    fun exportMarkdown(article: Article) {
        _uiState.update { it.copy(exportingType = "MD") }
        viewModelScope.launch {
            runCatching {
                val data = FeedEngine.exportMarkdown(article.id)
                exportStore.saveMarkdownExport(article, data.markdown, data.filename)
            }.onSuccess { item ->
                val exports = exportStore.loadExports()
                _uiState.update { it.copy(exportingType = null, localExports = exports) }
                showToast("Saved ${item.extension} to Downloads/Tsumo")
            }.onFailure { err ->
                _uiState.update { it.copy(exportingType = null) }
                showToast(err.message ?: "Markdown export failed")
            }
        }
    }

    fun exportPdf(article: Article) {
        _uiState.update { it.copy(exportingType = "PDF") }
        viewModelScope.launch {
            runCatching {
                val content = FeedEngine.articleContent(article.id)
                val mdMeta = FeedEngine.exportMarkdown(article.id)
                exportStore.savePdfExport(article, content.paragraphs, mdMeta.filename)
            }.onSuccess { item ->
                val exports = exportStore.loadExports()
                _uiState.update { it.copy(exportingType = null, localExports = exports) }
                showToast("Saved PDF to Downloads/Tsumo")
            }.onFailure { err ->
                _uiState.update { it.copy(exportingType = null) }
                showToast(err.message ?: "PDF export failed")
            }
        }
    }

    // --- Feeds tab: Add, Remove + Undo, Import/Export ---

    fun setFeedsFilterQuery(q: String) {
        _uiState.update { it.copy(feedsFilterQuery = q) }
    }

    fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val match = Regex("""https?://\S+""").find(shared)?.value ?: return
        if (!_uiState.value.initialized) {
            pendingSharedUrl = match
        } else {
            openAddFeedSheet(prefillUrl = match)
        }
    }

    fun openAddFeedSheet(prefillUrl: String = "") {
        _uiState.update {
            it.copy(
                addFeedOpen = true,
                addFeedUrl = prefillUrl,
                addFeedTagsText = "",
                addFeedCustomTitle = "",
                discoveredFeed = null,
                addFeedError = null,
            )
        }
        if (prefillUrl.isNotBlank()) {
            discoverFeed(prefillUrl)
        }
    }

    fun closeAddFeedSheet() {
        _uiState.update { it.copy(addFeedOpen = false, addFeedError = null) }
    }

    fun setAddFeedUrl(url: String) {
        _uiState.update { it.copy(addFeedUrl = url, discoveredFeed = null, addFeedError = null) }
    }

    fun setAddFeedTags(tags: String) {
        _uiState.update { it.copy(addFeedTagsText = tags) }
    }

    fun setAddFeedCustomTitle(title: String) {
        _uiState.update { it.copy(addFeedCustomTitle = title) }
    }

    fun discoverFeed(urlOverride: String? = null) {
        val raw = (urlOverride ?: _uiState.value.addFeedUrl).trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(addFeedError = "Enter a website or RSS/Atom URL") }
            return
        }
        _uiState.update { it.copy(isDiscovering = true, addFeedError = null) }
        viewModelScope.launch {
            runCatching {
                FeedEngine.discoverFeed(raw)
            }.onSuccess { disc ->
                _uiState.update {
                    it.copy(
                        isDiscovering = false,
                        discoveredFeed = disc,
                        addFeedUrl = disc.url,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDiscovering = false,
                        addFeedError = err.message ?: "No feed found",
                    )
                }
            }
        }
    }

    fun confirmAddFeed() {
        val s = _uiState.value
        val url = s.addFeedUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(addFeedError = "URL is required") }
            return
        }
        val tags = s.addFeedTagsText.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        _uiState.update { it.copy(isAddingFeed = true, addFeedError = null) }
        viewModelScope.launch {
            runCatching {
                FeedEngine.addFeed(url, customTitle = s.addFeedCustomTitle.trim(), tags = tags)
            }.onSuccess { feed ->
                _uiState.update { it.copy(isAddingFeed = false, addFeedOpen = false) }
                reloadAllLocal()
                openFeedInRead(feed)
                showToast("Added ${feed.displayTitle}")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAddingFeed = false,
                        addFeedError = err.message ?: "Could not add feed",
                    )
                }
            }
        }
    }

    fun removeFeedWithUndo(feed: Feed) {
        // Commit any previous pending delete first
        val prev = _uiState.value.undoFeed
        undoDeleteJob?.cancel()
        viewModelScope.launch {
            if (prev != null && prev.id != feed.id) {
                runCatching { FeedEngine.removeFeed(prev.id) }
            }
            val nextPending = (_uiState.value.pendingDeleteIds - (prev?.id ?: -1L)) + feed.id
            _uiState.update { s ->
                val remainingFeeds = s.feeds.filterNot { it.id in nextPending }
                s.copy(
                    pendingDeleteIds = nextPending,
                    undoFeed = feed,
                    channels = buildChannels(remainingFeeds, s.tagUnreads),
                )
            }
            undoDeleteJob = viewModelScope.launch {
                delay(4500)
                val toDelete = _uiState.value.undoFeed ?: return@launch
                if (toDelete.id == feed.id) {
                    runCatching { FeedEngine.removeFeed(feed.id) }
                    _uiState.update {
                        it.copy(
                            pendingDeleteIds = it.pendingDeleteIds - feed.id,
                            undoFeed = null,
                        )
                    }
                    reloadAllLocal()
                }
            }
        }
    }

    fun undoRemoveFeed() {
        undoDeleteJob?.cancel()
        val feed = _uiState.value.undoFeed ?: return
        _uiState.update { s ->
            val nextPending = s.pendingDeleteIds - feed.id
            s.copy(
                pendingDeleteIds = nextPending,
                undoFeed = null,
                channels = buildChannels(s.feeds.filterNot { it.id in nextPending }, s.tagUnreads),
            )
        }
        showToast("Restored ${feed.displayTitle}")
    }

    fun importFeedsContent(rawContent: String, fileName: String) {
        viewModelScope.launch {
            runCatching {
                val trimmed = rawContent.trim()
                val importedCount = if (fileName.endsWith(".opml", true) ||
                    fileName.endsWith(".xml", true) ||
                    trimmed.startsWith("<")
                ) {
                    FeedEngine.importOpml(rawContent)
                } else {
                    FeedEngine.importUrls(rawContent).first
                }
                reloadAllLocal()
                showToast("Imported $importedCount feeds")
                refreshFeeds(silent = true)
            }.onFailure { err ->
                showToast(err.message ?: "Import failed")
            }
        }
    }

    fun exportFeeds(asOpml: Boolean, onContentReady: (content: String, suggestedName: String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                if (asOpml) {
                    FeedEngine.exportOpml() to "tsumo-feeds.opml"
                } else {
                    FeedEngine.exportUrls() to "urls"
                }
            }.onSuccess { (content, name) ->
                onContentReady(content, name)
            }.onFailure { err ->
                showToast(err.message ?: "Export failed")
            }
        }
    }

    // --- Library: Archived, History, Local Exports & Email Delivery ---

    fun setLibrarySection(section: Int) {
        _uiState.update { it.copy(librarySection = section) }
        viewModelScope.launch { reloadLibrary() }
    }

    private suspend fun reloadLibrary() {
        val archived = FeedEngine.listItems(filter = "archived", limit = 200)
        val history = FeedEngine.history(query = _uiState.value.historyQuery, limit = 150)
        val exports = exportStore.loadExports()
        _uiState.update {
            it.copy(
                archivedArticles = archived,
                historyEntries = history,
                localExports = exports,
            )
        }
    }

    fun setHistoryQuery(q: String) {
        _uiState.update { it.copy(historyQuery = q) }
        viewModelScope.launch {
            runCatching { FeedEngine.history(query = q, limit = 150) }
                .onSuccess { entries -> _uiState.update { it.copy(historyEntries = entries) } }
        }
    }

    fun removeHistoryEntry(historyId: Long) {
        viewModelScope.launch {
            runCatching {
                FeedEngine.removeHistory(historyId)
                _uiState.update { s ->
                    s.copy(historyEntries = s.historyEntries.filterNot { it.historyId == historyId })
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching {
                FeedEngine.clearHistory()
                _uiState.update { it.copy(historyEntries = emptyList()) }
                showToast("Reading history cleared")
            }
        }
    }

    fun openLocalExport(item: LocalExportItem) {
        runCatching {
            getApplication<Application>().startActivity(exportStore.openFileIntent(item.filePath))
        }.onFailure {
            showToast("No app found to open ${item.extension}")
        }
    }

    fun shareLocalExport(item: LocalExportItem) {
        runCatching {
            getApplication<Application>().startActivity(exportStore.shareFileIntent(item.filePath))
        }.onFailure {
            showToast("Unable to share file")
        }
    }

    fun removeLocalExport(item: LocalExportItem) {
        viewModelScope.launch {
            exportStore.removeExport(item)
            _uiState.update { it.copy(localExports = exportStore.loadExports()) }
            showToast("Removed ${item.title}")
        }
    }

    fun sendExportViaEmail(item: LocalExportItem) {
        if (!_uiState.value.status.deliveryReady) {
            openSettingsSheet()
            showToast("Configure email delivery first")
            return
        }
        _uiState.update { it.copy(emailSendingKey = item.key) }
        viewModelScope.launch {
            runCatching { FeedEngine.sendDelivery(item.filePath) }
                .onSuccess { to -> showToast("Delivered to $to") }
                .onFailure { err -> showToast(err.message ?: "Delivery failed") }
            _uiState.update { it.copy(emailSendingKey = null) }
        }
    }

    // --- Settings / Delivery sheet ---

    fun openSettingsSheet() {
        _uiState.update { it.copy(settingsOpen = true) }
    }

    fun closeSettingsSheet() {
        _uiState.update { it.copy(settingsOpen = false) }
    }

    fun updateReaderSettings(openMode: OpenMode, keepReadDays: Int) {
        viewModelScope.launch {
            runCatching { FeedEngine.saveSettings(openMode, keepReadDays) }
                .onSuccess { status ->
                    _uiState.update { it.copy(status = status) }
                    showToast("Preferences updated")
                }
        }
    }

    fun saveDeliveryConfig(to: String, from: String, smtpHost: String, smtpPort: Int, password: String) {
        viewModelScope.launch {
            runCatching {
                FeedEngine.saveDelivery(to, from, smtpHost, smtpPort, password)
            }.onSuccess { status ->
                _uiState.update { it.copy(status = status) }
                showToast("Email delivery saved")
            }.onFailure { err ->
                showToast(err.message ?: "Invalid email settings")
            }
        }
    }

    private fun updateArticleFlagsLocally(articleId: Long, transform: (Article) -> Article) {
        _uiState.update { s ->
            s.copy(
                articles = s.articles.map { if (it.id == articleId) transform(it) else it },
                searchArticles = s.searchArticles.map { if (it.id == articleId) transform(it) else it },
                archivedArticles = s.archivedArticles.map { if (it.id == articleId) transform(it) else it },
                selectedArticle = s.selectedArticle?.let { if (it.id == articleId) transform(it) else it },
            )
        }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2800)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }
}
