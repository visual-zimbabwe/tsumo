#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

if [ -f "$DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    source "$DIR/.env"
    set +a
fi

NDK=${NDK:-/home/juwimana/Android/Sdk/ndk/27.1.12297006}
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
DEST_DIR="$DIR/../app/src/main/jniLibs/arm64-v8a"

mkdir -p "$DEST_DIR"

echo "=== Tidying Go module ==="
go mod tidy

echo "=== Building libtsumo.so for arm64-v8a ==="
export CC="$TOOLCHAIN/bin/aarch64-linux-android24-clang"
export CXX="$TOOLCHAIN/bin/aarch64-linux-android24-clang++"
export CGO_ENABLED=1
export GOOS=android
export GOARCH=arm64

LDFLAGS="-s -w"
if [ -n "${TSUMO_EMAIL:-}" ] && [ -n "${TSUMO_PASSWORD:-}" ]; then
    LDFLAGS="$LDFLAGS -X 'main.defaultLoginEmail=$TSUMO_EMAIL' -X 'main.defaultLoginPassword=$TSUMO_PASSWORD'"
fi

go build -ldflags="$LDFLAGS" -buildmode=c-shared -o "$DEST_DIR/libtsumo.so" .

echo "=== Verification ==="
ls -lh "$DEST_DIR/libtsumo.so"
nm -D "$DEST_DIR/libtsumo.so" | grep -E "Java_com_tsumo|TsumoCall"
echo "=== Done ==="
