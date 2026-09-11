package ir.tvgram.app.settings

/** Which side of the screen the navigation rail is pinned to. */
enum class RailSide { START, LEFT, RIGHT }

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    PERSIAN("fa"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Everything the user can change, in one immutable snapshot.
 *
 * Split across the two settings sections the TV UI shows: the Telegram half
 * (what is fetched) and the Android TV half (how it is shown and played).
 */
data class AppSettings(
    // --- Telegram ---
    val defaultFolderId: Int = ALL_FOLDER_ID,
    val includeArchived: Boolean = false,
    val cacheLimitBytes: Long = 4L * 1024 * 1024 * 1024,
    val downloadPriority: Int = 16,
    val indexDepth: Int = 400,
    val apiIdOverride: Int = 0,
    val apiHashOverride: String = "",
    val lastChatId: Long = 0,

    // --- Android TV ---
    val language: AppLanguage = AppLanguage.SYSTEM,
    val railSide: RailSide = RailSide.LEFT,
    val gridColumns: Int = 4,
    val autoplayNext: Boolean = true,
    val resumePlayback: Boolean = true,
    val seekStepSeconds: Int = 10,
    val preferredAudioLanguage: String = "",
    val preferredSubtitleLanguage: String = "",
    val hardwareDecoding: Boolean = true,
    val keepScreenOn: Boolean = true,
    val matchFrameRate: Boolean = true,
) {
    companion object {
        /** Matches TgFolder.builtIn(ALL).id. */
        const val ALL_FOLDER_ID: Int = -1

        val GRID_COLUMN_CHOICES: List<Int> = listOf(3, 4, 5, 6)
        val SEEK_STEP_CHOICES: List<Int> = listOf(5, 10, 15, 30, 60)
        val CACHE_LIMIT_CHOICES: List<Long> = listOf(
            1L * 1024 * 1024 * 1024,
            2L * 1024 * 1024 * 1024,
            4L * 1024 * 1024 * 1024,
            8L * 1024 * 1024 * 1024,
            16L * 1024 * 1024 * 1024,
        )
        val INDEX_DEPTH_CHOICES: List<Int> = listOf(200, 400, 1000, 2000)
    }
}
