package ir.tvgram.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import ir.tvgram.app.settings.LocalePreferences
import ir.tvgram.app.ui.LocalTelegramClient
import ir.tvgram.app.ui.TvGramApp
import ir.tvgram.app.ui.theme.TvGramTheme
import ir.tvgram.telegram.TelegramClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var telegramClient: TelegramClient

    override fun attachBaseContext(newBase: Context) {
        // The chosen UI language has to be applied before any resource is read.
        super.attachBaseContext(LocalePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A TV has no "dim after inactivity" expectation while the app is open,
        // and the player toggles this off again when the user pauses.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TvGramTheme {
                CompositionLocalProvider(LocalTelegramClient provides telegramClient) {
                    TvGramApp()
                }
            }
        }
    }
}
