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
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unsafe"

	"github.com/heartleo/zlib"
)

const (
	eapiProbePath      = "/eapi/info/domains"
	defaultLoginDomain = "https://z-lib.gd"
)

// Injected at build time via -ldflags when engine/.env is present (gitignored).
var (
	defaultLoginEmail    = ""
	defaultLoginPassword = ""
)

type sessionData struct {
	Cookies map[string]string `json:"cookies"`
	Domain  string            `json:"domain"`
	Mode    string            `json:"mode,omitempty"`
}

type appConfigData struct {
	LastLoginEmail string `json:"last_login_email,omitempty"`
}

type kindleSecretData struct {
	zlib.KindleConfig
	SMTPPassword string `json:"smtp_password,omitempty"`
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
	mu         sync.Mutex
	dataDir    string
	client     *zlib.Client
	session    sessionData
	appCfg     appConfigData
	kindleCfg  kindleSecretData

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
	case "login":
		return handleLogin(payload)
	case "autoLogin":
		return handleAutoLogin()
	case "importCookies":
		return handleImportCookies(payload)
	case "logout":
		return handleLogout()
	case "setDomain":
		return handleSetDomain(payload)
	case "doctor":
		return handleDoctor()
	case "profile":
		return handleProfile()
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
	rebuildClientLocked()
	logInfo("Initialized Tsumo engine in " + dataDir)
	return statusPayloadLocked()
}

func loadAllLocked() {
	session = sessionData{}
	if b, err := os.ReadFile(filepath.Join(dataDir, "session.json")); err == nil {
		_ = json.Unmarshal(b, &session)
	}
	appCfg = appConfigData{}
	if b, err := os.ReadFile(filepath.Join(dataDir, "config.json")); err == nil {
		_ = json.Unmarshal(b, &appCfg)
	}
	kindleCfg = kindleSecretData{}
	if b, err := os.ReadFile(filepath.Join(dataDir, "kindle.json")); err == nil {
		_ = json.Unmarshal(b, &kindleCfg)
	}
}

func saveJSONLocked(filename string, v any) error {
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(dataDir, filename), b, 0600)
}

func rebuildClientLocked() {
	c := zlib.NewClient()
	if session.Mode == string(zlib.ClientModeEAPI) {
		c.SetMode(zlib.ClientModeEAPI)
	} else {
		c.SetMode(zlib.ClientModeHTML)
	}
	if strings.TrimSpace(session.Domain) != "" {
		if allowed, err := zlib.ParseAllowedDomain(session.Domain); err == nil {
			c.SetDomain(allowed)
		}
	} else if session.Mode == string(zlib.ClientModeEAPI) {
		c.SetDomain(zlib.DefaultEAPIDomain)
	}
	if len(session.Cookies) > 0 {
		c.SetCookies(session.Cookies)
	}
	client = c
}

func getClient() (*zlib.Client, bool) {
	mu.Lock()
	defer mu.Unlock()
	if client == nil {
		rebuildClientLocked()
	}
	loggedIn := len(session.Cookies) > 0
	return client, loggedIn
}

func statusPayloadLocked() []byte {
	domain := session.Domain
	if domain == "" && client != nil {
		domain = client.Domain()
	}
	if domain == "" {
		domain = defaultLoginDomain
	}
	mode := session.Mode
	if mode == "" {
		mode = string(zlib.ClientModeEAPI)
	}
	lastEmail := appCfg.LastLoginEmail
	if lastEmail == "" {
		lastEmail = defaultLoginEmail
	}
	_, loggedOutManually := os.Stat(filepath.Join(dataDir, "logged_out.flag"))
	return jsonOK(map[string]any{
		"loggedIn":          len(session.Cookies) > 0,
		"loggedOutManually": loggedOutManually == nil,
		"domain":            domain,
		"mode":              mode,
		"lastEmail":         lastEmail,
		"kindleReady":       kindleCfg.Validate() == nil && strings.TrimSpace(kindleCfg.SMTPPassword) != "",
		"kindleTo":          kindleCfg.To,
		"kindleFrom":        kindleCfg.From,
		"kindleSmtpHost":    kindleCfg.SMTPHost,
		"kindleSmtpPort":    kindleCfg.SMTPPort,
		"hasSmtpPassword":   strings.TrimSpace(kindleCfg.SMTPPassword) != "",
	})
}

func handleStatus() []byte {
	mu.Lock()
	defer mu.Unlock()
	return statusPayloadLocked()
}

func resolveEAPIDomain(ctx context.Context, domain string) (string, error) {
	check := zlib.ProbeDomainWithOptions(ctx, domain, zlib.DomainProbeOptions{Path: eapiProbePath})
	if check.Status != zlib.DomainStatusHealthy {
		return "", fmt.Errorf("%s is not usable for EAPI (%s): try another domain from Mirror Doctor", domain, check.Status)
	}
	requested, err := url.Parse(domain)
	if err != nil {
		return domain, nil
	}
	finalURL, err := url.Parse(check.FinalURL)
	if err != nil || finalURL.Host == "" || strings.EqualFold(finalURL.Host, requested.Host) {
		return domain, nil
	}
	resolved, err := zlib.ParseAllowedDomain(finalURL.Scheme + "://" + finalURL.Host)
	if err != nil {
		return "", fmt.Errorf("%s redirects to %s, which is not an allowed Z-Library domain: %w", domain, finalURL.Host, err)
	}
	return resolved, nil
}

func handleLogin(payload []byte) []byte {
	var req struct {
		Email    string `json:"email"`
		Password string `json:"password"`
		Domain   string `json:"domain"`
		UseEAPI  bool   `json:"useEapi"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}

	email := strings.TrimSpace(req.Email)
	if email == "" || req.Password == "" {
		return jsonError(errors.New("email and password are required"))
	}

	domain := strings.TrimSpace(req.Domain)
	if domain == "" {
		if req.UseEAPI {
			domain = zlib.DefaultEAPIDomain
		} else {
			domain = zlib.DefaultDomain
		}
	}
	allowedDomain, err := zlib.ParseAllowedDomain(domain)
	if err != nil {
		return jsonError(err)
	}

	if req.UseEAPI {
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		resolved, err := resolveEAPIDomain(ctx, allowedDomain)
		cancel()
		if err != nil {
			return jsonError(err)
		}
		allowedDomain = resolved
	}

	c := zlib.NewClient()
	c.SetDomain(allowedDomain)
	if req.UseEAPI {
		err = c.LoginEAPI(email, req.Password)
	} else {
		err = c.Login(email, req.Password)
	}
	if err != nil {
		return jsonError(err)
	}

	mode := string(zlib.ClientModeHTML)
	if req.UseEAPI {
		mode = string(zlib.ClientModeEAPI)
	}

	mu.Lock()
	defer mu.Unlock()
	session = sessionData{
		Cookies: c.Cookies(),
		Domain:  c.Domain(),
		Mode:    mode,
	}
	appCfg.LastLoginEmail = email
	if dataDir != "" {
		_ = os.Remove(filepath.Join(dataDir, "logged_out.flag"))
		_ = saveJSONLocked("session.json", session)
		_ = saveJSONLocked("config.json", appCfg)
	}
	client = c
	return statusPayloadLocked()
}

func handleAutoLogin() []byte {
	if strings.TrimSpace(defaultLoginEmail) == "" || strings.TrimSpace(defaultLoginPassword) == "" {
		return handleStatus()
	}
	mu.Lock()
	alreadyLoggedIn := len(session.Cookies) > 0
	dir := dataDir
	mu.Unlock()
	if alreadyLoggedIn {
		return handleStatus()
	}
	if dir != "" {
		if _, err := os.Stat(filepath.Join(dir, "logged_out.flag")); err == nil {
			return handleStatus()
		}
	}
	logInfo("Auto-signing in with bundled credentials")
	payload, _ := json.Marshal(map[string]any{
		"email":    defaultLoginEmail,
		"password": defaultLoginPassword,
		"domain":   defaultLoginDomain,
		"useEapi":  true,
	})
	return handleLogin(payload)
}

func handleImportCookies(payload []byte) []byte {
	var req struct {
		CookiesText string `json:"cookiesText"`
		Domain      string `json:"domain"`
		UseEAPI     bool   `json:"useEapi"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}

	domain := strings.TrimSpace(req.Domain)
	if domain == "" {
		if req.UseEAPI {
			domain = zlib.DefaultEAPIDomain
		} else {
			domain = zlib.DefaultDomain
		}
	}
	allowedDomain, err := zlib.ParseAllowedDomain(domain)
	if err != nil {
		return jsonError(err)
	}

	cookies, err := parseCookiesInput(req.CookiesText, allowedDomain)
	if err != nil {
		return jsonError(err)
	}
	if strings.TrimSpace(cookies["remix_userid"]) == "" || strings.TrimSpace(cookies["remix_userkey"]) == "" {
		return jsonError(errors.New("cookies must include remix_userid and remix_userkey"))
	}

	mode := string(zlib.ClientModeHTML)
	if req.UseEAPI {
		mode = string(zlib.ClientModeEAPI)
	}

	mu.Lock()
	defer mu.Unlock()
	session = sessionData{
		Cookies: cookies,
		Domain:  allowedDomain,
		Mode:    mode,
	}
	if dataDir != "" {
		_ = os.Remove(filepath.Join(dataDir, "logged_out.flag"))
		if err := saveJSONLocked("session.json", session); err != nil {
			return jsonError(err)
		}
	}
	rebuildClientLocked()
	return statusPayloadLocked()
}

func parseCookiesInput(raw, domain string) (map[string]string, error) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return nil, errors.New("cookie input is empty")
	}

	// 1. JSON map format {"remix_userid":"...", "remix_userkey":"..."}
	if strings.HasPrefix(trimmed, "{") {
		var m map[string]string
		if err := json.Unmarshal([]byte(trimmed), &m); err == nil && len(m) > 0 {
			return m, nil
		}
	}

	// 2. Netscape cookie file format
	if strings.Contains(trimmed, "HTTP Cookie File") || strings.Contains(trimmed, "\t") {
		target, err := url.ParseRequestURI(domain)
		if err != nil {
			return nil, err
		}
		cookies := make(map[string]string)
		scanner := bufio.NewScanner(strings.NewReader(trimmed))
		now := time.Now()
		for scanner.Scan() {
			line := strings.TrimSuffix(scanner.Text(), "\r")
			if line == "" {
				continue
			}
			if strings.HasPrefix(line, "#HttpOnly_") {
				line = strings.TrimPrefix(line, "#HttpOnly_")
			} else if strings.HasPrefix(line, "#") {
				continue
			}
			fields := strings.SplitN(line, "\t", 7)
			if len(fields) != 7 {
				continue
			}
			cookieHost := strings.ToLower(strings.Trim(strings.TrimSpace(fields[0]), "."))
			targetHost := strings.ToLower(strings.TrimSuffix(target.Hostname(), "."))
			if targetHost != cookieHost && !strings.HasSuffix(targetHost, "."+cookieHost) {
				continue
			}
			if fields[5] != "" {
				// Skip expired
				if exp, err := parseUnixInt(fields[4]); err == nil && exp > 0 && !time.Unix(exp, 0).After(now) {
					continue
				}
				cookies[fields[5]] = fields[6]
			}
		}
		if len(cookies) > 0 {
			return cookies, nil
		}
	}

	// 3. Semicolon / newline key=value pairs
	cookies := make(map[string]string)
	normalized := strings.ReplaceAll(trimmed, "\n", ";")
	for _, part := range strings.Split(normalized, ";") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		k, v, ok := strings.Cut(part, "=")
		if ok {
			k = strings.TrimSpace(k)
			v = strings.TrimSpace(v)
			if k != "" && v != "" {
				cookies[k] = v
			}
		}
	}
	if len(cookies) == 0 {
		return nil, errors.New("could not parse cookies (expected JSON, Netscape cookies.txt, or key=value pairs)")
	}
	return cookies, nil
}

func parseUnixInt(s string) (int64, error) {
	var n int64
	_, err := fmt.Sscanf(strings.TrimSpace(s), "%d", &n)
	return n, err
}

func handleLogout() []byte {
	mu.Lock()
	defer mu.Unlock()
	if client != nil {
		client.Logout()
	}
	session = sessionData{}
	if dataDir != "" {
		_ = os.Remove(filepath.Join(dataDir, "session.json"))
		_ = os.WriteFile(filepath.Join(dataDir, "logged_out.flag"), []byte("1"), 0600)
	}
	rebuildClientLocked()
	return statusPayloadLocked()
}

func handleSetDomain(payload []byte) []byte {
	var req struct {
		Domain string `json:"domain"`
		Mode   string `json:"mode"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	allowed, err := zlib.ParseAllowedDomain(req.Domain)
	if err != nil {
		return jsonError(err)
	}

	mu.Lock()
	defer mu.Unlock()
	session.Domain = allowed
	if req.Mode == string(zlib.ClientModeEAPI) || req.Mode == string(zlib.ClientModeHTML) {
		session.Mode = req.Mode
	}
	if len(session.Cookies) > 0 && dataDir != "" {
		_ = saveJSONLocked("session.json", session)
	}
	rebuildClientLocked()
	return statusPayloadLocked()
}

func handleDoctor() []byte {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	domains := zlib.KnownDomains()
	htmlChecks := zlib.ProbeDomains(ctx, domains)
	eapiChecks := zlib.ProbeDomainsWithOptions(ctx, domains, zlib.DomainProbeOptions{Path: eapiProbePath})

	type doctorItem struct {
		Domain     string            `json:"domain"`
		Status     zlib.DomainStatus `json:"status"`
		HTTPStatus int               `json:"http_status,omitempty"`
		FinalURL   string            `json:"final_url,omitempty"`
		Detail     string            `json:"detail,omitempty"`
		EAPIStatus zlib.DomainStatus `json:"eapi_status"`
		EAPIOK     bool              `json:"eapi_ok"`
	}

	items := make([]doctorItem, len(htmlChecks))
	for i, hc := range htmlChecks {
		ec := eapiChecks[i]
		items[i] = doctorItem{
			Domain:     hc.Domain,
			Status:     hc.Status,
			HTTPStatus: hc.HTTPStatus,
			FinalURL:   hc.FinalURL,
			Detail:     hc.Detail,
			EAPIStatus: ec.Status,
			EAPIOK:     ec.Status == zlib.DomainStatusHealthy,
		}
	}

	return jsonOK(map[string]any{
		"checks": items,
	})
}

func handleProfile() []byte {
	c, loggedIn := getClient()
	if !loggedIn {
		return jsonError(zlib.ErrNotLoggedIn)
	}
	limits, err := c.GetLimits()
	if err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"daily_amount":    limits.DailyAmount,
		"daily_allowed":   limits.DailyAllowed,
		"daily_remaining": limits.DailyRemaining,
		"daily_reset":     limits.DailyReset,
	})
}

type bookDTO struct {
	zlib.Book
	CommandID string `json:"command_id"`
}

func bookIDFromURL(u string) string {
	parts := strings.Split(u, "/")
	for i, p := range parts {
		if p == "book" && i+1 < len(parts) {
			return parts[i+1]
		}
	}
	return u
}

func bookCommandID(book zlib.Book) string {
	if book.ID != "" && book.Hash != "" {
		return book.ID + ":" + book.Hash
	}
	id := bookIDFromURL(book.URL)
	if id == book.URL {
		return ""
	}
	return id
}

func absolutizeCover(domain, cover string) string {
	cover = strings.TrimSpace(cover)
	if cover == "" || strings.HasPrefix(cover, "http://") || strings.HasPrefix(cover, "https://") {
		return cover
	}
	if strings.HasPrefix(cover, "//") {
		return "https:" + cover
	}
	if strings.HasPrefix(cover, "/") {
		return strings.TrimRight(domain, "/") + cover
	}
	return cover
}

func toBookDTO(domain string, b zlib.Book) bookDTO {
	b.Cover = absolutizeCover(domain, b.Cover)
	return bookDTO{
		Book:      b,
		CommandID: bookCommandID(b),
	}
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

	c, loggedIn := getClient()
	if !loggedIn {
		return jsonError(zlib.ErrNotLoggedIn)
	}
	query := strings.TrimSpace(req.Query)
	if query == "" {
		return jsonError(zlib.ErrEmptyQuery)
	}
	if req.Page <= 0 {
		req.Page = 1
	}
	if req.Count <= 0 {
		req.Count = 24
	}

	var exts []zlib.Extension
	for _, e := range req.Extensions {
		e = strings.TrimSpace(strings.ToUpper(e))
		if e != "" && e != "ALL" {
			exts = append(exts, zlib.Extension(e))
		}
	}
	var langs []zlib.Language
	for _, l := range req.Languages {
		l = strings.TrimSpace(strings.ToLower(l))
		if l != "" && l != "all" {
			langs = append(langs, zlib.Language(l))
		}
	}

	var opts *zlib.SearchOptions
	if req.Exact || req.FromYear > 0 || req.ToYear > 0 || len(exts) > 0 || len(langs) > 0 {
		opts = &zlib.SearchOptions{
			Exact:      req.Exact,
			FromYear:   req.FromYear,
			ToYear:     req.ToYear,
			Languages:  langs,
			Extensions: exts,
		}
	}

	var res zlib.SearchResult
	var err error
	if req.FullText {
		var ftOpts zlib.FullTextSearchOptions
		if opts != nil {
			ftOpts.SearchOptions = *opts
		}
		ftOpts.Phrase = req.Phrase
		res, err = c.FullTextSearch(query, req.Page, req.Count, &ftOpts)
	} else {
		res, err = c.Search(query, req.Page, req.Count, opts)
	}
	if err != nil {
		return jsonError(err)
	}

	dtos := make([]bookDTO, 0, len(res.Books))
	for _, b := range res.Books {
		dtos = append(dtos, toBookDTO(c.Domain(), b))
	}

	return jsonOK(map[string]any{
		"books":       dtos,
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
	c, loggedIn := getClient()
	if !loggedIn {
		return jsonError(zlib.ErrNotLoggedIn)
	}
	b, err := c.FetchBook(strings.TrimSpace(req.BookID))
	if err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"book": toBookDTO(c.Domain(), b),
	})
}

func handleStartDownload(payload []byte) []byte {
	var req struct {
		DownloadID string `json:"downloadId"`
		BookID     string `json:"bookId"`
		DirectURL  string `json:"directUrl"`
		DestDir    string `json:"destDir"`
	}
	if err := json.Unmarshal(payload, &req); err != nil {
		return jsonError(err)
	}
	c, loggedIn := getClient()
	if !loggedIn {
		return jsonError(zlib.ErrNotLoggedIn)
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
		if dlURL == "" {
			book, err := c.FetchBook(strings.TrimSpace(req.BookID))
			if err != nil {
				dlMu.Lock()
				st.State = "error"
				st.Error = err.Error()
				dlMu.Unlock()
				return
			}
			dlURL = book.DownloadURL
		}
		if dlURL == "" {
			dlMu.Lock()
			st.State = "error"
			st.Error = "no download URL available for this book"
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

		res, err := c.DownloadWithContext(ctx, dlURL, req.DestDir, func(written, total int64) {
			dlMu.Lock()
			st.Written = written
			st.Total = total
			dlMu.Unlock()
		})

		dlMu.Lock()
		defer dlMu.Unlock()
		if err != nil {
			if errors.Is(err, context.Canceled) || st.State == "cancelled" {
				st.State = "cancelled"
			} else {
				st.State = "error"
				st.Error = err.Error()
			}
			return
		}
		st.FilePath = res.FilePath
		st.Size = res.Size
		st.Written = res.Size
		if st.Total <= 0 {
			st.Total = res.Size
		}
		st.State = "complete"
	}()

	return jsonOK(map[string]any{"downloadId": req.DownloadID})
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

type historyItemDTO struct {
	zlib.DownloadHistoryItem
	CommandID string `json:"command_id"`
}

func historyCommandID(item zlib.DownloadHistoryItem) string {
	if item.ID != "" && item.Hash != "" {
		return item.ID + ":" + item.Hash
	}
	id := bookIDFromURL(item.URL)
	if id == item.URL {
		return ""
	}
	return id
}

func handleHistory(payload []byte) []byte {
	var req struct {
		Page int `json:"page"`
	}
	_ = json.Unmarshal(payload, &req)
	if req.Page <= 0 {
		req.Page = 1
	}

	c, loggedIn := getClient()
	if !loggedIn {
		return jsonError(zlib.ErrNotLoggedIn)
	}
	res, err := c.DownloadHistory(req.Page)
	if err != nil {
		return jsonError(err)
	}

	items := make([]historyItemDTO, 0, len(res.Items))
	for _, it := range res.Items {
		items = append(items, historyItemDTO{
			DownloadHistoryItem: it,
			CommandID:           historyCommandID(it),
		})
	}

	return jsonOK(map[string]any{
		"items":       items,
		"page":        res.Page,
		"total_pages": res.TotalPages,
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
	cfg := zlib.KindleConfig{
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

	if err := zlib.SendToKindle(strings.TrimSpace(req.FilePath), cfg, pass); err != nil {
		return jsonError(err)
	}
	return jsonOK(map[string]any{
		"to": cfg.To,
	})
}

func main() {}
