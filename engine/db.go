package main

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"strings"
	"time"
	"unicode"

	_ "github.com/mattn/go-sqlite3"
)

// Feed represents a subscribed RSS/Atom/JSON feed.
type Feed struct {
	ID           int64    `json:"id"`
	URL          string   `json:"url"`
	Title        string   `json:"title"`
	SiteURL      string   `json:"site_url"`
	Tags         []string `json:"tags"`
	CustomTitle  string   `json:"custom_title"`
	ETag         string   `json:"-"`
	LastModified string   `json:"-"`
	LastFetch    int64    `json:"last_fetch"`
	Error        string   `json:"error"`
	Position     int      `json:"position"`
	UnreadCount  int      `json:"unread_count"`
}

func (f Feed) DisplayTitle() string {
	if strings.TrimSpace(f.CustomTitle) != "" {
		return strings.TrimSpace(f.CustomTitle)
	}
	if strings.TrimSpace(f.Title) != "" {
		return strings.TrimSpace(f.Title)
	}
	if u, err := url.Parse(f.URL); err == nil && u.Host != "" {
		return strings.TrimPrefix(u.Host, "www.")
	}
	return f.URL
}

// Article represents a single feed item stored locally.
type Article struct {
	ID        int64  `json:"id"`
	FeedID    int64  `json:"feed_id"`
	FeedTitle string `json:"feed_title"`
	GUID      string `json:"guid"`
	URL       string `json:"url"`
	Title     string `json:"title"`
	Author    string `json:"author"`
	Summary   string `json:"summary"`
	Content   string `json:"content,omitempty"`
	FullText  string `json:"full_text,omitempty"`
	Snippet   string `json:"snippet,omitempty"`
	Published int64  `json:"published"`
	Unread    bool   `json:"unread"`
	Archived  bool   `json:"archived"`
}

// HistoryRecord represents an article open event joined with its article metadata.
type HistoryRecord struct {
	HistoryID int64   `json:"history_id"`
	OpenedAt  int64   `json:"opened_at"`
	Via       string  `json:"via"`
	Article   Article `json:"article"`
}

// DB wraps SQLite + FTS5 storage for Tsumo.
type DB struct {
	sql *sql.DB
}

func OpenDB(path string) (*DB, error) {
	sqldb, err := sql.Open("sqlite3", path+"?_foreign_keys=on&_journal_mode=WAL&_busy_timeout=5000")
	if err != nil {
		return nil, err
	}
	sqldb.SetMaxOpenConns(1)
	d := &DB{sql: sqldb}
	if err := d.migrate(); err != nil {
		_ = sqldb.Close()
		return nil, err
	}
	return d, nil
}

func (d *DB) Close() error {
	if d == nil || d.sql == nil {
		return nil
	}
	return d.sql.Close()
}

func (d *DB) migrate() error {
	stmts := []string{
		`PRAGMA foreign_keys = ON;`,
		`CREATE TABLE IF NOT EXISTS meta (
			key TEXT PRIMARY KEY,
			value TEXT NOT NULL
		);`,
		`CREATE TABLE IF NOT EXISTS feeds (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			url TEXT NOT NULL UNIQUE,
			title TEXT NOT NULL DEFAULT '',
			site_url TEXT NOT NULL DEFAULT '',
			tags TEXT NOT NULL DEFAULT '[]',
			custom_title TEXT NOT NULL DEFAULT '',
			etag TEXT NOT NULL DEFAULT '',
			last_modified TEXT NOT NULL DEFAULT '',
			last_fetch INTEGER NOT NULL DEFAULT 0,
			error TEXT NOT NULL DEFAULT '',
			position INTEGER NOT NULL DEFAULT 0
		);`,
		`CREATE TABLE IF NOT EXISTS items (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			feed_id INTEGER NOT NULL REFERENCES feeds(id) ON DELETE CASCADE,
			feed_title TEXT NOT NULL DEFAULT '',
			guid TEXT NOT NULL,
			url TEXT NOT NULL DEFAULT '',
			title TEXT NOT NULL DEFAULT '',
			author TEXT NOT NULL DEFAULT '',
			summary TEXT NOT NULL DEFAULT '',
			content TEXT NOT NULL DEFAULT '',
			full_text TEXT NOT NULL DEFAULT '',
			published INTEGER NOT NULL DEFAULT 0,
			unread INTEGER NOT NULL DEFAULT 1,
			archived INTEGER NOT NULL DEFAULT 0,
			UNIQUE(feed_id, guid)
		);`,
		`CREATE TABLE IF NOT EXISTS history (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
			opened_at INTEGER NOT NULL,
			via TEXT NOT NULL DEFAULT 'reader'
		);`,
		`CREATE INDEX IF NOT EXISTS idx_items_feed_pub ON items(feed_id, published DESC);`,
		`CREATE INDEX IF NOT EXISTS idx_items_pub ON items(published DESC);`,
		`CREATE INDEX IF NOT EXISTS idx_items_unread ON items(unread, published DESC);`,
		`CREATE INDEX IF NOT EXISTS idx_items_archived ON items(archived, published DESC);`,
		`CREATE INDEX IF NOT EXISTS idx_history_opened ON history(opened_at DESC);`,
		`CREATE VIRTUAL TABLE IF NOT EXISTS items_fts USING fts5(
			title, author, summary, content, feed_title,
			content='items', content_rowid='id',
			tokenize='unicode61 remove_diacritics 2'
		);`,
		`CREATE TRIGGER IF NOT EXISTS items_ai AFTER INSERT ON items BEGIN
			INSERT INTO items_fts(rowid, title, author, summary, content, feed_title)
			VALUES (new.id, new.title, new.author, new.summary, new.content, new.feed_title);
		END;`,
		`CREATE TRIGGER IF NOT EXISTS items_ad AFTER DELETE ON items BEGIN
			INSERT INTO items_fts(items_fts, rowid, title, author, summary, content, feed_title)
			VALUES ('delete', old.id, old.title, old.author, old.summary, old.content, old.feed_title);
		END;`,
		`CREATE TRIGGER IF NOT EXISTS items_au AFTER UPDATE ON items BEGIN
			INSERT INTO items_fts(items_fts, rowid, title, author, summary, content, feed_title)
			VALUES ('delete', old.id, old.title, old.author, old.summary, old.content, old.feed_title);
			INSERT INTO items_fts(rowid, title, author, summary, content, feed_title)
			VALUES (new.id, new.title, new.author, new.summary, new.content, new.feed_title);
		END;`,
	}
	for _, s := range stmts {
		if _, err := d.sql.Exec(s); err != nil {
			return fmt.Errorf("migrate failed on %q: %w", s[:minInt(len(s), 48)], err)
		}
	}
	return nil
}

func (d *DB) GetMeta(key string) (string, bool) {
	var val string
	err := d.sql.QueryRow(`SELECT value FROM meta WHERE key = ?`, key).Scan(&val)
	if err != nil {
		return "", false
	}
	return val, true
}

func (d *DB) SetMeta(key, value string) error {
	_, err := d.sql.Exec(`INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value`, key, value)
	return err
}

func (d *DB) UpsertFeed(rawURL, title, siteURL, customTitle string, tags []string) (Feed, error) {
	rawURL = strings.TrimSpace(rawURL)
	if rawURL == "" {
		return Feed{}, errors.New("feed URL is required")
	}
	tagsJSON, _ := json.Marshal(uniqueStrings(tags))
	var nextPos int
	_ = d.sql.QueryRow(`SELECT COALESCE(MAX(position), -1) + 1 FROM feeds`).Scan(&nextPos)

	_, err := d.sql.Exec(`
		INSERT INTO feeds(url, title, site_url, tags, custom_title, position)
		VALUES(?, ?, ?, ?, ?, ?)
		ON CONFLICT(url) DO UPDATE SET
			title = CASE WHEN excluded.title != '' THEN excluded.title ELSE feeds.title END,
			site_url = CASE WHEN excluded.site_url != '' THEN excluded.site_url ELSE feeds.site_url END,
			custom_title = CASE WHEN excluded.custom_title != '' THEN excluded.custom_title ELSE feeds.custom_title END,
			tags = CASE WHEN excluded.tags != '[]' THEN excluded.tags ELSE feeds.tags END
	`, rawURL, strings.TrimSpace(title), strings.TrimSpace(siteURL), string(tagsJSON), strings.TrimSpace(customTitle), nextPos)
	if err != nil {
		return Feed{}, err
	}
	return d.GetFeedByURL(rawURL)
}

func (d *DB) GetFeedByURL(rawURL string) (Feed, error) {
	row := d.sql.QueryRow(`
		SELECT f.id, f.url, f.title, f.site_url, f.tags, f.custom_title, f.etag, f.last_modified, f.last_fetch, f.error, f.position,
			COALESCE((SELECT COUNT(*) FROM items i WHERE i.feed_id = f.id AND i.unread = 1), 0)
		FROM feeds f WHERE f.url = ?
	`, rawURL)
	return scanFeed(row)
}

func (d *DB) GetFeed(id int64) (Feed, error) {
	row := d.sql.QueryRow(`
		SELECT f.id, f.url, f.title, f.site_url, f.tags, f.custom_title, f.etag, f.last_modified, f.last_fetch, f.error, f.position,
			COALESCE((SELECT COUNT(*) FROM items i WHERE i.feed_id = f.id AND i.unread = 1), 0)
		FROM feeds f WHERE f.id = ?
	`, id)
	return scanFeed(row)
}

func (d *DB) ListFeeds() ([]Feed, error) {
	rows, err := d.sql.Query(`
		SELECT f.id, f.url, f.title, f.site_url, f.tags, f.custom_title, f.etag, f.last_modified, f.last_fetch, f.error, f.position,
			COALESCE(u.cnt, 0)
		FROM feeds f
		LEFT JOIN (
			SELECT feed_id, COUNT(*) AS cnt FROM items WHERE unread = 1 GROUP BY feed_id
		) u ON u.feed_id = f.id
		ORDER BY f.position ASC, f.id ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Feed
	for rows.Next() {
		f, err := scanFeed(rows)
		if err != nil {
			return nil, err
		}
		if f.Title == "" {
			f.Title = f.DisplayTitle()
		}
		out = append(out, f)
	}
	return out, rows.Err()
}

func (d *DB) UpdateFeedMeta(id int64, title, siteURL, customTitle string, tags []string, position *int) error {
	current, err := d.GetFeed(id)
	if err != nil {
		return err
	}
	if title != "" {
		current.Title = title
	}
	if siteURL != "" {
		current.SiteURL = siteURL
	}
	if customTitle != "" {
		current.CustomTitle = customTitle
	}
	if tags != nil {
		current.Tags = uniqueStrings(tags)
	}
	if position != nil {
		current.Position = *position
	}
	tagsJSON, _ := json.Marshal(current.Tags)
	_, err = d.sql.Exec(`
		UPDATE feeds SET title = ?, site_url = ?, custom_title = ?, tags = ?, position = ? WHERE id = ?
	`, current.Title, current.SiteURL, current.CustomTitle, string(tagsJSON), current.Position, id)
	if err == nil {
		display := current.DisplayTitle()
		_, _ = d.sql.Exec(`UPDATE items SET feed_title = ? WHERE feed_id = ? AND feed_title != ?`, display, id, display)
	}
	return err
}

func (d *DB) ReorderFeeds(orderedIDs []int64) error {
	tx, err := d.sql.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()
	stmt, err := tx.Prepare(`UPDATE feeds SET position = ? WHERE id = ?`)
	if err != nil {
		return err
	}
	defer stmt.Close()
	for i, id := range orderedIDs {
		if _, err := stmt.Exec(i, id); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (d *DB) UpdateFeedFetchState(id int64, title, siteURL, etag, lastMod, fetchErr string, fetchedAt int64) error {
	_, err := d.sql.Exec(`
		UPDATE feeds SET
			title = CASE WHEN ? != '' THEN ? ELSE title END,
			site_url = CASE WHEN ? != '' THEN ? ELSE site_url END,
			etag = ?,
			last_modified = ?,
			last_fetch = ?,
			error = ?
		WHERE id = ?
	`, title, title, siteURL, siteURL, etag, lastMod, fetchedAt, fetchErr, id)
	return err
}

func (d *DB) RemoveFeed(id int64) error {
	_, err := d.sql.Exec(`DELETE FROM feeds WHERE id = ?`, id)
	return err
}

func (d *DB) UpsertItems(feedID int64, feedTitle string, items []Article) (int, error) {
	if len(items) == 0 {
		return 0, nil
	}
	tx, err := d.sql.Begin()
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()

	stmt, err := tx.Prepare(`
		INSERT INTO items(feed_id, feed_title, guid, url, title, author, summary, content, published, unread)
		VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
		ON CONFLICT(feed_id, guid) DO UPDATE SET
			feed_title = excluded.feed_title,
			url = CASE WHEN excluded.url != '' THEN excluded.url ELSE items.url END,
			title = CASE WHEN excluded.title != '' THEN excluded.title ELSE items.title END,
			author = CASE WHEN excluded.author != '' THEN excluded.author ELSE items.author END,
			summary = CASE WHEN excluded.summary != '' THEN excluded.summary ELSE items.summary END,
			content = CASE WHEN LENGTH(excluded.content) > LENGTH(items.content) THEN excluded.content ELSE items.content END,
			published = CASE WHEN excluded.published > 0 THEN excluded.published ELSE items.published END
	`)
	if err != nil {
		return 0, err
	}
	defer stmt.Close()

	inserted := 0
	for _, it := range items {
		guid := strings.TrimSpace(it.GUID)
		if guid == "" {
			guid = strings.TrimSpace(it.URL)
		}
		if guid == "" {
			guid = strings.TrimSpace(it.Title)
		}
		if guid == "" {
			continue
		}
		var exists int
		_ = tx.QueryRow(`SELECT 1 FROM items WHERE feed_id = ? AND guid = ?`, feedID, guid).Scan(&exists)
		if _, err := stmt.Exec(feedID, feedTitle, guid, it.URL, it.Title, it.Author, it.Summary, it.Content, it.Published); err != nil {
			return inserted, err
		}
		if exists == 0 {
			inserted++
		}
	}
	if err := tx.Commit(); err != nil {
		return 0, err
	}
	return inserted, nil
}

func (d *DB) ListItems(feedID int64, tag, filter string, offset, limit int) ([]Article, error) {
	if limit <= 0 || limit > 200 {
		limit = 60
	}
	if offset < 0 {
		offset = 0
	}
	where := []string{"1=1"}
	var args []any

	if feedID > 0 {
		where = append(where, "i.feed_id = ?")
		args = append(args, feedID)
	}
	if strings.TrimSpace(tag) != "" {
		// Match exact JSON tag string in feeds.tags array
		where = append(where, `f.tags LIKE ?`)
		args = append(args, fmt.Sprintf("%%\"%s\"%%", escapeLike(strings.TrimSpace(tag))))
	}
	switch strings.ToLower(strings.TrimSpace(filter)) {
	case "unread":
		where = append(where, "i.unread = 1")
	case "archived":
		where = append(where, "i.archived = 1")
	}

	query := fmt.Sprintf(`
		SELECT i.id, i.feed_id, CASE WHEN f.custom_title != '' THEN f.custom_title ELSE i.feed_title END,
			i.guid, i.url, i.title, i.author, i.summary, i.published, i.unread, i.archived
		FROM items i
		JOIN feeds f ON f.id = i.feed_id
		WHERE %s
		ORDER BY i.published DESC, i.id DESC
		LIMIT ? OFFSET ?
	`, strings.Join(where, " AND "))
	args = append(args, limit, offset)

	rows, err := d.sql.Query(query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Article
	for rows.Next() {
		a, err := scanArticleSummary(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, a)
	}
	return out, rows.Err()
}

func (d *DB) GetArticle(id int64) (Article, error) {
	row := d.sql.QueryRow(`
		SELECT i.id, i.feed_id, CASE WHEN f.custom_title != '' THEN f.custom_title ELSE i.feed_title END,
			i.guid, i.url, i.title, i.author, i.summary, i.content, i.full_text, i.published, i.unread, i.archived
		FROM items i
		JOIN feeds f ON f.id = i.feed_id
		WHERE i.id = ?
	`, id)
	var a Article
	var unreadInt, archivedInt int
	err := row.Scan(
		&a.ID, &a.FeedID, &a.FeedTitle, &a.GUID, &a.URL, &a.Title, &a.Author,
		&a.Summary, &a.Content, &a.FullText, &a.Published, &unreadInt, &archivedInt,
	)
	if err != nil {
		return Article{}, err
	}
	a.Unread = unreadInt != 0
	a.Archived = archivedInt != 0
	return a, nil
}

// SearchFTS performs split-second FTS5 prefix search across items.
func (d *DB) SearchFTS(rawQuery, scope string, limit int) ([]Article, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	ftsQuery := buildFTSQuery(rawQuery)
	if ftsQuery == "" {
		return nil, nil
	}

	var extraWhere string
	var joinHistory string
	switch strings.ToLower(strings.TrimSpace(scope)) {
	case "archived":
		extraWhere = "AND i.archived = 1"
	case "history":
		joinHistory = "JOIN history h ON h.item_id = i.id"
		extraWhere = "GROUP BY i.id"
	}

	sqlStr := fmt.Sprintf(`
		SELECT i.id, i.feed_id, CASE WHEN f.custom_title != '' THEN f.custom_title ELSE i.feed_title END,
			i.guid, i.url, i.title, i.author, i.summary, i.published, i.unread, i.archived,
			snippet(items_fts, 2, '<b>', '</b>', '…', 18)
		FROM items_fts
		JOIN items i ON i.id = items_fts.rowid
		JOIN feeds f ON f.id = i.feed_id
		%s
		WHERE items_fts MATCH ? %s
		ORDER BY bm25(items_fts, 8.0, 3.0, 2.0, 1.0, 4.0), i.published DESC
		LIMIT ?
	`, joinHistory, extraWhere)

	rows, err := d.sql.Query(sqlStr, ftsQuery, limit)
	if err != nil {
		// Fallback to LIKE on edge syntax errors
		return d.searchFallbackLike(rawQuery, scope, limit)
	}
	defer rows.Close()
	var out []Article
	for rows.Next() {
		var a Article
		var unreadInt, archivedInt int
		if err := rows.Scan(
			&a.ID, &a.FeedID, &a.FeedTitle, &a.GUID, &a.URL, &a.Title, &a.Author,
			&a.Summary, &a.Published, &unreadInt, &archivedInt, &a.Snippet,
		); err != nil {
			return nil, err
		}
		a.Unread = unreadInt != 0
		a.Archived = archivedInt != 0
		out = append(out, a)
	}
	return out, rows.Err()
}

func (d *DB) searchFallbackLike(rawQuery, scope string, limit int) ([]Article, error) {
	like := "%" + escapeLike(strings.TrimSpace(rawQuery)) + "%"
	extra := ""
	if scope == "archived" {
		extra = "AND i.archived = 1"
	}
	rows, err := d.sql.Query(fmt.Sprintf(`
		SELECT i.id, i.feed_id, i.feed_title, i.guid, i.url, i.title, i.author, i.summary, i.published, i.unread, i.archived
		FROM items i
		WHERE (i.title LIKE ? OR i.summary LIKE ? OR i.feed_title LIKE ?) %s
		ORDER BY i.published DESC LIMIT ?
	`, extra), like, like, like, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Article
	for rows.Next() {
		a, err := scanArticleSummary(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, a)
	}
	return out, rows.Err()
}

func buildFTSQuery(raw string) string {
	var tokens []string
	var cur strings.Builder
	flush := func() {
		if cur.Len() > 0 {
			tokens = append(tokens, `"`+cur.String()+`"*`)
			cur.Reset()
		}
	}
	for _, r := range raw {
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' {
			cur.WriteRune(r)
		} else {
			flush()
		}
	}
	flush()
	return strings.Join(tokens, " ")
}

func (d *DB) MarkRead(ids []int64, read bool) error {
	if len(ids) == 0 {
		return nil
	}
	val := 1
	if read {
		val = 0
	}
	placeholders := make([]string, len(ids))
	args := make([]any, 0, len(ids)+1)
	args = append(args, val)
	for i, id := range ids {
		placeholders[i] = "?"
		args = append(args, id)
	}
	_, err := d.sql.Exec(fmt.Sprintf(`UPDATE items SET unread = ? WHERE id IN (%s)`, strings.Join(placeholders, ",")), args...)
	return err
}

func (d *DB) MarkFeedRead(feedID int64) error {
	if feedID <= 0 {
		_, err := d.sql.Exec(`UPDATE items SET unread = 0 WHERE unread = 1`)
		return err
	}
	_, err := d.sql.Exec(`UPDATE items SET unread = 0 WHERE feed_id = ? AND unread = 1`, feedID)
	return err
}

func (d *DB) SetArchived(id int64, archived bool, fullText string) error {
	flag := 0
	if archived {
		flag = 1
	}
	if fullText != "" {
		_, err := d.sql.Exec(`UPDATE items SET archived = ?, full_text = ? WHERE id = ?`, flag, fullText, id)
		return err
	}
	_, err := d.sql.Exec(`UPDATE items SET archived = ? WHERE id = ?`, flag, id)
	return err
}

func (d *DB) SaveFullText(id int64, fullText string) error {
	if strings.TrimSpace(fullText) == "" {
		return nil
	}
	_, err := d.sql.Exec(`UPDATE items SET full_text = ? WHERE id = ?`, fullText, id)
	return err
}

func (d *DB) RecordOpen(itemID int64, via string) error {
	if via == "" {
		via = "reader"
	}
	now := time.Now().Unix()
	tx, err := d.sql.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.Exec(`INSERT INTO history(item_id, opened_at, via) VALUES(?, ?, ?)`, itemID, now, via); err != nil {
		return err
	}
	if _, err := tx.Exec(`UPDATE items SET unread = 0 WHERE id = ?`, itemID); err != nil {
		return err
	}
	return tx.Commit()
}

func (d *DB) ListHistory(query string, offset, limit int) ([]HistoryRecord, error) {
	if limit <= 0 || limit > 200 {
		limit = 80
	}
	if offset < 0 {
		offset = 0
	}
	where := "1=1"
	var args []any
	if q := strings.TrimSpace(query); q != "" {
		like := "%" + escapeLike(q) + "%"
		where = "(i.title LIKE ? OR i.feed_title LIKE ? OR i.author LIKE ?)"
		args = append(args, like, like, like)
	}
	args = append(args, limit, offset)

	rows, err := d.sql.Query(fmt.Sprintf(`
		SELECT h.id, h.opened_at, h.via,
			i.id, i.feed_id, CASE WHEN f.custom_title != '' THEN f.custom_title ELSE i.feed_title END,
			i.guid, i.url, i.title, i.author, i.summary, i.published, i.unread, i.archived
		FROM history h
		JOIN items i ON i.id = h.item_id
		JOIN feeds f ON f.id = i.feed_id
		WHERE %s
		ORDER BY h.opened_at DESC, h.id DESC
		LIMIT ? OFFSET ?
	`, where), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []HistoryRecord
	for rows.Next() {
		var rec HistoryRecord
		var unreadInt, archivedInt int
		if err := rows.Scan(
			&rec.HistoryID, &rec.OpenedAt, &rec.Via,
			&rec.Article.ID, &rec.Article.FeedID, &rec.Article.FeedTitle,
			&rec.Article.GUID, &rec.Article.URL, &rec.Article.Title, &rec.Article.Author,
			&rec.Article.Summary, &rec.Article.Published, &unreadInt, &archivedInt,
		); err != nil {
			return nil, err
		}
		rec.Article.Unread = unreadInt != 0
		rec.Article.Archived = archivedInt != 0
		out = append(out, rec)
	}
	return out, rows.Err()
}

func (d *DB) RemoveHistoryEntry(historyID int64) error {
	_, err := d.sql.Exec(`DELETE FROM history WHERE id = ?`, historyID)
	return err
}

func (d *DB) ClearHistory() error {
	_, err := d.sql.Exec(`DELETE FROM history`)
	return err
}

func (d *DB) PruneOldReadItems(keepDays int) (int64, error) {
	if keepDays <= 0 {
		return 0, nil
	}
	cutoff := time.Now().AddDate(0, 0, -keepDays).Unix()
	res, err := d.sql.Exec(`
		DELETE FROM items
		WHERE unread = 0 AND archived = 0 AND published > 0 AND published < ?
		AND id NOT IN (SELECT DISTINCT item_id FROM history)
	`, cutoff)
	if err != nil {
		return 0, err
	}
	return res.RowsAffected()
}

type scannable interface {
	Scan(dest ...any) error
}

func scanFeed(row scannable) (Feed, error) {
	var f Feed
	var tagsJSON string
	err := row.Scan(
		&f.ID, &f.URL, &f.Title, &f.SiteURL, &tagsJSON, &f.CustomTitle,
		&f.ETag, &f.LastModified, &f.LastFetch, &f.Error, &f.Position, &f.UnreadCount,
	)
	if err != nil {
		return Feed{}, err
	}
	_ = json.Unmarshal([]byte(tagsJSON), &f.Tags)
	if f.Tags == nil {
		f.Tags = []string{}
	}
	return f, nil
}

func scanArticleSummary(row scannable) (Article, error) {
	var a Article
	var unreadInt, archivedInt int
	err := row.Scan(
		&a.ID, &a.FeedID, &a.FeedTitle, &a.GUID, &a.URL, &a.Title, &a.Author,
		&a.Summary, &a.Published, &unreadInt, &archivedInt,
	)
	if err != nil {
		return Article{}, err
	}
	a.Unread = unreadInt != 0
	a.Archived = archivedInt != 0
	return a, nil
}

func escapeLike(s string) string {
	s = strings.ReplaceAll(s, `\`, `\\`)
	s = strings.ReplaceAll(s, `%`, `\%`)
	s = strings.ReplaceAll(s, `_`, `\_`)
	return s
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}
