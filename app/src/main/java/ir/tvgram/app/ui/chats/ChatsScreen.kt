package ir.tvgram.app.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.settings.AppLanguage
import ir.tvgram.app.ui.common.CenteredMessage
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.common.TelegramImage
import ir.tvgram.app.ui.common.TvButton
import ir.tvgram.app.ui.common.TvIcon
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.live.LiveOverlay
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.app.util.Format
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgChat
import ir.tvgram.telegram.model.TgLiveStream
import ir.tvgram.telegram.model.TgMediaItem
import ir.tvgram.telegram.model.TgMessage
import java.util.Locale

/**
 * The text side of Telegram: chats on one edge, the conversation beside them.
 *
 * Media inside a message is a focusable chip rather than an inline preview —
 * on a TV the useful action is "play this", not "look at a thumbnail".
 */
@Composable
fun ChatsScreen(
    onOpenMedia: (TgMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var watching by remember { mutableStateOf<TgLiveStream?>(null) }

    val persianDates = remember(settings.language) {
        when (settings.language) {
            AppLanguage.PERSIAN -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> Locale.getDefault().language == "fa"
        }
    }

    LaunchedEffect(listState, state.selectedChat?.id) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
        }.collect { (total, lastVisible) ->
            if (total > 0 && lastVisible >= total - 5) viewModel.loadMore()
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        ChatList(
            chats = state.visibleChats,
            selectedId = state.selectedChat?.id,
            isLoading = state.isLoadingChats,
            query = state.query,
            onSearch = viewModel::search,
            onSelect = viewModel::selectChat,
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(TvGramColors.Surface),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            state.liveStream?.let { live ->
                LiveBanner(stream = live, onWatch = { watching = live })
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.selectedChat == null -> CenteredMessage(text = stringResource(R.string.chats_empty))

                messages.error != null -> CenteredMessage(
                    text = stringResource(R.string.error_generic, messages.error.orEmpty()),
                    actionLabel = stringResource(R.string.media_retry),
                    onAction = viewModel::retry,
                )

                messages.isInitialLoad -> CenteredMessage(text = stringResource(R.string.media_loading))

                messages.isEmpty -> CenteredMessage(text = stringResource(R.string.chats_messages_empty))

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = TvGramDimens.ScreenPaddingHorizontal,
                        vertical = TvGramDimens.ScreenPaddingVertical,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // Newest at the bottom reads like a conversation, but the
                    // feed arrives newest-first, so the list is reversed.
                    reverseLayout = true,
                ) {
                    items(messages.messages, key = { it.id }) { message ->
                        MessageRow(
                            message = message,
                            persianDates = persianDates,
                            onOpenMedia = { item ->
                                viewModel.openMedia(item)
                                onOpenMedia(item)
                            },
                        )
                    }
                }
            }
            }
        }
    }

    watching?.let { live ->
        LiveOverlay(stream = live, onClose = { watching = null })
    }
}

/**
 * Says that the chat is broadcasting right now, and opens it.
 *
 * Watching means joining the group call as a listener first — Telegram serves
 * stream segments to participants only. Whether the server accepts a listener
 * that was not introduced by tgcalls is settled when the button is pressed, so
 * the failure, if it comes, is shown there rather than guessed at here.
 */
@Composable
private fun LiveBanner(stream: TgLiveStream, onWatch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvGramDimens.ScreenPaddingHorizontal, vertical = 12.dp)
            .background(TvGramColors.SurfaceElevated, RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .background(TvGramColors.Danger, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.live_badge),
                style = MaterialTheme.typography.labelSmall,
                color = TvGramColors.OnBackground,
            )
        }
        Column {
            Text(
                text = stream.title.ifBlank { stringResource(R.string.live_title) },
                style = MaterialTheme.typography.bodyLarge,
                color = TvGramColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.live_watching, stream.participantCount),
                style = MaterialTheme.typography.labelSmall,
                color = TvGramColors.OnBackgroundMuted,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TvButton(
            text = stringResource(R.string.live_watch),
            leadingIcon = Icons.Filled.PlayArrow,
            onClick = onWatch,
        )
    }
}

@Composable
private fun ChatList(
    chats: List<TgChat>,
    selectedId: Long?,
    isLoading: Boolean,
    query: String,
    onSearch: (String) -> Unit,
    onSelect: (TgChat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = TvGramDimens.ScreenPaddingVertical)) {
        TvTextField(
            value = query,
            onValueChange = onSearch,
            placeholder = stringResource(R.string.chats_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (isLoading) LoadingBar(modifier = Modifier.padding(horizontal = 16.dp))
        if (chats.isEmpty() && query.isNotBlank()) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = TvGramColors.OnBackgroundMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(chats, key = { it.id }) { chat ->
                FocusableSurface(
                    onClick = { onSelect(chat) },
                    modifier = Modifier.fillMaxWidth(),
                    selected = chat.id == selectedId,
                    shape = RoundedCornerShape(8.dp),
                    focusScale = 1f,
                    background = Color.Transparent,
                    focusedBackground = TvGramColors.Accent,
                ) { focused ->
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val contentColor =
                            if (focused) TvGramColors.Background else TvGramColors.OnBackground
                        TelegramImage(
                            fileId = chat.photoFileId,
                            minithumbnail = chat.minithumbnail,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .background(TvGramColors.SurfaceElevated, RoundedCornerShape(20.dp)),
                        )
                        Column {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            chat.lastMessagePreview?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (focused) {
                                        TvGramColors.Background
                                    } else {
                                        TvGramColors.OnBackgroundMuted
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: TgMessage,
    persianDates: Boolean,
    onOpenMedia: (TgMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (message.isOutgoing) TvGramColors.AccentMuted else TvGramColors.Surface,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = TvGramColors.Accent,
            )
            Text(
                text = "${Format.date(message.date, persianDates)} ${Format.time(message.date)}",
                style = MaterialTheme.typography.labelSmall,
                color = TvGramColors.OnBackgroundMuted,
            )
        }

        message.media?.let { media ->
            FocusableSurface(
                onClick = { onOpenMedia(media) },
                shape = RoundedCornerShape(8.dp),
                focusScale = 1.02f,
                background = TvGramColors.SurfaceElevated,
                focusedBackground = TvGramColors.Accent,
            ) { focused ->
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val contentColor =
                        if (focused) TvGramColors.Background else TvGramColors.OnBackground
                    TvIcon(Icons.Filled.PlayArrow, null, tint = contentColor, size = 20.dp)
                    Text(
                        text = media.title.ifBlank { stringResource(media.kind.labelRes()) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (media.durationSeconds > 0) {
                        Text(
                            text = Format.duration(media.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                        )
                    }
                }
            }
        }

        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TvGramColors.OnBackground,
            )
        } else if (message.media == null && message.isService) {
            Text(
                text = stringResource(R.string.chats_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = TvGramColors.OnBackgroundMuted,
            )
        }
    }
}

private fun MediaKind.labelRes(): Int = when (this) {
    MediaKind.VIDEO -> R.string.chats_attachment_video
    MediaKind.PHOTO -> R.string.chats_attachment_photo
    MediaKind.AUDIO -> R.string.chats_attachment_audio
    MediaKind.VOICE -> R.string.chats_attachment_voice
    MediaKind.DOCUMENT -> R.string.chats_attachment_document
    MediaKind.ANIMATION -> R.string.chats_attachment_animation
}
