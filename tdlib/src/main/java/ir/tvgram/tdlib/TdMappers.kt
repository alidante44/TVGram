package ir.tvgram.tdlib

import ir.tvgram.telegram.model.ChatKind
import ir.tvgram.telegram.model.MediaCategory
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgChat
import ir.tvgram.telegram.model.TgFile
import ir.tvgram.telegram.model.TgMediaItem
import ir.tvgram.telegram.model.TgMessage
import ir.tvgram.telegram.model.TgUser
import org.drinkless.tdlib.TdApi

/** Conversions from TDLib's wire types to the plain types the app works with. */
internal object TdMappers {

    fun file(file: TdApi.File?): TgFile? {
        if (file == null) return null
        val local = file.local
        val remote = file.remote
        return TgFile(
            id = file.id,
            size = file.size,
            expectedSize = file.expectedSize,
            localPath = local?.path?.takeIf { it.isNotEmpty() },
            downloadedPrefixSize = local?.downloadedPrefixSize ?: 0L,
            downloadedSize = local?.downloadedSize ?: 0L,
            isDownloadingCompleted = local?.isDownloadingCompleted ?: false,
            isDownloadingActive = local?.isDownloadingActive ?: false,
            canBeDownloaded = local?.canBeDownloaded ?: (remote != null),
        )
    }

    fun user(user: TdApi.User?): TgUser? = user?.let {
        TgUser(
            id = it.id,
            firstName = it.firstName.orEmpty(),
            lastName = it.lastName.orEmpty(),
            username = it.usernames?.editableUsername?.takeIf(String::isNotEmpty),
            phoneNumber = it.phoneNumber?.takeIf(String::isNotEmpty),
            photoFileId = it.profilePhoto?.small?.id,
        )
    }

    fun chat(chat: TdApi.Chat, isBot: (Long) -> Boolean, isArchived: Boolean): TgChat = TgChat(
        id = chat.id,
        title = chat.title.orEmpty().ifBlank { "#${chat.id}" },
        kind = chatKind(chat.type, isBot),
        photoFileId = chat.photo?.small?.id,
        minithumbnail = chat.photo?.minithumbnail?.data,
        unreadCount = chat.unreadCount,
        lastMessagePreview = chat.lastMessage?.let(::previewOf),
        isArchived = isArchived,
    )

    private fun chatKind(type: TdApi.ChatType?, isBot: (Long) -> Boolean): ChatKind = when (type) {
        is TdApi.ChatTypePrivate -> if (isBot(type.userId)) ChatKind.BOT else ChatKind.PRIVATE
        is TdApi.ChatTypeSecret -> ChatKind.PRIVATE
        is TdApi.ChatTypeBasicGroup -> ChatKind.GROUP
        is TdApi.ChatTypeSupergroup -> if (type.isChannel) ChatKind.CHANNEL else ChatKind.GROUP
        else -> ChatKind.GROUP
    }

    fun searchFilter(category: MediaCategory): TdApi.SearchMessagesFilter = when (category) {
        MediaCategory.VIDEO -> TdApi.SearchMessagesFilterVideo()
        MediaCategory.PHOTO -> TdApi.SearchMessagesFilterPhoto()
        MediaCategory.AUDIO -> TdApi.SearchMessagesFilterAudio()
        MediaCategory.OTHER -> TdApi.SearchMessagesFilterDocument()
        // Telegram has no "any media" filter; MediaFeed merges the concrete
        // ones instead and never asks for ALL.
        MediaCategory.ALL -> TdApi.SearchMessagesFilterDocument()
    }

    /** Null when the message carries nothing the media grid can show. */
    fun mediaItem(message: TdApi.Message): TgMediaItem? {
        val content = message.content
        val base = { kind: MediaKind,
                     title: String,
                     subtitle: String?,
                     duration: Int,
                     file: TdApi.File?,
                     thumb: TdApi.File?,
                     mini: ByteArray?,
                     width: Int,
                     height: Int,
                     mime: String? ->
            file?.let {
                TgMediaItem(
                    chatId = message.chatId,
                    messageId = message.id,
                    kind = kind,
                    title = title.ifBlank { kind.name.lowercase() },
                    subtitle = subtitle?.takeIf(String::isNotBlank),
                    date = message.date,
                    durationSeconds = duration,
                    fileId = it.id,
                    fileSize = if (it.size > 0) it.size else it.expectedSize,
                    thumbnailFileId = thumb?.id,
                    minithumbnail = mini,
                    width = width,
                    height = height,
                    mimeType = mime,
                )
            }
        }

        return when (content) {
            is TdApi.MessageVideo -> content.video.let { video ->
                base(
                    MediaKind.VIDEO,
                    video.fileName.orEmpty(),
                    caption(content.caption),
                    video.duration,
                    video.video,
                    video.thumbnail?.file,
                    video.minithumbnail?.data,
                    video.width,
                    video.height,
                    video.mimeType,
                )
            }

            is TdApi.MessageAnimation -> content.animation.let { animation ->
                base(
                    MediaKind.ANIMATION,
                    animation.fileName.orEmpty(),
                    caption(content.caption),
                    animation.duration,
                    animation.animation,
                    animation.thumbnail?.file,
                    animation.minithumbnail?.data,
                    animation.width,
                    animation.height,
                    animation.mimeType,
                )
            }

            is TdApi.MessagePhoto -> content.photo.let { photo ->
                val largest = photo.sizes?.maxByOrNull { it.width * it.height }
                base(
                    MediaKind.PHOTO,
                    caption(content.caption).orEmpty(),
                    null,
                    0,
                    largest?.photo,
                    photo.sizes?.minByOrNull { it.width * it.height }?.photo,
                    photo.minithumbnail?.data,
                    largest?.width ?: 0,
                    largest?.height ?: 0,
                    "image/jpeg",
                )
            }

            is TdApi.MessageAudio -> content.audio.let { audio ->
                base(
                    MediaKind.AUDIO,
                    audio.title.orEmpty().ifBlank { audio.fileName.orEmpty() },
                    audio.performer?.takeIf(String::isNotBlank) ?: caption(content.caption),
                    audio.duration,
                    audio.audio,
                    audio.albumCoverThumbnail?.file,
                    audio.albumCoverMinithumbnail?.data,
                    0,
                    0,
                    audio.mimeType,
                )
            }

            is TdApi.MessageVoiceNote -> content.voiceNote.let { voice ->
                base(
                    MediaKind.VOICE,
                    "",
                    caption(content.caption),
                    voice.duration,
                    voice.voice,
                    null,
                    null,
                    0,
                    0,
                    voice.mimeType,
                )
            }

            is TdApi.MessageDocument -> content.document.let { document ->
                base(
                    MediaKind.DOCUMENT,
                    document.fileName.orEmpty(),
                    caption(content.caption),
                    0,
                    document.document,
                    document.thumbnail?.file,
                    document.minithumbnail?.data,
                    0,
                    0,
                    document.mimeType,
                )
            }

            else -> null
        }
    }

    fun message(message: TdApi.Message, senderName: String): TgMessage = TgMessage(
        id = message.id,
        chatId = message.chatId,
        senderName = senderName,
        isOutgoing = message.isOutgoing,
        date = message.date,
        text = textOf(message),
        media = mediaItem(message),
        isService = message.content !is TdApi.MessageText && mediaItem(message) == null,
    )

    private fun textOf(message: TdApi.Message): String = when (val content = message.content) {
        is TdApi.MessageText -> content.text?.text.orEmpty()
        is TdApi.MessagePhoto -> caption(content.caption).orEmpty()
        is TdApi.MessageVideo -> caption(content.caption).orEmpty()
        is TdApi.MessageAudio -> caption(content.caption).orEmpty()
        is TdApi.MessageDocument -> caption(content.caption).orEmpty()
        is TdApi.MessageAnimation -> caption(content.caption).orEmpty()
        is TdApi.MessageVoiceNote -> caption(content.caption).orEmpty()
        else -> ""
    }

    private fun previewOf(message: TdApi.Message): String = textOf(message).ifBlank {
        when (message.content) {
            is TdApi.MessagePhoto -> "📷"
            is TdApi.MessageVideo -> "🎬"
            is TdApi.MessageAudio -> "🎵"
            is TdApi.MessageVoiceNote -> "🎤"
            is TdApi.MessageDocument -> "📄"
            else -> ""
        }
    }

    private fun caption(text: TdApi.FormattedText?): String? =
        text?.text?.takeIf { it.isNotBlank() }
}
