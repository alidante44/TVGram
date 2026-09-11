package ir.tvgram.app.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import ir.tvgram.app.ui.LocalTelegramClient
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.telegram.TelegramClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A Telegram thumbnail, drawn as soon as anything is available.
 *
 * Telegram embeds a tiny blurred JPEG in the message itself, so a tile can show
 * something on the very first frame while the real thumbnail is still being
 * fetched. That is what makes the grid fill in smoothly instead of flashing
 * grey boxes.
 */
@Composable
fun TelegramImage(
    fileId: Int?,
    minithumbnail: ByteArray?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = TvGramColors.SurfaceElevated,
) {
    val client = LocalTelegramClient.current

    val preview: ImageBitmap? = remember(minithumbnail) {
        minithumbnail?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                .getOrNull()
        }
    }

    val full: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, fileId, client) {
        val id = fileId ?: return@produceState
        value = loadThumbnail(client, id)
    }

    Box(modifier = modifier.background(placeholderColor)) {
        val bitmap = full ?: preview
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

/** Thumbnails download at the lowest priority so they never starve playback. */
private suspend fun loadThumbnail(client: TelegramClient, fileId: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val path = client.downloadFully(fileId, priority = THUMBNAIL_PRIORITY)
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }.getOrNull()
    }

private const val THUMBNAIL_PRIORITY = 1
