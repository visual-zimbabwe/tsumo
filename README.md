# Tsumo

A minimalist, Newsboat-compatible Android RSS/Atom feed reader powered by a native Go engine (`SQLite` + `FTS5`) compiled via Android NDK + JNI.

## Features
- **Read**: Feed rail with tag & unread counts, horizontal swipe navigation between channels/feeds, pull-to-refresh + auto-sync on launch, swipe-to-toggle-read, and clean distraction-free in-app reader, Custom Tabs, or external phone browser.
- **Feeds**: Easy add/remove with auto-discovery from any website URL, clipboard & Chrome Share-to-Tsumo support, swipe-to-remove with Undo, and Newsboat `urls` / OPML 2.0 import & export. Pre-seeded with your Newsboat CLI feeds.
- **Split-Second Search**: Local SQLite FTS5 prefix index returning matching feeds and articles as you type across All, Archived, and History.
- **Library**: Offline archived articles (`★`), searchable reading history grouped by day, and one-tap `.md` / `.pdf` exports saved to `Downloads/Tsumo` with sharing and optional email delivery.

## Build Setup
1. Build the native Go library (`arm64-v8a`) and run Go tests:
   ```bash
   cd engine && go test -tags sqlite_fts5 ./... && ./build.sh
   ```
2. Build and test the Android app:
   ```bash
   ./gradlew testDebugUnitTest assembleRelease
   ```
