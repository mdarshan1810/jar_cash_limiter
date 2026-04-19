package com.jar.parser.hdfc

import com.jar.parser.BankParser
import com.jar.parser.ParseResult
import com.jar.parser.scoreConfidence

class HdfcParser : BankParser {
    override val bankId: String = "HDFC"

    /** A named regex pattern. Patterns are tried in the order they appear in `patterns`. */
    internal data class Pattern(val name: String, val regex: Regex)

    /** Extracted fields. Amount is required; everything else is optional. */
    internal data class Extracted(
        val amount: Long,
        val merchant: String?,
        val balance: Long?,
        val accountLast4: String?
    )

    /** Filled in by Tasks 8–12. */
    internal val patterns: List<Pattern> = emptyList()

    /** Filled in by Tasks 8–12 — one `extractFor...` per named pattern. */
    internal fun extract(pattern: Pattern, match: MatchResult): Extracted? = null

    override fun parse(text: String): ParseResult {
        for (p in patterns) {
            val m = p.regex.find(text) ?: continue
            val ex = extract(p, m) ?: continue
            return ParseResult.Success(
                amount = ex.amount,
                merchant = ex.merchant,
                balance = ex.balance,
                accountLast4 = ex.accountLast4,
                confidence = scoreConfidence(ex.amount, ex.merchant, ex.balance),
                matchedPattern = p.name
            )
        }
        return ParseResult.Failure("no pattern matched")
    }
}
