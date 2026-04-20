package com.jar.data

import com.jar.settings.Settings

data class JarState(
    val startingAmount: Long,
    val spent: Long,
    val monthlyLimit: Long,
    val fractionRemaining: Float,
    val isOverdrawn: Boolean,
    val isOverLimit: Boolean
) {
    companion object {
        fun from(settings: Settings, spent: Long): JarState {
            val remaining = settings.startingAmount - spent
            val fraction = if (settings.startingAmount > 0L) {
                (remaining.toFloat() / settings.startingAmount.toFloat()).coerceIn(0f, 1f)
            } else 0f
            return JarState(
                startingAmount = settings.startingAmount,
                spent = spent,
                monthlyLimit = settings.monthlyLimit,
                fractionRemaining = fraction,
                isOverdrawn = remaining < 0L,
                isOverLimit = settings.monthlyLimit > 0L && spent > settings.monthlyLimit
            )
        }
    }
}
