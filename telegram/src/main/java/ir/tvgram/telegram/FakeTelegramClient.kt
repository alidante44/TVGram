package ir.tvgram.telegram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import ir.tvgram.telegram.model.AuthState
import ir.tvgram.telegram.model.BuiltInFolder
import ir.tvgram.telegram.model.ChatKind
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.MediaCategory
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.MediaPage
import ir.tvgram.telegram.model.MessagePage
import ir.tvgram.telegram.model.TelegramCredentials
import ir.tvgram.telegram.model.TgChat
import ir.tvgram.telegram.model.TgFile
import ir.tvgram.telegram.model.TgFolder
import ir.tvgram.telegram.model.TgMediaItem
import ir.tvgram.telegram.model.TgLiveStream
import ir.tvgram.telegram.model.TgProxy
import ir.tvgram.telegram.model.TgStreamChannel
import ir.tvgram.telegram.model.TgMessage
import ir.tvgram.telegram.model.TgUser
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * Generated stand-in for [TelegramClient], used by the `mock` product flavour.
 *
 * It exists so the entire interface — folders, chats, paged media, download
 * progress, auth — can be exercised in a plain TV emulator with no account, no
 * credentials and no native library. Everything is derived from the chat id, so
 * the same chat always produces the same content between runs.
 */
class FakeTelegramClient(private val context: Context) : TelegramClient {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val downloads = HashMap<Int, Long>()

    // --- session ----------------------------------------------------------

    override suspend fun start(credentials: TelegramCredentials) {
        _authState.value = AuthState.Initializing
        delay(300)
        // The mock backend signs itself in: there is nothing to authenticate
        // against, and the login screens are reachable from Settings anyway.
        _connectionState.value = ConnectionState.READY
        _authState.value = AuthState.Ready
    }

    override suspend fun requestQrLogin() {
        _authState.value = AuthState.WaitQrCode("tg://login?token=MOCK-" + Random.nextInt(100000, 999999))
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        _authState.value = AuthState.WaitCode(phoneNumber)
    }

    override suspend fun submitCode(code: String) {
        _authState.value = if (code == "22222") AuthState.WaitPassword("mock") else AuthState.Ready
    }

    override suspend fun submitPassword(password: String) {
        _authState.value = AuthState.Ready
    }

    override suspend fun logOut() {
        _authState.value = AuthState.LoggingOut
        delay(200)
        _authState.value = AuthState.WaitPhoneNumber
    }

    override suspend fun currentUser(): TgUser =
        TgUser(id = 1, firstName = "Mock", lastName = "User", username = "mockuser", phoneNumber = "+10000000000")

    // --- browsing ---------------------------------------------------------

    // Same ordering as the real client: the account's own folders first, the
    // derived splits after.
    override suspend fun folders(): List<TgFolder> =
        listOf(TgFolder.forBuiltIn(BuiltInFolder.ALL)) +
            listOf(TgFolder(id = 1, title = "Work"), TgFolder(id = 2, title = "Music")) +
            BuiltInFolder.entries
                .filter { it != BuiltInFolder.ALL }
                .map(TgFolder::forBuiltIn)

    override suspend fun chats(folder: TgFolder, limit: Int): List<TgChat> {
        delay(120)
        val all = listOf(savedMessages()) + (0 until 48).map { index -> fakeChat(index) }
        return when (folder.builtIn) {
            BuiltInFolder.ALL, null -> all
            BuiltInFolder.PERSONAL -> all.filter { it.kind == ChatKind.PRIVATE }
            BuiltInFolder.BOTS -> all.filter { it.kind == ChatKind.BOT }
            BuiltInFolder.GROUPS -> all.filter { it.kind == ChatKind.GROUP }
            BuiltInFolder.CHANNELS -> all.filter { it.kind == ChatKind.CHANNEL }
            BuiltInFolder.ARCHIVED -> all.filter { it.isArchived }
        }.take(limit)
    }

    override suspend fun chat(chatId: Long): TgChat? =
        (listOf(savedMessages()) + (0 until 48).map(::fakeChat)).firstOrNull { it.id == chatId }

    private fun savedMessages(): TgChat = TgChat(
        id = SAVED_MESSAGES_ID,
        title = "Saved Messages",
        kind = ChatKind.PRIVATE,
        minithumbnail = gradientJpeg(40, 40, 999, quality = 60),
        lastMessagePreview = "Everything you kept for later",
    )

    override suspend fun searchChats(query: String, limit: Int): List<TgChat> {
        if (query.isBlank()) return emptyList()
        return chats(TgFolder.forBuiltIn(BuiltInFolder.ALL), limit = 200)
            .filter { it.title.contains(query, ignoreCase = true) }
            .take(limit)
    }

    override suspend fun mediaPage(
        chatId: Long,
        category: MediaCategory,
        fromMessageId: Long,
        limit: Int,
        query: String,
    ): MediaPage {
        delay(220) // let the caller show its loading state
        val all = mediaFor(chatId)
            .filter { category == MediaCategory.ALL || it.category == category }
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.orEmpty().contains(query, ignoreCase = true)
            }
        val startIndex = if (fromMessageId == 0L) 0 else all.indexOfFirst { it.messageId == fromMessageId } + 1
        if (startIndex <= 0 && fromMessageId != 0L) return MediaPage(emptyList(), 0, false)
        val page = all.drop(startIndex).take(limit)
        val hasMore = startIndex + page.size < all.size
        return MediaPage(page, page.lastOrNull()?.messageId ?: 0L, hasMore)
    }

    override suspend fun messagePage(chatId: Long, fromMessageId: Long, limit: Int): MessagePage {
        delay(180)
        val all = messagesFor(chatId)
        val startIndex = if (fromMessageId == 0L) 0 else all.indexOfFirst { it.id == fromMessageId } + 1
        if (startIndex <= 0 && fromMessageId != 0L) return MessagePage(emptyList(), 0, false)
        val page = all.drop(startIndex).take(limit)
        return MessagePage(page, page.lastOrNull()?.id ?: 0L, startIndex + page.size < all.size)
    }

    // --- files ------------------------------------------------------------

    override suspend fun file(fileId: Int): TgFile {
        val size = fakeSize(fileId)
        val done = downloads[fileId] ?: 0L
        return TgFile(
            id = fileId,
            size = size,
            expectedSize = size,
            localPath = if (done > 0) localFile(fileId).absolutePath else null,
            downloadedPrefixSize = done,
            downloadedSize = done,
            isDownloadingCompleted = done >= size,
            isDownloadingActive = done in 1 until size,
        )
    }

    override fun fileUpdates(fileId: Int): Flow<TgFile> = flow {
        val size = fakeSize(fileId)
        var done = downloads[fileId] ?: 0L
        while (done < size) {
            delay(160)
            done = minOf(size, done + size / 8 + 1)
            downloads[fileId] = done
            emit(file(fileId))
        }
        emit(file(fileId))
    }

    override suspend fun downloadFile(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Long,
        synchronous: Boolean,
    ): TgFile {
        if (!downloads.containsKey(fileId)) downloads[fileId] = 1L
        if (synchronous) {
            downloads[fileId] = fakeSize(fileId)
            writeLocalFile(fileId)
        }
        return file(fileId)
    }

    override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long =
        ((downloads[fileId] ?: 0L) - offset).coerceAtLeast(0)

    override suspend fun cancelDownload(fileId: Int) = Unit

    override suspend fun downloadFully(fileId: Int, priority: Int): String {
        downloads[fileId] = fakeSize(fileId)
        return writeLocalFile(fileId).absolutePath
    }

    // --- live streams -----------------------------------------------------

    /** The first channel in the generated data is always "live", to exercise the banner. */
    override suspend fun liveStream(chatId: Long): TgLiveStream? {
        val channels = chats(TgFolder.forBuiltIn(BuiltInFolder.CHANNELS), limit = 200)
        val live = channels.firstOrNull() ?: return null
        if (live.id != chatId) return null
        return TgLiveStream(
            groupCallId = 1,
            chatId = chatId,
            title = live.title,
            participantCount = 1_284,
            isRtmpStream = true,
        )
    }

    override val videoChatUpdates: Flow<Long> = emptyFlow()

    // Generated content has no real broadcast behind it, so the banner appears
    // but pressing play says so rather than pretending.
    override suspend fun joinLiveStream(groupCallId: Int): Boolean = false

    override suspend fun leaveLiveStream(groupCallId: Int) = Unit

    override suspend fun liveStreamChannels(groupCallId: Int): List<TgStreamChannel> = emptyList()

    override suspend fun liveStreamSegment(
        groupCallId: Int,
        timeOffsetMs: Long,
        scale: Int,
        channelId: Int,
    ): ByteArray? = null

    // --- proxies ----------------------------------------------------------

    private val fakeProxies = mutableListOf<TgProxy>()

    override suspend fun proxies(): List<TgProxy> = fakeProxies.toList()

    override suspend fun addProxy(proxy: TgProxy): TgProxy {
        val added = proxy.copy(id = (fakeProxies.maxOfOrNull { it.id } ?: 0) + 1, isEnabled = true)
        fakeProxies.replaceAll { it.copy(isEnabled = false) }
        fakeProxies += added
        return added
    }

    override suspend fun enableProxy(id: Int) {
        fakeProxies.replaceAll { it.copy(isEnabled = it.id == id) }
    }

    override suspend fun disableProxies() {
        fakeProxies.replaceAll { it.copy(isEnabled = false) }
    }

    override suspend fun removeProxy(id: Int) {
        fakeProxies.removeAll { it.id == id }
    }

    // --- storage ----------------------------------------------------------

    override suspend fun cacheSize(): Long = cacheDir().walkTopDown().filter(File::isFile).sumOf(File::length)

    override suspend fun clearCache() {
        cacheDir().deleteRecursively()
        downloads.clear()
    }

    override suspend fun setCacheLimit(bytes: Long) = Unit

    // --- generation -------------------------------------------------------

    private fun cacheDir(): File = File(context.cacheDir, "mock-telegram").apply { mkdirs() }

    private fun localFile(fileId: Int): File = File(cacheDir(), "file-$fileId.jpg")

    private fun writeLocalFile(fileId: Int): File = localFile(fileId).apply {
        if (!exists()) writeBytes(gradientJpeg(720, 405, fileId, quality = 85))
    }

    private fun fakeSize(fileId: Int): Long = 2_000_000L + abs(fileId) % 97 * 350_000L

    private fun fakeChat(index: Int): TgChat {
        val kind = when (index % 4) {
            0 -> ChatKind.PRIVATE
            1 -> ChatKind.BOT
            2 -> ChatKind.GROUP
            else -> ChatKind.CHANNEL
        }
        val name = when (kind) {
            ChatKind.PRIVATE -> PEOPLE[index % PEOPLE.size]
            ChatKind.BOT -> BOTS[index % BOTS.size]
            ChatKind.GROUP -> GROUPS[index % GROUPS.size]
            ChatKind.CHANNEL -> CHANNELS[index % CHANNELS.size]
        }
        return TgChat(
            id = 1000L + index,
            title = name,
            kind = kind,
            minithumbnail = gradientJpeg(40, 40, index, quality = 60),
            unreadCount = if (index % 5 == 0) index % 17 else 0,
            lastMessagePreview = PREVIEWS[index % PREVIEWS.size],
            isArchived = index % 11 == 0,
        )
    }

    private fun mediaFor(chatId: Long): List<TgMediaItem> = mediaCache.getOrPut(chatId) {
        val random = Random(chatId)
        val now = (System.currentTimeMillis() / 1000).toInt()
        (0 until 140).map { index ->
            val kind = when (random.nextInt(10)) {
                0, 1, 2, 3 -> MediaKind.VIDEO
                4, 5, 6 -> MediaKind.PHOTO
                7, 8 -> MediaKind.AUDIO
                else -> MediaKind.DOCUMENT
            }
            val id = (chatId * 100_000L) + (140 - index)
            TgMediaItem(
                chatId = chatId,
                messageId = id,
                kind = kind,
                title = titleFor(kind, index),
                subtitle = if (kind == MediaKind.AUDIO) ARTISTS[index % ARTISTS.size] else null,
                date = now - index * 5_400 - random.nextInt(3_600),
                durationSeconds = when (kind) {
                    MediaKind.VIDEO -> 90 + random.nextInt(5_000)
                    MediaKind.AUDIO -> 120 + random.nextInt(300)
                    else -> 0
                },
                fileId = (chatId.toInt() * 211 + index * 7),
                fileSize = 3_000_000L + random.nextInt(400) * 1_000_000L,
                thumbnailFileId = null,
                minithumbnail = gradientJpeg(48, 27, index + chatId.toInt(), quality = 60),
                width = 1920,
                height = 1080,
                mimeType = when (kind) {
                    MediaKind.VIDEO -> "video/mp4"
                    MediaKind.PHOTO -> "image/jpeg"
                    MediaKind.AUDIO -> "audio/mpeg"
                    else -> "application/octet-stream"
                },
            )
        }.sortedByDescending { it.date }
    }

    private fun messagesFor(chatId: Long): List<TgMessage> = messageCache.getOrPut(chatId) {
        val random = Random(chatId * 31)
        val now = (System.currentTimeMillis() / 1000).toInt()
        val media = mediaFor(chatId)
        (0 until 200).map { index ->
            val withMedia = index % 7 == 3
            TgMessage(
                id = (chatId * 1_000L) + (200 - index),
                chatId = chatId,
                senderName = if (index % 3 == 0) "Mock User" else PEOPLE[index % PEOPLE.size],
                isOutgoing = index % 3 == 0,
                date = now - index * 900 - random.nextInt(600),
                text = if (withMedia) "" else SENTENCES[index % SENTENCES.size],
                media = if (withMedia) media.getOrNull(index % media.size) else null,
            )
        }
    }

    private fun titleFor(kind: MediaKind, index: Int): String = when (kind) {
        MediaKind.VIDEO -> VIDEO_TITLES[index % VIDEO_TITLES.size]
        MediaKind.PHOTO -> "IMG_${4000 + index}.jpg"
        MediaKind.AUDIO -> TRACKS[index % TRACKS.size]
        MediaKind.DOCUMENT -> "document-${index + 1}.pdf"
        MediaKind.VOICE -> "voice-${index + 1}.ogg"
        MediaKind.ANIMATION -> "clip-${index + 1}.gif"
    }

    /** A deterministic two-tone gradient, so every tile looks distinct. */
    private fun gradientJpeg(width: Int, height: Int, seed: Int, quality: Int): ByteArray {
        val random = Random(seed)
        val top = Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 0.55f, 0.75f))
        val bottom = Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 0.65f, 0.35f))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPaint(
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    top, bottom, Shader.TileMode.CLAMP,
                )
            },
        )
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private val mediaCache = HashMap<Long, List<TgMediaItem>>()
    private val messageCache = HashMap<Long, List<TgMessage>>()

    private companion object {
        const val SAVED_MESSAGES_ID = 1L
        val PEOPLE = listOf("Ali", "Sara", "Reza", "Mina", "Hossein", "Neda", "Kaveh", "Leila")
        val BOTS = listOf("MovieBot", "MusicDownloaderBot", "FileStoreBot", "SeriesBot")
        val GROUPS = listOf("Family", "Work chat", "Weekend plans", "Dev team")
        val CHANNELS = listOf("4K Movies", "Persian Music", "Tech News", "Documentaries")
        val ARTISTS = listOf("Mohsen Chavoshi", "Homayoun", "Sirvan", "Shajarian", "Googoosh")
        val TRACKS = listOf("Parvaz.mp3", "Shab.mp3", "Baran.mp3", "Setare.mp3", "Royaa.mp3")
        val VIDEO_TITLES = listOf(
            "Interstellar.2014.1080p.mkv",
            "Nature.Documentary.E03.mp4",
            "Concert.Live.2023.mp4",
            "Trip.to.North.mp4",
            "Tutorial.Kotlin.Flow.mp4",
        )
        val PREVIEWS = listOf("Sent a video", "See you tomorrow", "Photo", "New episode is up", "👍")
        val SENTENCES = listOf(
            "سلام، فایل رو فرستادم",
            "This one is 4K, should look great on the TV.",
            "فردا ساعت ۸ می‌بینمت",
            "Uploaded the new episode, enjoy!",
            "مرسی 🙏",
        )
    }
}
