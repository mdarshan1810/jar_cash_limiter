package com.jar.notifications

class HdfcWhitelist : BankWhitelist {

    override val bankId: String = "HDFC"

    override fun matches(raw: RawNotification): Boolean =
        raw.packageName in PACKAGES || (raw.sender != null && matchesSender(raw.sender))

    /**
     * Indian telecom SMS routes prepend 2-letter operator codes (VM-, VK-, AD-, JD-, etc.),
     * so a substring check on "HDFCBK" / "HDFC" covers the full prefix family without
     * hardcoding every variant from spec §7.3.
     */
    private fun matchesSender(sender: String): Boolean {
        val upper = sender.uppercase()
        return SENDER_TOKENS.any { upper.endsWith(it) || upper.contains("-$it") }
    }

    companion object {
        private val SENDER_TOKENS = setOf("HDFCBK", "HDFC")
        private val PACKAGES = setOf(
            "com.snapwork.hdfc",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging"
        )
    }
}
