package ir.tvgram.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.tvgram.app.R
import ir.tvgram.app.ui.common.LoadingBar
import ir.tvgram.app.ui.common.TvButton
import ir.tvgram.app.ui.common.TvTextField
import ir.tvgram.app.ui.theme.TvGramColors
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ir.tvgram.telegram.model.AuthState

/**
 * Signing in from a sofa.
 *
 * QR is offered first because typing a phone number, then a code, then a
 * two-step password with a D-pad is genuinely painful — but the typed path is
 * always one button away, since QR login fails on some accounts.
 */
@Composable
fun LoginScreen(
    state: AuthState,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var usePhone by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.displayLarge,
                color = TvGramColors.OnBackground,
            )

            when {
                state is AuthState.WaitQrCode && !usePhone -> QrPanel(
                    link = state.link,
                    onUsePhone = { usePhone = true },
                    onRefresh = viewModel::requestQr,
                )

                state is AuthState.WaitCode -> CodeStep(
                    phoneNumber = state.phoneNumber,
                    onSubmit = viewModel::submitCode,
                )

                state is AuthState.WaitPassword -> PasswordStep(
                    hint = state.hint,
                    onSubmit = viewModel::submitPassword,
                )

                state is AuthState.Failed -> Text(
                    text = stringResource(R.string.error_generic, state.message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvGramColors.Danger,
                )

                state is AuthState.WaitPhoneNumber || usePhone -> PhoneStep(
                    onSubmit = viewModel::submitPhone,
                    onUseQr = {
                        usePhone = false
                        viewModel.requestQr()
                    },
                )

                else -> Text(
                    text = stringResource(R.string.login_waiting),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvGramColors.OnBackgroundMuted,
                )
            }

            if (busy) LoadingBar(modifier = Modifier.width(420.dp))

            error?.let {
                Text(
                    text = stringResource(R.string.error_generic, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvGramColors.Danger,
                )
            }
        }
    }
}

@Composable
private fun QrPanel(link: String, onUsePhone: () -> Unit, onRefresh: () -> Unit) {
    val qr = remember(link) { QrCode.render(link) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.login_qr_title),
            style = MaterialTheme.typography.titleLarge,
            color = TvGramColors.OnBackground,
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            qr?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.login_qr_title),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = stringResource(R.string.login_qr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = TvGramColors.OnBackgroundMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(text = stringResource(R.string.login_qr_refresh), onClick = onRefresh)
            TvButton(text = stringResource(R.string.login_use_phone), onClick = onUsePhone)
        }
    }
}

@Composable
private fun PhoneStep(onSubmit: (String) -> Unit, onUseQr: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    StepLayout(
        title = stringResource(R.string.login_phone_title),
        value = phone,
        onValueChange = { phone = it },
        placeholder = stringResource(R.string.login_phone_hint),
        keyboardType = KeyboardType.Phone,
        onSubmit = { onSubmit(phone) },
        secondaryLabel = stringResource(R.string.login_use_qr),
        onSecondary = onUseQr,
    )
}

@Composable
private fun CodeStep(phoneNumber: String, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    StepLayout(
        title = stringResource(R.string.login_code_title),
        subtitle = phoneNumber.takeIf { it.isNotBlank() },
        value = code,
        onValueChange = { code = it },
        placeholder = stringResource(R.string.login_code_hint),
        keyboardType = KeyboardType.NumberPassword,
        onSubmit = { onSubmit(code) },
    )
}

@Composable
private fun PasswordStep(hint: String?, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    StepLayout(
        title = stringResource(R.string.login_password_title),
        subtitle = hint,
        value = password,
        onValueChange = { password = it },
        placeholder = stringResource(R.string.login_password_hint),
        keyboardType = KeyboardType.Password,
        isPassword = true,
        onSubmit = { onSubmit(password) },
    )
}

@Composable
private fun StepLayout(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    onSubmit: () -> Unit,
    subtitle: String? = null,
    isPassword: Boolean = false,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.width(520.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvGramColors.OnBackground,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TvGramColors.OnBackgroundMuted,
            )
        }
        TvTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = keyboardType,
            isPassword = isPassword,
            modifier = Modifier.width(520.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(
                text = stringResource(R.string.login_continue),
                onClick = onSubmit,
                enabled = value.isNotBlank(),
            )
            if (secondaryLabel != null && onSecondary != null) {
                TvButton(text = secondaryLabel, onClick = onSecondary)
            }
        }
    }
}
