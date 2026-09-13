package ir.tvgram.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.TgFolder
import ir.tvgram.telegram.model.TgProxy
import ir.tvgram.telegram.model.TgUser
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SettingsUiState(
    val user: TgUser? = null,
    val folders: List<TgFolder> = emptyList(),
    val cacheBytes: Long = 0,
    val isBusy: Boolean = false,
    val proxies: List<TgProxy> = emptyList(),
    val isLoading: Boolean = false,
    /** "2/4" style count of the lookups Telegram did not answer. */
    val error: String? = null,
) {
    val activeProxy: TgProxy? get() = proxies.firstOrNull { it.isEnabled }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val client: TelegramClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        refresh()
    }

    /**
     * Loads the four things Telegram has to answer for, each on its own.
     *
     * They used to be gathered into one assignment, which meant a single slow
     * or wedged call left the whole screen blank — no account, no folders, no
     * cache size — with nothing to say why. Now each lands as it arrives, under
     * its own deadline, so one bad answer costs one row.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val failures = coroutineScope {
                listOf(
                    async {
                        load { client.currentUser() }
                            ?.also { _uiState.value = _uiState.value.copy(user = it) }
                    },
                    async {
                        load { client.folders() }
                            ?.also { _uiState.value = _uiState.value.copy(folders = it) }
                    },
                    async {
                        load { client.cacheSize() }
                            ?.also { _uiState.value = _uiState.value.copy(cacheBytes = it) }
                    },
                    async {
                        load { client.proxies() }
                            ?.also { _uiState.value = _uiState.value.copy(proxies = it) }
                    },
                ).awaitAll()
            }.count { it == null }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = if (failures > 0) "$failures/4" else null,
            )
        }
    }

    /** Null when Telegram errored or took longer than a viewer would wait. */
    private suspend fun <T> load(block: suspend () -> T): T? =
        runCatching { withTimeoutOrNull(REQUEST_TIMEOUT_MS) { block() } }.getOrNull()

    fun setPasscode(passcode: String) {
        viewModelScope.launch { settingsRepository.setPasscode(passcode) }
    }

    fun addProxy(proxy: TgProxy) {
        viewModelScope.launch {
            runCatching { client.addProxy(proxy) }
            refresh()
        }
    }

    fun enableProxy(id: Int) {
        viewModelScope.launch {
            runCatching { client.enableProxy(id) }
            refresh()
        }
    }

    fun disableProxies() {
        viewModelScope.launch {
            runCatching { client.disableProxies() }
            refresh()
        }
    }

    fun removeProxy(id: Int) {
        viewModelScope.launch {
            runCatching { client.removeProxy(id) }
            refresh()
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = settingsRepository.current()
            settingsRepository.update(transform)
            val after = settingsRepository.current()

            // Pushing the ceiling through means running TDLib's storage
            // optimiser over the whole cache. Doing that after every tap — a
            // toggle, a language change — made the settings screen feel dead,
            // because the next request queued behind a full disk scan. Only the
            // setting that actually changed is worth that.
            if (after.cacheLimitBytes != before.cacheLimitBytes) {
                runCatching { client.setCacheLimit(after.cacheLimitBytes) }
                refreshCacheSize()
            }
        }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            load { client.cacheSize() }
                ?.also { _uiState.value = _uiState.value.copy(cacheBytes = it) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            runCatching { client.clearCache() }
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                cacheBytes = load { client.cacheSize() } ?: 0L,
            )
        }
    }

    fun logOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            runCatching { client.logOut() }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    private companion object {
        /** Longer than a healthy round trip, short enough not to blank the screen. */
        const val REQUEST_TIMEOUT_MS = 8_000L
    }
}
