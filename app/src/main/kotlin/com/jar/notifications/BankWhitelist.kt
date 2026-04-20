package com.jar.notifications

/**
 * Decides whether a raw notification should be considered for parsing at all. The filter is
 * deliberately strict: anything that doesn't match goes to `/dev/null`, not `unparsed_notifications`,
 * so the debug view stays free of WhatsApp/Slack/etc. noise (spec §7.2).
 */
interface BankWhitelist {
    val bankId: String
    fun matches(raw: RawNotification): Boolean
}
