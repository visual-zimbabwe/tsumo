package main

import (
	"encoding/xml"
	"fmt"
	"net/url"
	"strings"
)

// ParsedFeedEntry represents a feed parsed from Newsboat urls or OPML.
type ParsedFeedEntry struct {
	URL         string
	CustomTitle string
	Tags        []string
}

// URLsImportResult reports parsed feeds and skipped lines (like exec:/query:).
type URLsImportResult struct {
	Entries []ParsedFeedEntry
	Skipped int
}

// ParseNewsboatURLs parses Newsboat's `urls` format:
//   https://example.com/feed "Tag1" "Tag2" "~Custom Title"
// Lines starting with # or empty lines are ignored; exec:/filter:/query: entries are skipped cleanly.
func ParseNewsboatURLs(raw string) URLsImportResult {
	var res URLsImportResult
	lines := strings.Split(raw, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		tokens := tokenizeNewsboatLine(line)
		if len(tokens) == 0 {
			continue
		}
		rawURL := strings.TrimSpace(tokens[0])
		lower := strings.ToLower(rawURL)
		if strings.HasPrefix(lower, "exec:") || strings.HasPrefix(lower, "filter:") || strings.HasPrefix(lower, "query:") {
			res.Skipped++
			continue
		}
		parsed, err := url.Parse(rawURL)
		if err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.Host == "" {
			res.Skipped++
			continue
		}
		entry := ParsedFeedEntry{URL: rawURL}
		seenTag := make(map[string]bool)
		for _, tok := range tokens[1:] {
			tok = strings.TrimSpace(tok)
			if tok == "" {
				continue
			}
			if strings.HasPrefix(tok, "~") {
				if title := strings.TrimSpace(tok[1:]); title != "" {
					entry.CustomTitle = title
				}
				continue
			}
			if strings.HasPrefix(tok, "!") {
				// Newsboat hidden tag; ignore marker or keep without '!'
				tok = strings.TrimSpace(tok[1:])
				if tok == "" {
					continue
				}
			}
			if !seenTag[tok] {
				seenTag[tok] = true
				entry.Tags = append(entry.Tags, tok)
			}
		}
		res.Entries = append(res.Entries, entry)
	}
	return res
}

func tokenizeNewsboatLine(line string) []string {
	var tokens []string
	var cur strings.Builder
	inQuote := false
	escaped := false
	for _, r := range line {
		if escaped {
			cur.WriteRune(r)
			escaped = false
			continue
		}
		if r == '\\' && inQuote {
			escaped = true
			continue
		}
		if r == '"' {
			if inQuote {
				tokens = append(tokens, cur.String())
				cur.Reset()
				inQuote = false
			} else {
				if cur.Len() > 0 {
					tokens = append(tokens, cur.String())
					cur.Reset()
				}
				inQuote = true
			}
			continue
		}
		if !inQuote && (r == ' ' || r == '\t') {
			if cur.Len() > 0 {
				tokens = append(tokens, cur.String())
				cur.Reset()
			}
			continue
		}
		cur.WriteRune(r)
	}
	if cur.Len() > 0 {
		tokens = append(tokens, cur.String())
	}
	return tokens
}

// ExportNewsboatURLs formats feeds back into Newsboat's `urls` format.
func ExportNewsboatURLs(feeds []Feed) string {
	var b strings.Builder
	b.WriteString("# Exported by Tsumo\n")
	for _, f := range feeds {
		b.WriteString(f.URL)
		for _, t := range f.Tags {
			t = strings.TrimSpace(t)
			if t != "" {
				b.WriteString(fmt.Sprintf(" %q", t))
			}
		}
		if strings.TrimSpace(f.CustomTitle) != "" {
			b.WriteString(fmt.Sprintf(" %q", "~"+strings.TrimSpace(f.CustomTitle)))
		}
		b.WriteByte('\n')
	}
	return b.String()
}

type opmlDoc struct {
	XMLName xml.Name `xml:"opml"`
	Version string   `xml:"version,attr"`
	Head    opmlHead `xml:"head"`
	Body    opmlBody `xml:"body"`
}

type opmlHead struct {
	Title string `xml:"title"`
}

type opmlBody struct {
	Outlines []opmlOutline `xml:"outline"`
}

type opmlOutline struct {
	Text     string        `xml:"text,attr,omitempty"`
	Title    string        `xml:"title,attr,omitempty"`
	Type     string        `xml:"type,attr,omitempty"`
	XMLURL   string        `xml:"xmlUrl,attr,omitempty"`
	HTMLURL  string        `xml:"htmlUrl,attr,omitempty"`
	Category string        `xml:"category,attr,omitempty"`
	Outlines []opmlOutline `xml:"outline,omitempty"`
}

// ParseOPML parses OPML 1.0/2.0 documents including nested folder categories.
func ParseOPML(data []byte) ([]ParsedFeedEntry, error) {
	var doc opmlDoc
	if err := xml.Unmarshal(data, &doc); err != nil {
		return nil, fmt.Errorf("invalid OPML: %w", err)
	}
	var out []ParsedFeedEntry
	var walk func(nodes []opmlOutline, parentTags []string)
	walk = func(nodes []opmlOutline, parentTags []string) {
		for _, n := range nodes {
			label := strings.TrimSpace(n.Title)
			if label == "" {
				label = strings.TrimSpace(n.Text)
			}
			if strings.TrimSpace(n.XMLURL) != "" {
				tags := append([]string(nil), parentTags...)
				if strings.TrimSpace(n.Category) != "" {
					for _, c := range strings.Split(n.Category, ",") {
						c = strings.TrimSpace(c)
						if c != "" {
							tags = append(tags, c)
						}
					}
				}
				entry := ParsedFeedEntry{
					URL:         strings.TrimSpace(n.XMLURL),
					CustomTitle: label,
					Tags:        uniqueStrings(tags),
				}
				out = append(out, entry)
			} else if len(n.Outlines) > 0 {
				nextTags := append([]string(nil), parentTags...)
				if label != "" {
					nextTags = append(nextTags, label)
				}
				walk(n.Outlines, nextTags)
			}
		}
	}
	walk(doc.Body.Outlines, nil)
	return out, nil
}

// ExportOPML serializes feeds into an OPML 2.0 XML document.
func ExportOPML(feeds []Feed) (string, error) {
	doc := opmlDoc{
		Version: "2.0",
		Head:    opmlHead{Title: "Tsumo Feeds"},
	}
	for _, f := range feeds {
		title := f.DisplayTitle()
		doc.Body.Outlines = append(doc.Body.Outlines, opmlOutline{
			Text:     title,
			Title:    title,
			Type:     "rss",
			XMLURL:   f.URL,
			HTMLURL:  f.SiteURL,
			Category: strings.Join(f.Tags, ","),
		})
	}
	raw, err := xml.MarshalIndent(doc, "", "  ")
	if err != nil {
		return "", err
	}
	return xml.Header + string(raw) + "\n", nil
}

func uniqueStrings(in []string) []string {
	seen := make(map[string]bool, len(in))
	var out []string
	for _, s := range in {
		s = strings.TrimSpace(s)
		if s != "" && !seen[s] {
			seen[s] = true
			out = append(out, s)
		}
	}
	return out
}
