package ir.tvgram.telegram.model

/** The five buckets the home screen groups a chat's media into. */
enum class MediaCategory {
    ALL,
    VIDEO,
    PHOTO,
    AUDIO,
    OTHER,
}

/** What a single media message actually is, within its [MediaCategory]. */
enum class MediaKind {
    VIDEO,
    PHOTO,
    AUDIO,
    VOICE,
    ANIMATION,
    DOCUMENT,
    ;

    val category: MediaCategory
        get() = when (this) {
            VIDEO, ANIMATION -> MediaCategory.VIDEO
            PHOTO -> MediaCategory.PHOTO
            AUDIO, VOICE -> MediaCategory.AUDIO
            DOCUMENT -> MediaCategory.OTHER
        }

    val isPlayable: Boolean
        get() = this != PHOTO && this != DOCUMENT
}

/**
 * Folders offered by the source picker. The first six are derived by TVGram
 * from the chat list itself; anything else mirrors a chat folder the user
 * created in Telegram.
 */
enum class BuiltInFolder {
    ALL,
    PERSONAL,
    BOTS,
    GROUPS,
    CHANNELS,
    ARCHIVED,
}

data class TgFolder(
    val id: Int,
    /** Empty for built-ins — the UI resolves a localized title from [builtIn]. */
    val title: String,
    val builtIn: BuiltInFolder? = null,
    val chatCount: Int = 0,
) {
    companion object {
        /**
         * Built-in folders use negative ids so they never clash with the ids
         * Telegram assigns to the user's own chat folders.
         */
        fun forBuiltIn(kind: BuiltInFolder): TgFolder =
            TgFolder(id = -(kind.ordinal + 1), title = "", builtIn = kind)
    }
}

enum class ChatKind { PRIVATE, BOT, GROUP, CHANNEL }

data class TgChat(
    val id: Long,
    val title: String,
    val kind: ChatKind,
    val photoFileId: Int? = null,
    val minithumbnail: ByteArray? = null,
    val unreadCount: Int = 0,
    val lastMessagePreview: String? = null,
    val isArchived: Boolean = false,
) {
    // ByteArray breaks the generated equals/hashCode; a chat is identified by
    // its id everywhere in this app, so compare on the fields that matter.
    override fun equals(other: Any?): Boolean =
        this === other || (other is TgChat && id == other.id && title == other.title &&
            unreadCount == other.unreadCount && lastMessagePreview == other.lastMessagePreview)

    override fun hashCode(): Int = id.hashCode()
}

/** A file as TDLib currently knows it. */
data class TgFile(
    val id: Int,
    /** Total size, or 0 when the server has not told us yet. */
    val size: Long = 0,
    val expectedSize: Long = 0,
    val localPath: String? = null,
    /** Contiguous bytes available from offset 0 (or from the active offset). */
    val downloadedPrefixSize: Long = 0,
    val downloadedSize: Long = 0,
    val isDownloadingCompleted: Boolean = false,
    val isDownloadingActive: Boolean = false,
    val canBeDownloaded: Boolean = true,
) {
    val bestKnownSize: Long get() = if (size > 0) size else expectedSize
}

data class TgMediaItem(
    val chatId: Long,
    val messageId: Long,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    /** Unix seconds. */
    val date: Int = 0,
    val durationSeconds: Int = 0,
    val fileId: Int,
    val fileSize: Long = 0,
    val thumbnailFileId: Int? = null,
    val minithumbnail: ByteArray? = null,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String? = null,
) {
    val category: MediaCategory get() = kind.category

    /** Stable across pages and flavours; used as the Paging/LazyList key. */
    val uid: String get() = "$chatId:$messageId"

    override fun equals(other: Any?): Boolean =
        this === other || (other is TgMediaItem && chatId == other.chatId && messageId == other.messageId)

    override fun hashCode(): Int = 31 * chatId.hashCode() + messageId.hashCode()
}

data class MediaPage(
    val items: List<TgMediaItem>,
    val nextFromMessageId: Long,
    val hasMore: Boolean,
)

/** Anything a chat message can carry that the Chats screen needs to render. */
data class TgMessage(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val isOutgoing: Boolean,
    val date: Int,
    val text: String,
    /** Present when the message carries media the player can open. */
    val media: TgMediaItem? = null,
    val isService: Boolean = false,
)

data class MessagePage(
    val messages: List<TgMessage>,
    val nextFromMessageId: Long,
    val hasMore: Boolean,
)

data class TgUser(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String? = null,
    val phoneNumber: String? = null,
    val photoFileId: Int? = null,
) {
    val displayName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

sealed interface AuthState {
    /** TDLib is booting or we have not asked it anything yet. */
    data object Initializing : AuthState

    /** api_id/api_hash are missing — the setup screen must collect them. */
    data object NeedsCredentials : AuthState

    data class WaitQrCode(val link: String) : AuthState
    data object WaitPhoneNumber : AuthState
    data class WaitCode(val phoneNumber: String) : AuthState
    data class WaitPassword(val hint: String?) : AuthState
    data object Ready : AuthState
    data object LoggingOut : AuthState
    data object Closed : AuthState
    data class Failed(val message: String) : AuthState
}

enum class ConnectionState { WAITING_FOR_NETWORK, CONNECTING_TO_PROXY, CONNECTING, UPDATING, READY }

/** The proxy kinds Telegram itself supports. */
enum class ProxyKind { MTPROTO, SOCKS5, HTTP }

data class TgProxy(
    val id: Int,
    val server: String,
    val port: Int,
    val kind: ProxyKind,
    val isEnabled: Boolean = false,
    /** MTProto uses a secret; SOCKS5 and HTTP use a username and password. */
    val secret: String = "",
    val username: String = "",
    val password: String = "",
) {
    val label: String get() = "${kind.name.lowercase()} · $server:$port"
}

data class TelegramCredentials(val apiId: Int, val apiHash: String) {
    val isUsable: Boolean get() = apiId != 0 && apiHash.isNotBlank()
}

class TelegramException(val code: Int, message: String) : Exception(message)
