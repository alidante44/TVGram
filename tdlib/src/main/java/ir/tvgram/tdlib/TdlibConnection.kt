package ir.tvgram.tdlib

import ir.tvgram.telegram.model.TelegramException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Thin coroutine bridge over TDLib's callback API.
 *
 * TDLib delivers everything — query results and unsolicited updates alike — on
 * its own handler threads. This class turns query results into suspending calls
 * and every update into a hot [SharedFlow] that repositories observe.
 */
class TdlibConnection {

    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 0,
        // TDLib bursts thousands of updates while it syncs; a generous buffer
        // keeps its handler thread from being blocked by a slow collector.
        extraBufferCapacity = 2048,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    @Volatile
    private var client: Client? = null

    fun open() {
        if (client != null) return
        loadNativeLibrary()
        runCatching {
            Client.execute(TdApi.SetLogVerbosityLevel().apply { newVerbosityLevel = LOG_VERBOSITY })
        }
        client = Client.create(
            /* updateHandler = */ { update -> _updates.tryEmit(update) },
            /* updateExceptionHandler = */ { it.printStackTrace() },
            /* defaultExceptionHandler = */ { it.printStackTrace() },
        )
    }

    fun close() {
        val current = client ?: return
        client = null
        runCatching { current.send(TdApi.Close(), {}) }
    }

    /**
     * Throws away the closed client and starts a fresh one.
     *
     * A TDLib client that has reached the closed state is finished — it answers
     * nothing further, and the only way on is a new instance. That is what
     * happens after a log out, and without this the app sat on the "please
     * wait" screen forever with a dead client behind it.
     *
     * The update flow belongs to this object rather than to the client, so the
     * pump collecting it keeps working across the swap and must not be
     * restarted.
     */
    fun reopen() {
        client = null
        open()
    }

    /**
     * Sends [query] and suspends until TDLib answers.
     *
     * @throws TelegramException when TDLib replies with an error.
     */
    suspend fun <T : TdApi.Object> send(query: TdApi.Function<*>): T =
        suspendCancellableCoroutine { continuation ->
            val current = client
            if (current == null) {
                continuation.resumeWithException(
                    TelegramException(CLIENT_CLOSED, "TDLib client is not running"),
                )
                return@suspendCancellableCoroutine
            }
            current.send(query) { result ->
                if (result is TdApi.Error) {
                    continuation.resumeWithException(TelegramException(result.code, result.message))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(result as T)
                }
            }
        }

    /** Like [send] but returns null instead of throwing; for optional lookups. */
    suspend fun <T : TdApi.Object> sendOrNull(query: TdApi.Function<*>): T? =
        runCatching { send<T>(query) }.getOrNull()

    private fun loadNativeLibrary() {
        if (nativeLoaded) return
        // Telegram's AAR ships libtdjni.so for every Android ABI. Failing here
        // means the wrong ABI was packaged, which is worth surfacing loudly.
        System.loadLibrary("tdjni")
        nativeLoaded = true
    }

    private companion object {
        const val LOG_VERBOSITY = 1
        const val CLIENT_CLOSED = -1

        @Volatile
        var nativeLoaded = false
    }
}
