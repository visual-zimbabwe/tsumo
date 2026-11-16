package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestInitSeedsDefaultNewsboatURLs(t *testing.T) {
	dir := t.TempDir()
	res := callDispatch(t, "init", map[string]any{"dataDir": dir})
	feedCount := int(res["feed_count"].(float64))
	if feedCount < 40 {
		t.Fatalf("expected seeded feeds >= 40 from default_urls.txt, got %d", feedCount)
	}

	feedsResp := callDispatch(t, "listFeeds", nil)
	feeds := feedsResp["feeds"].([]any)
	if len(feeds) != feedCount {
		t.Fatalf("listFeeds mismatch: %d vs %d", len(feeds), feedCount)
	}
}

func TestNewsboatURLsAndOPMLRoundTrip(t *testing.T) {
	raw := `
# comment line
https://aeon.co/feed.rss "Biography" "Philosophy" "~Aeon Essays"
"exec:/home/user/script.py" "Skipped"
https://www.quantamagazine.org/feed/ "Science"
`
	parsed := ParseNewsboatURLs(raw)
	if len(parsed.Entries) != 2 {
		t.Fatalf("expected 2 parsed entries, got %d", len(parsed.Entries))
	}
	if parsed.Skipped != 1 {
		t.Fatalf("expected 1 skipped exec entry, got %d", parsed.Skipped)
	}
	if parsed.Entries[0].CustomTitle != "Aeon Essays" {
		t.Fatalf("unexpected custom title: %q", parsed.Entries[0].CustomTitle)
	}

	feeds := []Feed{
		{URL: "https://aeon.co/feed.rss", CustomTitle: "Aeon Essays", Tags: []string{"Biography", "Philosophy"}},
		{URL: "https://www.quantamagazine.org/feed/", Title: "Quanta", Tags: []string{"Science"}},
	}
	opml, err := ExportOPML(feeds)
	if err != nil {
		t.Fatalf("ExportOPML: %v", err)
	}
	reimported, err := ParseOPML([]byte(opml))
	if err != nil {
		t.Fatalf("ParseOPML: %v", err)
	}
	if len(reimported) != 2 || reimported[0].URL != feeds[0].URL {
		t.Fatalf("OPML round-trip mismatch: %+v", reimported)
	}
}

func TestFeedDiscoveryRefreshArchiveHistoryAndExport(t *testing.T) {
	var srv *httptest.Server
	srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/":
			w.Header().Set("Content-Type", "text/html")
			fmt.Fprintf(w, `<html><head><link rel="alternate" type="application/rss+xml" href="%s/rss.xml"></head><body>Hi</body></html>`, srv.URL)
		case "/rss.xml":
			if r.Header.Get("If-None-Match") == `"v1"` {
				w.WriteHeader(http.StatusNotModified)
				return
			}
			w.Header().Set("Content-Type", "application/rss+xml")
			w.Header().Set("ETag", `"v1"`)
			fmt.Fprintf(w, `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel>
  <title>Test Dispatch</title>
  <link>%s</link>
  <item>
    <title>History of Typography</title>
    <link>%s/article-1</link>
    <guid>art-1</guid>
    <description><![CDATA[<p>Lead paragraph on Garamond.</p>]]></description>
  </item>
</channel></rss>`, srv.URL, srv.URL)
		case "/article-1":
			w.Header().Set("Content-Type", "text/html")
			fmt.Fprint(w, `<html><head><title>History of Typography</title></head><body><article><h1>History of Typography</h1><p>Claude Garamond crafted timeless letterforms in sixteenth-century Paris for enduring print books.</p><p>Second paragraph of editorial prose.</p></article></body></html>`)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	oldClient := httpClient
	setHTTPClientForTest(srv.Client())
	defer setHTTPClientForTest(oldClient)

	dir := t.TempDir()
	callDispatch(t, "init", map[string]any{"dataDir": dir})
	// Mark seeded feeds irrelevant by adding our test feed
	addRes := callDispatch(t, "addFeed", map[string]any{
		"url":  srv.URL,
		"tags": []string{"Design", "Typography"},
	})
	feedObj := addRes["feed"].(map[string]any)
	feedID := int64(feedObj["id"].(float64))
	if int(addRes["new_items"].(float64)) != 1 {
		t.Fatalf("expected 1 new item on addFeed, got %v", addRes["new_items"])
	}

	// Refresh same feed -> should set ETag and get 0 new items on second refresh
	ref1 := callDispatch(t, "refresh", map[string]any{"feed_ids": []int64{feedID}})
	if int(ref1["new_items"].(float64)) != 0 {
		t.Fatalf("expected 0 duplicate items, got %v", ref1["new_items"])
	}
	ref2 := callDispatch(t, "refresh", map[string]any{"feed_ids": []int64{feedID}})
	if int(ref2["refreshed"].(float64)) != 1 {
		t.Fatalf("expected 304 Not Modified to count as refreshed, got %v", ref2)
	}

	// List items
	itemsRes := callDispatch(t, "listItems", map[string]any{"feed_id": feedID, "filter": "unread"})
	items := itemsRes["items"].([]any)
	if len(items) != 1 {
		t.Fatalf("expected 1 unread item, got %d", len(items))
	}
	artID := int64(items[0].(map[string]any)["id"].(float64))

	// Search FTS5
	searchRes := callDispatch(t, "search", map[string]any{"query": "Garam"})
	foundItems := searchRes["items"].([]any)
	if len(foundItems) != 1 {
		t.Fatalf("expected FTS5 prefix search to find 1 item, got %d", len(foundItems))
	}

	// Archive fetches full readability text
	callDispatch(t, "setArchived", map[string]any{"id": artID, "archived": true})
	artRes := callDispatch(t, "getArticle", map[string]any{"id": artID})
	artMap := artRes["article"].(map[string]any)
	if !artMap["archived"].(bool) || !strings.Contains(artMap["full_text"].(string), "sixteenth-century Paris") {
		t.Fatalf("expected archived full_text, got %+v", artMap)
	}

	// Export Markdown
	mdRes := callDispatch(t, "exportMarkdown", map[string]any{"id": artID})
	if !strings.Contains(mdRes["markdown"].(string), "# History of Typography") {
		t.Fatalf("unexpected markdown: %v", mdRes["markdown"])
	}

	// Record open -> marks read + adds history
	callDispatch(t, "recordOpen", map[string]any{"id": artID, "via": "custom_tab"})
	histRes := callDispatch(t, "history", nil)
	entries := histRes["entries"].([]any)
	if len(entries) != 1 {
		t.Fatalf("expected 1 history entry, got %d", len(entries))
	}
}

func TestFTS5SplitSecondSearchAtScale(t *testing.T) {
	dir := t.TempDir()
	d, err := OpenDB(dir + "/bench.db")
	if err != nil {
		t.Fatal(err)
	}
	defer d.Close()

	f, err := d.UpsertFeed("https://example.com/feed", "Bench Feed", "", "", []string{"Tech"})
	if err != nil {
		t.Fatal(err)
	}
	const n = 25000
	batch := make([]Article, 0, n)
	for i := 0; i < n; i++ {
		title := fmt.Sprintf("Daily dispatch number %d about systems", i)
		if i == 12345 {
			title = "Byzantine Epigraphy and Mediterranean Trade Routes"
		}
		batch = append(batch, Article{
			GUID:      fmt.Sprintf("g-%d", i),
			URL:       fmt.Sprintf("https://example.com/%d", i),
			Title:     title,
			Author:    "Scholar",
			Summary:   "Short archival summary for indexing.",
			Published: int64(1700000000 + i),
		})
	}
	if _, err := d.UpsertItems(f.ID, "Bench Feed", batch); err != nil {
		t.Fatal(err)
	}

	start := time.Now()
	res, err := d.SearchFTS("Byzant Mediter", "all", 50)
	elapsed := time.Since(start)
	if err != nil {
		t.Fatal(err)
	}
	if len(res) != 1 || res[0].ID <= 0 {
		t.Fatalf("expected 1 FTS result, got %d", len(res))
	}
	if elapsed > 50*time.Millisecond {
		t.Fatalf("FTS5 search took %v (> 50ms budget)", elapsed)
	}
	t.Logf("FTS5 search across %d items completed in %v", n, elapsed)
}

func TestDeliveryConfigValidation(t *testing.T) {
	cfg := EmailDeliveryConfig{
		To:       "reader@example.com",
		From:     "sender@example.com",
		SMTPHost: "smtp.gmail.com",
		SMTPPort: 587,
	}
	if err := cfg.Validate(); err != nil {
		t.Fatalf("expected valid config, got %v", err)
	}
	cfg.To = "bad"
	if err := cfg.Validate(); err == nil {
		t.Fatal("expected error for invalid email")
	}
}

func callDispatch(t *testing.T, action string, payload map[string]any) map[string]any {
	t.Helper()
	var b []byte
	if payload != nil {
		var err error
		b, err = json.Marshal(payload)
		if err != nil {
			t.Fatal(err)
		}
	}
	raw := dispatch(action, b)
	var out map[string]any
	if err := json.Unmarshal(raw, &out); err != nil {
		t.Fatalf("dispatch(%s) invalid json: %v (%s)", action, err, string(raw))
	}
	if ok, _ := out["ok"].(bool); !ok {
		t.Fatalf("dispatch(%s) returned error: %v", action, out["error"])
	}
	return out
}
