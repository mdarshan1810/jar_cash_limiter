package com.jar.settings

enum class RolloverMode { RESET, ROLLOVER }

data class Settings(
    val startingAmount: Long,
    val periodStartDay: Int,
    val monthlyLimit: Long,
    val rolloverMode: RolloverMode,
    val trackedBank: String,
    val trackedAccountLast4: String?,
    val lastManualResetAt: Long?
) {
    companion object {
        val DEFAULT = Settings(
            startingAmount = 0L,
            periodStartDay = 1,
            monthlyLimit = 0L,
            rolloverMode = RolloverMode.RESET,
            trackedBank = "HDFC",
            trackedAccountLast4 = null,
            lastManualResetAt = null
        )
    }
}
