package ir.tvgram.app.ui.login

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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.TvButton
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.theme.TvGramColors

/**
 * Shown when the APK was built without api_id/api_hash.
 *
 * Baking Telegram credentials into a public repository is not an option, so a
 * build without them still installs and asks for them once, here.
 */
@Composable
fun CredentialsScreen(
    onSave: (apiId: Int, apiHash: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .width(640.dp)
                .padding(48.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleLarge,
                color = TvGramColors.OnBackground,
            )
            Text(
                text = stringResource(R.string.setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TvGramColors.OnBackgroundMuted,
            )
            TvTextField(
                value = apiId,
                onValueChange = { value -> apiId = value.filter(Char::isDigit) },
                placeholder = stringResource(R.string.setup_api_id),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(560.dp),
            )
            TvTextField(
                value = apiHash,
                onValueChange = { apiHash = it.trim() },
                placeholder = stringResource(R.string.setup_api_hash),
                modifier = Modifier.width(560.dp),
            )
            TvButton(
                text = stringResource(R.string.setup_save),
                enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
                onClick = { onSave(apiId.toIntOrNull() ?: 0, apiHash) },
            )
        }
    }
}
