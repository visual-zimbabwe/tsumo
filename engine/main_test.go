package main

import (
	"encoding/json"
	"encoding/xml"
	"net/http"
	"net/url"
	"testing"
)

func TestInitStatusAndSetSources(t *testing.T) {
	tmp := t.TempDir()
	payload, _ := json.Marshal(map[string]string{"dataDir": tmp})
	res := dispatch("init", payload)

	var out map[string]any
	if err := json.Unmarshal(res, &out); err != nil {
		t.Fatalf("unmarshal init response: %v", err)
	}
	if out["ok"] != true {
		t.Fatalf("expected ok=true, got %v", out)
	}
	if out["enableGutenberg"] != true || out["enableInternetArchive"] != true {
		t.Fatalf("expected both open sources enabled by default, got %v", out)
	}

	// Reject disabling both sources
	badPayload, _ := json.Marshal(map[string]bool{"enableGutenberg": false, "enableInternetArchive": false})
	badRes := dispatch("setSources", badPayload)
	var badOut map[string]any
	_ = json.Unmarshal(badRes, &badOut)
	if badOut["ok"] != false {
		t.Fatalf("expected error when disabling all sources, got %v", badOut)
	}

	// Disable Gutenberg only
	okPayload, _ := json.Marshal(map[string]bool{"enableGutenberg": false, "enableInternetArchive": true})
	okRes := dispatch("setSources", okPayload)
	var okOut map[string]any
	_ = json.Unmarshal(okRes, &okOut)
	if okOut["ok"] != true || okOut["enableGutenberg"] != false || okOut["enableInternetArchive"] != true {
		t.Fatalf("unexpected setSources output: %v", okOut)
	}
}

func TestGutenbergOPDSParsing(t *testing.T) {
	rawXML := `<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:dcterms="http://purl.org/dc/terms/">
  <entry>
    <id>https://www.gutenberg.org/ebooks/2680.opds</id>
    <title>Meditations</title>
    <published>2001-06-01T00:00:00+00:00</published>
    <dcterms:language>en</dcterms:language>
    <author><name>Marcus Aurelius, Emperor of Rome</name></author>
    <content type="xhtml"><div><p>Summary: Stoic reflections.</p><p>Author: Marcus Aurelius</p></div></content>
    <link type="application/epub+zip" rel="http://opds-spec.org/acquisition" length="454997" href="https://www.gutenberg.org/ebooks/2680.epub3.images"/>
    <link type="application/x-mobipocket-ebook" rel="http://opds-spec.org/acquisition" length="608874" href="https://www.gutenberg.org/ebooks/2680.kf8.images"/>
  </entry>
</feed>`

	var feed opdsFeed
	if err := xml.Unmarshal([]byte(rawXML), &feed); err != nil {
		t.Fatalf("parse XML: %v", err)
	}
	if len(feed.Entries) != 1 {
		t.Fatalf("expected 1 entry, got %d", len(feed.Entries))
	}
	entry := feed.Entries[0]
	if got := extractGutenbergID(entry.ID); got != "2680" {
		t.Fatalf("expected id 2680, got %q", got)
	}
	href, size, ext, rank := pickGutenbergLink(entry.Links, "epub")
	if rank <= 0 || ext != "epub" || size != 454997 || href != "https://www.gutenberg.org/ebooks/2680.epub3.images" {
		t.Fatalf("unexpected epub pick: href=%q size=%d ext=%q rank=%d", href, size, ext, rank)
	}
	if summary := extractGutenbergSummary(entry.Content.InnerXML); summary != "Stoic reflections." {
		t.Fatalf("unexpected summary: %q", summary)
	}
}

func TestInternetArchiveQueryAndFileRanking(t *testing.T) {
	q, pref := buildInternetArchiveQuery(SearchRequest{
		Query:      "Meditations",
		Extensions: []string{"EPUB"},
		Languages:  []string{"english"},
		FromYear:   1800,
		ToYear:     1950,
	})
	if pref != "epub" {
		t.Fatalf("expected pref epub, got %q", pref)
	}
	for _, want := range []string{"format:(EPUB)", "-collection:(inlibrary)", "-collection:(printdisabled)", "year:[1800 TO 1950]", "language:(eng)"} {
		if !contains(q, want) {
			t.Fatalf("expected query %q to contain %q", q, want)
		}
	}

	if ext, rank := rankIAFile("book_encrypted.pdf", "ACS Encrypted PDF", "pdf"); rank >= 0 || ext != "" {
		t.Fatalf("expected encrypted file to be rejected, got ext=%q rank=%d", ext, rank)
	}
	if ext, rank := rankIAFile("meditations.epub", "EPUB", "epub"); ext != "epub" || rank != 100 {
		t.Fatalf("expected epub rank 100, got ext=%q rank=%d", ext, rank)
	}
	if got := formatByteSize(454997); got != "445 KB" {
		t.Fatalf("expected 445 KB, got %q", got)
	}
}

func TestHistoryAndKindleValidation(t *testing.T) {
	tmp := t.TempDir()
	initPayload, _ := json.Marshal(map[string]string{"dataDir": tmp})
	dispatch("init", initPayload)

	recordHistory("gutenberg:2680", "Meditations", "epub", "https://www.gutenberg.org/ebooks/2680.epub3.images", 454997, &Book{
		ID:     "2680",
		URL:    "https://www.gutenberg.org/ebooks/2680",
		Source: "gutenberg",
	})

	histRes := dispatch("history", []byte(`{"page":1}`))
	var out struct {
		OK    bool           `json:"ok"`
		Items []HistoryEntry `json:"items"`
	}
	if err := json.Unmarshal(histRes, &out); err != nil {
		t.Fatalf("unmarshal history: %v", err)
	}
	if !out.OK || len(out.Items) != 1 || out.Items[0].Name != "Meditations" {
		t.Fatalf("unexpected history response: %+v", out)
	}

	badCfg := KindleConfig{To: "invalid", From: "a@b.com", SMTPHost: "smtp.gmail.com", SMTPPort: 587}
	if err := badCfg.Validate(); err == nil {
		t.Fatal("expected invalid kindle email to fail validation")
	}
	goodCfg := KindleConfig{To: "reader@kindle.com", From: "sender@gmail.com", SMTPHost: "smtp.gmail.com", SMTPPort: 587}
	if err := goodCfg.Validate(); err != nil {
		t.Fatalf("expected valid kindle config, got %v", err)
	}
}

func TestResolveDownloadFilename(t *testing.T) {
	finalURL, _ := url.Parse("https://www.gutenberg.org/cache/epub/2680/pg2680-images-3.epub")
	resp := &http.Response{
		Header:  http.Header{},
		Request: &http.Request{URL: finalURL},
	}
	if got := resolveDownloadFilename(resp, "https://www.gutenberg.org/ebooks/2680.epub3.images", "Meditations", "epub"); got != "pg2680-images-3.epub" {
		t.Fatalf("expected final redirected filename pg2680-images-3.epub, got %q", got)
	}

	// Non-redirected .images URL falls back to clean title + ext
	noRedirect := &http.Response{Header: http.Header{}}
	if got := resolveDownloadFilename(noRedirect, "https://www.gutenberg.org/ebooks/2680.epub3.images", "Meditations", "epub"); got != "Meditations.epub" {
		t.Fatalf("expected fallback Meditations.epub, got %q", got)
	}
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(substr) == 0 || indexString(s, substr) >= 0)
}

func indexString(s, substr string) int {
	for i := 0; i+len(substr) <= len(s); i++ {
		if s[i:i+len(substr)] == substr {
			return i
		}
	}
	return -1
}
