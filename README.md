# Tsumo

An editorial-archive Android client for public-domain and open-source digital libraries (**Project Gutenberg** + **Internet Archive / Open Library**), powered by a native Go engine compiled via Android NDK + JNI.

## Features
- **Discover**: Borderless archival search across Project Gutenberg OPDS and Internet Archive / Open Library public-domain collections, format filters (`ALL`, `EPUB`, `PDF`, `MOBI`), language/year/exact-match refinement, 2-column 2:3 cover plates, and pagination.
- **Catalog**: Local download history and full-text inside-book search via Internet Archive (`fulltext`).
- **Shelves**: Local downloaded editions exported to `Downloads/Tsumo` (`OPEN`, `SHARE`, `KINDLE`, `REMOVE`) and saved bookmarks.
- **Archive Ledger**: Open-source provider toggles (Project Gutenberg / Internet Archive & Open Library), concurrent Source Health Check (`doctor`), and Send-to-Kindle SMTP configuration.

## Build Setup
1. Build the native Go library (`arm64-v8a`):
   ```bash
   ./engine/build.sh
   ```
2. Build the Android APK:
   ```bash
   ./gradlew assembleRelease
   ```
