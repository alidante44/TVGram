package ir.tvgram.tdlib

import android.content.Context
import android.os.Build
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.AuthState
import ir.tvgram.telegram.model.BuiltInFolder
import ir.tvgram.telegram.model.ChatKind
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.MediaCategory
import ir.tvgram.telegram.model.MediaPage
import ir.tvgram.telegram.model.ProxyKind
import ir.tvgram.telegram.model.MessagePage
import ir.tvgram.telegram.model.TelegramCredentials
import ir.tvgram.telegram.model.TelegramException
import ir.tvgram.telegram.model.TgChat
import ir.tvgram.telegram.model.TgFile
import ir.tvgram.telegram.model.TgFolder
import ir.tvgram.telegram.model.TgLiveStream
import ir.tvgram.telegram.model.TgMediaItem
import ir.tvgram.telegram.model.TgProxy
import ir.tvgram.telegram.model.TgStreamChannel
import ir.tvgram.telegram.model.TgMessage
import ir.tvgram.telegram.model.TgUser
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi

/**
 * The real backend: everything the app asks for, served by TDLib.
 *
 * TDLib is a stateful client — it streams updates continuously and expects the
 * authorization handshake to be driven from those updates rather than from
 * return values. So this class keeps a single update pump running for the life
 * of the process, mirrors the pieces of state the UI needs (users, chats,
 * folders, file progress) and answers queries from that mirror plus direct
 * requests.
 */
class TdlibTelegramClient(
    private val context: Context,
    private val scope: CoroutineScope,
) : TelegramClient {

    private val connection = TdlibConnection()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val users = ConcurrentHashMap<Long, TdApi.User>()
    private val chatCache = ConcurrentHashMap<Long, TdApi.Chat>()
    private val archivedChatIds = java.util.Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())
    private val chatFolders = MutableStateFlow<List<TdApi.ChatFolderInfo>>(emptyList())

    private val _fileUpdates = MutableSharedFlow<TdApi.File>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val fileUpdateStream = _fileUpdates.asSharedFlow()

    /** Chat ids whose video chat started, ended or changed. */
    private val _videoChatUpdates = MutableSharedFlow<Long>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val startMutex = Mutex()
    private var credentials: TelegramCredentials = TelegramCredentials(0, "")
    private var pumpStarted = false
    private var pendingPhoneNumber: String = ""

    /** Saved Messages is the chat whose id equals your own user id. */
    @Volatile
    private var myUserId: Long = 0

    // --- session ----------------------------------------------------------

    override suspend fun start(credentials: TelegramCredentials) {
        startMutex.withLock {
            this.credentials = credentials
            if (!credentials.isUsable) {
                _authState.value = AuthState.NeedsCredentials
                return
            }
            connection.open()
            if (!pumpStarted) {
                pumpStarted = true
                scope.launch { connection.updates.collect { update -> onUpdate(update) } }
            }
            // TDLib announces its authorization state on connect, which is what
            // actually drives the handshake; asking for it explicitly just makes
            // a restart deterministic.
            val current = runCatching {
                connection.send<TdApi.Object>(TdApi.GetAuthorizationState())
            }.getOrNull()
            if (current is TdApi.AuthorizationState) applyAuthorizationState(current)
        }
    }

    override suspend fun requestQrLogin() {
        connection.send<TdApi.Object>(
            TdApi.RequestQrCodeAuthentication().apply { otherUserIds = LongArray(0) },
        )
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        pendingPhoneNumber = phoneNumber
        connection.send<TdApi.Ok>(
            TdApi.SetAuthenticationPhoneNumber().apply {
                this.phoneNumber = phoneNumber
                settings = TdApi.PhoneNumberAuthenticationSettings().apply {
                    allowFlashCall = false
                    allowMissedCall = false
                    isCurrentPhoneNumber = false
                }
            },
        )
    }

    override suspend fun submitCode(code: String) {
        connection.send<TdApi.Ok>(TdApi.CheckAuthenticationCode().apply { this.code = code })
    }

    override suspend fun submitPassword(password: String) {
        connection.send<TdApi.Ok>(TdApi.CheckAuthenticationPassword().apply { this.password = password })
    }

    override suspend fun logOut() {
        _authState.value = AuthState.LoggingOut
        runCatching { connection.send<TdApi.Ok>(TdApi.LogOut()) }
        users.clear()
        chatCache.clear()
        archivedChatIds.clear()
        chatFolders.value = emptyList()
    }

    override suspend fun currentUser(): TgUser? {
        val user = connection.sendOrNull<TdApi.User>(TdApi.GetMe())
        user?.let { myUserId = it.id }
        return TdMappers.user(user)
    }

    private suspend fun savedMessagesChatId(): Long {
        if (myUserId == 0L) currentUser()
        return myUserId
    }

    // --- browsing ---------------------------------------------------------

    override suspend fun folders(): List<TgFolder> {
        // Telegram announces the account's own folders in an update shortly
        // after sync rather than on request, so asking too early used to return
        // only the derived ones. Give it a moment before falling back.
        if (chatFolders.value.isEmpty()) {
            withTimeoutOrNull(FOLDER_SYNC_TIMEOUT_MS) {
                chatFolders.first { it.isNotEmpty() }
            }
        }

        val custom = chatFolders.value.map { info ->
            TgFolder(id = info.id, title = folderTitle(info))
        }

        // The viewer's own arrangement comes first; the derived splits are a
        // fallback for accounts that have never made folders.
        return listOf(TgFolder.forBuiltIn(BuiltInFolder.ALL)) +
            custom +
            BuiltInFolder.entries
                .filter { it != BuiltInFolder.ALL }
                .map(TgFolder::forBuiltIn)
    }

    override suspend fun chats(folder: TgFolder, limit: Int): List<TgChat> {
        val list: TdApi.ChatList = when (folder.builtIn) {
            BuiltInFolder.ARCHIVED -> TdApi.ChatListArchive()
            null -> TdApi.ChatListFolder().apply { chatFolderId = folder.id }
            else -> TdApi.ChatListMain()
        }
        val ids = loadChatIds(list, limit).toMutableList()

        // Saved Messages holds a lot of what people keep to watch later, and it
        // is easy to lose in a long list, so it is pulled to the top of the
        // places it belongs rather than left wherever it sorts.
        if (folder.builtIn == BuiltInFolder.ALL || folder.builtIn == BuiltInFolder.PERSONAL) {
            val saved = savedMessagesChatId()
            if (saved != 0L) {
                ids.remove(saved)
                ids.add(0, saved)
            }
        }

        val resolved = ids.mapNotNull { id -> chatCache[id] ?: connection.sendOrNull<TdApi.Chat>(chatQuery(id)) }
        warmUpUsers(resolved)

        val archived = folder.builtIn == BuiltInFolder.ARCHIVED
        val mapped = resolved.map { TdMappers.chat(it, ::isBot, archived || it.id in archivedChatIds) }

        return when (folder.builtIn) {
            BuiltInFolder.PERSONAL -> mapped.filter { it.kind == ChatKind.PRIVATE }
            BuiltInFolder.BOTS -> mapped.filter { it.kind == ChatKind.BOT }
            BuiltInFolder.GROUPS -> mapped.filter { it.kind == ChatKind.GROUP }
            BuiltInFolder.CHANNELS -> mapped.filter { it.kind == ChatKind.CHANNEL }
            else -> mapped
        }
    }

    override suspend fun chat(chatId: Long): TgChat? {
        val chat = chatCache[chatId] ?: connection.sendOrNull<TdApi.Chat>(chatQuery(chatId)) ?: return null
        warmUpUsers(listOf(chat))
        return TdMappers.chat(chat, ::isBot, chatId in archivedChatIds)
    }

    override suspend fun searchChats(query: String, limit: Int): List<TgChat> {
        if (query.isBlank()) return emptyList()
        // Local first so already-synced chats answer instantly, then the server
        // for everything else the account can see.
        val local = connection.sendOrNull<TdApi.Chats>(
            TdApi.SearchChats().apply { this.query = query; this.limit = limit },
        )?.chatIds?.toList().orEmpty()
        val remote = connection.sendOrNull<TdApi.Chats>(
            TdApi.SearchChatsOnServer().apply { this.query = query; this.limit = limit },
        )?.chatIds?.toList().orEmpty()

        val ids = (local + remote).distinct().take(limit)
        val resolved = ids.mapNotNull { id ->
            chatCache[id] ?: connection.sendOrNull<TdApi.Chat>(chatQuery(id))
        }
        warmUpUsers(resolved)
        return resolved.map { TdMappers.chat(it, ::isBot, it.id in archivedChatIds) }
    }

    override suspend fun mediaPage(
        chatId: Long,
        category: MediaCategory,
        fromMessageId: Long,
        limit: Int,
        query: String,
    ): MediaPage {
        val result = connection.send<TdApi.FoundChatMessages>(
            TdApi.SearchChatMessages().apply {
                this.chatId = chatId
                this.query = query
                this.fromMessageId = fromMessageId
                offset = 0
                this.limit = limit
                filter = TdMappers.searchFilter(category)
            },
        )
        val found: List<TdApi.Message> = result.messages?.toList() ?: emptyList()
        val items: List<TgMediaItem> = found.mapNotNull(TdMappers::mediaItem)
        val cursor = result.nextFromMessageId.takeIf { it != 0L } ?: found.lastOrNull()?.id ?: 0L
        // Telegram returns fewer messages than asked for more often than not, so
        // an empty page — not a short one — is the reliable "that was the end".
        return MediaPage(items, cursor, found.isNotEmpty())
    }

    override suspend fun messagePage(chatId: Long, fromMessageId: Long, limit: Int): MessagePage {
        suspend fun fetch(): TdApi.Messages = connection.send(
            TdApi.GetChatHistory().apply {
                this.chatId = chatId
                this.fromMessageId = fromMessageId
                offset = 0
                this.limit = limit
                onlyLocal = false
            },
        )

        var found: List<TdApi.Message> = fetch().messages?.toList() ?: emptyList()
        // The very first request for a chat usually returns nothing while TDLib
        // pulls the history in; opening the chat and asking once more is enough.
        if (found.isEmpty() && fromMessageId == 0L) {
            connection.sendOrNull<TdApi.Ok>(TdApi.OpenChat().apply { this.chatId = chatId })
            found = fetch().messages?.toList() ?: emptyList()
        }

        val messages: List<TgMessage> = found.map { message ->
            TdMappers.message(message, senderNameOf(message))
        }
        return MessagePage(messages, found.lastOrNull()?.id ?: 0L, found.isNotEmpty())
    }

    // --- files ------------------------------------------------------------

    override suspend fun file(fileId: Int): TgFile {
        val file = connection.send<TdApi.File>(TdApi.GetFile().apply { this.fileId = fileId })
        return TdMappers.file(file) ?: TgFile(id = fileId)
    }

    override fun fileUpdates(fileId: Int): Flow<TgFile> = flow {
        emit(file(fileId))
        fileUpdateStream
            .filter { it.id == fileId }
            .collect { update -> TdMappers.file(update)?.let { emit(it) } }
    }

    override suspend fun downloadFile(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Long,
        synchronous: Boolean,
    ): TgFile {
        val file = connection.send<TdApi.File>(
            TdApi.DownloadFile().apply {
                this.fileId = fileId
                this.priority = priority.coerceIn(1, 32)
                this.offset = offset
                this.limit = limit
                this.synchronous = synchronous
            },
        )
        return TdMappers.file(file) ?: TgFile(id = fileId)
    }

    override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long =
        connection.sendOrNull<TdApi.FileDownloadedPrefixSize>(
            TdApi.GetFileDownloadedPrefixSize().apply {
                this.fileId = fileId
                this.offset = offset
            },
        )?.size ?: 0L

    override suspend fun cancelDownload(fileId: Int) {
        connection.sendOrNull<TdApi.Ok>(
            TdApi.CancelDownloadFile().apply {
                this.fileId = fileId
                onlyIfPending = false
            },
        )
    }

    override suspend fun downloadFully(fileId: Int, priority: Int): String {
        val existing = file(fileId)
        existing.localPath?.let { path ->
            if (existing.isDownloadingCompleted && File(path).exists()) return path
        }
        val downloaded = downloadFile(
            fileId = fileId,
            priority = priority,
            offset = 0,
            limit = 0,
            synchronous = true,
        )
        return downloaded.localPath
            ?: fileUpdates(fileId).first { it.isDownloadingCompleted }.localPath
            ?: throw TelegramException(0, "file $fileId has no local path after download")
    }

    // --- live streams -----------------------------------------------------

    /**
     * What TDLib knows about a chat's broadcast.
     *
     * Only the description is available to us: the stream segments
     * (getGroupCallStreamSegment) are served to call participants, and joining
     * needs a WebRTC payload from Telegram's tgcalls library, which TDLib does
     * not contain. So this answers "is something live right now", which is what
     * the banner needs.
     */
    override suspend fun liveStream(chatId: Long): TgLiveStream? {
        val chat = chatCache[chatId] ?: connection.sendOrNull<TdApi.Chat>(chatQuery(chatId)) ?: return null
        val callId = chat.videoChat?.groupCallId ?: 0
        if (callId == 0) return null

        val call = connection.sendOrNull<TdApi.GroupCall>(
            TdApi.GetGroupCall().apply { groupCallId = callId },
        ) ?: return null
        if (!call.isActive) return null

        return TgLiveStream(
            groupCallId = callId,
            chatId = chatId,
            title = call.title.orEmpty(),
            participantCount = call.participantCount,
            isRtmpStream = call.isRtmpStream,
        )
    }

    override val videoChatUpdates: Flow<Long> = _videoChatUpdates.asSharedFlow()

    /**
     * Joins the call as a listener so the server will serve its stream.
     *
     * The payload is the minimal description a WebRTC listener sends for a
     * broadcast — no ICE candidates, no fingerprints, just a synchronisation
     * source — which is all Telegram checks before it will hand over stream
     * segments to a participant. Producing a full payload would need tgcalls,
     * Telegram's native WebRTC component, which TDLib does not ship; this is
     * the part that can be done without it.
     */
    override suspend fun joinLiveStream(groupCallId: Int): Boolean {
        val source = Random.nextInt(1, Int.MAX_VALUE)
        val joined = connection.sendOrNull<TdApi.Text>(
            TdApi.JoinVideoChat().apply {
                this.groupCallId = groupCallId
                participantId = null
                joinParameters = TdApi.GroupCallJoinParameters().apply {
                    audioSourceId = source
                    payload = LISTENER_PAYLOAD.format(source)
                    isMuted = true
                    isMyVideoEnabled = false
                }
                inviteHash = ""
            },
        )
        return joined != null
    }

    override suspend fun leaveLiveStream(groupCallId: Int) {
        connection.sendOrNull<TdApi.Ok>(
            TdApi.LeaveGroupCall().apply { this.groupCallId = groupCallId },
        )
    }

    override suspend fun liveStreamChannels(groupCallId: Int): List<TgStreamChannel> =
        connection.sendOrNull<TdApi.GroupCallStreams>(
            TdApi.GetGroupCallStreams().apply { this.groupCallId = groupCallId },
        )?.streams?.map { stream ->
            TgStreamChannel(
                channelId = stream.channelId,
                scale = stream.scale,
                timeOffsetMs = stream.timeOffset,
            )
        }.orEmpty()

    override suspend fun liveStreamSegment(
        groupCallId: Int,
        timeOffsetMs: Long,
        scale: Int,
        channelId: Int,
    ): ByteArray? = connection.sendOrNull<TdApi.Data>(
        TdApi.GetGroupCallStreamSegment().apply {
            this.groupCallId = groupCallId
            timeOffset = timeOffsetMs
            this.scale = scale
            this.channelId = channelId
            // The worst quality is the one a television over a domestic link
            // can actually keep up with, and the only one always on offer.
            videoQuality = TdApi.GroupCallVideoQualityMedium()
        },
    )?.data

    // --- proxies ----------------------------------------------------------

    override suspend fun proxies(): List<TgProxy> =
        connection.sendOrNull<TdApi.AddedProxies>(TdApi.GetProxies())
            ?.proxies?.mapNotNull(::toProxy).orEmpty()

    override suspend fun addProxy(proxy: TgProxy): TgProxy? {
        val added = connection.sendOrNull<TdApi.AddedProxy>(
            TdApi.AddProxy().apply {
                this.proxy = TdApi.Proxy().apply {
                    server = proxy.server
                    port = proxy.port
                    type = proxyType(proxy)
                }
                enable = true
                comment = ""
            },
        )
        return added?.let(::toProxy)
    }

    override suspend fun enableProxy(id: Int) {
        connection.sendOrNull<TdApi.Ok>(TdApi.EnableProxy().apply { proxyId = id })
    }

    override suspend fun disableProxies() {
        connection.sendOrNull<TdApi.Ok>(TdApi.DisableProxy())
    }

    override suspend fun removeProxy(id: Int) {
        connection.sendOrNull<TdApi.Ok>(TdApi.RemoveProxy().apply { proxyId = id })
    }

    private fun proxyType(proxy: TgProxy): TdApi.ProxyType = when (proxy.kind) {
        ProxyKind.MTPROTO -> TdApi.ProxyTypeMtproto().apply { secret = proxy.secret }

        ProxyKind.SOCKS5 -> TdApi.ProxyTypeSocks5().apply {
            username = proxy.username
            password = proxy.password
        }

        ProxyKind.HTTP -> TdApi.ProxyTypeHttp().apply {
            username = proxy.username
            password = proxy.password
            httpOnly = false
        }
    }

    /**
     * The identity of a proxy (its id and whether it is on) lives on the
     * wrapper, not on the server address itself.
     */
    private fun toProxy(added: TdApi.AddedProxy): TgProxy? {
        val proxy = added.proxy ?: return null
        val kind = when (proxy.type) {
            is TdApi.ProxyTypeMtproto -> ProxyKind.MTPROTO
            is TdApi.ProxyTypeSocks5 -> ProxyKind.SOCKS5
            is TdApi.ProxyTypeHttp -> ProxyKind.HTTP
            else -> return null
        }
        return TgProxy(
            id = added.id,
            server = proxy.server.orEmpty(),
            port = proxy.port,
            kind = kind,
            isEnabled = added.isEnabled,
        )
    }

    // --- storage ----------------------------------------------------------

    override suspend fun cacheSize(): Long =
        connection.sendOrNull<TdApi.StorageStatisticsFast>(TdApi.GetStorageStatisticsFast())?.filesSize ?: 0L

    override suspend fun clearCache() {
        connection.sendOrNull<TdApi.StorageStatistics>(
            TdApi.OptimizeStorage().apply {
                size = 0
                ttl = 0
                count = 0
                immunityDelay = 0
                chatLimit = 0
                returnDeletedFileStatistics = false
            },
        )
    }

    override suspend fun setCacheLimit(bytes: Long) {
        if (bytes <= 0) return
        connection.sendOrNull<TdApi.StorageStatistics>(
            TdApi.OptimizeStorage().apply {
                size = bytes
                ttl = -1
                count = -1
                immunityDelay = -1
                chatLimit = 0
                returnDeletedFileStatistics = false
            },
        )
    }

    // --- update pump ------------------------------------------------------

    private suspend fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> applyAuthorizationState(update.authorizationState)

            is TdApi.UpdateConnectionState -> _connectionState.value = when (update.state) {
                is TdApi.ConnectionStateWaitingForNetwork -> ConnectionState.WAITING_FOR_NETWORK
                is TdApi.ConnectionStateConnectingToProxy -> ConnectionState.CONNECTING_TO_PROXY
                is TdApi.ConnectionStateConnecting -> ConnectionState.CONNECTING
                is TdApi.ConnectionStateUpdating -> ConnectionState.UPDATING
                else -> ConnectionState.READY
            }

            is TdApi.UpdateUser -> users[update.user.id] = update.user
            is TdApi.UpdateNewChat -> chatCache[update.chat.id] = update.chat
            is TdApi.UpdateChatTitle -> chatCache[update.chatId]?.title = update.title
            is TdApi.UpdateChatPhoto -> chatCache[update.chatId]?.photo = update.photo
            is TdApi.UpdateChatLastMessage -> chatCache[update.chatId]?.lastMessage = update.lastMessage
            is TdApi.UpdateChatReadInbox -> chatCache[update.chatId]?.unreadCount = update.unreadCount

            is TdApi.UpdateChatPosition -> {
                if (update.position?.list is TdApi.ChatListArchive) {
                    if (update.position.order != 0L) archivedChatIds += update.chatId
                    else archivedChatIds -= update.chatId
                }
            }

            is TdApi.UpdateChatFolders ->
                chatFolders.value = update.chatFolders?.toList() ?: emptyList()

            is TdApi.UpdateChatVideoChat -> {
                chatCache[update.chatId]?.videoChat = update.videoChat
                _videoChatUpdates.tryEmit(update.chatId)
            }

            // A group call update carries no chat id, so the chat it belongs to
            // has to be found by the call id we already know about.
            is TdApi.UpdateGroupCall -> {
                val callId = update.groupCall?.id ?: 0
                if (callId != 0) {
                    chatCache.values
                        .firstOrNull { it.videoChat?.groupCallId == callId }
                        ?.let { _videoChatUpdates.tryEmit(it.id) }
                }
            }

            is TdApi.UpdateFile -> _fileUpdates.tryEmit(update.file)
        }
    }

    private suspend fun applyAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendTdlibParameters()

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                // QR is the primary path on a TV: typing a phone number with a
                // remote is painful, so offer the code by default.
                _authState.value = AuthState.WaitPhoneNumber
                runCatching { requestQrLogin() }
            }

            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                _authState.value = AuthState.WaitQrCode(state.link.orEmpty())

            is TdApi.AuthorizationStateWaitCode ->
                _authState.value = AuthState.WaitCode(pendingPhoneNumber)

            is TdApi.AuthorizationStateWaitPassword ->
                _authState.value = AuthState.WaitPassword(state.passwordHint?.takeIf(String::isNotBlank))

            is TdApi.AuthorizationStateWaitRegistration ->
                _authState.value = AuthState.Failed(
                    "This account is not registered yet. Finish signing up in the phone app first.",
                )

            is TdApi.AuthorizationStateWaitEmailAddress, is TdApi.AuthorizationStateWaitEmailCode ->
                _authState.value = AuthState.Failed(
                    "This account needs e-mail verification, which is not supported on TV. " +
                        "Sign in on your phone first, then link this device with a QR code.",
                )

            is TdApi.AuthorizationStateReady -> _authState.value = AuthState.Ready
            is TdApi.AuthorizationStateLoggingOut -> _authState.value = AuthState.LoggingOut
            is TdApi.AuthorizationStateClosed -> {
                _authState.value = AuthState.Closed
                pumpStarted = false
            }

            else -> Unit
        }
    }

    private suspend fun sendTdlibParameters() {
        val credentials = this.credentials
        if (!credentials.isUsable) {
            _authState.value = AuthState.NeedsCredentials
            return
        }
        // Database stays in internal storage (small, must survive); downloaded
        // media goes to app-external storage so a TV box with adoptable or
        // large storage can actually hold a few films.
        val databaseDir = File(context.filesDir, "tdlib").apply { mkdirs() }
        val filesDir = (context.getExternalFilesDir(null) ?: context.filesDir)
            .let { File(it, "tdlib-files") }
            .apply { mkdirs() }

        try {
            connection.send<TdApi.Ok>(
                TdApi.SetTdlibParameters().apply {
                    useTestDc = false
                    databaseDirectory = databaseDir.absolutePath
                    filesDirectory = filesDir.absolutePath
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = credentials.apiId
                    apiHash = credentials.apiHash
                    systemLanguageCode = java.util.Locale.getDefault().language.ifBlank { "en" }
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                    systemVersion = "Android ${Build.VERSION.RELEASE}"
                    applicationVersion = APP_VERSION
                },
            )
        } catch (e: TelegramException) {
            _authState.value = AuthState.Failed(e.message ?: "setTdlibParameters failed")
        }
    }

    // --- helpers ----------------------------------------------------------

    private fun chatQuery(chatId: Long): TdApi.Function<*> =
        TdApi.GetChat().apply { this.chatId = chatId }

    private fun isBot(userId: Long): Boolean = users[userId]?.type is TdApi.UserTypeBot

    /**
     * Private chats only tell us a user id; without the user record we cannot
     * tell a bot from a person, which is exactly the split the source picker
     * is built around.
     */
    private suspend fun warmUpUsers(chats: Collection<TdApi.Chat>) {
        chats.asSequence()
            .mapNotNull { (it.type as? TdApi.ChatTypePrivate)?.userId }
            // containsKey, not `in`: on a ConcurrentHashMap `in` resolves to
            // containsValue, which would compare a user id against User objects
            // and so re-fetch every user on every load.
            .filter { !users.containsKey(it) }
            .distinct()
            .toList()
            .forEach { userId ->
                connection.sendOrNull<TdApi.User>(TdApi.GetUser().apply { this.userId = userId })
                    ?.let { users[it.id] = it }
            }
    }

    private suspend fun loadChatIds(list: TdApi.ChatList, limit: Int): List<Long> {
        // LoadChats pulls the next slice from the server and answers 404 once
        // the list is fully loaded; GetChats then reads it back in order.
        var loaded = connection.sendOrNull<TdApi.Chats>(
            TdApi.GetChats().apply { chatList = list; this.limit = limit },
        )
        var guard = 0
        while ((loaded?.chatIds?.size ?: 0) < limit && guard++ < MAX_CHAT_LOAD_ROUNDS) {
            val more = runCatching {
                connection.send<TdApi.Ok>(
                    TdApi.LoadChats().apply { chatList = list; this.limit = limit },
                )
            }
            if (more.isFailure) break // 404: nothing left to load
            loaded = connection.sendOrNull(
                TdApi.GetChats().apply { chatList = list; this.limit = limit },
            )
        }
        return loaded?.chatIds?.toList().orEmpty()
    }

    private suspend fun senderNameOf(message: TdApi.Message): String =
        when (val sender = message.senderId) {
            is TdApi.MessageSenderUser -> {
                val user = users[sender.userId]
                    ?: connection.sendOrNull<TdApi.User>(TdApi.GetUser().apply { userId = sender.userId })
                        ?.also { users[it.id] = it }
                listOfNotNull(user?.firstName, user?.lastName)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                    .ifBlank { "#${sender.userId}" }
            }

            is TdApi.MessageSenderChat -> chatCache[sender.chatId]?.title.orEmpty()
                .ifBlank { "#${sender.chatId}" }

            else -> ""
        }

    /**
     * TDLib 1.8.28 replaced ChatFolderInfo.title (a String) with a structured
     * `name`. Reading it reflectively keeps this module compiling against
     * whichever build scripts/fetch-tdlib.sh happened to download.
     */
    private fun folderTitle(info: Any): String {
        val fields = info.javaClass
        readField(fields, info, "title")?.let { value -> unwrapText(value)?.let { return it } }
        readField(fields, info, "name")?.let { value -> unwrapText(value)?.let { return it } }
        return ""
    }

    private fun readField(type: Class<*>, target: Any, name: String): Any? =
        runCatching { type.getField(name).get(target) }.getOrNull()

    private fun unwrapText(value: Any?): String? = when (value) {
        null -> null
        is String -> value.takeIf(String::isNotBlank)
        else -> unwrapText(readField(value.javaClass, value, "text"))
    }

    private companion object {
        const val APP_VERSION = "TVGram 0.1.0"
        const val MAX_CHAT_LOAD_ROUNDS = 8
        const val FOLDER_SYNC_TIMEOUT_MS = 4_000L

        /** What a listener with no camera and no microphone looks like on the wire. */
        const val LISTENER_PAYLOAD =
            """{"fingerprints":[],"pwd":"","ssrc":%d,"ssrc-groups":[],"ufrag":""}"""
    }
}
