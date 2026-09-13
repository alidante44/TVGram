package ir.tvgram.app.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A proxy link that arrived from outside the app, waiting to be acted on.
 *
 * Tapping a `tg://proxy` link hands it to the activity, which may well be
 * running already; this is where it waits until the proxy screen is looking.
 */
@Singleton
class PendingProxyLink @Inject constructor() {

    private val _link = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = _link.asStateFlow()

    fun offer(raw: String?) {
        if (!raw.isNullOrBlank()) _link.value = raw
    }

    fun consume() {
        _link.value = null
    }
}
