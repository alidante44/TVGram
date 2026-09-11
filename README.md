# TVGram

A media-first Telegram client for Android TV.

TVGram is not a messenger you drive with a remote. It treats your Telegram
chats as a media library: pick a folder and a chat, and the videos, photos and
audio in it fill a grid you can walk with the D-pad and play straight from the
sofa — streamed while they download, not after.

```
┌────┬───────────────────────────────────────────────────────────┐
│Media│ [All│Videos│Photos│Audio│Other]        [▼ Source: Bots › chat] │
│Chats├───────────────────────────────────────────────────────────┤
│ ⚙  │  ▢ ▢ ▢ ▢ ▢    media tiles, loaded page by page            │
│    │  ▢ ▢ ▢ ▢ ▢                                                │
└────┴───────────────────────────────────────────────────────────┘
```

* **Media** — five category tabs across the top, a two-level source picker in
  the top-right corner (folders, then the chats inside them), and a grid that
  fills in as pages arrive.
* **Chats** — the text side: chat list beside the conversation, with media in a
  message as a focusable chip that opens the player.
* **Settings** — two sections, one for Telegram (account, default folder, cache
  ceiling, download priority) and one for Android TV (language, rail position,
  grid density, autoplay, seek step, track languages, decoding).

UI language is Persian by default with a full English translation; dates follow
the language, so a Persian UI shows Jalali dates.

## Building

### 1. Get Telegram API credentials

Create an application at <https://my.telegram.org/apps> and put the values in
`local.properties` (git-ignored):

```properties
TELEGRAM_API_ID=1234567
TELEGRAM_API_HASH=0123456789abcdef0123456789abcdef
```

A build without them still installs — it asks for them once on first launch.

### 2. Install TDLib

The real backend talks to Telegram through TDLib, which ships as native code and
is therefore never committed. The script installs it into `:tdlib`:

```bash
./scripts/fetch-tdlib.sh
```

It downloads Telegram's published Android archive and accepts either layout that
archive has used (a ready-made `.aar`, or loose `libtdjni.so` files plus the
`org.drinkless.tdlib` Java sources).

**Heads-up:** as of this writing `https://core.telegram.org/tdlib/tdlib.zip`
answers with a 262-byte file rather than an Android build, so the script stops
and prints what it actually received. Until that URL serves a real build again,
supply one yourself — either way round:

```bash
# an archive you downloaded or built elsewhere
TDLIB_URL=file:///path/to/tdlib.zip ./scripts/fetch-tdlib.sh

# or an AAR, dropped straight in
cp your-tdlib.aar tdlib/libs/tdlib.aar
```

To build one from source, use TDLib's own Android instructions — they are
maintained upstream and produce exactly the archive this script expects:
<https://github.com/tdlib/td/tree/master/example/android>. TVGram needs TDLib
1.8.14 or newer.

None of this affects the `mock` flavour, which builds and runs with no TDLib at
all.

### 3. Build and install

```bash
./gradlew assembleRealDebug
adb install -r app/build/outputs/apk/real/debug/app-real-debug.apk
```

## The mock flavour

`mock` runs the whole UI on generated content — no account, no credentials, no
native library — which makes it the fastest way to check a layout in a TV
emulator, and the flavour CI can always build:

```bash
./gradlew installMockDebug
```

## Signing in

QR is the default: Telegram on your phone → **Settings → Devices → Link Desktop
Device**, then scan what the TV shows. Phone number, code and two-step password
work too, through the on-screen keyboard, for accounts where QR login is not
offered.

## Layout

| Module | What lives there |
|---|---|
| `:app` | Compose-for-TV UI, player, settings, DI |
| `:telegram` | Domain types, the `TelegramClient` interface, paging feeds, the streaming `DataSource`, and the mock backend |
| `:tdlib` | The TDLib-backed implementation of `TelegramClient` |

The UI never sees TDLib types. Everything crosses the boundary as plain domain
models, which is what lets the mock flavour exist and keeps a TDLib version bump
from reaching into screens.

### Streaming

`TelegramFileDataSource` is the piece that makes playback start immediately. TDLib
exposes a downloading file as a growing local file plus a count of contiguous
ready bytes; the data source asks TDLib to download from the byte the player
wants, then blocks on file updates only until each read can be satisfied. Seeking
re-issues the download at the new offset rather than waiting for the whole file.

## Not implemented yet

* **Background audio.** Playback lives in the player screen; leaving it stops
  the audio. Doing this properly means moving playback behind a Media3
  `MediaSessionService` and driving it from a `MediaController`, which also
  brings the TV's own media controls and the remote's transport keys.
* **Sending anything.** TVGram reads; it does not post messages, react, or
  forward.

## Requirements

* Android 6.0 (API 23) or newer, `android.software.leanback`
* TDLib 1.8.14 or newer for the `real` flavour
* JDK 17, Android SDK 35
