package ir.tvgram.app.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * The UI language, stored where it can be read synchronously.
 *
 * `Activity.attachBaseContext` has to know the locale before anything else
 * exists — no Hilt, no coroutines, no DataStore — so the language (and only the
 * language) is mirrored here by [SettingsRepository].
 */
object LocalePreferences {

    private const val FILE = "tvgram-locale"
    private const val KEY = "language"

    fun write(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.tag)
            .apply()
    }

    fun read(context: Context): AppLanguage = AppLanguage.fromTag(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null),
    )

    /** Returns [context] re-based on the chosen locale, or unchanged for SYSTEM. */
    fun wrap(context: Context): Context {
        val language = read(context)
        if (language == AppLanguage.SYSTEM) return context

        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
