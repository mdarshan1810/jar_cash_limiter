package com.jar.parser

sealed class ParseResult {
    data class Success(
        val amount: Long,
        val merchant: String?,
        val balance: Long?,
        val accountLast4: String?,
        val confidence: Float,
        val matchedPattern: String
    ) : ParseResult()

    data class Failure(val reason: String) : ParseResult()
}
