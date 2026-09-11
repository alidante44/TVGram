package ir.tvgram.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class JalaliTest {

    /**
     * Nowruz is the only date that has to be exactly right: if the new year
     * lands on the wrong day, every date in the app is off by one.
     */
    @Test
    fun `new year falls on the correct gregorian day`() {
        assertEquals(Triple(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
        assertEquals(Triple(1404, 1, 1), Jalali.fromGregorian(2025, 3, 21))
        assertEquals(Triple(1405, 1, 1), Jalali.fromGregorian(2026, 3, 21))
        assertEquals(Triple(1402, 1, 1), Jalali.fromGregorian(2023, 3, 21))
        assertEquals(Triple(1400, 1, 1), Jalali.fromGregorian(2021, 3, 21))
        assertEquals(Triple(1395, 1, 1), Jalali.fromGregorian(2016, 3, 20))
        assertEquals(Triple(1369, 1, 1), Jalali.fromGregorian(1990, 3, 21))
    }

    @Test
    fun `the day before nowruz is the last day of esfand`() {
        // 1402 was a common year, so Esfand had 29 days.
        assertEquals(Triple(1402, 12, 29), Jalali.fromGregorian(2024, 3, 19))
    }

    @Test
    fun `mid-year dates land in the right month`() {
        assertEquals(Triple(1405, 6, 20), Jalali.fromGregorian(2026, 9, 11))
        assertEquals(Triple(1378, 10, 11), Jalali.fromGregorian(2000, 1, 1))
        assertEquals(Triple(1357, 11, 22), Jalali.fromGregorian(1979, 2, 11))
    }

    @Test
    fun `month boundaries respect the 31-then-30 day layout`() {
        // Day 186 of the year is the first day of Mehr, the seventh month.
        val (_, month, day) = Jalali.fromGregorian(2025, 9, 23)
        assertEquals(7, month)
        assertEquals(1, day)
    }
}

class FormatTest {

    @Test
    fun `durations drop the hour field when there is none`() {
        assertEquals("0:45", Format.duration(45))
        assertEquals("2:05", Format.duration(125))
        assertEquals("1:00:00", Format.duration(3600))
        assertEquals("2:03:04", Format.duration(7384))
        assertEquals("", Format.duration(0))
    }

    @Test
    fun `file sizes use one decimal only where it adds information`() {
        assertEquals("512 B", Format.fileSize(512))
        assertEquals("1.0 KB", Format.fileSize(1024))
        assertEquals("1.5 MB", Format.fileSize(1024 * 1024 * 3 / 2))
        assertEquals("2.0 GB", Format.fileSize(2L * 1024 * 1024 * 1024))
        assertEquals("", Format.fileSize(0))
    }
}
