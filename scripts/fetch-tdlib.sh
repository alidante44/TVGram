#!/usr/bin/env bash
#
# Fetches the official TDLib build for Android published by Telegram and drops
# it into the :tdlib module.
#
# Telegram ships one archive containing the JNI bindings plus prebuilt native
# libraries for every Android ABI. Two layouts have been used over time, so we
# accept either:
#
#   1. an .aar        -> copied to tdlib/libs/tdlib.aar
#   2. loose files    -> libtdjni.so per ABI  -> tdlib/src/main/jniLibs/<abi>/
#                        org/drinkless/tdlib/*.java -> tdlib/src/main/java/
#
# Nothing produced here is committed; see .gitignore.
set -euo pipefail

TDLIB_URL="${TDLIB_URL:-https://core.telegram.org/tdlib/tdlib.zip}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="$ROOT/tdlib"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail()  { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

info "Downloading $TDLIB_URL"
if ! curl -fSL --retry 4 --retry-delay 2 -o "$WORK/tdlib.zip" "$TDLIB_URL"; then
    fail "could not download TDLib from $TDLIB_URL
      If your network blocks core.telegram.org, download tdlib.zip elsewhere and
      re-run with TDLIB_URL=file:///path/to/tdlib.zip, or build the mock flavour:
          ./gradlew assembleMockDebug"
fi

info "Extracting"
unzip -q "$WORK/tdlib.zip" -d "$WORK/extracted"

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
[[ -n "$tdapi" ]] || fail "archive contains neither an .aar nor org/drinkless/.../TdApi.java.
      Layout changed upstream — inspect tdlib.zip and update scripts/fetch-tdlib.sh."

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
