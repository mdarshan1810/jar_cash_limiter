package com.jar.notifications

class HdfcWhitelist : BankWhitelist {

    override val bankId: String = "HDFC"

    override fun matches(raw: RawNotification): Boolean =
        raw.packageName in PACKAGES || (raw.sender != null && matchesSender(raw.sender))

    /**
     * Indian telecom SMS routes prepend 2-letter operator codes (VM-, VK-, AD-, JD-, etc.),
     * so we accept either the bare token or the token preceded by a `-` separator. A bare
     * `endsWith("HDFCBK")` would incorrectly match senders like `XHDFCBK` or `COOLHDFCBK`
     * which are not HDFC; the `-` anchor rules those out.
     */
    private fun matchesSender(sender: String): Boolean {
        val upper = sender.uppercase()
        return SENDER_TOKENS.any { token -> upper == token || upper.endsWith("-$token") }
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
