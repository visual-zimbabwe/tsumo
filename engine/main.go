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
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unsafe"
)

type sourceConfig struct {
	EnableGutenberg       bool `json:"enable_gutenberg"`
	EnableInternetArchive bool `json:"enable_internet_archive"`
}

type kindleSecretData struct {
	KindleConfig
	SMTPPassword string `json:"smtp_password,omitempty"`
}

type HistoryEntry struct {
	ID          string `json:"id"`
	CommandID   string `json:"command_id"`
	Name        string `json:"name"`
	URL         string `json:"url"`
	DownloadURL string `json:"download_url"`
	Extension   string `json:"extension"`
	Size        string `json:"size"`
	Date        string `json:"date"`
	Source      string `json:"source,omitempty"`
}

type downloadState struct {
	ID       string             `json:"id"`
	State    string             `json:"state"` // fetching, downloading, complete, error, cancelled
	Written  int64              `json:"written"`
	Total    int64              `json:"total"`
	FilePath string             `json:"file_path,omitempty"`
	Size     int64              `json:"size,omitempty"`
	Error    string             `json:"error,omitempty"`
	cancel   context.CancelFunc `json:"-"`
}

var (
	mu           sync.Mutex
	dataDir      string
	srcCfg       = sourceConfig{EnableGutenberg: true, EnableInternetArchive: true}
	kindleCfg    kindleSecretData
	historyItems []HistoryEntry

	dlMu      sync.Mutex
	downloads = make(map[string]*downloadState)
)

func logInfo(msg string) {
	cMsg := C.CString(msg)
	C.tsumo_log(C.ANDROID_LOG_INFO, cMsg)
	C.free(unsafe.Pointer(cMsg))
}

//export TsumoCall
func TsumoCall(actionPtr *C.char, actionLen C.int, payloadPtr *C.char, payloadLen C.int, outLen *C.int) unsafe.Pointer {
	action := ""
	if actionPtr != nil && actionLen > 0 {
		action = C.GoStringN(actionPtr, actionLen)
	}
	payload := []byte("{}")
	if payloadPtr != nil && payloadLen > 0 {
		payload = C.GoBytes(unsafe.Pointer(payloadPtr), payloadLen)
	}

	resp := dispatch(action, payload)
	*outLen = C.int(len(resp))
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
	case "setSources":
		return handleSetSources(payload)
	case "doctor":
		return handleDoctor()
	case "search":
		return handleSearch(payload)
	case "bookDetail":
		return handleBookDetail(payload)
	case "startDownload":
		return handleStartDownload(payload)
	case "downloadProgress":
		return handleDownloadProgress(payload)
	case "cancelDownload":
		return handleCancelDownload(payload)
	case "history":
		return handleHistory(payload)
	case "saveKindle":
		return handleSaveKindle(payload)
	case "sendToKindle":
		return handleSendToKindle(payload)
	default:
		return jsonError(fmt.Errorf("unknown action: %s", action))
	}
}

func handleInit(payload []byte) []byte {
	var req struct {
		DataDir string `json:"dataDir"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if strings.TrimSpace(req.DataDir) == "" {
		return jsonError(errors.New("dataDir is required"))
	}

	// Ensure Go's crypto/x509 finds Android Conscrypt APEX and legacy cacerts.
	_ = os.Setenv("SSL_CERT_DIR", "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts")

	mu.Lock()
	defer mu.Unlock()

	dataDir = req.DataDir
	_ = os.MkdirAll(dataDir, 0700)

	loadAllLocked()
	logInfo("Initialized Tsumo open-archive engine in " + dataDir)
	return statusPayloadLocked()
}

func loadAllLocked() {
	srcCfg = sourceConfig{EnableGutenberg: true, EnableInternetArchive: true}
	if b, err := os.ReadFile(filepath.Join(dataDir, "sources.json")); err == nil {
		_ = json.Unmarshal(b, &srcCfg)
	}
	if !srcCfg.EnableGutenberg && !srcCfg.EnableInternetArchive {
		srcCfg.EnableGutenberg = true
		srcCfg.EnableInternetArchive = true
	}

	kindleCfg = kindleSecretData{}
	if b, err := os.ReadFile(filepath.Join(dataDir, "kindle.json")); err == nil {
		_ = json.Unmarshal(b, &kindleCfg)
	}

	historyItems = nil
	if b, err := os.ReadFile(filepath.Join(dataDir, "history.json")); err == nil {
		_ = json.Unmarshal(b, &historyItems)
	}
}

func saveJSONLocked(filename string, v any) error {
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(dataDir, filename), b, 0600)
}

func statusPayloadLocked() []byte {
	return jsonOK(map[string]any{
		"enableGutenberg":       srcCfg.EnableGutenberg,
		"enableInternetArchive": srcCfg.EnableInternetArchive,
		"kindleReady":           kindleCfg.Validate() == nil && strings.TrimSpace(kindleCfg.SMTPPassword) != "",
		"kindleTo":              kindleCfg.To,
		"kindleFrom":            kindleCfg.From,
		"kindleSmtpHost":        kindleCfg.SMTPHost,
		"kindleSmtpPort":        kindleCfg.SMTPPort,
		"hasSmtpPassword":       strings.TrimSpace(kindleCfg.SMTPPassword) != "",
	})
}

func handleStatus() []byte {
	mu.Lock()
	defer mu.Unlock()
	return statusPayloadLocked()
}

func handleSetSources(payload []byte) []byte {
	var req struct {
		EnableGutenberg       bool `json:"enableGutenberg"`
		EnableInternetArchive bool `json:"enableInternetArchive"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if !req.EnableGutenberg && !req.EnableInternetArchive {
		return jsonError(errors.New("at least one open archive source must remain enabled"))
	}

	mu.Lock()
	defer mu.Unlock()
	srcCfg = sourceConfig{
		EnableGutenberg:       req.EnableGutenberg,
		EnableInternetArchive: req.EnableInternetArchive,
	}
	if dataDir != "" {
		_ = saveJSONLocked("sources.json", srcCfg)
	}
	return statusPayloadLocked()
}

func handleDoctor() []byte {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	checks := checkOpenSources(ctx)
	return jsonOK(map[string]any{
		"checks": checks,
	})
}

func handleSearch(payload []byte) []byte {
	var req struct {
		Query      string   `json:"query"`
		Page       int      `json:"page"`
		Count      int      `json:"count"`
		Extensions []string `json:"extensions"`
		Languages  []string `json:"languages"`
		FromYear   int      `json:"fromYear"`
		ToYear     int      `json:"toYear"`
		Exact      bool     `json:"exact"`
		FullText   bool     `json:"fullText"`
		Phrase     bool     `json:"phrase"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}

	query := strings.TrimSpace(req.Query)
	if query == "" {
		return jsonError(errors.New("search query cannot be empty"))
	}

	mu.Lock()
	gb := srcCfg.EnableGutenberg
	ia := srcCfg.EnableInternetArchive
	mu.Unlock()

	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()

	res, err := searchOpenArchives(ctx, SearchRequest{
		Query:      query,
		Page:       req.Page,
		Count:      req.Count,
		Extensions: req.Extensions,
		Languages:  req.Languages,
		FromYear:   req.FromYear,
		ToYear:     req.ToYear,
		Exact:      req.Exact,
		FullText:   req.FullText,
		Phrase:     req.Phrase,
	}, gb, ia)
	if err != nil {
		return jsonError(err)
	}

	return jsonOK(map[string]any{
		"books":       res.Books,
		"page":        res.Page,
		"total_pages": res.TotalPages,
	})
}

func handleBookDetail(payload []byte) []byte {
	var req struct {
		BookID string `json:"bookId"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	b, err := fetchOpenBookDetail(ctx, req.BookID)
	if err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"book": b,
	})
}

func handleStartDownload(payload []byte) []byte {
	var req struct {
		DownloadID string `json:"downloadId"`
		BookID     string `json:"bookId"`
		DirectURL  string `json:"directUrl"`
		DestDir    string `json:"destDir"`
		Title      string `json:"title"`
		Extension  string `json:"extension"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if strings.TrimSpace(req.DownloadID) == "" || strings.TrimSpace(req.DestDir) == "" {
		return jsonError(errors.New("downloadId and destDir are required"))
	}
	if err := os.MkdirAll(req.DestDir, 0755); err != nil {
		return jsonError(err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	st := &downloadState{
		ID:     req.DownloadID,
		State:  "fetching",
		cancel: cancel,
	}

	dlMu.Lock()
	downloads[req.DownloadID] = st
	dlMu.Unlock()

	go func() {
		dlURL := strings.TrimSpace(req.DirectURL)
		title := strings.TrimSpace(req.Title)
		ext := strings.TrimSpace(req.Extension)
		var resolvedBook *Book

		if dlURL == "" && strings.TrimSpace(req.BookID) != "" {
			b, err := fetchOpenBookDetail(ctx, req.BookID)
			if err != nil {
				dlMu.Lock()
				if errors.Is(err, context.Canceled) || st.State == "cancelled" {
					st.State = "cancelled"
				} else {
					st.State = "error"
					st.Error = err.Error()
				}
				dlMu.Unlock()
				return
			}
			resolvedBook = &b
			dlURL = b.DownloadURL
			if title == "" {
				title = b.Name
			}
			if ext == "" {
				ext = b.Extension
			}
		}

		if dlURL == "" {
			dlMu.Lock()
			st.State = "error"
			st.Error = "no downloadable file URL available for this book"
			dlMu.Unlock()
			return
		}

		dlMu.Lock()
		if st.State == "cancelled" {
			dlMu.Unlock()
			return
		}
		st.State = "downloading"
		dlMu.Unlock()

		res, err := downloadFileWithProgress(ctx, dlURL, req.DestDir, title, ext, func(written, total int64) {
			dlMu.Lock()
			st.Written = written
			st.Total = total
			dlMu.Unlock()
		})

		dlMu.Lock()
		if err != nil {
			if errors.Is(err, context.Canceled) || st.State == "cancelled" {
				st.State = "cancelled"
			} else {
				st.State = "error"
				st.Error = err.Error()
			}
			dlMu.Unlock()
			return
		}
		st.FilePath = res.FilePath
		st.Size = res.Size
		st.Written = res.Size
		if st.Total <= 0 {
			st.Total = res.Size
		}
		st.State = "complete"
		dlMu.Unlock()

		recordHistory(req.BookID, title, ext, dlURL, res.Size, resolvedBook)
	}()

	return jsonOK(map[string]any{"downloadId": req.DownloadID})
}

func recordHistory(bookID, title, ext, dlURL string, sizeBytes int64, resolved *Book) {
	if title == "" && resolved != nil {
		title = resolved.Name
	}
	if title == "" {
		return
	}
	if ext == "" && resolved != nil {
		ext = resolved.Extension
	}
	pageURL := ""
	source := ""
	id := bookID
	if resolved != nil {
		pageURL = resolved.URL
		source = resolved.Source
		if resolved.ID != "" {
			id = resolved.ID
		}
	}
	entry := HistoryEntry{
		ID:          id,
		CommandID:   bookID,
		Name:        title,
		URL:         pageURL,
		DownloadURL: dlURL,
		Extension:   ext,
		Size:        formatByteSize(sizeBytes),
		Date:        time.Now().Format("2006-01-02"),
		Source:      source,
	}

	mu.Lock()
	defer mu.Unlock()
	filtered := make([]HistoryEntry, 0, len(historyItems)+1)
	filtered = append(filtered, entry)
	for _, existing := range historyItems {
		if existing.CommandID == entry.CommandID && entry.CommandID != "" {
			continue
		}
		filtered = append(filtered, existing)
		if len(filtered) >= 100 {
			break
		}
	}
	historyItems = filtered
	if dataDir != "" {
		_ = saveJSONLocked("history.json", historyItems)
	}
}

func handleDownloadProgress(payload []byte) []byte {
	var req struct {
		DownloadID string `json:"downloadId"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	dlMu.Lock()
	st, ok := downloads[req.DownloadID]
	if !ok {
		dlMu.Unlock()
		return jsonError(errors.New("download not found"))
	}
	copySt := *st
	dlMu.Unlock()

	return jsonOK(map[string]any{
		"id":        copySt.ID,
		"state":     copySt.State,
		"written":   copySt.Written,
		"total":     copySt.Total,
		"file_path": copySt.FilePath,
		"size":      copySt.Size,
		"error":     copySt.Error,
	})
}

func handleCancelDownload(payload []byte) []byte {
	var req struct {
		DownloadID string `json:"downloadId"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	dlMu.Lock()
	if st, ok := downloads[req.DownloadID]; ok {
		st.State = "cancelled"
		if st.cancel != nil {
			st.cancel()
		}
	}
	dlMu.Unlock()
	return jsonOK(nil)
}

func handleHistory(payload []byte) []byte {
	var req struct {
		Page int `json:"page"`
	}
	_ = json.Unmarshal(payload, &req)
	if req.Page <= 0 {
		req.Page = 1
	}

	mu.Lock()
	all := make([]HistoryEntry, len(historyItems))
	copy(all, historyItems)
	mu.Unlock()

	const pageSize = 25
	totalPages := (len(all) + pageSize - 1) / pageSize
	if totalPages <= 0 {
		totalPages = 1
	}
	start := (req.Page - 1) * pageSize
	if start >= len(all) {
		return jsonOK(map[string]any{
			"items":       []HistoryEntry{},
			"page":        req.Page,
			"total_pages": totalPages,
		})
	}
	end := start + pageSize
	if end > len(all) {
		end = len(all)
	}

	return jsonOK(map[string]any{
		"items":       all[start:end],
		"page":        req.Page,
		"total_pages": totalPages,
	})
}

func handleSaveKindle(payload []byte) []byte {
	var req struct {
		To           string `json:"to"`
		From         string `json:"from"`
		SMTPHost     string `json:"smtpHost"`
		SMTPPort     int    `json:"smtpPort"`
		SMTPPassword string `json:"smtpPassword"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	if req.SMTPPort <= 0 {
		req.SMTPPort = 587
	}
	cfg := KindleConfig{
		To:       strings.TrimSpace(req.To),
		From:     strings.TrimSpace(req.From),
		SMTPHost: strings.TrimSpace(req.SMTPHost),
		SMTPPort: req.SMTPPort,
	}
	if err := cfg.Validate(); err != nil {
		return jsonError(err)
	}

	mu.Lock()
	defer mu.Unlock()
	pass := strings.TrimSpace(req.SMTPPassword)
	if pass == "" {
		pass = kindleCfg.SMTPPassword
	}
	if pass == "" {
		return jsonError(errors.New("SMTP password is required"))
	}
	kindleCfg = kindleSecretData{
		KindleConfig: cfg,
		SMTPPassword: pass,
	}
	if dataDir != "" {
		if err := saveJSONLocked("kindle.json", kindleCfg); err != nil {
			return jsonError(err)
		}
	}
	return statusPayloadLocked()
}

func handleSendToKindle(payload []byte) []byte {
	var req struct {
		FilePath string `json:"filePath"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}

	mu.Lock()
	cfg := kindleCfg.KindleConfig
	pass := kindleCfg.SMTPPassword
	mu.Unlock()

	if err := sendToKindle(strings.TrimSpace(req.FilePath), cfg, pass); err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"to": cfg.To,
	})
}

func main() {}
