package main

/*
#cgo android LDFLAGS: -llog
#include <stdlib.h>
#include <string.h>
#ifdef __ANDROID__
#include <android/log.h>
static void tsumo_log(int prio, const char* msg) {
    __android_log_print(prio, "TsumoEngine", "%s", msg);
}
#else
#include <stdio.h>
#define ANDROID_LOG_INFO 4
static void tsumo_log(int prio, const char* msg) {
    (void)prio;
    fprintf(stderr, "[TsumoEngine] %s\n", msg);
}
#endif
*/
import "C"
import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unsafe"
)

//go:embed default_urls.txt
var defaultNewsboatURLs string

type appSettings struct {
	OpenMode     string `json:"open_mode"`      // reader | custom_tab | browser
	KeepReadDays int    `json:"keep_read_days"` // 0 = keep forever, default 30
}

type deliverySecretData struct {
	EmailDeliveryConfig
	SMTPPassword string `json:"smtp_password,omitempty"`
}

var (
	stateMu    sync.Mutex
	dataDir    string
	db         *DB
	httpClient = newHTTPClient(25 * time.Second)
	settings   = appSettings{OpenMode: "reader", KeepReadDays: 30}
	delivery   deliverySecretData
)

func logInfo(msg string) {
	cs := C.CString(msg)
	defer C.free(unsafe.Pointer(cs))
	C.tsumo_log(C.ANDROID_LOG_INFO, cs)
}

//export TsumoCall
func TsumoCall(actionPtr *C.char, actionLen C.int, payloadPtr *C.char, payloadLen C.int, outLen *C.int) unsafe.Pointer {
	action := ""
	if actionPtr != nil && actionLen > 0 {
		action = C.GoStringN(actionPtr, actionLen)
	}
	var payload []byte
	if payloadPtr != nil && payloadLen > 0 {
		payload = C.GoBytes(unsafe.Pointer(payloadPtr), payloadLen)
	}

	resp := dispatch(action, payload)
	if outLen != nil {
		*outLen = C.int(len(resp))
	}
	if len(resp) == 0 {
		return nil
	}
	return C.CBytes(resp)
}

func jsonError(err error) []byte {
	msg := "unknown error"
	if err != nil {
		msg = err.Error()
	}
	b, _ := json.Marshal(map[string]any{
		"ok":    false,
		"error": msg,
	})
	return b
}

func jsonOK(data map[string]any) []byte {
	if data == nil {
		data = map[string]any{}
	}
	data["ok"] = true
	b, _ := json.Marshal(data)
	return b
}

func dispatch(action string, payload []byte) []byte {
	switch action {
	case "init":
		return handleInit(payload)
	case "status":
		return handleStatus()
	case "saveSettings":
		return handleSaveSettings(payload)
	case "discoverFeed":
		return handleDiscoverFeed(payload)
	case "addFeed":
		return handleAddFeed(payload)
	case "removeFeed":
		return handleRemoveFeed(payload)
	case "updateFeed":
		return handleUpdateFeed(payload)
	case "reorderFeeds":
		return handleReorderFeeds(payload)
	case "listFeeds":
		return handleListFeeds()
	case "refresh":
		return handleRefresh(payload)
	case "listItems":
		return handleListItems(payload)
	case "getArticle":
		return handleGetArticle(payload)
	case "articleContent":
		return handleArticleContent(payload)
	case "exportMarkdown":
		return handleExportMarkdown(payload)
	case "search":
		return handleSearch(payload)
	case "markRead":
		return handleMarkRead(payload)
	case "markFeedRead":
		return handleMarkFeedRead(payload)
	case "setArchived":
		return handleSetArchived(payload)
	case "recordOpen":
		return handleRecordOpen(payload)
	case "history":
		return handleHistory(payload)
	case "removeHistory":
		return handleRemoveHistory(payload)
	case "clearHistory":
		return handleClearHistory()
	case "importUrls":
		return handleImportURLs(payload)
	case "exportUrls":
		return handleExportURLs()
	case "importOpml":
		return handleImportOPML(payload)
	case "exportOpml":
		return handleExportOPML()
	case "saveDelivery":
		return handleSaveDelivery(payload)
	case "sendDelivery":
		return handleSendDelivery(payload)
	default:
		return jsonError(fmt.Errorf("unknown action: %s", action))
	}
}

func handleInit(payload []byte) []byte {
	var req struct {
		DataDir string `json:"dataDir"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(fmt.Errorf("invalid init payload: %w", err))
	}
	if strings.TrimSpace(req.DataDir) == "" {
		return jsonError(errors.New("dataDir is required"))
	}
	if err := os.MkdirAll(req.DataDir, 0o755); err != nil {
		return jsonError(fmt.Errorf("create dataDir: %w", err))
	}

	stateMu.Lock()
	defer stateMu.Unlock()

	if db != nil {
		_ = db.Close()
		db = nil
	}
	dataDir = req.DataDir
	opened, err := OpenDB(filepath.Join(dataDir, "tsumo.db"))
	if err != nil {
		return jsonError(fmt.Errorf("open database: %w", err))
	}
	db = opened
	loadSettingsLocked()
	seedDefaultFeedsLocked()
	if settings.KeepReadDays > 0 {
		_, _ = db.PruneOldReadItems(settings.KeepReadDays)
	}

	logInfo("Engine initialized at " + dataDir)
	return statusPayloadLocked()
}

func loadSettingsLocked() {
	settings = appSettings{OpenMode: "reader", KeepReadDays: 30}
	if raw, err := os.ReadFile(filepath.Join(dataDir, "settings.json")); err == nil {
		_ = json.Unmarshal(raw, &settings)
	}
	if settings.OpenMode == "" {
		settings.OpenMode = "reader"
	}

	delivery = deliverySecretData{
		EmailDeliveryConfig: EmailDeliveryConfig{
			SMTPHost: "smtp.gmail.com",
			SMTPPort: 587,
		},
	}
	deliveryPath := filepath.Join(dataDir, "delivery.json")
	if raw, err := os.ReadFile(deliveryPath); err == nil {
		_ = json.Unmarshal(raw, &delivery)
	} else if legacy, err := os.ReadFile(filepath.Join(dataDir, "kindle.json")); err == nil {
		_ = json.Unmarshal(legacy, &delivery)
	}
	if delivery.SMTPHost == "" {
		delivery.SMTPHost = "smtp.gmail.com"
	}
	if delivery.SMTPPort == 0 {
		delivery.SMTPPort = 587
	}
}

func saveJSONLocked(filename string, v any) error {
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(dataDir, filename), b, 0o600)
}

func seedDefaultFeedsLocked() {
	if db == nil {
		return
	}
	if _, ok := db.GetMeta("seeded_default_urls"); ok {
		return
	}
	res := ParseNewsboatURLs(defaultNewsboatURLs)
	for _, entry := range res.Entries {
		_, _ = db.UpsertFeed(entry.URL, entry.CustomTitle, "", entry.CustomTitle, entry.Tags)
	}
	_ = db.SetMeta("seeded_default_urls", "1")
}

func getDB() (*DB, error) {
	stateMu.Lock()
	defer stateMu.Unlock()
	if db == nil {
		return nil, errors.New("engine not initialized")
	}
	return db, nil
}

func statusPayloadLocked() []byte {
	feedCount := 0
	unreadCount := 0
	archivedCount := 0
	if db != nil {
		_ = db.sql.QueryRow(`SELECT COUNT(*) FROM feeds`).Scan(&feedCount)
		_ = db.sql.QueryRow(`SELECT COUNT(*) FROM items WHERE unread = 1`).Scan(&unreadCount)
		_ = db.sql.QueryRow(`SELECT COUNT(*) FROM items WHERE archived = 1`).Scan(&archivedCount)
	}
	return jsonOK(map[string]any{
		"open_mode":          settings.OpenMode,
		"keep_read_days":     settings.KeepReadDays,
		"feed_count":         feedCount,
		"unread_count":       unreadCount,
		"archived_count":     archivedCount,
		"delivery_ready":     delivery.EmailDeliveryConfig.Validate() == nil && delivery.SMTPPassword != "",
		"delivery_to":        delivery.To,
		"delivery_from":      delivery.From,
		"delivery_smtp_host": delivery.SMTPHost,
		"delivery_smtp_port": delivery.SMTPPort,
		"has_smtp_password":  delivery.SMTPPassword != "",
	})
}

func handleStatus() []byte {
	stateMu.Lock()
	defer stateMu.Unlock()
	return statusPayloadLocked()
}

func handleSaveSettings(payload []byte) []byte {
	var req appSettings
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	switch req.OpenMode {
	case "reader", "custom_tab", "browser":
	default:
		req.OpenMode = "reader"
	}
	if req.KeepReadDays < 0 {
		req.KeepReadDays = 0
	}

	stateMu.Lock()
	defer stateMu.Unlock()
	settings = req
	if dataDir != "" {
		if err := saveJSONLocked("settings.json", settings); err != nil {
			return jsonError(err)
		}
	}
	if db != nil && settings.KeepReadDays > 0 {
		_, _ = db.PruneOldReadItems(settings.KeepReadDays)
	}
	return statusPayloadLocked()
}

func handleDiscoverFeed(payload []byte) []byte {
	var req struct {
		URL string `json:"url"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	disc, _, err := DiscoverFeed(ctx, httpClient, req.URL)
	if err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"feed": disc,
	})
}

func handleAddFeed(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		URL         string   `json:"url"`
		CustomTitle string   `json:"custom_title"`
		Tags        []string `json:"tags"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()

	disc, parsed, err := DiscoverFeed(ctx, httpClient, req.URL)
	if err != nil {
		return jsonError(err)
	}

	feed, err := database.UpsertFeed(disc.URL, disc.Title, disc.SiteURL, req.CustomTitle, req.Tags)
	if err != nil {
		return jsonError(err)
	}

	newCount := 0
	if parsed != nil {
		displayTitle := feed.DisplayTitle()
		articles := convertFeedItems(parsed, time.Now().Unix())
		newCount, _ = database.UpsertItems(feed.ID, displayTitle, articles)
		_ = database.UpdateFeedFetchState(feed.ID, disc.Title, disc.SiteURL, "", "", "", time.Now().Unix())
	}
	feed, _ = database.GetFeed(feed.ID)
	return jsonOK(map[string]any{
		"feed":      feed,
		"new_items": newCount,
	})
}

func handleRemoveFeed(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		ID int64 `json:"id"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if err := database.RemoveFeed(req.ID); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleUpdateFeed(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		ID          int64    `json:"id"`
		CustomTitle string   `json:"custom_title"`
		Tags        []string `json:"tags"`
		Position    *int     `json:"position"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if err := database.UpdateFeedMeta(req.ID, "", "", req.CustomTitle, req.Tags, req.Position); err != nil {
		return jsonError(err)
	}
	f, _ := database.GetFeed(req.ID)
	return jsonOK(map[string]any{"feed": f})
}

func handleReorderFeeds(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		IDs []int64 `json:"ids"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if err := database.ReorderFeeds(req.IDs); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleListFeeds() []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	feeds, err := database.ListFeeds()
	if err != nil {
		return jsonError(err)
	}
	if feeds == nil {
		feeds = []Feed{}
	}
	tagMap := make(map[string]int)
	totalUnread := 0
	for _, f := range feeds {
		totalUnread += f.UnreadCount
		for _, t := range f.Tags {
			tagMap[t] += f.UnreadCount
		}
	}
	return jsonOK(map[string]any{
		"feeds":        feeds,
		"total_unread": totalUnread,
		"tag_unreads":  tagMap,
	})
}

func handleRefresh(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		FeedIDs []int64 `json:"feed_ids"`
	}
	if len(payload) > 0 {
		_ = json.Unmarshal(payload, &req)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	res, err := RefreshFeeds(ctx, database, httpClient, req.FeedIDs)
	if err != nil {
		return jsonError(err)
	}
	if res.Errors == nil {
		res.Errors = []RefreshError{}
	}
	return jsonOK(map[string]any{
		"refreshed": res.Refreshed,
		"new_items": res.NewItems,
		"errors":    res.Errors,
	})
}

func handleListItems(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		FeedID int64  `json:"feed_id"`
		Tag    string `json:"tag"`
		Filter string `json:"filter"` // all | unread | archived
		Offset int    `json:"offset"`
		Limit  int    `json:"limit"`
	}
	if len(payload) > 0 {
		if err := json.Unmarshal(payload, &req); err != nil {
			return jsonError(err)
		}
	}
	items, err := database.ListItems(req.FeedID, req.Tag, req.Filter, req.Offset, req.Limit)
	if err != nil {
		return jsonError(err)
	}
	if items == nil {
		items = []Article{}
	}
	return jsonOK(map[string]any{
		"items": items,
	})
}

func handleGetArticle(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		ID int64 `json:"id"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	art, err := database.GetArticle(req.ID)
	if err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{"article": art})
}

func handleArticleContent(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		ID int64 `json:"id"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	art, err := database.GetArticle(req.ID)
	if err != nil {
		return jsonError(err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	cleanHTML, err := FetchReadableContent(ctx, httpClient, art)
	if err != nil {
		return jsonError(err)
	}
	// Cache extracted readability HTML if article is archived or had thin feed content
	if art.FullText == "" && len(cleanHTML) > len(art.Content) {
		_ = database.SaveFullText(art.ID, cleanHTML)
	}
	paragraphs := ArticleToPlainParagraphs(cleanHTML)
	if paragraphs == nil {
		paragraphs = []string{}
	}
	return jsonOK(map[string]any{
		"html":       cleanHTML,
		"paragraphs": paragraphs,
	})
}

func handleExportMarkdown(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		ID int64 `json:"id"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	art, err := database.GetArticle(req.ID)
	if err != nil {
		return jsonError(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	cleanHTML, err := FetchReadableContent(ctx, httpClient, art)
	if err != nil {
		return jsonError(err)
	}
	markdown, filename := ArticleToMarkdown(art, cleanHTML)
	return jsonOK(map[string]any{
		"markdown": markdown,
		"filename": filename,
	})
}

func handleSearch(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		Query string `json:"query"`
		Scope string `json:"scope"` // all | archived | history
		Limit int    `json:"limit"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	q := strings.TrimSpace(req.Query)
	if q == "" {
		return jsonOK(map[string]any{
			"items": []Article{},
			"feeds": []Feed{},
		})
	}

	items, err := database.SearchFTS(q, req.Scope, req.Limit)
	if err != nil {
		return jsonError(err)
	}
	if items == nil {
		items = []Article{}
	}

	// Instant in-memory feed & tag match
	allFeeds, _ := database.ListFeeds()
	lq := strings.ToLower(q)
	var matchedFeeds []Feed
	for _, f := range allFeeds {
		if strings.Contains(strings.ToLower(f.DisplayTitle()), lq) ||
			strings.Contains(strings.ToLower(f.URL), lq) {
			matchedFeeds = append(matchedFeeds, f)
			continue
		}
		for _, t := range f.Tags {
			if strings.Contains(strings.ToLower(t), lq) {
				matchedFeeds = append(matchedFeeds, f)
				break
			}
		}
	}
	if matchedFeeds == nil {
		matchedFeeds = []Feed{}
	}

	return jsonOK(map[string]any{
		"items": items,
		"feeds": matchedFeeds,
	})
}

func handleMarkRead(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		IDs  []int64 `json:"ids"`
		Read bool    `json:"read"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if err := database.MarkRead(req.IDs, req.Read); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleMarkFeedRead(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		FeedID int64 `json:"feed_id"`
	}
	if len(payload) > 0 {
		_ = json.Unmarshal(payload, &req)
	}
	if err := database.MarkFeedRead(req.FeedID); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleSetArchived(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		ID       int64 `json:"id"`
		Archived bool  `json:"archived"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	var fullText string
	if req.Archived {
		if art, err := database.GetArticle(req.ID); err == nil && art.FullText == "" {
			ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
			if htmlContent, fetchErr := FetchReadableContent(ctx, httpClient, art); fetchErr == nil {
				fullText = htmlContent
			}
			cancel()
		}
	}
	if err := database.SetArchived(req.ID, req.Archived, fullText); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleRecordOpen(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		ID  int64  `json:"id"`
		Via string `json:"via"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if err := database.RecordOpen(req.ID, req.Via); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleHistory(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		Query  string `json:"query"`
		Offset int    `json:"offset"`
		Limit  int    `json:"limit"`
	}
	if len(payload) > 0 {
		_ = json.Unmarshal(payload, &req)
	}
	entries, err := database.ListHistory(req.Query, req.Offset, req.Limit)
	if err != nil {
		return jsonError(err)
	}
	if entries == nil {
		entries = []HistoryRecord{}
	}
	return jsonOK(map[string]any{"entries": entries})
}

func handleRemoveHistory(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		HistoryID int64 `json:"history_id"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if err := database.RemoveHistoryEntry(req.HistoryID); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleClearHistory() []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	if err := database.ClearHistory(); err != nil {
		return jsonError(err)
	}
	return jsonOK(nil)
}

func handleImportURLs(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		Content string `json:"content"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	res := ParseNewsboatURLs(req.Content)
	imported := 0
	for _, entry := range res.Entries {
		if _, err := database.UpsertFeed(entry.URL, entry.CustomTitle, "", entry.CustomTitle, entry.Tags); err == nil {
			imported++
		}
	}
	return jsonOK(map[string]any{
		"imported": imported,
		"skipped":  res.Skipped,
	})
}

func handleExportURLs() []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	feeds, err := database.ListFeeds()
	if err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"content": ExportNewsboatURLs(feeds),
	})
}

func handleImportOPML(payload []byte) []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	var req struct {
		Content string `json:"content"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	entries, err := ParseOPML([]byte(req.Content))
	if err != nil {
		return jsonError(err)
	}
	imported := 0
	for _, entry := range entries {
		if _, err := database.UpsertFeed(entry.URL, entry.CustomTitle, "", entry.CustomTitle, entry.Tags); err == nil {
			imported++
		}
	}
	return jsonOK(map[string]any{
		"imported": imported,
	})
}

func handleExportOPML() []byte {
	database, err := getDB()
	if err != nil {
		return jsonError(err)
	}
	feeds, err := database.ListFeeds()
	if err != nil {
		return jsonError(err)
	}
	xmlStr, err := ExportOPML(feeds)
	if err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"content": xmlStr,
	})
}

func handleSaveDelivery(payload []byte) []byte {
	var req struct {
		To       string `json:"to"`
		From     string `json:"from"`
		SMTPHost string `json:"smtp_host"`
		SMTPPort int    `json:"smtp_port"`
		Password string `json:"password"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if req.SMTPHost == "" {
		req.SMTPHost = "smtp.gmail.com"
	}
	if req.SMTPPort == 0 {
		req.SMTPPort = 587
	}
	cfg := EmailDeliveryConfig{
		To:       strings.TrimSpace(req.To),
		From:     strings.TrimSpace(req.From),
		SMTPHost: strings.TrimSpace(req.SMTPHost),
		SMTPPort: req.SMTPPort,
	}
	if err := cfg.Validate(); err != nil {
		return jsonError(err)
	}

	stateMu.Lock()
	defer stateMu.Unlock()
	pass := delivery.SMTPPassword
	if strings.TrimSpace(req.Password) != "" {
		pass = req.Password
	}
	if strings.TrimSpace(pass) == "" {
		return jsonError(errors.New("SMTP password is required"))
	}
	delivery = deliverySecretData{
		EmailDeliveryConfig: cfg,
		SMTPPassword:        pass,
	}
	if dataDir != "" {
		if err := saveJSONLocked("delivery.json", delivery); err != nil {
			return jsonError(err)
		}
	}
	return statusPayloadLocked()
}

func handleSendDelivery(payload []byte) []byte {
	var req struct {
		FilePath string `json:"filePath"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	stateMu.Lock()
	cfg := delivery.EmailDeliveryConfig
	pass := delivery.SMTPPassword
	stateMu.Unlock()

	if err := sendViaEmail(req.FilePath, cfg, pass); err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{"sent_to": cfg.To})
}

func setHTTPClientForTest(c *http.Client) {
	httpClient = c
}

func main() {}
