package ir.tvgram.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.BuildConfig
import ir.tvgram.app.settings.AppSettings
import ir.tvgram.app.settings.PendingProxyLink
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.AuthState
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.TelegramCredentials
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    private val client: TelegramClient,
    private val settingsRepository: SettingsRepository,
    pendingProxyLink: PendingProxyLink,
) : ViewModel() {

    /** Non-null while a proxy link opened from outside is waiting to be applied. */
    val proxyLink: StateFlow<String?> = pendingProxyLink.link

    val authState: StateFlow<AuthState> = client.authState
    val connectionState: StateFlow<ConnectionState> = client.connectionState

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * Null until the stored settings have actually been read.
     *
     * `settings` starts from defaults, and the default is "no passcode" — so
     * reading the lock from it would flash the account on screen for a frame
     * before the lock appeared. This stays null until the answer is real.
     */
    val isLocked: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.isLocked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { connect() }
    }

    /**
     * Credentials come from the build (local.properties / CI secrets) and can be
     * overridden at runtime, which is what makes an APK without baked-in keys
     * still usable.
     */
    private suspend fun connect() {
        val stored = settingsRepository.settings.first()
        val credentials = TelegramCredentials(
            apiId = stored.apiIdOverride.takeIf { it != 0 } ?: BuildConfig.TELEGRAM_API_ID,
            apiHash = stored.apiHashOverride.ifBlank { BuildConfig.TELEGRAM_API_HASH },
        )
        client.start(credentials)
        if (stored.cacheLimitBytes > 0) {
            runCatching { client.setCacheLimit(stored.cacheLimitBytes) }
        }
    }

    fun saveCredentials(apiId: Int, apiHash: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(apiIdOverride = apiId, apiHashOverride = apiHash) }
            connect()
        }
    }
}
