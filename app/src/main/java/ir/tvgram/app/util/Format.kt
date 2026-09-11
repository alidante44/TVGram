package ir.tvgram.app.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Formatting helpers shared by the grid, the chat reader and the player. */
object Format {

    fun duration(seconds: Int): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }

    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (value >= 100 || unit == 0) {
            String.format(Locale.US, "%.0f %s", value, units[unit])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unit])
        }
    }

    /**
     * Dates follow the UI language: a Persian UI gets Jalali dates, because a
     * Gregorian date on a Persian screen is exactly the kind of detail that
     * makes an app feel foreign.
     */
    fun date(unixSeconds: Int, persian: Boolean): String {
        if (unixSeconds <= 0) return ""
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = unixSeconds * 1000L
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return if (persian) {
            val (jy, jm, jd) = Jalali.fromGregorian(year, month, day)
            "$jy/${two(jm)}/${two(jd)}"
        } else {
            "$year-${two(month)}-${two(day)}"
        }
    }

    fun time(unixSeconds: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = unixSeconds * 1000L
        }
        return String.format(
            Locale.US,
            "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
    }

    private fun two(value: Int): String = if (value < 10) "0$value" else value.toString()
}

/**
 * Gregorian to Jalali (Solar Hijri) conversion.
 *
 * The algorithm is the standard day-count one: convert to a Julian Day Number,
 * then walk it back through the Jalali calendar's 33-year leap cycle.
 */
object Jalali {

    private val GREGORIAN_MONTH_DAYS = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun fromGregorian(year: Int, month: Int, day: Int): Triple<Int, Int, Int> {
        val gy = year - 1600
        val gm = month - 1
        val gd = day - 1

        var dayCount = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400 +
            gd + GREGORIAN_MONTH_DAYS[gm]
        if (gm > 1 && isGregorianLeap(year)) dayCount++
        dayCount -= 79 // offset between the two epochs

        val cycle = dayCount / 12053 // 33-year cycles
        var remaining = dayCount % 12053
        var jy = 979 + 33 * cycle + 4 * (remaining / 1461)
        remaining %= 1461

        if (remaining >= 366) {
            jy += (remaining - 366) / 365 + 1
            remaining = (remaining - 366) % 365
        }

        // First six months have 31 days, the next five have 30.
        val jm: Int
        val jd: Int
        if (remaining < 186) {
            jm = 1 + remaining / 31
            jd = 1 + remaining % 31
        } else {
            val rest = remaining - 186
            jm = 7 + rest / 30
            jd = 1 + rest % 30
        }
        return Triple(jy, jm, jd)
    }

    private fun isGregorianLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** Month names for a Persian UI. */
    val MONTH_NAMES: List<String> = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    fun monthName(month: Int): String = MONTH_NAMES.getOrElse(abs(month - 1) % 12) { "" }
}
