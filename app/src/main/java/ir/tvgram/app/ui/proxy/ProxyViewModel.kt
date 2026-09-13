package ir.tvgram.app.ui.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.settings.PendingProxyLink
import ir.tvgram.telegram.TelegramClient
import ir.tvgram.telegram.model.ConnectionState
import ir.tvgram.telegram.model.ProxyLink
import ir.tvgram.telegram.model.TgProxy
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProxyUiState(
    val proxies: List<TgProxy> = emptyList(),
    val isBusy: Boolean = false,
    /** Set when the last link the viewer pasted or opened made no sense. */
    val badLink: Boolean = false,
) {
    val active: TgProxy? get() = proxies.firstOrNull { it.isEnabled }
}

@HiltViewModel
class ProxyViewModel @Inject constructor(
    private val client: TelegramClient,
    private val pendingLink: PendingProxyLink,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProxyUiState())
    val uiState: StateFlow<ProxyUiState> = _uiState.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = client.connectionState

    init {
        refresh()
        // A link tapped elsewhere on the television lands here.
        viewModelScope.launch {
            pendingLink.link.collect { raw ->
                if (raw != null) {
                    addFromLink(raw)
                    pendingLink.consume()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                proxies = runCatching { client.proxies() }.getOrElse { emptyList() },
            )
        }
    }

    /**
     * Takes a pasted or opened link and sets it up in one step, which is the
     * whole point of a proxy link: no fields to copy across by hand.
     */
    fun addFromLink(raw: String) {
        val parsed = ProxyLink.parse(raw)
        if (parsed == null) {
            _uiState.value = _uiState.value.copy(badLink = true)
            return
        }
        _uiState.value = _uiState.value.copy(badLink = false)
        add(parsed)
    }

    fun add(proxy: TgProxy) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            runCatching { client.addProxy(proxy) }
            _uiState.value = _uiState.value.copy(isBusy = false)
            refresh()
        }
    }

    fun enable(id: Int) {
        viewModelScope.launch {
            runCatching { client.enableProxy(id) }
            refresh()
        }
    }

    fun disable() {
        viewModelScope.launch {
            runCatching { client.disableProxies() }
            refresh()
        }
    }

    fun remove(id: Int) {
        viewModelScope.launch {
            runCatching { client.removeProxy(id) }
            refresh()
        }
    }

    fun dismissBadLink() {
        _uiState.value = _uiState.value.copy(badLink = false)
    }
}
