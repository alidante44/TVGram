package ir.tvgram.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tvgram-settings")

@Singleton
class SettingsRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map(::toSettings)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { preferences ->
            val updated = transform(toSettings(preferences))
            preferences[Keys.DEFAULT_FOLDER] = updated.defaultFolderId
            preferences[Keys.INCLUDE_ARCHIVED] = updated.includeArchived
            preferences[Keys.CACHE_LIMIT] = updated.cacheLimitBytes
            preferences[Keys.DOWNLOAD_PRIORITY] = updated.downloadPriority
            preferences[Keys.INDEX_DEPTH] = updated.indexDepth
            preferences[Keys.API_ID] = updated.apiIdOverride
            preferences[Keys.API_HASH] = updated.apiHashOverride
            preferences[Keys.LAST_CHAT] = updated.lastChatId
            preferences[Keys.LANGUAGE] = updated.language.tag
            preferences[Keys.RAIL_SIDE] = updated.railSide.name
            preferences[Keys.GRID_COLUMNS] = updated.gridColumns
            preferences[Keys.AUTOPLAY_NEXT] = updated.autoplayNext
            preferences[Keys.RESUME_PLAYBACK] = updated.resumePlayback
            preferences[Keys.SEEK_STEP] = updated.seekStepSeconds
            preferences[Keys.AUDIO_LANGUAGE] = updated.preferredAudioLanguage
            preferences[Keys.SUBTITLE_LANGUAGE] = updated.preferredSubtitleLanguage
            preferences[Keys.HARDWARE_DECODING] = updated.hardwareDecoding
            preferences[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            preferences[Keys.MATCH_FRAME_RATE] = updated.matchFrameRate

            // The chosen locale has to be readable before Hilt or coroutines are
            // available — Activity.attachBaseContext runs first — so it is also
            // mirrored into a plain SharedPreferences file.
            LocalePreferences.write(context, updated.language)
        }
    }

    private fun toSettings(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            defaultFolderId = preferences[Keys.DEFAULT_FOLDER] ?: defaults.defaultFolderId,
            includeArchived = preferences[Keys.INCLUDE_ARCHIVED] ?: defaults.includeArchived,
            cacheLimitBytes = preferences[Keys.CACHE_LIMIT] ?: defaults.cacheLimitBytes,
            downloadPriority = preferences[Keys.DOWNLOAD_PRIORITY] ?: defaults.downloadPriority,
            indexDepth = preferences[Keys.INDEX_DEPTH] ?: defaults.indexDepth,
            apiIdOverride = preferences[Keys.API_ID] ?: defaults.apiIdOverride,
            apiHashOverride = preferences[Keys.API_HASH] ?: defaults.apiHashOverride,
            lastChatId = preferences[Keys.LAST_CHAT] ?: defaults.lastChatId,
            language = AppLanguage.fromTag(preferences[Keys.LANGUAGE]),
            railSide = preferences[Keys.RAIL_SIDE]
                ?.let { name -> runCatching { RailSide.valueOf(name) }.getOrNull() }
                ?: defaults.railSide,
            gridColumns = preferences[Keys.GRID_COLUMNS] ?: defaults.gridColumns,
            autoplayNext = preferences[Keys.AUTOPLAY_NEXT] ?: defaults.autoplayNext,
            resumePlayback = preferences[Keys.RESUME_PLAYBACK] ?: defaults.resumePlayback,
            seekStepSeconds = preferences[Keys.SEEK_STEP] ?: defaults.seekStepSeconds,
            preferredAudioLanguage = preferences[Keys.AUDIO_LANGUAGE] ?: defaults.preferredAudioLanguage,
            preferredSubtitleLanguage = preferences[Keys.SUBTITLE_LANGUAGE] ?: defaults.preferredSubtitleLanguage,
            hardwareDecoding = preferences[Keys.HARDWARE_DECODING] ?: defaults.hardwareDecoding,
            keepScreenOn = preferences[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            matchFrameRate = preferences[Keys.MATCH_FRAME_RATE] ?: defaults.matchFrameRate,
        )
    }

    private object Keys {
        val DEFAULT_FOLDER = intPreferencesKey("default_folder")
        val INCLUDE_ARCHIVED = booleanPreferencesKey("include_archived")
        val CACHE_LIMIT = longPreferencesKey("cache_limit")
        val DOWNLOAD_PRIORITY = intPreferencesKey("download_priority")
        val INDEX_DEPTH = intPreferencesKey("index_depth")
        val API_ID = intPreferencesKey("api_id")
        val API_HASH = stringPreferencesKey("api_hash")
        val LAST_CHAT = longPreferencesKey("last_chat")
        val LANGUAGE = stringPreferencesKey("language")
        val RAIL_SIDE = stringPreferencesKey("rail_side")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val SEEK_STEP = intPreferencesKey("seek_step")
        val AUDIO_LANGUAGE = stringPreferencesKey("audio_language")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val HARDWARE_DECODING = booleanPreferencesKey("hardware_decoding")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val MATCH_FRAME_RATE = booleanPreferencesKey("match_frame_rate")
    }
}
