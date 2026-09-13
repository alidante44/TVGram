package ir.tvgram.app.ui.media

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.tvgram.app.R
import ir.tvgram.app.settings.AppLanguage
import ir.tvgram.app.ui.common.CenteredMessage
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.telegram.model.TgMediaItem
import java.util.Locale

/**
 * The screen the app opens on: categories across the top, the source picker
 * pinned to the top-right corner, and the chat's media filling everything else,
 * appearing tile by tile as it loads.
 */
@Composable
fun MediaScreen(
    onOpen: (TgMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val feed by viewModel.feedState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mediaQuery by viewModel.mediaQuery.collectAsStateWithLifecycle()

    var pickerOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    val persianDates = remember(settings.language) {
        when (settings.language) {
            AppLanguage.PERSIAN -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> Locale.getDefault().language == "fa"
        }
    }

    // Switching chat or category replaces the feed; without this the grid would
    // stay scrolled where the previous chat left it.
    LaunchedEffect(source.selectedChat?.id, category) {
        gridState.scrollToItem(0)
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
            // One row, not a Box with two aligned children: aligning them to
            // opposite edges let the search box and the chat picker sit on top
            // of the tabs as soon as the three together were wider than the
            // screen, which is exactly what happened — "videos" and "photos"
            // vanished behind the picker. A row cannot overlap.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TvGramDimens.ScreenPaddingHorizontal,
                        end = TvGramDimens.ScreenPaddingHorizontal,
                        top = TvGramDimens.ScreenPaddingVertical,
                        bottom = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The tabs take whatever is left and scroll sideways if even
                // that is not enough, so every category stays reachable.
                CategoryTabs(
                    selected = category,
                    onSelect = viewModel::selectCategory,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                )
                TvTextField(
                    value = mediaQuery,
                    onValueChange = viewModel::searchMedia,
                    placeholder = stringResource(R.string.media_search_hint),
                    modifier = Modifier.width(200.dp),
                )
                SourceButton(
                    source = source,
                    onClick = { pickerOpen = true },
                    modifier = Modifier.widthIn(max = 240.dp),
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
                                onOpen(item)
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
                onSearch = viewModel::searchChats,
                onDismiss = { pickerOpen = false },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
