package com.jar.parser

/**
 * Spec §5.3. Scoring:
 *  - 1.0 = amount + merchant + balance all present
 *  - 0.7 = amount + at least one of merchant/balance
 *  - 0.4 = amount only
 */
fun scoreConfidence(merchant: String?, balance: Long?): Float {
    val hasMerchant = !merchant.isNullOrBlank()
    val hasBalance = balance != null
    return when {
        hasMerchant && hasBalance -> 1.0f
        hasMerchant || hasBalance -> 0.7f
        else -> 0.4f
    }
}
