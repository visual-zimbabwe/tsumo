package main

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"

	md "github.com/JohannesKaufmann/html-to-markdown"
	"github.com/PuerkitoBio/goquery"
	"github.com/go-shiori/go-readability"
	"github.com/mmcdole/gofeed"
)

const (
	tsumoUserAgent = "Tsumo/2.0 (Android RSS Reader; +https://github.com/juwimana/tsumo)"
	maxFeedBytes   = 10 << 20 // 10 MiB
	maxPageBytes   = 8 << 20  // 8 MiB
	refreshWorkers = 8
)

var (
	reTagStrip   = regexp.MustCompile(`(?s)<[^>]*>`)
	reWhitespace = regexp.MustCompile(`\s+`)
	reSlugUnsafe = regexp.MustCompile(`[^a-z0-9]+`)
)

func newHTTPClient(timeout time.Duration) *http.Client {
	return &http.Client{
		Timeout: timeout,
		Transport: &http.Transport{
			MaxIdleConns:        32,
			MaxIdleConnsPerHost: 4,
			IdleConnTimeout:     30 * time.Second,
		},
	}
}

type DiscoveredFeed struct {
	URL     string `json:"url"`
	Title   string `json:"title"`
	SiteURL string `json:"site_url"`
	Count   int    `json:"count"`
}

// DiscoverFeed takes any site or feed URL, resolves the actual RSS/Atom/JSON feed URL,
// and parses it once so the UI can show a live preview before adding.
func DiscoverFeed(ctx context.Context, client *http.Client, rawInput string) (DiscoveredFeed, *gofeed.Feed, error) {
	normalized := normalizeInputURL(rawInput)
	if normalized == "" {
		return DiscoveredFeed{}, nil, errors.New("please enter a valid URL")
	}

	body, finalURL, contentType, status, err := fetchBytes(ctx, client, normalized, "", "")
	if err != nil {
		return DiscoveredFeed{}, nil, err
	}
	if status >= 400 {
		return DiscoveredFeed{}, nil, fmt.Errorf("HTTP %d from %s", status, finalURL)
	}

	parser := gofeed.NewParser()
	if parsed, err := parser.Parse(bytes.NewReader(body)); err == nil && parsed != nil {
		return DiscoveredFeed{
			URL:     finalURL,
			Title:   strings.TrimSpace(parsed.Title),
			SiteURL: strings.TrimSpace(parsed.Link),
			Count:   len(parsed.Items),
		}, parsed, nil
	}

	// If HTML, look for <link rel="alternate" type="application/rss+xml|atom+xml|feed+json">
	if strings.Contains(strings.ToLower(contentType), "html") || bytes.Contains(bytes.ToLower(body[:minInt(len(body), 512)]), []byte("<html")) {
		candidates := extractAlternateFeedLinks(body, finalURL)
		// Common fallback paths if no <link> was found
		if base, err := url.Parse(finalURL); err == nil {
			for _, suffix := range []string{"/feed", "/feed/", "/rss.xml", "/atom.xml", "/feed.xml", "/rss"} {
				ref, _ := url.Parse(suffix)
				candidates = append(candidates, base.ResolveReference(ref).String())
			}
		}
		seen := make(map[string]bool)
		for _, cand := range candidates {
			if seen[cand] {
				continue
			}
			seen[cand] = true
			subCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
			b, resolved, _, st, err := fetchBytes(subCtx, client, cand, "", "")
			cancel()
			if err != nil || st >= 400 || len(b) == 0 {
				continue
			}
			if parsed, err := parser.Parse(bytes.NewReader(b)); err == nil && parsed != nil {
				return DiscoveredFeed{
					URL:     resolved,
					Title:   strings.TrimSpace(parsed.Title),
					SiteURL: strings.TrimSpace(parsed.Link),
					Count:   len(parsed.Items),
				}, parsed, nil
			}
		}
	}

	return DiscoveredFeed{}, nil, errors.New("no RSS, Atom, or JSON feed found at that URL")
}

func extractAlternateFeedLinks(htmlBytes []byte, baseURL string) []string {
	doc, err := goquery.NewDocumentFromReader(bytes.NewReader(htmlBytes))
	if err != nil {
		return nil
	}
	base, _ := url.Parse(baseURL)
	var links []string
	doc.Find(`link[rel~="alternate"]`).Each(func(_ int, s *goquery.Selection) {
		typ, _ := s.Attr("type")
		href, ok := s.Attr("href")
		if !ok || strings.TrimSpace(href) == "" {
			return
		}
		lt := strings.ToLower(typ)
		if strings.Contains(lt, "rss") || strings.Contains(lt, "atom") || strings.Contains(lt, "feed+json") || strings.Contains(lt, "xml") {
			if ref, err := url.Parse(strings.TrimSpace(href)); err == nil && base != nil {
				links = append(links, base.ResolveReference(ref).String())
			} else {
				links = append(links, strings.TrimSpace(href))
			}
		}
	})
	return links
}

func normalizeInputURL(raw string) string {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return ""
	}
	if !strings.Contains(raw, "://") {
		raw = "https://" + raw
	}
	u, err := url.Parse(raw)
	if err != nil || u.Host == "" || (u.Scheme != "http" && u.Scheme != "https") {
		return ""
	}
	return u.String()
}

type RefreshError struct {
	FeedID int64  `json:"feed_id"`
	Title  string `json:"title"`
	Error  string `json:"error"`
}

type RefreshResult struct {
	Refreshed int            `json:"refreshed"`
	NewItems  int            `json:"new_items"`
	Errors    []RefreshError `json:"errors"`
}

// RefreshFeeds concurrently fetches and stores items for the given feeds (or all feeds if feedIDs is empty).
func RefreshFeeds(ctx context.Context, db *DB, client *http.Client, feedIDs []int64) (RefreshResult, error) {
	allFeeds, err := db.ListFeeds()
	if err != nil {
		return RefreshResult{}, err
	}
	var targets []Feed
	if len(feedIDs) == 0 {
		targets = allFeeds
	} else {
		want := make(map[int64]bool, len(feedIDs))
		for _, id := range feedIDs {
			want[id] = true
		}
		for _, f := range allFeeds {
			if want[f.ID] {
				targets = append(targets, f)
			}
		}
	}

	if len(targets) == 0 {
		return RefreshResult{}, nil
	}

	type jobResult struct {
		feed     Feed
		newCount int
		err      error
	}

	jobs := make(chan Feed, len(targets))
	results := make(chan jobResult, len(targets))

	workers := minInt(refreshWorkers, len(targets))
	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			parser := gofeed.NewParser()
			for f := range jobs {
				if ctx.Err() != nil {
					results <- jobResult{feed: f, err: ctx.Err()}
					continue
				}
				n, err := fetchAndStoreFeed(ctx, db, client, parser, f)
				results <- jobResult{feed: f, newCount: n, err: err}
			}
		}()
	}

	for _, f := range targets {
		jobs <- f
	}
	close(jobs)
	wg.Wait()
	close(results)

	var out RefreshResult
	for r := range results {
		if r.err != nil {
			out.Errors = append(out.Errors, RefreshError{
				FeedID: r.feed.ID,
				Title:  r.feed.DisplayTitle(),
				Error:  r.err.Error(),
			})
		} else {
			out.Refreshed++
			out.NewItems += r.newCount
		}
	}
	return out, nil
}

func fetchAndStoreFeed(ctx context.Context, db *DB, client *http.Client, parser *gofeed.Parser, f Feed) (int, error) {
	reqCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodGet, f.URL, nil)
	if err != nil {
		_ = db.UpdateFeedFetchState(f.ID, "", "", f.ETag, f.LastModified, err.Error(), time.Now().Unix())
		return 0, err
	}
	req.Header.Set("User-Agent", tsumoUserAgent)
	req.Header.Set("Accept", "application/rss+xml, application/atom+xml, application/feed+json, application/xml, text/xml;q=0.9, */*;q=0.8")
	if f.ETag != "" {
		req.Header.Set("If-None-Match", f.ETag)
	}
	if f.LastModified != "" {
		req.Header.Set("If-Modified-Since", f.LastModified)
	}

	resp, err := client.Do(req)
	if err != nil {
		_ = db.UpdateFeedFetchState(f.ID, "", "", f.ETag, f.LastModified, err.Error(), time.Now().Unix())
		return 0, err
	}
	defer resp.Body.Close()

	now := time.Now().Unix()
	if resp.StatusCode == http.StatusNotModified {
		_ = db.UpdateFeedFetchState(f.ID, "", "", f.ETag, f.LastModified, "", now)
		return 0, nil
	}
	if resp.StatusCode >= 400 {
		e := fmt.Errorf("HTTP %d", resp.StatusCode)
		_ = db.UpdateFeedFetchState(f.ID, "", "", f.ETag, f.LastModified, e.Error(), now)
		return 0, e
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, maxFeedBytes))
	if err != nil {
		_ = db.UpdateFeedFetchState(f.ID, "", "", f.ETag, f.LastModified, err.Error(), now)
		return 0, err
	}

	parsed, err := parser.Parse(bytes.NewReader(body))
	if err != nil {
		e := fmt.Errorf("parse feed: %w", err)
		_ = db.UpdateFeedFetchState(f.ID, "", "", f.ETag, f.LastModified, e.Error(), now)
		return 0, e
	}

	newETag := strings.TrimSpace(resp.Header.Get("ETag"))
	newLastMod := strings.TrimSpace(resp.Header.Get("Last-Modified"))
	title := strings.TrimSpace(parsed.Title)
	siteURL := strings.TrimSpace(parsed.Link)

	displayTitle := f.CustomTitle
	if displayTitle == "" {
		displayTitle = title
	}
	if displayTitle == "" {
		displayTitle = f.DisplayTitle()
	}

	articles := convertFeedItems(parsed, now)
	inserted, err := db.UpsertItems(f.ID, displayTitle, articles)
	if err != nil {
		_ = db.UpdateFeedFetchState(f.ID, title, siteURL, newETag, newLastMod, err.Error(), now)
		return 0, err
	}
	_ = db.UpdateFeedFetchState(f.ID, title, siteURL, newETag, newLastMod, "", now)
	return inserted, nil
}

func convertFeedItems(parsed *gofeed.Feed, fallbackNow int64) []Article {
	if parsed == nil || len(parsed.Items) == 0 {
		return nil
	}
	out := make([]Article, 0, len(parsed.Items))
	for _, it := range parsed.Items {
		if it == nil {
			continue
		}
		var pub int64
		if it.PublishedParsed != nil {
			pub = it.PublishedParsed.Unix()
		} else if it.UpdatedParsed != nil {
			pub = it.UpdatedParsed.Unix()
		} else {
			pub = fallbackNow
		}

		var author string
		if len(it.Authors) > 0 && it.Authors[0] != nil {
			author = strings.TrimSpace(it.Authors[0].Name)
		} else if it.Author != nil {
			author = strings.TrimSpace(it.Author.Name)
		}

		contentHTML := strings.TrimSpace(it.Content)
		if contentHTML == "" {
			contentHTML = strings.TrimSpace(it.Description)
		}
		contentHTML = sanitizeArticleHTML(contentHTML)

		summary := plainSummary(it.Description)
		if summary == "" {
			summary = plainSummary(contentHTML)
		}

		guid := strings.TrimSpace(it.GUID)
		link := strings.TrimSpace(it.Link)
		if guid == "" {
			guid = link
		}
		title := strings.TrimSpace(html.UnescapeString(it.Title))
		if title == "" {
			title = link
		}

		out = append(out, Article{
			GUID:      guid,
			URL:       link,
			Title:     title,
			Author:    author,
			Summary:   summary,
			Content:   contentHTML,
			Published: pub,
		})
	}
	return out
}

func fetchBytes(ctx context.Context, client *http.Client, rawURL, etag, lastMod string) ([]byte, string, string, int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, rawURL, "", 0, err
	}
	req.Header.Set("User-Agent", tsumoUserAgent)
	if etag != "" {
		req.Header.Set("If-None-Match", etag)
	}
	if lastMod != "" {
		req.Header.Set("If-Modified-Since", lastMod)
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, rawURL, "", 0, err
	}
	defer resp.Body.Close()

	finalURL := rawURL
	if resp.Request != nil && resp.Request.URL != nil {
		finalURL = resp.Request.URL.String()
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxPageBytes))
	return body, finalURL, resp.Header.Get("Content-Type"), resp.StatusCode, err
}

func sanitizeArticleHTML(raw string) string {
	if strings.TrimSpace(raw) == "" {
		return ""
	}
	doc, err := goquery.NewDocumentFromReader(strings.NewReader("<body>" + raw + "</body>"))
	if err != nil {
		return raw
	}
	doc.Find("script, style, iframe, noscript, form").Remove()
	out, err := doc.Find("body").Html()
	if err != nil {
		return raw
	}
	return strings.TrimSpace(out)
}

func plainSummary(rawHTML string) string {
	if strings.TrimSpace(rawHTML) == "" {
		return ""
	}
	stripped := reTagStrip.ReplaceAllString(rawHTML, " ")
	stripped = html.UnescapeString(stripped)
	stripped = reWhitespace.ReplaceAllString(stripped, " ")
	stripped = strings.TrimSpace(stripped)
	if len(stripped) > 280 {
		// Trim cleanly on rune boundary
		runes := []rune(stripped)
		if len(runes) > 260 {
			return strings.TrimSpace(string(runes[:260])) + "…"
		}
	}
	return stripped
}

// FetchReadableContent grabs the full article HTML via readability when online,
// falling back to stored content/summary when offline.
func FetchReadableContent(ctx context.Context, client *http.Client, art Article) (cleanHTML string, err error) {
	if strings.TrimSpace(art.FullText) != "" {
		return art.FullText, nil
	}
	if strings.TrimSpace(art.URL) != "" {
		reqCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
		defer cancel()
		body, finalURL, _, status, fetchErr := fetchBytes(reqCtx, client, art.URL, "", "")
		if fetchErr == nil && status < 400 && len(body) > 0 {
			parsedURL, _ := url.Parse(finalURL)
			if readable, rErr := readability.FromReader(bytes.NewReader(body), parsedURL); rErr == nil {
				if clean := sanitizeArticleHTML(readable.Content); strings.TrimSpace(clean) != "" {
					return clean, nil
				}
			}
		}
	}
	if strings.TrimSpace(art.Content) != "" {
		return sanitizeArticleHTML(art.Content), nil
	}
	if strings.TrimSpace(art.Summary) != "" {
		return "<p>" + html.EscapeString(art.Summary) + "</p>", nil
	}
	return "", errors.New("no article content available")
}

// ArticleToMarkdown converts an article + its HTML into clean Markdown with metadata header.
func ArticleToMarkdown(art Article, articleHTML string) (markdown string, filename string) {
	converter := md.NewConverter("", true, nil)
	bodyMD, err := converter.ConvertString(articleHTML)
	if err != nil || strings.TrimSpace(bodyMD) == "" {
		bodyMD = plainSummary(articleHTML)
	}

	var dateStr string
	if art.Published > 0 {
		dateStr = time.Unix(art.Published, 0).UTC().Format("2006-01-02")
	}

	var b strings.Builder
	b.WriteString("# " + art.Title + "\n\n")
	if art.FeedTitle != "" {
		b.WriteString("**Source:** " + art.FeedTitle + "  \n")
	}
	if art.Author != "" {
		b.WriteString("**Author:** " + art.Author + "  \n")
	}
	if dateStr != "" {
		b.WriteString("**Published:** " + dateStr + "  \n")
	}
	if art.URL != "" {
		b.WriteString("**Link:** " + art.URL + "  \n")
	}
	b.WriteString("\n---\n\n")
	b.WriteString(strings.TrimSpace(bodyMD))
	b.WriteString("\n")

	return b.String(), slugifyFilename(art.Title, "md")
}

// ArticleToPlainParagraphs extracts clean paragraphs for crisp native PDF rendering.
func ArticleToPlainParagraphs(articleHTML string) []string {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader("<body>" + articleHTML + "</body>"))
	if err != nil {
		text := plainSummary(articleHTML)
		if text == "" {
			return nil
		}
		return []string{text}
	}
	var blocks []string
	doc.Find("h1, h2, h3, h4, p, blockquote, li, pre").Each(func(_ int, s *goquery.Selection) {
		txt := strings.TrimSpace(reWhitespace.ReplaceAllString(s.Text(), " "))
		if txt == "" {
			return
		}
		tag := goquery.NodeName(s)
		switch tag {
		case "h1", "h2", "h3", "h4":
			blocks = append(blocks, "## "+txt)
		case "blockquote":
			blocks = append(blocks, "> "+txt)
		case "li":
			blocks = append(blocks, "• "+txt)
		default:
			blocks = append(blocks, txt)
		}
	})
	if len(blocks) == 0 {
		txt := strings.TrimSpace(reWhitespace.ReplaceAllString(doc.Text(), " "))
		if txt != "" {
			blocks = append(blocks, txt)
		}
	}
	return blocks
}

func slugifyFilename(title, ext string) string {
	s := strings.ToLower(strings.TrimSpace(title))
	s = reSlugUnsafe.ReplaceAllString(s, "-")
	s = strings.Trim(s, "-")
	if s == "" {
		s = "tsumo-article"
	}
	if len(s) > 64 {
		s = strings.Trim(s[:64], "-")
	}
	return s + "." + ext
}
