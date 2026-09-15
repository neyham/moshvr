#!/bin/bash
# Build a pinned supported OpenSSL LTS libcrypto for mosh's EVP AES primitives.
# No signing, device operations or system installation.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION=3.5.8
SHA256=a8f84a39918ec6415ce765d9b429d313ba97b8143169c172e734b9514464f5b2
CACHE="$SCRIPT_DIR/openssl"
ARCHIVE="$CACHE/openssl-$VERSION.tar.gz"
SOURCE="$CACHE/openssl-$VERSION"
DEST="$CACHE/install"
ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_NDK_ROOT="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.2.12479018}"
HOST_TAG="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
export PATH="$TOOLCHAIN/bin:$PATH"
mkdir -p "$CACHE"
if [ ! -f "$ARCHIVE" ]; then
  curl --fail --location --proto '=https' --proto-redir '=https' \
    "https://github.com/openssl/openssl/releases/download/openssl-$VERSION/openssl-$VERSION.tar.gz" -o "$ARCHIVE"
fi
printf '%s  %s\n' "$SHA256" "$ARCHIVE" | shasum -a 256 -c -
if [ ! -d "$SOURCE" ]; then tar xzf "$ARCHIVE" -C "$CACHE"; fi
cd "$SOURCE"
export CC="$TOOLCHAIN/bin/aarch64-linux-android34-clang"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
# Runtime paths must not embed the developer's installation directory.
./Configure android-arm64 -D__ANDROID_API__=34 --prefix=/moshvr --openssldir=/moshvr/ssl --libdir=lib "-ffile-prefix-map=$SCRIPT_DIR=." no-shared no-tests no-module no-legacy
make -j4 build_libs
make DESTDIR="$CACHE/stage" install_dev
mkdir -p "$DEST"
cp -R "$CACHE/stage/moshvr/." "$DEST/"
cp LICENSE.txt "$SCRIPT_DIR/../../app/src/main/assets/legal/OpenSSL-LICENSE.txt"
printf '%s\n' "$SHA256" > "$DEST/SOURCE_SHA256"
printf '%s\n' 'portable-runtime-paths-v1' > "$DEST/BUILD_PROFILE"
echo "Built OpenSSL $VERSION (API 34 arm64) in $DEST"
