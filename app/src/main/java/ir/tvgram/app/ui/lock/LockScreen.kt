package ir.tvgram.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.tvgram.app.R
import ir.tvgram.app.settings.SettingsRepository
import ir.tvgram.app.ui.common.TvButton
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.theme.TvGramColors
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _wrong = MutableStateFlow(false)
    val wrong: StateFlow<Boolean> = _wrong.asStateFlow()

    fun submit(passcode: String, onUnlocked: () -> Unit) {
        viewModelScope.launch {
            if (settingsRepository.verifyPasscode(passcode)) {
                _wrong.value = false
                onUnlocked()
            } else {
                _wrong.value = true
            }
        }
    }
}

/**
 * Shown before anything else when a passcode is set.
 *
 * There is no "forgot my code" here on purpose: the lock exists because someone
 * may have a personal account signed in on a television that other people use,
 * and an escape hatch would defeat it. Clearing the app's data is the only way
 * past, and that signs the account out too.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
) {
    var passcode by remember { mutableStateOf("") }
    val wrong by viewModel.wrong.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.displayLarge,
                color = TvGramColors.OnBackground,
            )
            Text(
                text = stringResource(R.string.lock_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = TvGramColors.OnBackgroundMuted,
            )
            TvTextField(
                value = passcode,
                onValueChange = {
                    passcode = it.filter(Char::isDigit)
                },
                placeholder = stringResource(R.string.lock_placeholder),
                keyboardType = KeyboardType.NumberPassword,
                isPassword = true,
                modifier = Modifier.width(420.dp),
            )
            if (wrong) {
                Text(
                    text = stringResource(R.string.lock_wrong),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvGramColors.Danger,
                )
            }
            TvButton(
                text = stringResource(R.string.lock_unlock),
                enabled = passcode.isNotBlank(),
                onClick = { viewModel.submit(passcode) { onUnlocked() } },
            )
        }
    }
}
