package ir.tvgram.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import ir.tvgram.app.ui.common.TvButton
import ir.tvgram.app.ui.common.TvIcon
import ir.tvgram.app.ui.common.TvSwitch
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.media.displayTitle
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.app.util.Format
import ir.tvgram.telegram.model.BuiltInFolder
import ir.tvgram.telegram.model.ProxyKind
import ir.tvgram.telegram.model.TgFolder
import ir.tvgram.telegram.model.TgProxy

/**
 * Two sections, exactly as asked: what TVGram does with your Telegram account,
 * and how it behaves as an Android TV app.
 *
 * Every row is a single focusable control that cycles through its choices on
 * click — no nested dialogs, because a dialog is one more thing to escape from
 * with a remote. The two exceptions are the passcode and a new proxy, which
 * need text typed in.
 *
 * The search box at the top filters the rows by name, so a setting can be found
 * without walking the whole list with the D-pad.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var editingPasscode by remember { mutableStateOf(false) }
    var addingProxy by remember { mutableStateOf(false) }

    // The open picker is held here rather than inside a row, because a row
    // scrolled out of the lazy list leaves the composition and would take its
    // dialog with it.
    var picker by remember { mutableStateOf<PickerRequest?>(null) }

    // Built every recomposition so each row reads the current value, then
    // filtered by the search box. Rows are described rather than emitted
    // because LazyListScope is not composable, and stringResource is.
    val rows = settingsRows(onOpenPicker = { picker = it }) {
        // --- Telegram -----------------------------------------------------
        section(stringResource(R.string.settings_section_telegram))

        val pending = stringResource(R.string.settings_value_pending)
        row(
            key = "account",
            title = stringResource(R.string.settings_account),
            value = uiState.user?.let { user ->
                listOfNotNull(user.displayName, user.phoneNumber)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
            }?.takeIf(String::isNotBlank)
                ?: if (uiState.isLoading) pending else stringResource(R.string.settings_value_retry),
            onClick = viewModel::refresh,
        )

        // Falling back to the built-in folders keeps this row usable while the
        // account's own folders are still on their way — before, an empty list
        // meant an empty label and a click that did nothing at all.
        val folders = uiState.folders.ifEmpty { BUILT_IN_FOLDERS }
        choice(
            key = "default-folder",
            title = stringResource(R.string.settings_default_folder),
            current = folders.firstOrNull { it.id == settings.defaultFolderId } ?: folders.first(),
            options = folders,
            label = { it.displayTitle() },
            onPick = { folder -> viewModel.update { it.copy(defaultFolderId = folder.id) } },
        )

        toggle(
            key = "archived",
            title = stringResource(R.string.settings_include_archived),
            checked = settings.includeArchived,
            onToggle = { viewModel.update { s -> s.copy(includeArchived = !s.includeArchived) } },
        )

        choice(
            key = "cache-limit",
            title = stringResource(R.string.settings_cache_limit),
            current = settings.cacheLimitBytes,
            options = AppSettings.CACHE_LIMIT_CHOICES,
            label = { Format.fileSize(it) },
            onPick = { bytes -> viewModel.update { it.copy(cacheLimitBytes = bytes) } },
        )

        row(
            key = "clear-cache",
            title = stringResource(R.string.settings_clear_cache),
            value = if (uiState.cacheBytes > 0) {
                stringResource(R.string.settings_cache_size, Format.fileSize(uiState.cacheBytes))
            } else if (uiState.isLoading) {
                pending
            } else {
                stringResource(R.string.settings_cache_size, Format.fileSize(0))
            },
            onClick = viewModel::clearCache,
        )

        choice(
            key = "download-priority",
            title = stringResource(R.string.settings_download_priority),
            current = settings.downloadPriority,
            options = DOWNLOAD_PRIORITIES,
            label = Int::toString,
            onPick = { priority -> viewModel.update { it.copy(downloadPriority = priority) } },
        )

        choice(
            key = "index-depth",
            title = stringResource(R.string.settings_index_depth),
            current = settings.indexDepth,
            options = AppSettings.INDEX_DEPTH_CHOICES,
            label = Int::toString,
            onPick = { depth -> viewModel.update { it.copy(indexDepth = depth) } },
        )

        // --- Proxy --------------------------------------------------------
        val proxyLabel = stringResource(R.string.settings_proxy)
        row(
            key = "proxy-add",
            title = stringResource(R.string.settings_proxy_add),
            value = uiState.activeProxy?.label ?: stringResource(R.string.settings_proxy_none),
            search = "$proxyLabel mtproto socks5 http",
            onClick = { addingProxy = true },
        )

        uiState.proxies.forEach { proxy ->
            entry(key = "proxy-${proxy.id}", search = "$proxyLabel ${proxy.label}") {
                ProxyRow(
                    proxy = proxy,
                    onSelect = { viewModel.enableProxy(proxy.id) },
                    onRemove = { viewModel.removeProxy(proxy.id) },
                )
            }
        }

        if (uiState.activeProxy != null) {
            row(
                key = "proxy-disable",
                title = stringResource(R.string.settings_proxy_disable),
                value = "",
                search = proxyLabel,
                onClick = viewModel::disableProxies,
            )
        }

        row(
            key = "logout",
            title = stringResource(R.string.settings_logout),
            value = stringResource(R.string.settings_logout_confirm),
            destructive = true,
            onClick = viewModel::logOut,
        )

        // --- Android TV ---------------------------------------------------
        section(stringResource(R.string.settings_section_tv))

        row(
            key = "passcode",
            title = stringResource(R.string.settings_passcode),
            value = stringResource(
                if (settings.isLocked) R.string.settings_passcode_on else R.string.settings_passcode_off,
            ),
            footnote = stringResource(R.string.settings_passcode_warning),
            onClick = { editingPasscode = true },
        )

        val languageLabels = AppLanguage.entries.associateWith { stringResource(it.labelRes()) }
        choice(
            key = "language",
            title = stringResource(R.string.settings_language),
            current = settings.language,
            options = AppLanguage.entries,
            label = { languageLabels[it].orEmpty() },
            footnote = stringResource(R.string.settings_restart_needed),
            onPick = { language -> viewModel.update { it.copy(language = language) } },
        )

        val railLabels = RailSide.entries.associateWith { stringResource(it.labelRes()) }
        choice(
            key = "rail-side",
            title = stringResource(R.string.settings_rail_side),
            current = settings.railSide,
            options = RailSide.entries,
            label = { railLabels[it].orEmpty() },
            onPick = { side -> viewModel.update { it.copy(railSide = side) } },
        )

        choice(
            key = "grid-columns",
            title = stringResource(R.string.settings_grid_columns),
            current = settings.gridColumns,
            options = AppSettings.GRID_COLUMN_CHOICES,
            label = Int::toString,
            onPick = { columns -> viewModel.update { it.copy(gridColumns = columns) } },
        )

        toggle(
            key = "autoplay",
            title = stringResource(R.string.settings_autoplay_next),
            checked = settings.autoplayNext,
            onToggle = { viewModel.update { s -> s.copy(autoplayNext = !s.autoplayNext) } },
        )

        toggle(
            key = "resume",
            title = stringResource(R.string.settings_resume_playback),
            checked = settings.resumePlayback,
            onToggle = { viewModel.update { s -> s.copy(resumePlayback = !s.resumePlayback) } },
        )

        val seekLabels = AppSettings.SEEK_STEP_CHOICES.associateWith {
            stringResource(R.string.settings_seek_step_value, it)
        }
        choice(
            key = "seek-step",
            title = stringResource(R.string.settings_seek_step),
            current = settings.seekStepSeconds,
            options = AppSettings.SEEK_STEP_CHOICES,
            label = { seekLabels[it].orEmpty() },
            onPick = { seconds -> viewModel.update { it.copy(seekStepSeconds = seconds) } },
        )

        val anyLanguage = stringResource(R.string.settings_track_any)
        choice(
            key = "audio-language",
            title = stringResource(R.string.settings_preferred_audio),
            current = settings.preferredAudioLanguage,
            options = TRACK_LANGUAGES,
            label = { it.ifBlank { anyLanguage } },
            onPick = { tag -> viewModel.update { it.copy(preferredAudioLanguage = tag) } },
        )

        val subtitlesOff = stringResource(R.string.player_subtitle_off)
        choice(
            key = "subtitle-language",
            title = stringResource(R.string.settings_preferred_subtitle),
            current = settings.preferredSubtitleLanguage,
            options = TRACK_LANGUAGES,
            label = { it.ifBlank { subtitlesOff } },
            onPick = { tag -> viewModel.update { it.copy(preferredSubtitleLanguage = tag) } },
        )

        toggle(
            key = "hardware-decoding",
            title = stringResource(R.string.settings_hardware_decoding),
            checked = settings.hardwareDecoding,
            onToggle = { viewModel.update { s -> s.copy(hardwareDecoding = !s.hardwareDecoding) } },
        )

        toggle(
            key = "keep-screen-on",
            title = stringResource(R.string.settings_keep_screen_on),
            checked = settings.keepScreenOn,
            onToggle = { viewModel.update { s -> s.copy(keepScreenOn = !s.keepScreenOn) } },
        )

        toggle(
            key = "match-frame-rate",
            title = stringResource(R.string.settings_match_frame_rate),
            checked = settings.matchFrameRate,
            onToggle = { viewModel.update { s -> s.copy(matchFrameRate = !s.matchFrameRate) } },
        )

        row(
            key = "about",
            title = stringResource(R.string.settings_about),
            value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            onClick = {},
        )
    }

    val visible = rows.filtered(query)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = TvGramDimens.ScreenPaddingHorizontal,
                end = TvGramDimens.ScreenPaddingHorizontal,
                top = TvGramDimens.ScreenPaddingVertical,
            )
            // A television is wide enough that a full-width row leaves the value
            // stranded an arm's length from its title.
            .widthIn(max = SETTINGS_CONTENT_WIDTH),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Outside the list on purpose: as the first item it scrolled away with
        // everything else and took the top of the first section heading with it.
        TvTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.settings_search_hint),
            modifier = Modifier.fillMaxWidth(0.5f),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = TvGramDimens.ScreenPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.isBusy || uiState.isLoading) {
                item(key = "busy") { LoadingBar() }
            }

            uiState.error?.let { failed ->
                item(key = "error") {
                    Text(
                        text = stringResource(R.string.settings_load_failed, failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = TvGramColors.Danger,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            if (visible.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvGramColors.OnBackgroundMuted,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }

            items(visible, key = { it.key }) { it.content() }
        }
    }

    picker?.let { request ->
        PickerDialog(request = request, onClose = { picker = null })
    }

    if (editingPasscode) {
        PasscodeDialog(
            onSave = { code ->
                viewModel.setPasscode(code)
                editingPasscode = false
            },
            onClose = { editingPasscode = false },
        )
    }

    if (addingProxy) {
        ProxyDialog(
            onSave = { proxy ->
                viewModel.addProxy(proxy)
                addingProxy = false
            },
            onClose = { addingProxy = false },
        )
    }
}

// --- row model ------------------------------------------------------------

/**
 * Collects the rows inside a composable scope, so each one can read string
 * resources and the current setting value as it is described.
 */
@Composable
private fun settingsRows(
    onOpenPicker: (PickerRequest) -> Unit,
    content: @Composable SettingsRows.() -> Unit,
): SettingsRows {
    val rows = SettingsRows(onOpenPicker)
    rows.content()
    return rows
}

/** A list of choices waiting to be shown, with what to do about each one. */
private class PickerRequest(val title: String, val options: List<PickerOption>)

private class PickerOption(val label: String, val selected: Boolean, val onPick: () -> Unit)

private class SettingsEntry(
    val key: String,
    val search: String,
    val isSection: Boolean,
    val content: @Composable () -> Unit,
)

/**
 * Collects the rows so they can be filtered before the lazy list emits them.
 * Each row keeps the text the search box matches against.
 */
private class SettingsRows(private val onOpenPicker: (PickerRequest) -> Unit) {
    private val rows = mutableListOf<SettingsEntry>()

    fun section(title: String) {
        rows += SettingsEntry("section-$title", title, isSection = true) { SectionHeader(title) }
    }

    fun row(
        key: String,
        title: String,
        value: String,
        onClick: () -> Unit,
        footnote: String? = null,
        destructive: Boolean = false,
        search: String = "",
    ) {
        rows += SettingsEntry(key, "$title $value $search", isSection = false) {
            SettingRow(title, value, onClick, footnote, destructive)
        }
    }

    /**
     * A setting with a fixed set of values, shown as a list to pick from.
     *
     * Rows used to step to the next value on every press. That hid what the
     * choices were, took several presses to reach the one you wanted, and wrote
     * to disk each time, so it felt slow and gave no sign of what it had done.
     * Opening a list costs nothing and writes once.
     */
    fun <T> choice(
        key: String,
        title: String,
        current: T,
        options: List<T>,
        label: (T) -> String,
        onPick: (T) -> Unit,
        footnote: String? = null,
    ) {
        if (options.isEmpty()) return
        val currentLabel = label(current)
        rows += SettingsEntry(key, "$title $currentLabel", isSection = false) {
            SettingRow(
                title = title,
                value = currentLabel,
                footnote = footnote,
                opensPicker = true,
                onClick = {
                    onOpenPicker(
                        PickerRequest(
                            title = title,
                            options = options.map { option ->
                                PickerOption(label(option), option == current) { onPick(option) }
                            },
                        ),
                    )
                },
            )
        }
    }

    fun toggle(key: String, title: String, checked: Boolean, onToggle: () -> Unit) {
        rows += SettingsEntry(key, title, isSection = false) { ToggleRow(title, checked, onToggle) }
    }

    fun entry(key: String, search: String, content: @Composable () -> Unit) {
        rows += SettingsEntry(key, search, isSection = false, content = content)
    }

    /** Section headers drop out while searching — they match nothing useful. */
    fun filtered(query: String): List<SettingsEntry> =
        if (query.isBlank()) rows
        else rows.filter { !it.isSection && it.search.contains(query.trim(), ignoreCase = true) }
}

// --- rows -----------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = TvGramColors.Accent,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

/**
 * The list of values behind a setting.
 *
 * The current one starts focused, so the D-pad lands where the viewer already
 * is and one press either side moves to a neighbour.
 */
@Composable
private fun PickerDialog(request: PickerRequest, onClose: () -> Unit) {
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = request.options.indexOfFirst { it.selected }

    LaunchedEffect(request) {
        if (selectedIndex >= 0) runCatching { selectedFocus.requestFocus() }
    }

    SettingsDialog(title = request.title, onClose = onClose) {
        LazyColumn(
            modifier = Modifier.heightIn(max = PICKER_MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(request.options) { index, option ->
                FocusableSurface(
                    onClick = {
                        option.onPick()
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (index == selectedIndex) {
                                Modifier.focusRequester(selectedFocus)
                            } else {
                                Modifier
                            },
                        ),
                    selected = option.selected,
                    shape = RoundedCornerShape(10.dp),
                    focusScale = 1f,
                    background = if (option.selected) {
                        TvGramColors.AccentMuted
                    } else {
                        TvGramColors.SurfaceElevated
                    },
                    focusedBackground = TvGramColors.Accent,
                ) { focused ->
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val contentColor =
                            if (focused) TvGramColors.Background else TvGramColors.OnBackground
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (option.selected) {
                            TvIcon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = contentColor,
                                size = 20.dp,
                            )
                        }
                    }
                }
            }
        }
        TvButton(text = stringResource(R.string.action_close), onClick = onClose)
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    footnote: String? = null,
    destructive: Boolean = false,
    opensPicker: Boolean = false,
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
            modifier = Modifier
                .heightIn(min = SETTINGS_ROW_HEIGHT)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val contentColor = if (focused) TvGramColors.Background else TvGramColors.OnBackground
            val mutedColor =
                if (focused) TvGramColors.Background else TvGramColors.OnBackgroundMuted

            // Title at the start, value at the end: the old half-width column
            // left every value floating in the middle of the row.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                footnote?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Says the row opens something, rather than being a value on its own.
            if (opensPicker) {
                TvIcon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = mutedColor,
                    size = 20.dp,
                )
            }
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
            modifier = Modifier
                .heightIn(min = SETTINGS_ROW_HEIGHT)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (focused) TvGramColors.Background else TvGramColors.OnBackground,
                modifier = Modifier.weight(1f),
            )
            TvSwitch(checked = checked)
        }
    }
}

/**
 * One saved proxy: the wide half selects it, the narrow half deletes it, so
 * neither needs a confirmation dialog to reach with a remote.
 */
@Composable
private fun ProxyRow(proxy: TgProxy, onSelect: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusableSurface(
            onClick = onSelect,
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(10.dp),
            focusScale = 1f,
            background = if (proxy.isEnabled) TvGramColors.AccentMuted else TvGramColors.Surface,
            focusedBackground = TvGramColors.Accent,
        ) { focused ->
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = proxy.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (focused) TvGramColors.Background else TvGramColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (proxy.isEnabled) {
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (focused) TvGramColors.Background else TvGramColors.Accent,
                    )
                }
            }
        }
        FocusableSurface(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            focusScale = 1f,
            background = TvGramColors.Surface,
            focusedBackground = TvGramColors.Danger,
        ) { focused ->
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_proxy_remove),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (focused) TvGramColors.Background else TvGramColors.OnBackgroundMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

// --- dialogs --------------------------------------------------------------

/**
 * Sets, changes or removes the passcode.
 *
 * There is no "forgot it" path and there never will be: the lock is there
 * because a personal account may be signed in on a shared television, and a
 * way around it would make it pointless. Saving an empty field removes it.
 */
@Composable
private fun PasscodeDialog(onSave: (String) -> Unit, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }

    SettingsDialog(title = stringResource(R.string.settings_passcode), onClose = onClose) {
        Text(
            text = stringResource(R.string.settings_passcode_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = TvGramColors.Danger,
        )
        TvTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit) },
            placeholder = stringResource(R.string.settings_passcode_set),
            keyboardType = KeyboardType.NumberPassword,
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(text = stringResource(R.string.action_save), onClick = { onSave(code) })
            TvButton(text = stringResource(R.string.action_close), onClick = onClose)
        }
    }
}

/**
 * Adds a proxy. Which fields matter depends on the kind, so the irrelevant ones
 * are simply not shown — MTProto wants a secret, SOCKS5 and HTTP want
 * credentials if the server asks for them.
 */
@Composable
private fun ProxyDialog(onSave: (TgProxy) -> Unit, onClose: () -> Unit) {
    var kind by remember { mutableStateOf(ProxyKind.MTPROTO) }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val portNumber = port.toIntOrNull() ?: 0
    val complete = server.isNotBlank() && portNumber in 1..65535 &&
        (kind != ProxyKind.MTPROTO || secret.isNotBlank())

    SettingsDialog(title = stringResource(R.string.settings_proxy_add), onClose = onClose) {
        // Three kinds fit side by side, so they are all visible at once rather
        // than hidden behind a second dialog opened from inside this one.
        Text(
            text = stringResource(R.string.settings_proxy_type),
            style = MaterialTheme.typography.labelSmall,
            color = TvGramColors.OnBackgroundMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProxyKind.entries.forEach { option ->
                FocusableSurface(
                    onClick = { kind = option },
                    selected = option == kind,
                    shape = RoundedCornerShape(10.dp),
                    focusScale = 1f,
                    background = if (option == kind) {
                        TvGramColors.AccentMuted
                    } else {
                        TvGramColors.SurfaceElevated
                    },
                    focusedBackground = TvGramColors.Accent,
                ) { focused ->
                    Text(
                        text = option.name.lowercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (focused) TvGramColors.Background else TvGramColors.OnBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
        TvTextField(
            value = server,
            onValueChange = { server = it.trim() },
            placeholder = stringResource(R.string.settings_proxy_server),
            modifier = Modifier.fillMaxWidth(),
        )
        TvTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit).take(5) },
            placeholder = stringResource(R.string.settings_proxy_port),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        if (kind == ProxyKind.MTPROTO) {
            TvTextField(
                value = secret,
                onValueChange = { secret = it.trim() },
                placeholder = stringResource(R.string.settings_proxy_secret),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TvTextField(
                value = username,
                onValueChange = { username = it.trim() },
                placeholder = stringResource(R.string.settings_proxy_username),
                modifier = Modifier.fillMaxWidth(),
            )
            TvTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.settings_proxy_password),
                isPassword = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = stringResource(R.string.action_save),
                enabled = complete,
                onClick = {
                    onSave(
                        TgProxy(
                            id = 0,
                            server = server,
                            port = portNumber,
                            kind = kind,
                            secret = secret,
                            username = username,
                            password = password,
                        ),
                    )
                },
            )
            TvButton(text = stringResource(R.string.action_close), onClick = onClose)
        }
    }
}

/**
 * A dialog owns its own window, which is what keeps D-pad focus inside it
 * instead of leaking back into the list underneath.
 */
@Composable
private fun SettingsDialog(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .background(TvGramColors.Surface, RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TvGramColors.OnBackground,
            )
            content()
        }
    }
}

// --- helpers --------------------------------------------------------------

/** Even heights, so the list reads as a column rather than a pile. */
private val SETTINGS_ROW_HEIGHT = 68.dp

/** Wide enough to read, narrow enough that a value stays near its title. */
private val SETTINGS_CONTENT_WIDTH = 900.dp

private val PICKER_MAX_HEIGHT = 420.dp

private val TRACK_LANGUAGES = listOf("", "fa", "en", "ar", "tr")

/** TDLib takes 1-32; these are the steps worth offering. */
private val DOWNLOAD_PRIORITIES = listOf(1, 8, 16, 32)

/** What the folder row offers before the account's own folders arrive. */
private val BUILT_IN_FOLDERS: List<TgFolder> =
    BuiltInFolder.entries.map(TgFolder::forBuiltIn)

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
