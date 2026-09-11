package ir.tvgram.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ir.tvgram.telegram.TelegramClient

/**
 * Thumbnails are requested from deep inside lists, far from any ViewModel, so
 * the client is published to the composition rather than threaded through every
 * call site.
 */
val LocalTelegramClient = staticCompositionLocalOf<TelegramClient> {
    error("LocalTelegramClient was not provided")
}
