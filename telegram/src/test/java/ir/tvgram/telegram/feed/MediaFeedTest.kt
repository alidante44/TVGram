package ir.tvgram.telegram.feed

import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.AuthState
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
import ir.tvgram.telegram.model.TgUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaFeedTest {

    @Test
    fun `single category pages through until the source is exhausted`() = runTest {
        val client = StubClient(videos = item(MediaKind.VIDEO, count = 25, startDate = 1_000))
        val feed = MediaFeed(client, TestScope(testScheduler), CHAT_ID, MediaCategory.VIDEO, pageSize = 10)

        // Each page has to be awaited on its own: a second loadMore() while one
        // is in flight is deliberately dropped.
        feed.loadMore()
        testScheduler.advanceUntilIdle()
        assertEquals(10, feed.state.value.items.size)

        feed.loadMore()
        testScheduler.advanceUntilIdle()
        assertEquals(20, feed.state.value.items.size)

        feed.loadMore()
        testScheduler.advanceUntilIdle()
        assertEquals(25, feed.state.value.items.size)
        assertTrue(feed.state.value.endReached)
    }

    /**
     * The merged "All" tab is the part that can silently go wrong: it runs four
     * independent cursors, so an item must never appear before a newer one from
     * a different category — including across page boundaries.
     */
    @Test
    fun `all category merges the four sources in date order`() = runTest {
        val client = StubClient(
            videos = item(MediaKind.VIDEO, count = 15, startDate = 3_000, step = 3),
            photos = item(MediaKind.PHOTO, count = 15, startDate = 2_990, step = 3),
            audio = item(MediaKind.AUDIO, count = 15, startDate = 2_980, step = 3),
            documents = item(MediaKind.DOCUMENT, count = 15, startDate = 2_970, step = 3),
        )
        val feed = MediaFeed(client, TestScope(testScheduler), CHAT_ID, MediaCategory.ALL, pageSize = 8)

        repeat(10) {
            feed.loadMore()
            testScheduler.advanceUntilIdle()
        }

        val dates = feed.state.value.items.map { it.date }
        assertEquals(60, dates.size)
        assertEquals(dates.sortedDescending(), dates)
    }

    @Test
    fun `the same message is never emitted twice`() = runTest {
        val client = StubClient(videos = item(MediaKind.VIDEO, count = 12, startDate = 500))
        val feed = MediaFeed(client, TestScope(testScheduler), CHAT_ID, MediaCategory.VIDEO, pageSize = 5)

        repeat(5) {
            feed.loadMore()
            testScheduler.advanceUntilIdle()
        }

        val ids = feed.state.value.items.map { it.messageId }
        assertEquals(ids.distinct().size, ids.size)
    }

    @Test
    fun `a failing page surfaces the error and can be retried`() = runTest {
        val client = StubClient(videos = item(MediaKind.VIDEO, count = 5, startDate = 100), failFirst = true)
        val feed = MediaFeed(client, TestScope(testScheduler), CHAT_ID, MediaCategory.VIDEO, pageSize = 5)

        feed.loadMore()
        testScheduler.advanceUntilIdle()
        assertEquals("boom", feed.state.value.error)

        feed.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(null, feed.state.value.error)
        assertEquals(5, feed.state.value.items.size)
    }

    // --- helpers ----------------------------------------------------------

    private fun item(
        kind: MediaKind,
        count: Int,
        startDate: Int,
        step: Int = 1,
    ): List<TgMediaItem> = (0 until count).map { index ->
        TgMediaItem(
            chatId = CHAT_ID,
            messageId = kind.ordinal * 10_000L + (count - index),
            kind = kind,
            title = "${kind.name}-$index",
            date = startDate - index * step,
            fileId = kind.ordinal * 10_000 + index,
        )
    }

    private class StubClient(
        private val videos: List<TgMediaItem> = emptyList(),
        private val photos: List<TgMediaItem> = emptyList(),
        private val audio: List<TgMediaItem> = emptyList(),
        private val documents: List<TgMediaItem> = emptyList(),
        private val failFirst: Boolean = false,
    ) : TelegramClient {

        private var calls = 0

        override suspend fun mediaPage(
            chatId: Long,
            category: MediaCategory,
            fromMessageId: Long,
            limit: Int,
        ): MediaPage {
            if (failFirst && calls++ == 0) throw IllegalStateException("boom")

            val source = when (category) {
                MediaCategory.VIDEO -> videos
                MediaCategory.PHOTO -> photos
                MediaCategory.AUDIO -> audio
                else -> documents
            }
            val start = if (fromMessageId == 0L) {
                0
            } else {
                source.indexOfFirst { it.messageId == fromMessageId } + 1
            }
            if (start <= 0 && fromMessageId != 0L) return MediaPage(emptyList(), 0, false)

            val page = source.drop(start).take(limit)
            return MediaPage(page, page.lastOrNull()?.messageId ?: 0L, page.isNotEmpty())
        }

        // Nothing below is exercised by these tests.
        override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Ready)
        override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.READY)
        override suspend fun start(credentials: TelegramCredentials) = Unit
        override suspend fun requestQrLogin() = Unit
        override suspend fun submitPhoneNumber(phoneNumber: String) = Unit
        override suspend fun submitCode(code: String) = Unit
        override suspend fun submitPassword(password: String) = Unit
        override suspend fun logOut() = Unit
        override suspend fun currentUser(): TgUser? = null
        override suspend fun folders(): List<TgFolder> = emptyList()
        override suspend fun chats(folder: TgFolder, limit: Int): List<TgChat> = emptyList()
        override suspend fun chat(chatId: Long): TgChat? = null
        override suspend fun messagePage(chatId: Long, fromMessageId: Long, limit: Int) =
            MessagePage(emptyList(), 0, false)

        override suspend fun file(fileId: Int): TgFile = TgFile(fileId)
        override fun fileUpdates(fileId: Int): Flow<TgFile> = emptyFlow()
        override suspend fun downloadFile(
            fileId: Int,
            priority: Int,
            offset: Long,
            limit: Long,
            synchronous: Boolean,
        ): TgFile = TgFile(fileId)

        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long = 0
        override suspend fun cancelDownload(fileId: Int) = Unit
        override suspend fun downloadFully(fileId: Int, priority: Int): String = ""
        override suspend fun cacheSize(): Long = 0
        override suspend fun clearCache() = Unit
        override suspend fun setCacheLimit(bytes: Long) = Unit
    }

    private companion object {
        const val CHAT_ID = 42L
    }
}
