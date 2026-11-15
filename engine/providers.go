package main

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"html"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	gutenbergBaseURL   = "https://www.gutenberg.org"
	archiveOrgBaseURL  = "https://archive.org"
	openLibraryBaseURL = "https://openlibrary.org"
	tsumoUserAgent     = "Tsumo/1.0 (+https://github.com/tsumo)"
)

type Book struct {
	ID          string   `json:"id"`
	CommandID   string   `json:"command_id"`
	Hash        string   `json:"hash,omitempty"`
	ISBN        string   `json:"isbn,omitempty"`
	URL         string   `json:"url"`
	Cover       string   `json:"cover"`
	Name        string   `json:"name"`
	Authors     []string `json:"authors"`
	Publisher   string   `json:"publisher,omitempty"`
	Year        string   `json:"year,omitempty"`
	Language    string   `json:"language,omitempty"`
	Extension   string   `json:"extension,omitempty"`
	Size        string   `json:"size,omitempty"`
	Rating      string   `json:"rating,omitempty"`
	Quality     string   `json:"quality,omitempty"`
	Description string   `json:"description,omitempty"`
	Categories  string   `json:"categories,omitempty"`
	Edition     string   `json:"edition,omitempty"`
	DownloadURL string   `json:"download_url,omitempty"`
	Source      string   `json:"source,omitempty"`
}

type SearchRequest struct {
	Query      string
	Page       int
	Count      int
	Extensions []string
	Languages  []string
	FromYear   int
	ToYear     int
	Exact      bool
	FullText   bool
	Phrase     bool
}

type SearchResult struct {
	Books      []Book
	Page       int
	TotalPages int
}

type SourceHealth struct {
	Domain     string `json:"domain"`
	Name       string `json:"name"`
	Status     string `json:"status"`
	HTTPStatus int    `json:"http_status,omitempty"`
	Detail     string `json:"detail,omitempty"`
	OK         bool   `json:"ok"`
}

type DownloadResult struct {
	FilePath string
	Size     int64
}

var (
	sharedTransport = &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: (&net.Dialer{
			Timeout:   15 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		TLSHandshakeTimeout:   15 * time.Second,
		ResponseHeaderTimeout: 30 * time.Second,
		IdleConnTimeout:       60 * time.Second,
		MaxIdleConns:          20,
	}

	apiHTTPClient = &http.Client{
		Transport: sharedTransport,
		Timeout:   25 * time.Second,
	}

	// Download client relies on ResponseHeaderTimeout + ctx cancellation rather than an overall request timeout
	// so large public-domain files do not time out mid-stream while stalled headers still fail within 30s.
	downloadHTTPClient = &http.Client{
		Transport: sharedTransport,
	}
)

var (
	htmlTagRe    = regexp.MustCompile(`<[^>]+>`)
	whitespaceRe = regexp.MustCompile(`\s+`)
	yearRe       = regexp.MustCompile(`\b(1[0-9]{3}|20[0-9]{2})\b`)
)

func httpGetBytes(ctx context.Context, client *http.Client, rawURL string) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("User-Agent", tsumoUserAgent)
	req.Header.Set("Accept", "application/json, application/atom+xml, application/xml, text/xml, */*")

	resp, err := client.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 8<<20))
	if err != nil {
		return nil, resp.StatusCode, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return body, resp.StatusCode, fmt.Errorf("HTTP %d from %s", resp.StatusCode, rawURL)
	}
	return body, resp.StatusCode, nil
}

// --- Unified Search & Detail ---

func searchOpenArchives(ctx context.Context, req SearchRequest, enableGutenberg, enableIA bool) (SearchResult, error) {
	if req.Page <= 0 {
		req.Page = 1
	}
	if req.Count <= 0 {
		req.Count = 24
	}

	if req.FullText {
		if !enableIA {
			return SearchResult{}, errors.New("Internet Archive source is disabled")
		}
		return searchInternetArchive(ctx, req)
	}

	var (
		wg          sync.WaitGroup
		gbRes       SearchResult
		gbErr       error
		iaRes       SearchResult
		iaErr       error
		olBooks     []Book
		olErr       error
		wantGB      = enableGutenberg && gutenbergSupportsExtensions(req.Extensions)
		wantIA      = enableIA
		wantOL      = enableIA && shouldQueryOpenLibrary(req.Extensions)
	)

	if wantGB {
		wg.Add(1)
		go func() {
			defer wg.Done()
			gbRes, gbErr = searchProjectGutenberg(ctx, req)
		}()
	}

	if wantIA {
		wg.Add(1)
		go func() {
			defer wg.Done()
			iaRes, iaErr = searchInternetArchive(ctx, req)
		}()
	}

	if wantOL {
		wg.Add(1)
		go func() {
			defer wg.Done()
			olBooks, olErr = searchOpenLibraryPublic(ctx, req)
		}()
	}

	wg.Wait()

	if len(gbRes.Books) == 0 && len(iaRes.Books) == 0 && len(olBooks) == 0 {
		if gbErr != nil && iaErr != nil {
			return SearchResult{}, fmt.Errorf("Gutenberg: %v; Internet Archive: %v", gbErr, iaErr)
		}
		if gbErr != nil && !wantIA {
			return SearchResult{}, gbErr
		}
		if iaErr != nil && !wantGB {
			return SearchResult{}, iaErr
		}
		if olErr != nil && gbErr != nil {
			return SearchResult{}, gbErr
		}
	}

	iaMerged := mergeIAAndOpenLibrary(iaRes.Books, olBooks)
	combined := interleaveBooks(gbRes.Books, iaMerged)

	totalPages := gbRes.TotalPages
	if iaRes.TotalPages > totalPages {
		totalPages = iaRes.TotalPages
	}
	if totalPages <= 0 {
		totalPages = 1
	}

	return SearchResult{
		Books:      combined,
		Page:       req.Page,
		TotalPages: totalPages,
	}, nil
}

func interleaveBooks(a, b []Book) []Book {
	out := make([]Book, 0, len(a)+len(b))
	maxLen := len(a)
	if len(b) > maxLen {
		maxLen = len(b)
	}
	for i := 0; i < maxLen; i++ {
		if i < len(a) {
			out = append(out, a[i])
		}
		if i < len(b) {
			out = append(out, b[i])
		}
	}
	return out
}

func mergeIAAndOpenLibrary(iaBooks, olBooks []Book) []Book {
	seen := make(map[string]struct{}, len(iaBooks))
	out := make([]Book, 0, len(iaBooks)+len(olBooks))
	for _, b := range iaBooks {
		seen[b.ID] = struct{}{}
		out = append(out, b)
	}
	for _, b := range olBooks {
		if _, exists := seen[b.ID]; !exists {
			seen[b.ID] = struct{}{}
			out = append(out, b)
		}
	}
	return out
}

func fetchOpenBookDetail(ctx context.Context, commandID string) (Book, error) {
	commandID = strings.TrimSpace(commandID)
	switch {
	case strings.HasPrefix(commandID, "gutenberg:"):
		id := strings.TrimPrefix(commandID, "gutenberg:")
		return fetchGutenbergDetail(ctx, id, "epub")
	case strings.HasPrefix(commandID, "gutenberg-mobi:"):
		id := strings.TrimPrefix(commandID, "gutenberg-mobi:")
		return fetchGutenbergDetail(ctx, id, "mobi")
	case strings.HasPrefix(commandID, "ia:"):
		rest := strings.TrimPrefix(commandID, "ia:")
		prefExt := "epub"
		if ext, id, ok := strings.Cut(rest, ":"); ok && (ext == "epub" || ext == "pdf" || ext == "mobi") {
			prefExt = ext
			rest = id
		}
		return fetchInternetArchiveDetail(ctx, rest, prefExt)
	default:
		return Book{}, fmt.Errorf("unsupported book reference: %s", commandID)
	}
}

func checkOpenSources(ctx context.Context) []SourceHealth {
	type target struct {
		name   string
		domain string
		url    string
	}
	targets := []target{
		{
			name:   "Project Gutenberg",
			domain: gutenbergBaseURL,
			url:    gutenbergBaseURL + "/ebooks/search.opds/?query=meditations",
		},
		{
			name:   "Internet Archive",
			domain: archiveOrgBaseURL,
			url:    archiveOrgBaseURL + "/advancedsearch.php?q=mediatype:texts&rows=1&output=json",
		},
		{
			name:   "Open Library",
			domain: openLibraryBaseURL,
			url:    openLibraryBaseURL + "/search.json?q=meditations&limit=1",
		},
	}

	results := make([]SourceHealth, len(targets))
	var wg sync.WaitGroup
	for i, t := range targets {
		wg.Add(1)
		go func(idx int, tgt target) {
			defer wg.Done()
			start := time.Now()
			_, code, err := httpGetBytes(ctx, apiHTTPClient, tgt.url)
			elapsed := time.Since(start).Round(time.Millisecond)
			if err != nil {
				results[idx] = SourceHealth{
					Domain:     tgt.domain,
					Name:       tgt.name,
					Status:     "unreachable",
					HTTPStatus: code,
					Detail:     err.Error(),
					OK:         false,
				}
				return
			}
			results[idx] = SourceHealth{
				Domain:     tgt.domain,
				Name:       tgt.name,
				Status:     "healthy",
				HTTPStatus: code,
				Detail:     fmt.Sprintf("HTTP %d · %s", code, elapsed),
				OK:         true,
			}
		}(i, t)
	}
	wg.Wait()
	return results
}

// --- Project Gutenberg OPDS ---

type opdsFeed struct {
	TotalResults int        `xml:"totalResults"`
	ItemsPerPage int        `xml:"itemsPerPage"`
	Links        []opdsLink `xml:"link"`
	Entries      []opdsEntry `xml:"entry"`
}

type opdsEntry struct {
	ID         string         `xml:"id"`
	Title      string         `xml:"title"`
	Published  string         `xml:"published"`
	Rights     string         `xml:"rights"`
	Language   string         `xml:"http://purl.org/dc/terms/ language"`
	Authors    []opdsAuthor   `xml:"author"`
	Categories []opdsCategory `xml:"category"`
	Links      []opdsLink     `xml:"link"`
	Content    opdsContent    `xml:"content"`
}

type opdsAuthor struct {
	Name string `xml:"name"`
}

type opdsCategory struct {
	Term  string `xml:"term,attr"`
	Label string `xml:"label,attr"`
}

type opdsLink struct {
	Rel    string `xml:"rel,attr"`
	Type   string `xml:"type,attr"`
	Href   string `xml:"href,attr"`
	Title  string `xml:"title,attr"`
	Length int64  `xml:"length,attr"`
}

type opdsContent struct {
	Type     string `xml:"type,attr"`
	InnerXML string `xml:",innerxml"`
}

func gutenbergSupportsExtensions(exts []string) bool {
	if len(exts) == 0 {
		return true
	}
	for _, e := range exts {
		u := strings.ToUpper(strings.TrimSpace(e))
		if u == "ALL" || u == "EPUB" || u == "MOBI" {
			return true
		}
	}
	return false
}

func gutenbergLangCode(langs []string) string {
	for _, l := range langs {
		switch strings.ToLower(strings.TrimSpace(l)) {
		case "english", "en", "eng":
			return "en"
		case "french", "fr", "fre", "fra":
			return "fr"
		case "german", "de", "ger", "deu":
			return "de"
		case "spanish", "es", "spa":
			return "es"
		case "italian", "it", "ita":
			return "it"
		case "portuguese", "pt", "por":
			return "pt"
		case "dutch", "nl", "dut", "nld":
			return "nl"
		case "japanese", "ja", "jpn":
			return "ja"
		case "chinese", "zh", "chi", "zho":
			return "zh"
		case "latin", "la", "lat":
			return "la"
		case "greek", "el", "gre", "ell":
			return "el"
		case "russian", "ru", "rus":
			return "ru"
		default:
			code := strings.ToLower(strings.TrimSpace(l))
			if len(code) == 2 {
				return code
			}
		}
	}
	return ""
}

func searchProjectGutenberg(ctx context.Context, req SearchRequest) (SearchResult, error) {
	q := strings.TrimSpace(req.Query)
	if langCode := gutenbergLangCode(req.Languages); langCode != "" {
		q = q + " l." + langCode
	}
	startIdx := (req.Page-1)*25 + 1
	searchURL := fmt.Sprintf(
		"%s/ebooks/search.opds/?query=%s&start_index=%d",
		gutenbergBaseURL,
		url.QueryEscape(q),
		startIdx,
	)

	raw, _, err := httpGetBytes(ctx, apiHTTPClient, searchURL)
	if err != nil {
		return SearchResult{}, err
	}

	var feed opdsFeed
	if err := xml.Unmarshal(raw, &feed); err != nil {
		return SearchResult{}, fmt.Errorf("parse Gutenberg OPDS: %w", err)
	}

	wantMobi := len(req.Extensions) == 1 && strings.EqualFold(req.Extensions[0], "MOBI")
	ext := "epub"
	cmdPrefix := "gutenberg:"
	dlSuffix := ".epub3.images"
	if wantMobi {
		ext = "mobi"
		cmdPrefix = "gutenberg-mobi:"
		dlSuffix = ".kf8.images"
	}

	books := make([]Book, 0, len(feed.Entries))
	for _, entry := range feed.Entries {
		id := extractGutenbergID(entry.ID)
		if id == "" {
			continue
		}
		title := cleanText(entry.Title)
		if title == "" {
			continue
		}
		if req.Exact && !strings.EqualFold(title, req.Query) && !strings.Contains(strings.ToLower(title), strings.ToLower(req.Query)) {
			continue
		}
		authorText := cleanText(stripHTMLTags(entry.Content.InnerXML))
		var authors []string
		if authorText != "" {
			authors = []string{authorText}
		}
		books = append(books, Book{
			ID:          id,
			CommandID:   cmdPrefix + id,
			URL:         gutenbergBaseURL + "/ebooks/" + id,
			Cover:       fmt.Sprintf("%s/cache/epub/%s/pg%s.cover.medium.jpg", gutenbergBaseURL, id, id),
			Name:        title,
			Authors:     authors,
			Publisher:   "Project Gutenberg",
			Extension:   ext,
			DownloadURL: gutenbergBaseURL + "/ebooks/" + id + dlSuffix,
			Source:      "gutenberg",
		})
	}

	hasNext := false
	for _, l := range feed.Links {
		if l.Rel == "next" {
			hasNext = true
			break
		}
	}
	totalPages := req.Page
	if hasNext {
		totalPages = req.Page + 1
	}
	if feed.TotalResults > 0 && feed.ItemsPerPage > 0 {
		totalPages = (feed.TotalResults + feed.ItemsPerPage - 1) / feed.ItemsPerPage
	}

	return SearchResult{
		Books:      books,
		Page:       req.Page,
		TotalPages: totalPages,
	}, nil
}

func extractGutenbergID(rawID string) string {
	rawID = strings.TrimSpace(rawID)
	rawID = strings.TrimSuffix(rawID, ".opds")
	parts := strings.Split(rawID, "/")
	for i := len(parts) - 1; i >= 0; i-- {
		if parts[i] != "" {
			if _, err := strconv.Atoi(parts[i]); err == nil {
				return parts[i]
			}
		}
	}
	return ""
}

func fetchGutenbergDetail(ctx context.Context, id, preferredExt string) (Book, error) {
	detailURL := fmt.Sprintf("%s/ebooks/%s.opds", gutenbergBaseURL, url.PathEscape(id))
	raw, _, err := httpGetBytes(ctx, apiHTTPClient, detailURL)
	if err != nil {
		return Book{}, err
	}

	var feed opdsFeed
	if err := xml.Unmarshal(raw, &feed); err != nil {
		return Book{}, fmt.Errorf("parse Gutenberg detail OPDS: %w", err)
	}
	if len(feed.Entries) == 0 {
		return Book{}, fmt.Errorf("Project Gutenberg entry %s not found", id)
	}

	var bestBook Book
	bestRank := -1
	for _, entry := range feed.Entries {
		dlHref, dlSize, dlExt, rank := pickGutenbergLink(entry.Links, preferredExt)
		if rank < bestRank {
			continue
		}
		bestRank = rank

		var authors []string
		for _, a := range entry.Authors {
			if n := cleanText(a.Name); n != "" {
				authors = append(authors, n)
			}
		}
		var cats []string
		for _, c := range entry.Categories {
			label := cleanText(c.Label)
			if label == "" {
				label = cleanText(c.Term)
			}
			if label != "" && label != "Text" {
				cats = append(cats, label)
			}
		}
		cover := fmt.Sprintf("%s/cache/epub/%s/pg%s.cover.medium.jpg", gutenbergBaseURL, id, id)
		for _, l := range entry.Links {
			if l.Rel == "http://opds-spec.org/image" && l.Href != "" {
				cover = absolutizeHTTPURL(gutenbergBaseURL, l.Href)
				break
			}
		}
		year := ""
		if m := yearRe.FindString(entry.Published); m != "" {
			year = m
		}
		desc := extractGutenbergSummary(entry.Content.InnerXML)
		cmdPrefix := "gutenberg:"
		if dlExt == "mobi" {
			cmdPrefix = "gutenberg-mobi:"
		}

		bestBook = Book{
			ID:          id,
			CommandID:   cmdPrefix + id,
			URL:         gutenbergBaseURL + "/ebooks/" + id,
			Cover:       cover,
			Name:        cleanText(entry.Title),
			Authors:     authors,
			Publisher:   "Project Gutenberg",
			Year:        year,
			Language:    prettyLanguage(entry.Language),
			Extension:   dlExt,
			Size:        formatByteSize(dlSize),
			Description: desc,
			Categories:  strings.Join(cats, ", "),
			Edition:     cleanText(entry.Rights),
			DownloadURL: dlHref,
			Source:      "gutenberg",
		}
	}

	if bestBook.DownloadURL == "" {
		return Book{}, fmt.Errorf("no downloadable %s file found for Gutenberg #%s", strings.ToUpper(preferredExt), id)
	}
	return bestBook, nil
}

func pickGutenbergLink(links []opdsLink, preferredExt string) (href string, size int64, ext string, rank int) {
	bestRank := -1
	wantMobi := strings.EqualFold(preferredExt, "mobi")
	for _, l := range links {
		if l.Rel != "http://opds-spec.org/acquisition" || l.Href == "" {
			continue
		}
		fullHref := absolutizeHTTPURL(gutenbergBaseURL, l.Href)
		lower := strings.ToLower(fullHref)
		var r int
		var e string
		switch {
		case !wantMobi && strings.Contains(lower, ".epub3.images"):
			r, e = 100, "epub"
		case !wantMobi && strings.Contains(lower, ".epub.images"):
			r, e = 90, "epub"
		case !wantMobi && strings.Contains(lower, ".epub.noimages"):
			r, e = 80, "epub"
		case !wantMobi && strings.Contains(l.Type, "epub"):
			r, e = 70, "epub"
		case wantMobi && strings.Contains(lower, ".kf8.images"):
			r, e = 100, "mobi"
		case wantMobi && strings.Contains(lower, ".kindle.images"):
			r, e = 90, "mobi"
		case wantMobi && strings.Contains(lower, ".kindle.noimages"):
			r, e = 80, "mobi"
		case strings.Contains(l.Type, "mobipocket") && !wantMobi:
			r, e = 20, "mobi"
		default:
			r = -1
		}
		if r > bestRank {
			bestRank = r
			href = fullHref
			size = l.Length
			ext = e
		}
	}
	return href, size, ext, bestRank
}

func extractGutenbergSummary(rawXML string) string {
	text := stripHTMLTags(rawXML)
	if idx := strings.Index(text, "Summary:"); idx >= 0 {
		sub := strings.TrimSpace(text[idx+len("Summary:"):])
		for _, stop := range []string{"Reading Level:", "Author:", "EBook No.:", "Published:", "Language:"} {
			if sIdx := strings.Index(sub, stop); sIdx >= 0 {
				sub = sub[:sIdx]
			}
		}
		return cleanText(sub)
	}
	return cleanText(text)
}

// --- Internet Archive & Open Library ---

func shouldQueryOpenLibrary(exts []string) bool {
	if len(exts) == 0 {
		return true
	}
	for _, e := range exts {
		u := strings.ToUpper(strings.TrimSpace(e))
		if u == "ALL" || u == "EPUB" || u == "PDF" {
			return true
		}
	}
	return false
}

func buildInternetArchiveQuery(req SearchRequest) (queryStr string, prefExt string) {
	q := strings.TrimSpace(req.Query)
	var parts []string
	switch {
	case req.FullText && req.Phrase:
		parts = append(parts, fmt.Sprintf(`text:("%s")`, escapeIAQuery(q)))
	case req.FullText:
		parts = append(parts, fmt.Sprintf(`text:(%s)`, escapeIAQuery(q)))
	case req.Exact:
		parts = append(parts, fmt.Sprintf(`(title:("%s") OR creator:("%s"))`, escapeIAQuery(q), escapeIAQuery(q)))
	default:
		parts = append(parts, fmt.Sprintf(`(%s)`, escapeIAQuery(q)))
	}

	parts = append(parts, "mediatype:(texts)", "-collection:(inlibrary)", "-collection:(printdisabled)")

	prefExt = "epub"
	if len(req.Extensions) > 0 {
		var fmtClauses []string
		for _, e := range req.Extensions {
			switch strings.ToUpper(strings.TrimSpace(e)) {
			case "EPUB":
				fmtClauses = append(fmtClauses, "format:(EPUB)")
				prefExt = "epub"
			case "PDF":
				fmtClauses = append(fmtClauses, `format:("Text PDF")`)
				prefExt = "pdf"
			case "MOBI":
				fmtClauses = append(fmtClauses, "format:(Kindle)")
				prefExt = "mobi"
			case "AZW3":
				fmtClauses = append(fmtClauses, "format:(AZW3)")
				prefExt = "azw3"
			}
		}
		if len(fmtClauses) > 0 {
			parts = append(parts, "("+strings.Join(fmtClauses, " OR ")+")")
		}
	} else {
		parts = append(parts, `(format:(EPUB) OR format:("Text PDF"))`)
	}

	if langClause := iaLangClause(req.Languages); langClause != "" {
		parts = append(parts, langClause)
	}

	if req.FromYear > 0 || req.ToYear > 0 {
		from := req.FromYear
		to := req.ToYear
		if from <= 0 {
			from = 1000
		}
		if to <= 0 {
			to = time.Now().Year()
		}
		parts = append(parts, fmt.Sprintf("year:[%d TO %d]", from, to))
	}

	return strings.Join(parts, " AND "), prefExt
}

func escapeIAQuery(s string) string {
	replacer := strings.NewReplacer(`"`, ` `, `(`, ` `, `)`, ` `, `:`, ` `)
	return cleanText(replacer.Replace(s))
}

func iaLangClause(langs []string) string {
	for _, l := range langs {
		low := strings.ToLower(strings.TrimSpace(l))
		if low == "" {
			continue
		}
		switch low {
		case "english", "en", "eng":
			return `(language:(eng) OR language:(English))`
		case "french", "fr", "fre", "fra":
			return `(language:(fre) OR language:(fra) OR language:(French))`
		case "german", "de", "ger", "deu":
			return `(language:(ger) OR language:(deu) OR language:(German))`
		case "spanish", "es", "spa":
			return `(language:(spa) OR language:(Spanish))`
		case "italian", "it", "ita":
			return `(language:(ita) OR language:(Italian))`
		case "japanese", "ja", "jpn":
			return `(language:(jpn) OR language:(Japanese))`
		case "chinese", "zh", "chi", "zho":
			return `(language:(chi) OR language:(zho) OR language:(Chinese))`
		case "latin", "la", "lat":
			return `(language:(lat) OR language:(Latin))`
		default:
			return fmt.Sprintf(`language:(%s)`, escapeIAQuery(low))
		}
	}
	return ""
}

func searchInternetArchive(ctx context.Context, req SearchRequest) (SearchResult, error) {
	q, prefExt := buildInternetArchiveQuery(req)
	rows := req.Count
	if rows <= 0 || rows > 25 {
		rows = 20
	}

	u := archiveOrgBaseURL + "/advancedsearch.php?" +
		"q=" + url.QueryEscape(q) +
		"&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&fl[]=language&fl[]=publisher&fl[]=description&fl[]=subject" +
		"&sort[]=downloads+desc" +
		fmt.Sprintf("&rows=%d&page=%d&output=json", rows, req.Page)

	raw, _, err := httpGetBytes(ctx, apiHTTPClient, u)
	if err != nil {
		return SearchResult{}, err
	}

	var payload struct {
		Response struct {
			NumFound int              `json:"numFound"`
			Docs     []map[string]any `json:"docs"`
		} `json:"response"`
	}
	if err := json.Unmarshal(raw, &payload); err != nil {
		return SearchResult{}, fmt.Errorf("parse Internet Archive search: %w", err)
	}

	books := make([]Book, 0, len(payload.Response.Docs))
	for _, doc := range payload.Response.Docs {
		id := firstString(doc["identifier"])
		title := cleanText(firstString(doc["title"]))
		if id == "" || title == "" {
			continue
		}
		authors := stringSlice(doc["creator"])
		year := firstString(doc["year"])
		if m := yearRe.FindString(year); m != "" {
			year = m
		}
		lang := prettyLanguage(firstString(doc["language"]))
		pub := cleanText(firstString(doc["publisher"]))
		desc := cleanText(stripHTMLTags(firstString(doc["description"])))
		cats := strings.Join(stringSlice(doc["subject"]), ", ")

		books = append(books, Book{
			ID:          id,
			CommandID:   fmt.Sprintf("ia:%s:%s", prefExt, id),
			URL:         archiveOrgBaseURL + "/details/" + id,
			Cover:       archiveOrgBaseURL + "/services/img/" + id,
			Name:        title,
			Authors:     authors,
			Publisher:   pub,
			Year:        year,
			Language:    lang,
			Extension:   prefExt,
			Description: desc,
			Categories:  cats,
			Source:      "archive.org",
		})
	}

	totalPages := 1
	if payload.Response.NumFound > 0 && rows > 0 {
		totalPages = (payload.Response.NumFound + rows - 1) / rows
	}

	return SearchResult{
		Books:      books,
		Page:       req.Page,
		TotalPages: totalPages,
	}, nil
}

func searchOpenLibraryPublic(ctx context.Context, req SearchRequest) ([]Book, error) {
	params := url.Values{}
	if req.Exact {
		params.Set("title", req.Query)
	} else {
		params.Set("q", req.Query)
	}
	params.Set("has_fulltext", "true")
	params.Set("limit", "10")
	params.Set("page", strconv.Itoa(req.Page))
	params.Set("fields", "title,author_name,first_publish_year,ebook_access,public_scan_b,ia,cover_i,publisher,language,isbn")

	u := openLibraryBaseURL + "/search.json?" + params.Encode()
	raw, _, err := httpGetBytes(ctx, apiHTTPClient, u)
	if err != nil {
		return nil, err
	}

	var payload struct {
		Docs []struct {
			Title            string   `json:"title"`
			AuthorName       []string `json:"author_name"`
			FirstPublishYear int      `json:"first_publish_year"`
			EbookAccess      string   `json:"ebook_access"`
			PublicScan       bool     `json:"public_scan_b"`
			IA               []string `json:"ia"`
			CoverI           int64    `json:"cover_i"`
			Publisher        []string `json:"publisher"`
			Language         []string `json:"language"`
			ISBN             []string `json:"isbn"`
		} `json:"docs"`
	}
	if err := json.Unmarshal(raw, &payload); err != nil {
		return nil, fmt.Errorf("parse Open Library search: %w", err)
	}

	prefExt := "epub"
	if len(req.Extensions) == 1 {
		prefExt = strings.ToLower(strings.TrimSpace(req.Extensions[0]))
		if prefExt == "all" || prefExt == "" {
			prefExt = "epub"
		}
	}

	var books []Book
	for _, d := range payload.Docs {
		if d.EbookAccess != "public" || !d.PublicScan || len(d.IA) == 0 {
			continue
		}
		if req.FromYear > 0 && d.FirstPublishYear > 0 && d.FirstPublishYear < req.FromYear {
			continue
		}
		if req.ToYear > 0 && d.FirstPublishYear > 0 && d.FirstPublishYear > req.ToYear {
			continue
		}
		lang := ""
		if len(d.Language) > 0 {
			lang = prettyLanguage(d.Language[0])
		}
		if len(req.Languages) > 0 && lang != "" && !matchesAnyLanguage(lang, d.Language, req.Languages) {
			continue
		}

		iaID := strings.TrimSpace(d.IA[0])
		if iaID == "" || strings.TrimSpace(d.Title) == "" {
			continue
		}
		cover := archiveOrgBaseURL + "/services/img/" + iaID
		if d.CoverI > 0 {
			cover = fmt.Sprintf("https://covers.openlibrary.org/b/id/%d-L.jpg", d.CoverI)
		}
		year := ""
		if d.FirstPublishYear > 0 {
			year = strconv.Itoa(d.FirstPublishYear)
		}
		pub := ""
		if len(d.Publisher) > 0 {
			pub = d.Publisher[0]
		}
		isbn := ""
		if len(d.ISBN) > 0 {
			isbn = d.ISBN[0]
		}

		books = append(books, Book{
			ID:        iaID,
			CommandID: fmt.Sprintf("ia:%s:%s", prefExt, iaID),
			ISBN:      isbn,
			URL:       archiveOrgBaseURL + "/details/" + iaID,
			Cover:     cover,
			Name:      cleanText(d.Title),
			Authors:   d.AuthorName,
			Publisher: pub,
			Year:      year,
			Language:  lang,
			Extension: prefExt,
			Source:    "openlibrary",
		})
	}
	return books, nil
}

func matchesAnyLanguage(pretty string, rawCodes, filters []string) bool {
	for _, f := range filters {
		fl := strings.ToLower(strings.TrimSpace(f))
		if fl == "" {
			continue
		}
		if strings.Contains(strings.ToLower(pretty), fl) {
			return true
		}
		for _, c := range rawCodes {
			if strings.EqualFold(c, fl) || strings.HasPrefix(strings.ToLower(pretty), fl) {
				return true
			}
		}
	}
	return false
}

func fetchInternetArchiveDetail(ctx context.Context, identifier, preferredExt string) (Book, error) {
	metaURL := fmt.Sprintf("%s/metadata/%s", archiveOrgBaseURL, url.PathEscape(identifier))
	raw, _, err := httpGetBytes(ctx, apiHTTPClient, metaURL)
	if err != nil {
		return Book{}, err
	}

	var payload struct {
		IsDark   bool           `json:"is_dark"`
		Metadata map[string]any `json:"metadata"`
		Files    []struct {
			Name    string `json:"name"`
			Format  string `json:"format"`
			Size    string `json:"size"`
			Private string `json:"private"`
		} `json:"files"`
	}
	if err := json.Unmarshal(raw, &payload); err != nil {
		return Book{}, fmt.Errorf("parse Internet Archive metadata: %w", err)
	}
	if payload.IsDark {
		return Book{}, fmt.Errorf("Internet Archive item %s is unavailable", identifier)
	}

	var bestName, bestExt string
	var bestSize int64
	bestRank := -1

	pref := strings.ToLower(strings.TrimSpace(preferredExt))
	if pref == "" {
		pref = "epub"
	}

	for _, f := range payload.Files {
		if strings.EqualFold(f.Private, "true") || strings.TrimSpace(f.Name) == "" {
			continue
		}
		ext, rank := rankIAFile(f.Name, f.Format, pref)
		if rank > bestRank {
			bestRank = rank
			bestName = f.Name
			bestExt = ext
			bestSize, _ = strconv.ParseInt(strings.TrimSpace(f.Size), 10, 64)
		}
	}

	if bestName == "" {
		return Book{}, fmt.Errorf("no public downloadable EPUB/PDF found for %s (may be borrow-only)", identifier)
	}

	encodedParts := strings.Split(bestName, "/")
	for i, p := range encodedParts {
		encodedParts[i] = url.PathEscape(p)
	}
	dlURL := fmt.Sprintf("%s/download/%s/%s", archiveOrgBaseURL, url.PathEscape(identifier), strings.Join(encodedParts, "/"))

	meta := payload.Metadata
	year := firstString(meta["year"])
	if year == "" {
		year = firstString(meta["date"])
	}
	if m := yearRe.FindString(year); m != "" {
		year = m
	}

	return Book{
		ID:          identifier,
		CommandID:   fmt.Sprintf("ia:%s:%s", bestExt, identifier),
		ISBN:        firstString(meta["isbn"]),
		URL:         archiveOrgBaseURL + "/details/" + identifier,
		Cover:       archiveOrgBaseURL + "/services/img/" + identifier,
		Name:        cleanText(firstString(meta["title"])),
		Authors:     stringSlice(meta["creator"]),
		Publisher:   cleanText(firstString(meta["publisher"])),
		Year:        year,
		Language:    prettyLanguage(firstString(meta["language"])),
		Extension:   bestExt,
		Size:        formatByteSize(bestSize),
		Description: cleanText(stripHTMLTags(firstString(meta["description"]))),
		Categories:  strings.Join(stringSlice(meta["subject"]), ", "),
		DownloadURL: dlURL,
		Source:      "archive.org",
	}, nil
}

func rankIAFile(name, format, preferredExt string) (ext string, rank int) {
	lowerName := strings.ToLower(name)
	lowerFmt := strings.ToLower(format)
	if strings.Contains(lowerFmt, "encrypted") || strings.Contains(lowerName, "_encrypted") {
		return "", -1
	}

	switch {
	case strings.HasSuffix(lowerName, ".epub") || lowerFmt == "epub":
		ext = "epub"
	case strings.HasSuffix(lowerName, ".pdf") && !strings.HasSuffix(lowerName, "_bw.pdf") && !strings.HasSuffix(lowerName, "_text.pdf"):
		ext = "pdf"
	case strings.HasSuffix(lowerName, ".mobi") || lowerFmt == "kindle":
		ext = "mobi"
	case strings.HasSuffix(lowerName, ".azw3"):
		ext = "azw3"
	default:
		return "", -1
	}

	if ext == preferredExt {
		return ext, 100
	}
	switch ext {
	case "epub":
		return ext, 80
	case "pdf":
		return ext, 60
	case "mobi":
		return ext, 40
	default:
		return ext, 20
	}
}

// --- Streaming File Download ---

func downloadFileWithProgress(ctx context.Context, rawURL, destDir, fallbackTitle, fallbackExt string, progressFn func(written, total int64)) (DownloadResult, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return DownloadResult{}, err
	}
	req.Header.Set("User-Agent", tsumoUserAgent)
	req.Header.Set("Accept", "application/epub+zip, application/pdf, application/octet-stream, */*")

	resp, err := downloadHTTPClient.Do(req)
	if err != nil {
		return DownloadResult{}, err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return DownloadResult{}, fmt.Errorf("download failed: HTTP %d", resp.StatusCode)
	}

	filename := resolveDownloadFilename(resp, rawURL, fallbackTitle, fallbackExt)
	destPath := filepath.Join(destDir, filename)

	f, err := os.Create(destPath)
	if err != nil {
		return DownloadResult{}, err
	}

	cleanupOnErr := true
	defer func() {
		f.Close()
		if cleanupOnErr {
			_ = os.Remove(destPath)
		}
	}()

	total := resp.ContentLength
	var written int64
	buf := make([]byte, 32*1024)
	for {
		n, readErr := resp.Body.Read(buf)
		if n > 0 {
			nw, writeErr := f.Write(buf[:n])
			if writeErr != nil {
				return DownloadResult{}, writeErr
			}
			written += int64(nw)
			if progressFn != nil {
				progressFn(written, total)
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return DownloadResult{}, readErr
		}
	}

	cleanupOnErr = false
	return DownloadResult{FilePath: destPath, Size: written}, nil
}

func resolveDownloadFilename(resp *http.Response, rawURL, fallbackTitle, fallbackExt string) string {
	name := ""
	if resp != nil {
		if cd := resp.Header.Get("Content-Disposition"); cd != "" {
			for _, part := range strings.Split(cd, ";") {
				part = strings.TrimSpace(part)
				if strings.HasPrefix(strings.ToLower(part), "filename=") {
					name = strings.TrimSpace(part[len("filename="):])
					name = strings.Trim(name, `"'`)
					break
				}
			}
		}
		if name == "" && resp.Request != nil && resp.Request.URL != nil {
			base := path.Base(resp.Request.URL.Path)
			if unescaped, err := url.PathUnescape(base); err == nil {
				base = unescaped
			}
			if base != "" && base != "." && base != "/" {
				name = base
			}
		}
	}
	if name == "" {
		if u, err := url.Parse(rawURL); err == nil {
			base := path.Base(u.Path)
			if unescaped, err := url.PathUnescape(base); err == nil {
				base = unescaped
			}
			if base != "" && base != "." && base != "/" {
				name = base
			}
		}
	}
	if name == "" || !hasKnownEbookExtension(name) {
		titlePart := sanitizeFilename(fallbackTitle)
		if titlePart == "download" {
			titlePart = "tsumo-book"
		}
		ext := strings.TrimPrefix(strings.ToLower(strings.TrimSpace(fallbackExt)), ".")
		if ext == "" {
			ext = "epub"
		}
		name = titlePart + "." + ext
	}
	return sanitizeFilename(name)
}

func hasKnownEbookExtension(name string) bool {
	lower := strings.ToLower(strings.TrimSpace(name))
	for _, ext := range []string{".epub", ".pdf", ".mobi", ".azw3", ".txt"} {
		if strings.HasSuffix(lower, ext) {
			return true
		}
	}
	return false
}

func sanitizeFilename(name string) string {
	name = strings.ReplaceAll(name, `\`, "/")
	name = path.Base(name)
	replacer := strings.NewReplacer(":", "_", "*", "_", "?", "_", "\"", "_", "<", "_", ">", "_", "|", "_")
	name = strings.TrimSpace(replacer.Replace(name))
	if name == "" || name == "." || name == ".." || name == "/" {
		return "download"
	}
	return name
}

// --- Helpers ---

func absolutizeHTTPURL(base, raw string) string {
	raw = strings.TrimSpace(raw)
	if raw == "" || strings.HasPrefix(raw, "http://") || strings.HasPrefix(raw, "https://") {
		return raw
	}
	if strings.HasPrefix(raw, "//") {
		return "https:" + raw
	}
	if strings.HasPrefix(raw, "/") {
		return strings.TrimRight(base, "/") + raw
	}
	return strings.TrimRight(base, "/") + "/" + raw
}

func stripHTMLTags(s string) string {
	if s == "" {
		return ""
	}
	return html.UnescapeString(htmlTagRe.ReplaceAllString(s, " "))
}

func cleanText(s string) string {
	return strings.TrimSpace(whitespaceRe.ReplaceAllString(s, " "))
}

func formatByteSize(bytes int64) string {
	if bytes <= 0 {
		return ""
	}
	const kb = 1024.0
	const mb = 1024.0 * 1024.0
	if float64(bytes) >= mb {
		return fmt.Sprintf("%.1f MB", float64(bytes)/mb)
	}
	return fmt.Sprintf("%d KB", (bytes+1023)/1024)
}

func prettyLanguage(raw string) string {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "en", "eng", "english":
		return "English"
	case "fr", "fre", "fra", "french":
		return "French"
	case "de", "ger", "deu", "german":
		return "German"
	case "es", "spa", "spanish":
		return "Spanish"
	case "it", "ita", "italian":
		return "Italian"
	case "pt", "por", "portuguese":
		return "Portuguese"
	case "ja", "jpn", "japanese":
		return "Japanese"
	case "zh", "chi", "zho", "chinese":
		return "Chinese"
	case "la", "lat", "latin":
		return "Latin"
	case "el", "gre", "ell", "grc", "greek":
		return "Greek"
	case "ru", "rus", "russian":
		return "Russian"
	default:
		return cleanText(raw)
	}
}

func firstString(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case float64:
		return strconv.FormatInt(int64(t), 10)
	case []any:
		for _, item := range t {
			if s, ok := item.(string); ok && strings.TrimSpace(s) != "" {
				return s
			}
		}
	}
	return ""
}

func stringSlice(v any) []string {
	switch t := v.(type) {
	case string:
		if s := cleanText(t); s != "" {
			return []string{s}
		}
	case []any:
		out := make([]string, 0, len(t))
		for _, item := range t {
			if s, ok := item.(string); ok {
				if c := cleanText(s); c != "" {
					out = append(out, c)
				}
			}
		}
		return out
	}
	return nil
}
