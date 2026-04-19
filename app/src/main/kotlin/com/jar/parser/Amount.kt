package com.jar.parser

/**
 * Parses an amount string like "Rs.1,23,456.78" into paise (Long).
 * Returns null for malformed or negative inputs.
 * Supports: Rs., Rs , INR, ₹, bare numbers, western ("1,234,567") and Indian ("1,23,45,678") grouping.
 */
fun parseAmountToPaise(raw: String): Long? {
    if (raw.isBlank()) return null
    val stripped = raw.trim()
        .replace("Rs.", "", ignoreCase = true)
        .replace("Rs ", "", ignoreCase = true)
        .replace("INR", "", ignoreCase = true)
        .replace("₹", "")
        .replace(",", "")
        .trim()

    if (stripped.isEmpty() || stripped.startsWith("-")) return null

    val parts = stripped.split(".")
    return when (parts.size) {
        1 -> parts[0].toLongOrNull()?.let { it * 100 }
        2 -> {
            val rupees = parts[0].toLongOrNull() ?: return null
            val paiseStr = parts[1]
            if (paiseStr.isEmpty() || paiseStr.length > 2 || !paiseStr.all { it.isDigit() }) return null
            val paise = paiseStr.padEnd(2, '0').toLongOrNull() ?: return null
            rupees * 100 + paise
        }
        else -> null
    }
}
