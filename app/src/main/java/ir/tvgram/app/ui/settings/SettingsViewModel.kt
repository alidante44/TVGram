package ir.tvgram.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.TgFolder
import ir.tvgram.telegram.model.TgUser
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: TgUser? = null,
    val folders: List<TgFolder> = emptyList(),
    val cacheBytes: Long = 0,
    val isBusy: Boolean = false,
)

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

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                user = runCatching { client.currentUser() }.getOrNull(),
                folders = runCatching { client.folders() }.getOrElse { emptyList() },
                cacheBytes = runCatching { client.cacheSize() }.getOrDefault(0L),
            )
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            // The cache ceiling is a TDLib-side setting, so push it through as
            // soon as it changes rather than at the next start.
            val current = settings.value
            runCatching { client.setCacheLimit(current.cacheLimitBytes) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            runCatching { client.clearCache() }
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                cacheBytes = runCatching { client.cacheSize() }.getOrDefault(0L),
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
}
