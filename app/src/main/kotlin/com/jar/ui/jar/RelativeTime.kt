package com.jar.ui.jar

import java.time.Clock

/**
 * Coarse "time ago" formatter for the recent-transactions list. Returns at most one unit
 * of granularity — this is a glance surface, not an audit log.
 */
fun formatRelativeTime(timestamp: Long, clock: Clock = Clock.systemDefaultZone()): String {
    val diff = clock.millis() - timestamp
    return when {
        diff < 0L -> "just now"
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000L} hr ago"
        diff < 7L * 86_400_000L -> "${diff / 86_400_000L} d ago"
        else -> "${diff / (7L * 86_400_000L)} wk ago"
    }
}
