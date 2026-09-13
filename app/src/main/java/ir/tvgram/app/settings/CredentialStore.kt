package ir.tvgram.app.settings

import ir.tvgram.app.BuildConfig
import ir.tvgram.telegram.model.TelegramCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the api_id and api_hash come from.
 *
 * The build's own keys are used unless the viewer typed their own on the setup
 * screen, which is what makes an APK published without keys still usable. Both
 * the first connection and a reconnection after signing out need the same
 * answer, so it lives in one place.
 */
@Singleton
class CredentialStore @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun current(): TelegramCredentials {
        val stored = settingsRepository.current()
        return TelegramCredentials(
            apiId = stored.apiIdOverride.takeIf { it != 0 } ?: BuildConfig.TELEGRAM_API_ID,
            apiHash = stored.apiHashOverride.ifBlank { BuildConfig.TELEGRAM_API_HASH },
        )
    }
}
