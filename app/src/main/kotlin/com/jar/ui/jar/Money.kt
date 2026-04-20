package com.jar.ui.jar

import kotlin.math.abs

/**
 * Paise → Indian-grouped rupee string. Whole rupees only; the jar is not a bookkeeping
 * surface. E.g. 2_500_000 → "₹25,000", 25_000_000 → "₹2,50,000", negative for overdraw.
 */
fun formatRupees(paise: Long): String {
    val rupees = paise / 100
    val grouped = addIndianGrouping(abs(rupees).toString())
    return if (rupees < 0) "-₹$grouped" else "₹$grouped"
}

private fun addIndianGrouping(digits: String): String {
    if (digits.length <= 3) return digits
    val lastThree = digits.takeLast(3)
    val rest = digits.dropLast(3)
    val groups = mutableListOf<String>()
    var remaining = rest
    while (remaining.length > 2) {
        groups.add(0, remaining.takeLast(2))
        remaining = remaining.dropLast(2)
    }
    if (remaining.isNotEmpty()) groups.add(0, remaining)
    return groups.joinToString(",") + "," + lastThree
}
