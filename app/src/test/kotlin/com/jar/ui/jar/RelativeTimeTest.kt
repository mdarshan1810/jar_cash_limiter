package com.jar.ui.jar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RelativeTimeTest {

    private val now = Instant.parse("2026-04-20T12:00:00Z").toEpochMilli()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    private fun format(deltaMs: Long) = formatRelativeTime(now - deltaMs, clock)

    @Test fun zeroDeltaIsJustNow() = assertEquals("just now", format(0L))
    @Test fun under60sIsJustNow() = assertEquals("just now", format(45_000L))
    @Test fun oneMinute() = assertEquals("1 min ago", format(60_000L))
    @Test fun elevenMinutes() = assertEquals("11 min ago", format(11 * 60_000L))
    @Test fun oneHour() = assertEquals("1 hr ago", format(3_600_000L))
    @Test fun fiveHours() = assertEquals("5 hr ago", format(5 * 3_600_000L))
    @Test fun oneDay() = assertEquals("1 d ago", format(86_400_000L))
    @Test fun sixDays() = assertEquals("6 d ago", format(6 * 86_400_000L))
    @Test fun oneWeek() = assertEquals("1 wk ago", format(7 * 86_400_000L))
    @Test fun futureTimestampIsJustNow() = assertEquals("just now", format(-5_000L))
}
