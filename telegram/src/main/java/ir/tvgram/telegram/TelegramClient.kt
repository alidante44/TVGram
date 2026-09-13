package ir.tvgram.telegram

import ir.tvgram.telegram.model.AuthState
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.MediaCategory
import ir.tvgram.telegram.model.MediaPage
import ir.tvgram.telegram.model.MessagePage
import ir.tvgram.telegram.model.TelegramCredentials
import ir.tvgram.telegram.model.TgChat
import ir.tvgram.telegram.model.TgFile
import ir.tvgram.telegram.model.TgFolder
import ir.tvgram.telegram.model.TgLiveStream
import ir.tvgram.telegram.model.TgProxy
import ir.tvgram.telegram.model.TgStreamChannel
import ir.tvgram.telegram.model.TgUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the app needs from Telegram, expressed in plain domain types.
 *
 * The UI never sees TDLib: the `real` flavour binds this to a TDLib-backed
 * implementation, the `mock` flavour to generated sample data, so the whole
 * interface can be exercised in a TV emulator without an account.
 */
interface TelegramClient {

    val authState: StateFlow<AuthState>
    val connectionState: StateFlow<ConnectionState>

    // --- session ----------------------------------------------------------

    /** Boots the client. Safe to call again with new credentials. */
    suspend fun start(credentials: TelegramCredentials)

    /** Asks for a login link to render as a QR code. */
    suspend fun requestQrLogin()

    suspend fun submitPhoneNumber(phoneNumber: String)

    suspend fun submitCode(code: String)

    suspend fun submitPassword(password: String)

    suspend fun logOut()

    suspend fun currentUser(): TgUser?

    // --- browsing ---------------------------------------------------------

    /** Built-in buckets first, then the user's own Telegram chat folders. */
    suspend fun folders(): List<TgFolder>

    suspend fun chats(folder: TgFolder, limit: Int = 200): List<TgChat>

    suspend fun chat(chatId: Long): TgChat?

    /**
     * One page of media, newest first.
     *
     * @param fromMessageId 0 starts from the newest message; otherwise pass the
     *   [MediaPage.nextFromMessageId] returned by the previous page.
     */
    suspend fun mediaPage(
        chatId: Long,
        category: MediaCategory,
        fromMessageId: Long,
        limit: Int,
        /** Free text to match against captions and file names; empty means all. */
        query: String = "",
    ): MediaPage

    /** Chats whose name matches [query], across the account rather than a folder. */
    suspend fun searchChats(query: String, limit: Int = 50): List<TgChat>

    /** One page of chat history, newest first. */
    suspend fun messagePage(chatId: Long, fromMessageId: Long, limit: Int): MessagePage

    // --- files ------------------------------------------------------------

    suspend fun file(fileId: Int): TgFile

    /** Emits the current state and every subsequent update for [fileId]. */
    fun fileUpdates(fileId: Int): Flow<TgFile>

    /**
     * Starts (or re-prioritises) a download.
     *
     * @param offset byte offset to download from — this is what makes seeking
     *   in a half-downloaded video cheap.
     * @param limit bytes to fetch from [offset]; 0 means "to the end".
     */
    suspend fun downloadFile(
        fileId: Int,
        priority: Int = DEFAULT_PRIORITY,
        offset: Long = 0,
        limit: Long = 0,
        synchronous: Boolean = false,
    ): TgFile

    /** Contiguous bytes already on disk starting at [offset]. */
    suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long

    suspend fun cancelDownload(fileId: Int)

    /** Downloads the whole file and returns its local path. */
    suspend fun downloadFully(fileId: Int, priority: Int = DEFAULT_PRIORITY): String

    // --- live streams -----------------------------------------------------

    /**
     * The chat's active video chat or channel live stream, or null when there is
     * none.
     */
    suspend fun liveStream(chatId: Long): TgLiveStream?

    /**
     * Emits a chat id whenever its video chat starts, ends or changes, so a
     * banner can appear and disappear without polling.
     */
    val videoChatUpdates: Flow<Long>

    /**
     * Becomes a listener of the call so the server will serve its stream.
     *
     * Telegram hands stream segments only to participants, so watching means
     * joining first — muted, with no camera. Returns false when the server
     * refuses, which is the honest answer for a call that cannot be watched
     * this way.
     */
    suspend fun joinLiveStream(groupCallId: Int): Boolean

    suspend fun leaveLiveStream(groupCallId: Int)

    /** The tracks on offer; empty until [joinLiveStream] has succeeded. */
    suspend fun liveStreamChannels(groupCallId: Int): List<TgStreamChannel>

    /**
     * One segment of the stream: MPEG-4 for video, a modified OGG for audio.
     *
     * @param timeOffsetMs the moment the segment begins, in Unix milliseconds.
     */
    suspend fun liveStreamSegment(
        groupCallId: Int,
        timeOffsetMs: Long,
        scale: Int,
        channelId: Int,
    ): ByteArray?

    // --- proxies ----------------------------------------------------------

    suspend fun proxies(): List<TgProxy>

    /** Adds a proxy and immediately routes through it. */
    suspend fun addProxy(proxy: TgProxy): TgProxy?

    suspend fun enableProxy(id: Int)

    /** Goes back to connecting directly. */
    suspend fun disableProxies()

    suspend fun removeProxy(id: Int)

    // --- storage ----------------------------------------------------------

    suspend fun cacheSize(): Long

    suspend fun clearCache()

    /** Applies a cache ceiling in bytes; 0 or negative means unlimited. */
    suspend fun setCacheLimit(bytes: Long)

    companion object {
        const val DEFAULT_PRIORITY: Int = 16

        /** Playback outranks thumbnails and prefetching. */
        const val STREAMING_PRIORITY: Int = 32
    }
}
