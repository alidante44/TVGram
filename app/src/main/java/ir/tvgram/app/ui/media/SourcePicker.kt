package ir.tvgram.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.common.TelegramImage
import ir.tvgram.app.ui.common.TvIcon
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.telegram.model.BuiltInFolder
import ir.tvgram.telegram.model.ChatKind
import ir.tvgram.telegram.model.TgChat
import ir.tvgram.telegram.model.TgFolder

/**
 * The button in the top-right corner that opens the source list.
 *
 * It drops a panel that scrolls one row at a time under the D-pad: folders
 * first, then the chats inside the chosen folder, with BACK stepping back up a
 * level rather than leaving the screen.
 */
@Composable
fun SourceButton(
    source: SourceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        focusScale = 1.03f,
        background = TvGramColors.Surface,
        focusedBackground = TvGramColors.Accent,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val contentColor = if (focused) TvGramColors.Background else TvGramColors.OnBackground
            TvIcon(Icons.Filled.Folder, null, tint = contentColor, size = 20.dp)
            Column {
                Text(
                    text = source.selectedChat?.title
                        ?: stringResource(R.string.source_none),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                source.selectedFolder?.let { folder ->
                    Text(
                        text = folder.displayTitle(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                        maxLines = 1,
                    )
                }
            }
            TvIcon(Icons.Filled.ArrowDropDown, null, tint = contentColor, size = 20.dp)
        }
    }
}

@Composable
fun SourcePanel(
    source: SourceState,
    onFolderSelected: (TgFolder) -> Unit,
    onChatSelected: (TgChat) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opening the panel always starts at the folder level; picking a folder
    // slides down into its chats.
    var showingChats by remember { mutableStateOf(source.selectedFolder != null) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(showingChats) {
        listState.scrollToItem(0)
        runCatching { focusRequester.requestFocus() }
    }

    // A dialog owns its own window, and that is what keeps the D-pad inside the
    // list. As a plain Box the focus escaped into the media grid behind, so the
    // second time the picker was opened it scrolled the grid instead of the chats.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TvGramColors.Scrim),
            contentAlignment = Alignment.TopEnd,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(460.dp)
                    .background(TvGramColors.Surface, RoundedCornerShape(14.dp))
                    .padding(vertical = 16.dp)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                if (showingChats) showingChats = false else onDismiss()
                                true
                            }

                            else -> false
                        }
                    },
            ) {
                Text(
                    text = stringResource(
                        if (showingChats) R.string.source_pick_chat else R.string.source_pick_folder,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = TvGramColors.OnBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                if (source.isLoadingChats && showingChats) {
                    LoadingBar(modifier = Modifier.padding(horizontal = 20.dp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 520.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!showingChats) {
                        items(source.folders, key = { it.id }) { folder ->
                            SourceRow(
                                title = folder.displayTitle(),
                                selected = folder.id == source.selectedFolder?.id,
                                modifier = if (folder.id == source.selectedFolder?.id) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                                onClick = {
                                    onFolderSelected(folder)
                                    showingChats = true
                                },
                            )
                        }
                    } else {
                        item {
                            SourceRow(
                                title = stringResource(R.string.source_back_to_folders),
                                selected = false,
                                leading = { tint ->
                                    TvIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = tint, size = 20.dp)
                                },
                                onClick = { showingChats = false },
                            )
                        }
                        if (source.chats.isEmpty() && !source.isLoadingChats) {
                            item {
                                Text(
                                    text = stringResource(R.string.source_empty_folder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TvGramColors.OnBackgroundMuted,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        }
                        items(source.chats, key = { it.id }) { chat ->
                            SourceRow(
                                title = chat.title,
                                subtitle = chat.lastMessagePreview,
                                selected = chat.id == source.selectedChat?.id,
                                modifier = if (chat.id == source.selectedChat?.id) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                                leading = {
                                    TelegramImage(
                                        fileId = chat.photoFileId,
                                        minithumbnail = chat.minithumbnail,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(TvGramColors.SurfaceElevated, RoundedCornerShape(18.dp)),
                                    )
                                },
                                onClick = {
                                    onChatSelected(chat)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    leading: (@Composable (tint: androidx.compose.ui.graphics.Color) -> Unit)? = null,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = selected,
        shape = RoundedCornerShape(8.dp),
        focusScale = 1.0f,
        background = androidx.compose.ui.graphics.Color.Transparent,
        focusedBackground = TvGramColors.Accent,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val contentColor = if (focused) TvGramColors.Background else TvGramColors.OnBackground
            leading?.invoke(contentColor)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun TgFolder.displayTitle(): String = when (builtIn) {
    BuiltInFolder.ALL -> stringResource(R.string.folder_all)
    BuiltInFolder.PERSONAL -> stringResource(R.string.folder_personal)
    BuiltInFolder.BOTS -> stringResource(R.string.folder_bots)
    BuiltInFolder.GROUPS -> stringResource(R.string.folder_groups)
    BuiltInFolder.CHANNELS -> stringResource(R.string.folder_channels)
    BuiltInFolder.ARCHIVED -> stringResource(R.string.folder_archived)
    null -> title
}

/** Used by the chat list to show what kind of chat a row is. */
fun ChatKind.isConversation(): Boolean = this == ChatKind.PRIVATE || this == ChatKind.BOT
