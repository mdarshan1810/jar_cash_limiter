package com.jar.notifications

class KotakWhitelist : BankWhitelist {

    override val bankId: String = "KOTAK"

    override fun matches(raw: RawNotification): Boolean =
        raw.packageName in PACKAGES || (raw.sender != null && matchesSender(raw.sender))

    /**
     * Indian DLT senders look like `<2-letter-op>-<bank-token>` and may carry an extra
     * route designator suffix (`-S`, `-P`, `-T`, …). Real observed sender for Kotak
     * transactional SMS: `AX-KOTAKB-S`. We accept the bare token, the standard
     * operator-prefixed form, and any operator-prefixed form with a trailing
     * single-letter route designator.
     */
    private fun matchesSender(sender: String): Boolean {
        val upper = sender.uppercase()
        return SENDER_TOKENS.any { token ->
            upper == token ||
                upper.endsWith("-$token") ||
                upper.matches(Regex(""".+-$token-[A-Z]"""))
        }
    }

    companion object {
        private val SENDER_TOKENS = setOf("KOTAKB", "KOTAK", "KMBL")
        private val PACKAGES = setOf(
            "com.msf.kbank.mobile",
            "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging"
        )
    }
}
