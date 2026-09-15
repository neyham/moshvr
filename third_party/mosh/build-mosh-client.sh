#!/bin/bash
# Builds the mosh-client executable for Android (arm64-v8a) and installs it as
# app/src/main/jniLibs/arm64-v8a/libmosh_client.so so it can be exec'd from
# the app's nativeLibraryDir (the only exec-able location on targetSdk >= 29).
#
# Approach:
#   - prebuilt static libs (mosh core + protobuf/absl/openssl/ncurses) come from
#     the rjyo/mosh-android release, built from mosh commit 9c4e59a
#   - we compile only the client frontend (mosh-client.cc, stmclient.cc,
#     terminaloverlay.cc) from the same upstream commit and link it all together
#
# mosh is GPLv3; this app is GPLv3. See LICENSE.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

MOSH_COMMIT="9c4e59a701a0d1c0d5c00f08a13bfb2b5354ba73"
LIBS_URL="https://github.com/rjyo/mosh-android/releases/download/v1.0.0/mosh-android-libs-v1.0.0.tar.gz"

ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
NDK="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.2.12479018}"
HOST_TAG="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64" # NDK ships darwin-x86_64 (works on arm64 macs via Rosetta-free universal binaries)
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
API=34
CXX="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang++"
STRIP="$TOOLCHAIN/bin/llvm-strip"

PREBUILT="$SCRIPT_DIR/prebuilt"
UPSTREAM="$SCRIPT_DIR/upstream"
OUT="$SCRIPT_DIR/out"
JNILIBS="$PROJECT_DIR/app/src/main/jniLibs/arm64-v8a"

# 1. Fetch prebuilt static libraries
if [ ! -d "$PREBUILT/android-libs" ]; then
  echo "==> Downloading prebuilt mosh static libraries..."
  mkdir -p "$PREBUILT"
  curl -sfL "$LIBS_URL" -o "$PREBUILT/libs.tar.gz"
  tar xzf "$PREBUILT/libs.tar.gz" -C "$PREBUILT"
  rm "$PREBUILT/libs.tar.gz"
fi
LIBS="$PREBUILT/android-libs/static/arm64-v8a"
INC="$PREBUILT/android-libs/include"

# 2. Fetch mosh frontend sources at the matching commit
if [ ! -d "$UPSTREAM" ]; then
  echo "==> Cloning mosh upstream @ $MOSH_COMMIT..."
  git clone https://github.com/mobile-shell/mosh.git "$UPSTREAM"
  git -C "$UPSTREAM" checkout "$MOSH_COMMIT"
fi

# Refuse a stale or edited tracked checkout rather than silently mixing revisions.
if [ "$(git -C "$UPSTREAM" rev-parse HEAD)" != "$MOSH_COMMIT" ] ||
   ! git -C "$UPSTREAM" diff --quiet HEAD --; then
  echo "mosh upstream must match the pinned clean commit" >&2
  exit 1
fi

# Replace the original unsupported OpenSSL 3.2.1 static crypto dependency.
CRYPTO="$SCRIPT_DIR/openssl/install/lib/libcrypto.a"
if [ "$(cat "$SCRIPT_DIR/openssl/install/BUILD_PROFILE" 2>/dev/null || true)" != "portable-runtime-paths-v1" ] || [ ! -f "$CRYPTO" ] || [ "$(cat "$SCRIPT_DIR/openssl/install/SOURCE_SHA256" 2>/dev/null || true)" != "a8f84a39918ec6415ce765d9b429d313ba97b8143169c172e734b9514464f5b2" ]; then
  "$SCRIPT_DIR/build-openssl.sh"
fi

# 3. Compile the client frontend
echo "==> Compiling mosh-client frontend..."
mkdir -p "$OUT"
FRONTEND="$UPSTREAM/src/frontend"

# config.h / version.h are configure-generated; reuse the ones the prebuilt
# libraries were built with.
cp -f "$INC/config.h" "$UPSTREAM/src/include/config.h"
cp -f "$INC/version.h" "$UPSTREAM/src/include/version.h" 2>/dev/null || true
cp -f "$INC"/*.pb.h "$UPSTREAM/src/protobufs/"

CXXFLAGS=(
  -std=c++17 -Os -fPIE
  # Keep __FILE__ and debug paths independent of the developer machine.
  "-ffile-prefix-map=$PROJECT_DIR=."
  -DHAVE_CONFIG_H
  -I"$UPSTREAM"
  -I"$INC" -I"$INC/ncurses"
  -I"$UPSTREAM/src/frontend"
  -I"$UPSTREAM/src/crypto" -I"$UPSTREAM/src/network" -I"$UPSTREAM/src/statesync"
  -I"$UPSTREAM/src/terminal" -I"$UPSTREAM/src/util" -I"$UPSTREAM/src/include"
)

for src in mosh-client stmclient terminaloverlay; do
  "$CXX" "${CXXFLAGS[@]}" -c "$FRONTEND/$src.cc" -o "$OUT/$src.o"
done

# 4. Link (group the static libs to dodge ordering problems)
echo "==> Linking..."
"$CXX" -pie -Wl,-z,max-page-size=16384 -o "$OUT/mosh-client" \
  "$OUT/mosh-client.o" "$OUT/stmclient.o" "$OUT/terminaloverlay.o" \
  -Wl,--start-group \
  "$LIBS/libmoshcrypto.a" "$LIBS/libmoshnetwork.a" "$LIBS/libmoshstatesync.a" \
  "$LIBS/libmoshterminal.a" "$LIBS/libmoshutil.a" "$LIBS/libmoshprotos.a" \
  "$LIBS/libprotobuf.a" "$LIBS/libutf8_validity.a" "$LIBS/libutf8_range.a" \
  $LIBS/libabsl_*.a \
  "$CRYPTO" "$LIBS/libncursesw.a" \
  -Wl,--end-group \
  -llog -lz -ldl -static-libstdc++

"$STRIP" "$OUT/mosh-client"

# 5. Install as an exec-able "shared library"
mkdir -p "$JNILIBS"
cp "$OUT/mosh-client" "$JNILIBS/libmosh_client.so"
echo "==> Installed $JNILIBS/libmosh_client.so ($(du -h "$JNILIBS/libmosh_client.so" | cut -f1))"
