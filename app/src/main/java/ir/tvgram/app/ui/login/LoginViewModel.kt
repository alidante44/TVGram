package ir.tvgram.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.telegram.TelegramClient
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val client: TelegramClient,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun requestQr() = submit { client.requestQrLogin() }

    fun submitPhone(phone: String) = submit { client.submitPhoneNumber(phone.trim()) }

    fun submitCode(code: String) = submit { client.submitCode(code.trim()) }

    fun submitPassword(password: String) = submit { client.submitPassword(password) }

    fun clearError() {
        _error.value = null
    }

    private fun submit(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }.onFailure { _error.value = it.message ?: it::class.java.simpleName }
            _busy.value = false
        }
    }
}
