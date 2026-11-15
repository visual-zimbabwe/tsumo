# Tsumo

An editorial-archive Android client for Z-Library, powered by the [`heartleo/zlib`](https://github.com/heartleo/zlib) Go engine compiled natively via Android NDK + JNI.

## Features
- **Discover**: Borderless archival search, format filters (`ALL`, `EPUB`, `PDF`, `MOBI`, `AZW3`), language/year/exact-match refinement, 2-column 2:3 cover plates, and pagination.
- **Catalog**: Cloud download history (`zlib history`) and full-text inside-book search (`zlib fulltext`).
- **Shelves**: Local downloaded editions exported to `Downloads/Tsumo` (`OPEN`, `SHARE`, `KINDLE`, `REMOVE`) and saved bookmarks.
- **Archive Ledger**: Session & cookie import, daily download quota meter, concurrent Mirror Doctor (`zlib doctor`), and Send-to-Kindle SMTP configuration.

## Build Setup
1. Copy `engine/.env.example` to `engine/.env` (optional, gitignored) if you want build-time default credentials injected via `-ldflags`.
2. Build the native Go library (`arm64-v8a`):
   ```bash
   ./engine/build.sh
   ```
3. Build the Android APK:
   ```bash
   ./gradlew assembleRelease
   ```
