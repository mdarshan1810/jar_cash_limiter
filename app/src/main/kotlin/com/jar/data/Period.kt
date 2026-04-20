package com.jar.data

import com.jar.settings.Settings
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Period(val startMillis: Long, val endMillis: Long)

/**
 * Computes the active period window for [now] given [settings]. Pure function of its inputs —
 * no background state, no stored period row; survives kills and reinstalls.
 *
 * The chosen `periodStartDay` is clamped to the month's actual length (so a value of 31 in
 * February resolves to the 28th/29th). The end boundary is `start + 1 month` with the same
 * Java-native clamping. `lastManualResetAt`, if later than the computed start, overrides it.
 */
fun currentPeriod(now: LocalDateTime, settings: Settings, zone: ZoneId): Period {
    val lengthOfMonth = YearMonth.from(now).lengthOfMonth()
    val dayOfMonth = minOf(settings.periodStartDay, lengthOfMonth)
    val candidate = now.withDayOfMonth(dayOfMonth).truncatedTo(ChronoUnit.DAYS)
    val start = if (candidate.isAfter(now)) candidate.minusMonths(1) else candidate
    val end = start.plusMonths(1)
    val startMs = start.atZone(zone).toInstant().toEpochMilli()
    val endMs = end.atZone(zone).toInstant().toEpochMilli()
    val effective = maxOf(startMs, settings.lastManualResetAt ?: Long.MIN_VALUE)
    return Period(effective, endMs)
}
