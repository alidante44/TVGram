package ir.tvgram.telegram.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.TgFile
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Plays a Telegram file while it is still downloading.
 *
 * Telegram hands out files as a growing local file plus a "how many contiguous
 * bytes are ready from this offset" counter. This source turns that into a
 * regular Media3 [DataSource]: it asks TDLib to download from the byte the
 * player wants, then blocks on the file's update stream until enough bytes have
 * landed to satisfy each read. Seeking re-issues the download at the new offset
 * instead of waiting for the whole file.
 */
class TelegramFileDataSource(
    private val client: TelegramClient,
) : BaseDataSource(/* isNetwork = */ true) {

    private var dataSpec: DataSpec? = null
    private var fileId: Int = INVALID_FILE_ID
    private var readPosition: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var handle: RandomAccessFile? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        fileId = fileIdOf(dataSpec.uri)
            ?: throw IOException("not a telegram file uri: ${dataSpec.uri}")
        readPosition = dataSpec.position

        val file = runBlocking {
            client.downloadFile(
                fileId = fileId,
                priority = TelegramClient.STREAMING_PRIORITY,
                offset = readPosition,
                limit = 0,
                synchronous = false,
            )
        }
        if (!file.canBeDownloaded) throw IOException("file $fileId cannot be downloaded")

        val totalSize = file.bestKnownSize
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            totalSize > 0 -> (totalSize - readPosition).coerceAtLeast(0)
            else -> C.LENGTH_UNSET.toLong()
        }

        // The local path only exists once TDLib has allocated the file, which
        // happens as soon as the first bytes arrive.
        awaitBytes(readPosition, 1)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val wanted = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length.toLong()
        } else {
            minOf(length.toLong(), bytesRemaining)
        }

        val available = try {
            awaitBytes(readPosition, 1)
        } catch (e: EOFException) {
            // The download finished exactly at our read position.
            bytesRemaining = 0
            return C.RESULT_END_OF_INPUT
        }

        val toRead = minOf(wanted, available).toInt()
        val file = handle ?: throw IOException("file $fileId is not open")
        val read = synchronized(file) {
            file.seek(readPosition)
            file.read(buffer, offset, toRead)
        }
        if (read <= 0) return C.RESULT_END_OF_INPUT

        readPosition += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /**
     * Blocks until at least [minimum] contiguous bytes are readable from
     * [position], returning how many are actually available.
     */
    private fun awaitBytes(position: Long, minimum: Long): Long = runBlocking {
        // downloadedPrefixSize is asked for at our exact read position, so it
        // stays correct no matter which offset the download was started at.
        var available = client.downloadedPrefixSize(fileId, position)
        if (available >= minimum) {
            openHandle(client.file(fileId))
            return@runBlocking available
        }

        val settled = client.fileUpdates(fileId).first { update ->
            available = client.downloadedPrefixSize(fileId, position)
            when {
                available >= minimum -> true
                update.isDownloadingCompleted -> true
                !update.isDownloadingActive -> {
                    // TDLib dropped the download (cache eviction, a competing
                    // priority); ask again from where the player actually is.
                    client.downloadFile(
                        fileId = fileId,
                        priority = TelegramClient.STREAMING_PRIORITY,
                        offset = position,
                        limit = 0,
                        synchronous = false,
                    )
                    false
                }
                else -> false
            }
        }

        openHandle(settled)
        if (settled.isDownloadingCompleted) {
            available = (settled.bestKnownSize - position).coerceAtLeast(0)
        }
        if (available < minimum) throw EOFException("no more data for file $fileId at $position")
        available
    }

    private fun openHandle(file: TgFile) {
        if (handle != null) return
        val path = file.localPath?.takeIf { it.isNotEmpty() }
            ?: throw IOException("file $fileId has no local path yet")
        handle = RandomAccessFile(path, "r")
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        handle?.let { runCatching { it.close() } }
        handle = null
        if (fileId != INVALID_FILE_ID) {
            runCatching { runBlocking { client.cancelDownload(fileId) } }
        }
        dataSpec = null
        fileId = INVALID_FILE_ID
        readPosition = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val client: TelegramClient) : DataSource.Factory {
        private val listeners = mutableListOf<TransferListener>()

        fun addTransferListener(listener: TransferListener): Factory = apply {
            listeners += listener
        }

        override fun createDataSource(): DataSource =
            TelegramFileDataSource(client).also { source ->
                listeners.forEach(source::addTransferListener)
            }
    }

    companion object {
        const val SCHEME: String = "tgfile"
        private const val INVALID_FILE_ID = -1

        /** `tgfile://file/<id>` — what the player is handed instead of a path. */
        fun uriFor(fileId: Int): Uri =
            Uri.Builder().scheme(SCHEME).authority("file").appendPath(fileId.toString()).build()

        fun fileIdOf(uri: Uri): Int? =
            if (uri.scheme == SCHEME) uri.lastPathSegment?.toIntOrNull() else null
    }
}
