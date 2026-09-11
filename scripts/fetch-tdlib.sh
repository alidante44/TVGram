#!/usr/bin/env bash
#
# Installs TDLib's Android build into the :tdlib module.
#
# TDLib is native code, so it is never committed — it is fetched here and
# git-ignored. Two upstream layouts are accepted:
#
#   1. an .aar        -> copied to tdlib/libs/tdlib.aar
#   2. loose files    -> libtdjni.so per ABI  -> tdlib/src/main/jniLibs/<abi>/
#                        org/drinkless/tdlib/*.java -> tdlib/src/main/java/
#
# Set TDLIB_URL to override the source, including a local file:
#
#   TDLIB_URL=file:///path/to/tdlib.zip ./scripts/fetch-tdlib.sh
#
# If you already have an AAR, you can skip this script entirely and drop it at
# tdlib/libs/tdlib.aar yourself.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="$ROOT/tdlib"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

DEFAULT_URLS=(
    # Built from source by .github/workflows/tdlib.yml, because Telegram's own
    # archive below is not currently serving an Android build. The repository is
    # public, so this needs no token.
    "https://github.com/alidante44/TVGram/releases/download/tdlib-latest/tdlib.zip"
    "https://core.telegram.org/tdlib/tdlib.zip"
)

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mWARN:\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

describe_archive() {
    local archive="$1"
    printf '\n--- what %s actually contains ---\n' "$(basename "$archive")" >&2
    printf 'size: %s bytes\n' "$(wc -c <"$archive")" >&2
    file "$archive" >&2 || true
    if unzip -l "$archive" >/dev/null 2>&1; then
        unzip -l "$archive" >&2
    else
        head -c 400 "$archive" >&2
        printf '\n' >&2
    fi
    printf -- '--- end ---\n\n' >&2
}

# --- download -------------------------------------------------------------

urls=("${DEFAULT_URLS[@]}")
[[ -n "${TDLIB_URL:-}" ]] && urls=("$TDLIB_URL")

archive="$WORK/tdlib.zip"
downloaded=""
for url in "${urls[@]}"; do
    info "Downloading $url"
    if curl -fSL --retry 4 --retry-delay 2 -o "$archive" "$url"; then
        # A real TDLib build is tens of megabytes; anything tiny is a notice
        # page or a placeholder, not the library.
        size="$(wc -c <"$archive")"
        if [[ "$size" -lt 1000000 ]]; then
            warn "$url returned only $size bytes — that is not a TDLib build."
            describe_archive "$archive"
            continue
        fi
        downloaded="$url"
        break
    fi
    warn "could not download $url"
done

[[ -n "$downloaded" ]] || fail "no usable TDLib archive could be downloaded.

      Telegram's published archive is not serving an Android build right now.
      Supply one yourself and re-run, either way round:

        * point this script at a local archive:
              TDLIB_URL=file:///path/to/tdlib.zip ./scripts/fetch-tdlib.sh
        * or drop an AAR straight in:
              cp your-tdlib.aar tdlib/libs/tdlib.aar

      To build one here, run the \"Build TDLib\" workflow (Actions tab) — it uses
      TDLib's own Dockerfile and publishes the result as the tdlib-latest release.

      Meanwhile the mock flavour needs none of this:
              ./gradlew assembleMockDebug"

info "Extracting"
unzip -q "$archive" -d "$WORK/extracted" || {
    describe_archive "$archive"
    fail "the downloaded file is not a zip archive."
}

# --- layout 1: a ready-made AAR -------------------------------------------

aar="$(find "$WORK/extracted" -name '*.aar' -print -quit)"
if [[ -n "$aar" ]]; then
    mkdir -p "$MODULE/libs"
    cp "$aar" "$MODULE/libs/tdlib.aar"
    info "Installed $(basename "$aar") -> tdlib/libs/tdlib.aar"
    exit 0
fi

# --- layout 2: loose native libs + Java sources ----------------------------

tdapi="$(find "$WORK/extracted" -path '*org/drinkless/*' -name 'TdApi.java' -print -quit)"
if [[ -z "$tdapi" ]]; then
    describe_archive "$archive"
    fail "archive contains neither an .aar nor org/drinkless/.../TdApi.java.
      The layout listed above is not one this script knows how to install."
fi

# The java root is the directory holding the 'org' package folder.
java_root="${tdapi%%/org/drinkless/*}"
rm -rf "$MODULE/src/main/java/org"
mkdir -p "$MODULE/src/main/java"
cp -r "$java_root/org" "$MODULE/src/main/java/"
info "Installed Java bindings -> tdlib/src/main/java/org/drinkless"

found_abi=0
rm -rf "$MODULE/src/main/jniLibs"
while IFS= read -r so; do
    abi="$(basename "$(dirname "$so")")"
    case "$abi" in
        armeabi-v7a|arm64-v8a|x86|x86_64) ;;
        *) continue ;;
    esac
    mkdir -p "$MODULE/src/main/jniLibs/$abi"
    cp "$so" "$MODULE/src/main/jniLibs/$abi/"
    info "Installed $abi/$(basename "$so")"
    found_abi=1
done < <(find "$WORK/extracted" -name 'libtd*.so')

[[ "$found_abi" == 1 ]] || fail "no libtd*.so found for any supported ABI."

info "TDLib is ready. Build with: ./gradlew assembleRealDebug"
