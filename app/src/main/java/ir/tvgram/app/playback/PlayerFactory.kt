package ir.tvgram.app.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgMediaItem
import ir.tvgram.telegram.player.TelegramFileDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds players and media sources that read straight out of Telegram.
 *
 * Every source goes through [TelegramFileDataSource], so playback starts on the
 * first megabyte rather than after the whole file has been fetched.
 */
@Singleton
class PlayerFactory @Inject constructor(
    private val client: TelegramClient,
) {

    fun createPlayer(context: Context, settings: AppSettings): ExoPlayer {
        val renderers = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                if (settings.hardwareDecoding) {
                    // Prefer the device's hardware decoders; a TV box decodes
                    // HEVC/AV1 far better than any software fallback.
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                },
            )
            setEnableDecoderFallback(true)
        }

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(TelegramFileDataSource.Factory(client)))
            .setSeekBackIncrementMs(settings.seekStepSeconds * 1000L)
            .setSeekForwardIncrementMs(settings.seekStepSeconds * 1000L)
            .build()
            .apply {
                trackSelectionParameters = applyLanguagePreferences(
                    trackSelectionParameters,
                    settings,
                )
            }
    }

    fun mediaSource(item: TgMediaItem): MediaSource =
        ProgressiveMediaSource.Factory(TelegramFileDataSource.Factory(client))
            .createMediaSource(mediaItem(item))

    fun mediaItem(item: TgMediaItem): MediaItem = MediaItem.Builder()
        .setUri(TelegramFileDataSource.uriFor(item.fileId))
        .setMediaId(item.uid)
        .setMimeType(item.mimeType ?: defaultMimeType(item))
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.subtitle)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private fun defaultMimeType(item: TgMediaItem): String = when (item.kind) {
        MediaKind.VIDEO, MediaKind.ANIMATION -> MimeTypes.VIDEO_MP4
        MediaKind.AUDIO -> MimeTypes.AUDIO_MPEG
        MediaKind.VOICE -> MimeTypes.AUDIO_OGG
        else -> MimeTypes.VIDEO_MP4
    }

    private fun applyLanguagePreferences(
        parameters: TrackSelectionParameters,
        settings: AppSettings,
    ): TrackSelectionParameters {
        val builder = parameters.buildUpon()
        settings.preferredAudioLanguage.takeIf { it.isNotBlank() }
            ?.let { builder.setPreferredAudioLanguage(it) }
        if (settings.preferredSubtitleLanguage.isBlank()) {
            builder.setPreferredTextLanguage(null)
        } else {
            builder.setPreferredTextLanguage(settings.preferredSubtitleLanguage)
        }
        return builder.build()
    }
}
