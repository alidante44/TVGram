package ir.tvgram.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.media.displayTitle
import ir.tvgram.app.ui.theme.TvGramColors
import ir.tvgram.app.ui.theme.TvGramDimens
import ir.tvgram.app.util.Format
import ir.tvgram.telegram.model.ProxyKind
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

    // Built every recomposition so each row reads the current value, then
    // filtered by the search box. Rows are described rather than emitted
    // because LazyListScope is not composable, and stringResource is.
    val rows = settingsRows {
        // --- Telegram -----------------------------------------------------
        section(stringResource(R.string.settings_section_telegram))

        row(
            key = "account",
            title = stringResource(R.string.settings_account),
            value = uiState.user?.let { user ->
                listOfNotNull(user.displayName, user.phoneNumber)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
            }.orEmpty(),
            onClick = viewModel::refresh,
        )

        val folders = uiState.folders
        row(
            key = "default-folder",
            title = stringResource(R.string.settings_default_folder),
            value = folders.firstOrNull { it.id == settings.defaultFolderId }?.displayTitle().orEmpty(),
            onClick = {
                val next = folders.cycleAfter { it.id == settings.defaultFolderId }
                if (next != null) viewModel.update { it.copy(defaultFolderId = next.id) }
            },
        )

        toggle(
            key = "archived",
            title = stringResource(R.string.settings_include_archived),
            checked = settings.includeArchived,
            onToggle = { viewModel.update { s -> s.copy(includeArchived = !s.includeArchived) } },
        )

        row(
            key = "cache-limit",
            title = stringResource(R.string.settings_cache_limit),
            value = Format.fileSize(settings.cacheLimitBytes),
            onClick = {
                val next = AppSettings.CACHE_LIMIT_CHOICES.cycleAfter { it == settings.cacheLimitBytes }
                if (next != null) viewModel.update { it.copy(cacheLimitBytes = next) }
            },
        )

        row(
            key = "clear-cache",
            title = stringResource(R.string.settings_clear_cache),
            value = stringResource(R.string.settings_cache_size, Format.fileSize(uiState.cacheBytes)),
            onClick = viewModel::clearCache,
        )

        row(
            key = "download-priority",
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

        row(
            key = "index-depth",
            title = stringResource(R.string.settings_index_depth),
            value = settings.indexDepth.toString(),
            onClick = {
                val next = AppSettings.INDEX_DEPTH_CHOICES.cycleAfter { it == settings.indexDepth }
                if (next != null) viewModel.update { it.copy(indexDepth = next) }
            },
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

        row(
            key = "language",
            title = stringResource(R.string.settings_language),
            value = stringResource(settings.language.labelRes()),
            footnote = stringResource(R.string.settings_restart_needed),
            onClick = {
                val next = AppLanguage.entries.cycleAfter { it == settings.language }
                if (next != null) viewModel.update { it.copy(language = next) }
            },
        )

        row(
            key = "rail-side",
            title = stringResource(R.string.settings_rail_side),
            value = stringResource(settings.railSide.labelRes()),
            onClick = {
                val next = RailSide.entries.cycleAfter { it == settings.railSide }
                if (next != null) viewModel.update { it.copy(railSide = next) }
            },
        )

        row(
            key = "grid-columns",
            title = stringResource(R.string.settings_grid_columns),
            value = settings.gridColumns.toString(),
            onClick = {
                val next = AppSettings.GRID_COLUMN_CHOICES.cycleAfter { it == settings.gridColumns }
                if (next != null) viewModel.update { it.copy(gridColumns = next) }
            },
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

        row(
            key = "seek-step",
            title = stringResource(R.string.settings_seek_step),
            value = stringResource(R.string.settings_seek_step_value, settings.seekStepSeconds),
            onClick = {
                val next = AppSettings.SEEK_STEP_CHOICES.cycleAfter { it == settings.seekStepSeconds }
                if (next != null) viewModel.update { it.copy(seekStepSeconds = next) }
            },
        )

        row(
            key = "audio-language",
            title = stringResource(R.string.settings_preferred_audio),
            value = settings.preferredAudioLanguage.ifBlank { "—" },
            onClick = {
                val next = TRACK_LANGUAGES.cycleAfter { it == settings.preferredAudioLanguage }
                if (next != null) viewModel.update { it.copy(preferredAudioLanguage = next) }
            },
        )

        row(
            key = "subtitle-language",
            title = stringResource(R.string.settings_preferred_subtitle),
            value = settings.preferredSubtitleLanguage.ifBlank {
                stringResource(R.string.player_subtitle_off)
            },
            onClick = {
                val next = TRACK_LANGUAGES.cycleAfter { it == settings.preferredSubtitleLanguage }
                if (next != null) viewModel.update { it.copy(preferredSubtitleLanguage = next) }
            },
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = TvGramDimens.ScreenPaddingHorizontal,
            vertical = TvGramDimens.ScreenPaddingVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search") {
            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.settings_search_hint),
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }

        if (uiState.isBusy) {
            item(key = "busy") { LoadingBar() }
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
private fun settingsRows(content: @Composable SettingsRows.() -> Unit): SettingsRows {
    val rows = SettingsRows()
    rows.content()
    return rows
}

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
private class SettingsRows {
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
        SettingRow(
            title = stringResource(R.string.settings_proxy_type),
            value = kind.name.lowercase(),
            onClick = { kind = ProxyKind.entries.cycleAfter { it == kind } ?: kind },
        )
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
