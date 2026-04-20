package com.jar.data

import com.jar.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PeriodTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    private fun settings(
        periodStartDay: Int = 1,
        lastManualResetAt: Long? = null
    ): Settings = Settings.DEFAULT.copy(
        periodStartDay = periodStartDay,
        lastManualResetAt = lastManualResetAt
    )

    private fun millisAt(zone: ZoneId, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun midMonthWithStartDay1() {
        val now = LocalDateTime.of(2026, 4, 15, 10, 30)
        val p = currentPeriod(now, settings(periodStartDay = 1), kolkata)
        assertEquals(millisAt(kolkata, 2026, 4, 1), p.startMillis)
        assertEquals(millisAt(kolkata, 2026, 5, 1), p.endMillis)
    }

    @Test fun exactlyOnStartDayStartsNewPeriod() {
        val now = LocalDateTime.of(2026, 4, 5, 9, 0)
        val p = currentPeriod(now, settings(periodStartDay = 5), kolkata)
        assertEquals(millisAt(kolkata, 2026, 4, 5), p.startMillis)
        assertEquals(millisAt(kolkata, 2026, 5, 5), p.endMillis)
    }

    @Test fun beforeStartDayRollsBackToPrevMonth() {
        val now = LocalDateTime.of(2026, 4, 3, 9, 0)
        val p = currentPeriod(now, settings(periodStartDay = 5), kolkata)
        assertEquals(millisAt(kolkata, 2026, 3, 5), p.startMillis)
        assertEquals(millisAt(kolkata, 2026, 4, 5), p.endMillis)
    }

    @Test fun periodStartDay31InFebruaryClampsToMonthLength() {
        // February 2026 has 28 days; periodStartDay=31 clamps to 28
        val now = LocalDateTime.of(2026, 2, 15, 12, 0)
        val p = currentPeriod(now, settings(periodStartDay = 31), kolkata)
        // candidate = Feb 28, which is after Feb 15 → start = Jan 28
        assertEquals(millisAt(kolkata, 2026, 1, 28), p.startMillis)
        assertEquals(millisAt(kolkata, 2026, 2, 28), p.endMillis)
    }

    @Test fun periodStartDay31OnJan31IncludesJan31() {
        val now = LocalDateTime.of(2026, 1, 31, 10, 0)
        val p = currentPeriod(now, settings(periodStartDay = 31), kolkata)
        assertEquals(millisAt(kolkata, 2026, 1, 31), p.startMillis)
        // plusMonths(1) clamps Jan 31 → Feb 28
        assertEquals(millisAt(kolkata, 2026, 2, 28), p.endMillis)
    }

    @Test fun dstTransitionInAmericaNewYork() {
        // DST spring-forward: 2026-03-08 02:00 → 03:00 in America/New_York
        val now = LocalDateTime.of(2026, 3, 15, 10, 0)
        val p = currentPeriod(now, settings(periodStartDay = 1), newYork)
        // Period covers the DST transition; boundary math must still produce coherent ms values
        assertEquals(millisAt(newYork, 2026, 3, 1), p.startMillis)
        assertEquals(millisAt(newYork, 2026, 4, 1), p.endMillis)
        // Sanity: period spans just under 31 days due to DST losing one hour
        val spanHours = (p.endMillis - p.startMillis) / (1000 * 60 * 60)
        assertEquals(31L * 24 - 1, spanHours)
    }

    @Test fun lastManualResetOverridesComputedStartWhenLater() {
        val now = LocalDateTime.of(2026, 4, 20, 12, 0)
        val resetAt = millisAt(kolkata, 2026, 4, 10)
        val p = currentPeriod(now, settings(periodStartDay = 1, lastManualResetAt = resetAt), kolkata)
        assertEquals(resetAt, p.startMillis)
        assertEquals(millisAt(kolkata, 2026, 5, 1), p.endMillis)
    }

    @Test fun lastManualResetIgnoredWhenEarlierThanComputedStart() {
        val now = LocalDateTime.of(2026, 4, 20, 12, 0)
        val oldReset = millisAt(kolkata, 2026, 1, 1)
        val p = currentPeriod(now, settings(periodStartDay = 1, lastManualResetAt = oldReset), kolkata)
        assertEquals(millisAt(kolkata, 2026, 4, 1), p.startMillis)
    }

    @Test fun endIsStrictlyAfterStart() {
        val now = LocalDateTime.of(2026, 6, 15, 0, 0)
        val p = currentPeriod(now, settings(periodStartDay = 15), kolkata)
        assertTrue(p.endMillis > p.startMillis)
    }
}
