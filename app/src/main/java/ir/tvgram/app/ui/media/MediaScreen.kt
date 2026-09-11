package ir.tvgram.app.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.tvgram.app.R
import ir.tvgram.app.settings.AppLanguage
import ir.tvgram.app.ui.common.CenteredMessage
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.telegram.model.MediaKind
import ir.tvgram.telegram.model.TgMediaItem
import java.util.Locale

/**
 * The screen the app opens on: categories across the top, the source picker
 * pinned to the top-right corner, and the chat's media filling everything else,
 * appearing tile by tile as it loads.
 */
@Composable
fun MediaScreen(
    onPlay: (TgMediaItem) -> Unit,
    onShowPhoto: (TgMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val feed by viewModel.feedState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var pickerOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    val persianDates = remember(settings.language) {
        when (settings.language) {
            AppLanguage.PERSIAN -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> Locale.getDefault().language == "fa"
        }
    }

    // Fetch the next page a row early, so scrolling never stops at a spinner.
    LaunchedEffect(gridState, settings.gridColumns) {
        snapshotFlow {
            val info = gridState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
        }.collect { (total, lastVisible) ->
            if (total > 0 && lastVisible >= total - settings.gridColumns * 2) {
                viewModel.loadMore()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tabs sit physically left and the source picker physically right in
            // both writing directions — the layout the TV remote's shape implies.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TvGramDimens.ScreenPaddingHorizontal,
                        end = TvGramDimens.ScreenPaddingHorizontal,
                        top = TvGramDimens.ScreenPaddingVertical,
                        bottom = 12.dp,
                    ),
            ) {
                CategoryTabs(
                    selected = category,
                    onSelect = viewModel::selectCategory,
                    modifier = Modifier.align(AbsoluteAlignment.CenterLeft),
                )
                SourceButton(
                    source = source,
                    onClick = { pickerOpen = true },
                    modifier = Modifier.align(AbsoluteAlignment.CenterRight),
                )
            }

            if (feed.isLoading) {
                LoadingBar(
                    modifier = Modifier.padding(horizontal = TvGramDimens.ScreenPaddingHorizontal),
                )
            }

            when {
                source.selectedChat == null -> CenteredMessage(
                    text = stringResource(R.string.source_none),
                    actionLabel = stringResource(R.string.source_pick_chat),
                    onAction = { pickerOpen = true },
                )

                feed.error != null -> CenteredMessage(
                    text = stringResource(R.string.error_generic, feed.error.orEmpty()),
                    actionLabel = stringResource(R.string.media_retry),
                    onAction = viewModel::retry,
                )

                feed.isInitialLoad -> CenteredMessage(text = stringResource(R.string.media_loading))

                feed.isEmpty -> CenteredMessage(text = stringResource(R.string.media_empty))

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.gridColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = TvGramDimens.ScreenPaddingHorizontal,
                        end = TvGramDimens.ScreenPaddingHorizontal,
                        bottom = TvGramDimens.ScreenPaddingVertical,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TvGramDimens.GridSpacing),
                    verticalArrangement = Arrangement.spacedBy(TvGramDimens.GridSpacing),
                ) {
                    items(feed.items, key = { it.uid }) { item ->
                        MediaCard(
                            item = item,
                            persianDates = persianDates,
                            onClick = {
                                viewModel.openItem(item)
                                if (item.kind == MediaKind.PHOTO) onShowPhoto(item) else onPlay(item)
                            },
                        )
                    }
                }
            }
        }

        if (pickerOpen) {
            SourcePanel(
                source = source,
                onFolderSelected = viewModel::selectFolder,
                onChatSelected = viewModel::selectChat,
                onDismiss = { pickerOpen = false },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
