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

    /** Patterns are tried in declaration order; the first matching pattern wins. `amount_only` must remain last. */
    internal val patterns: List<Pattern> = listOf(
        Pattern(
            name = "upi_sent",
            regex = Regex(
                """Sent\s+Rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s+from\s+HDFC\s+Bank\s+A/?C\s+[x*]?(\d{4,6})\s+to\s+(.+?)(?:\s+on\s+|\s*\.)""",
                RegexOption.IGNORE_CASE
            )
        ),
        Pattern(
            name = "card_spent",
            regex = Regex(
                """Rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s+spent\s+on\s+HDFC\s+Bank\s+Card\s+[x*]?(\d{4,6})\s+at\s+(.+?)(?:\s+on\s+|\s*\.)""",
                RegexOption.IGNORE_CASE
            )
        ),
        Pattern(
            name = "debit_std",
            regex = Regex(
                """INR\s+([\d,]+(?:\.\d{1,2})?)\s+debited\s+from\s+A/c\s+[X*]*(\d{4,6}).*?Avl\s+Bal:\s+INR\s+([\d,]+(?:\.\d{1,2})?)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ),
        Pattern(
            name = "upi_slash",
            regex = Regex(
                """Rs\.?\s*([\d,]+(?:\.\d{1,2})?).*?UPI/\d+/([^/]+)/""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ),
        Pattern(
            name = "amount_only",
            regex = Regex(
                """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )
    )

    /** Returns null if the pattern name is unrecognised, causing parse() to skip to the next pattern. */
    internal fun extract(pattern: Pattern, match: MatchResult): Extracted? {
        return when (pattern.name) {
            "upi_sent" -> {
                val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
                val last4 = match.groupValues[2].takeLast(4)
                val merchant = match.groupValues[3].trim().trimEnd('.')
                Extracted(amount = amount, merchant = merchant, balance = null, accountLast4 = last4)
            }
            "card_spent" -> {
                val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
                val last4 = match.groupValues[2].takeLast(4)
                val merchant = match.groupValues[3].trim().trimEnd('.')
                Extracted(amount = amount, merchant = merchant, balance = null, accountLast4 = last4)
            }
            "debit_std" -> {
                val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
                val last4 = match.groupValues[2].takeLast(4)
                val balance = com.jar.parser.parseAmountToPaise(match.groupValues[3])
                Extracted(amount = amount, merchant = null, balance = balance, accountLast4 = last4)
            }
            "upi_slash" -> {
                val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
                val merchant = match.groupValues[2].trim().ifBlank { null }
                Extracted(amount = amount, merchant = merchant, balance = null, accountLast4 = null)
            }
            "amount_only" -> {
                val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
                Extracted(amount = amount, merchant = null, balance = null, accountLast4 = null)
            }
            else -> null
        }
    }

    override fun parse(text: String): ParseResult {
        for (p in patterns) {
            val m = p.regex.find(text) ?: continue
            val ex = extract(p, m) ?: continue
            return ParseResult.Success(
                amount = ex.amount,
                merchant = ex.merchant,
                balance = ex.balance,
                accountLast4 = ex.accountLast4,
                confidence = scoreConfidence(ex.merchant, ex.balance),
                matchedPattern = p.name
            )
        }
        return ParseResult.Failure("no pattern matched")
    }
}
