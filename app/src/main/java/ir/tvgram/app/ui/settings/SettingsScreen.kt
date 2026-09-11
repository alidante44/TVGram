package ir.tvgram.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ir.tvgram.app.BuildConfig
import ir.tvgram.app.R
import ir.tvgram.app.settings.AppLanguage
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.app.settings.RailSide
import ir.tvgram.app.ui.common.FocusableSurface
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.media.displayTitle
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.app.util.Format

/**
 * Two sections, exactly as asked: what TVGram does with your Telegram account,
 * and how it behaves as an Android TV app.
 *
 * Every row is a single focusable control that cycles through its choices on
 * click — no nested dialogs, because a dialog is one more thing to escape from
 * with a remote.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = TvGramDimens.ScreenPaddingHorizontal,
            vertical = TvGramDimens.ScreenPaddingVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.isBusy) {
            item { LoadingBar() }
        }

        // --- Telegram -----------------------------------------------------
        item { SectionHeader(stringResource(R.string.settings_section_telegram)) }

        item {
            SettingRow(
                title = stringResource(R.string.settings_account),
                value = uiState.user?.let { user ->
                    listOfNotNull(user.displayName, user.phoneNumber)
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                }.orEmpty(),
                onClick = viewModel::refresh,
            )
        }

        item {
            val folders = uiState.folders
            val current = folders.firstOrNull { it.id == settings.defaultFolderId }
            SettingRow(
                title = stringResource(R.string.settings_default_folder),
                value = current?.displayTitle().orEmpty(),
                onClick = {
                    val next = folders.cycleAfter { it.id == settings.defaultFolderId }
                    if (next != null) viewModel.update { it.copy(defaultFolderId = next.id) }
                },
            )
        }

        item {
            ToggleRow(
                title = stringResource(R.string.settings_include_archived),
                checked = settings.includeArchived,
                onToggle = { viewModel.update { s -> s.copy(includeArchived = !s.includeArchived) } },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_cache_limit),
                value = Format.fileSize(settings.cacheLimitBytes),
                onClick = {
                    val next = AppSettings.CACHE_LIMIT_CHOICES.cycleAfter { it == settings.cacheLimitBytes }
                    if (next != null) viewModel.update { it.copy(cacheLimitBytes = next) }
                },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_clear_cache),
                value = stringResource(R.string.settings_cache_size, Format.fileSize(uiState.cacheBytes)),
                onClick = viewModel::clearCache,
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_download_priority),
                value = settings.downloadPriority.toString(),
                onClick = {
                    val next = when (settings.downloadPriority) {
                        1 -> 8
                        8 -> 16
                        16 -> 32
                        else -> 1
                    }
                    viewModel.update { it.copy(downloadPriority = next) }
                },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_index_depth),
                value = settings.indexDepth.toString(),
                onClick = {
                    val next = AppSettings.INDEX_DEPTH_CHOICES.cycleAfter { it == settings.indexDepth }
                    if (next != null) viewModel.update { it.copy(indexDepth = next) }
                },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_logout),
                value = stringResource(R.string.settings_logout_confirm),
                destructive = true,
                onClick = viewModel::logOut,
            )
        }

        // --- Android TV ---------------------------------------------------
        item { SectionHeader(stringResource(R.string.settings_section_tv)) }

        item {
            SettingRow(
                title = stringResource(R.string.settings_language),
                value = stringResource(settings.language.labelRes()),
                footnote = stringResource(R.string.settings_restart_needed),
                onClick = {
                    val next = AppLanguage.entries.cycleAfter { it == settings.language }
                    if (next != null) viewModel.update { it.copy(language = next) }
                },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_rail_side),
                value = stringResource(settings.railSide.labelRes()),
                onClick = {
                    val next = RailSide.entries.cycleAfter { it == settings.railSide }
                    if (next != null) viewModel.update { it.copy(railSide = next) }
                },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_grid_columns),
                value = settings.gridColumns.toString(),
                onClick = {
                    val next = AppSettings.GRID_COLUMN_CHOICES.cycleAfter { it == settings.gridColumns }
                    if (next != null) viewModel.update { it.copy(gridColumns = next) }
                },
            )
        }

        item {
            ToggleRow(
                title = stringResource(R.string.settings_autoplay_next),
                checked = settings.autoplayNext,
                onToggle = { viewModel.update { s -> s.copy(autoplayNext = !s.autoplayNext) } },
            )
        }

        item {
            ToggleRow(
                title = stringResource(R.string.settings_resume_playback),
                checked = settings.resumePlayback,
                onToggle = { viewModel.update { s -> s.copy(resumePlayback = !s.resumePlayback) } },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_seek_step),
                value = stringResource(R.string.settings_seek_step_value, settings.seekStepSeconds),
                onClick = {
                    val next = AppSettings.SEEK_STEP_CHOICES.cycleAfter { it == settings.seekStepSeconds }
                    if (next != null) viewModel.update { it.copy(seekStepSeconds = next) }
                },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_preferred_audio),
                value = settings.preferredAudioLanguage.ifBlank { "—" },
                onClick = {
                    val next = TRACK_LANGUAGES.cycleAfter { it == settings.preferredAudioLanguage }
                    if (next != null) viewModel.update { it.copy(preferredAudioLanguage = next) }
                },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_preferred_subtitle),
                value = settings.preferredSubtitleLanguage.ifBlank { stringResource(R.string.player_subtitle_off) },
                onClick = {
                    val next = TRACK_LANGUAGES.cycleAfter { it == settings.preferredSubtitleLanguage }
                    if (next != null) viewModel.update { it.copy(preferredSubtitleLanguage = next) }
                },
            )
        }

        item {
            ToggleRow(
                title = stringResource(R.string.settings_hardware_decoding),
                checked = settings.hardwareDecoding,
                onToggle = { viewModel.update { s -> s.copy(hardwareDecoding = !s.hardwareDecoding) } },
            )
        }

        item {
            ToggleRow(
                title = stringResource(R.string.settings_keep_screen_on),
                checked = settings.keepScreenOn,
                onToggle = { viewModel.update { s -> s.copy(keepScreenOn = !s.keepScreenOn) } },
            )
        }

        item {
            ToggleRow(
                title = stringResource(R.string.settings_match_frame_rate),
                checked = settings.matchFrameRate,
                onToggle = { viewModel.update { s -> s.copy(matchFrameRate = !s.matchFrameRate) } },
            )
        }

        item {
            SettingRow(
                title = stringResource(R.string.settings_about),
                value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                onClick = {},
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = TvGramColors.Accent,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    footnote: String? = null,
    destructive: Boolean = false,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        focusScale = 1f,
        background = TvGramColors.Surface,
        focusedBackground = if (destructive) TvGramColors.Danger else TvGramColors.Accent,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val contentColor = if (focused) TvGramColors.Background else TvGramColors.OnBackground
            Column(modifier = Modifier.fillMaxWidth(0.5f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                footnote?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    FocusableSurface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        focusScale = 1f,
        background = TvGramColors.Surface,
        focusedBackground = TvGramColors.Accent,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val contentColor = if (focused) TvGramColors.Background else TvGramColors.OnBackground
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Box(
                modifier = Modifier
                    .background(
                        if (checked) TvGramColors.Accent else TvGramColors.SurfaceElevated,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (checked) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (checked) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                )
            }
        }
    }
}

/** Steps to the next choice, wrapping around — the whole control is one click. */
private fun <T> List<T>.cycleAfter(isCurrent: (T) -> Boolean): T? {
    if (isEmpty()) return null
    val index = indexOfFirst(isCurrent)
    return this[(index + 1).mod(size)]
}

private val TRACK_LANGUAGES = listOf("", "fa", "en", "ar", "tr")

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.PERSIAN -> R.string.settings_language_fa
    AppLanguage.ENGLISH -> R.string.settings_language_en
}

private fun RailSide.labelRes(): Int = when (this) {
    RailSide.START -> R.string.settings_rail_start
    RailSide.LEFT -> R.string.settings_rail_left
    RailSide.RIGHT -> R.string.settings_rail_right
}
