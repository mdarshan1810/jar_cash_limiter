package com.jar.parser.kotak

import com.jar.parser.BankParser
import com.jar.parser.ParseResult
import com.jar.parser.parseAmountToPaise
import com.jar.parser.scoreConfidence

class KotakParser : BankParser {
    override val bankId: String = "KOTAK"

    internal data class Pattern(val name: String, val regex: Regex)

    internal data class Extracted(
        val amount: Long,
        val merchant: String?,
        val balance: Long?,
        val accountLast4: String?
    )

    /**
     * Patterns are tried in declaration order; the first matching pattern wins.
     * `kotak_amount_only` must remain last — it's the catch-all fallback.
     *
     * Naming convention prefixes every pattern with `kotak_` so a future pattern-precedence
     * audit across banks can identify the bank from the pattern alone.
     */
    internal val patterns: List<Pattern> = listOf(
        Pattern(
            name = "kotak_upi_sent",
            regex = Regex(
                """Sent\s+Rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s+from\s+Kotak\s+Bank\s+A/?C\s+[xX*]?(\d{4,6})\s+to\s+(\S+?)\s+on\s+""",
                RegexOption.IGNORE_CASE
            )
        ),
        Pattern(
            // In-app notification posted by the Kotak banking app (separate from the SMS).
            // Title is the amount, body carries the account. Captures both from the joined
            // " · " representation that JarNotificationListener.toRawNotification produces.
            name = "kotak_app_upi_sent",
            regex = Regex(
                """(?:₹|Rs\.?|INR)\s*([\d,]+(?:\.\d{1,2})?)\s+sent\s+via\s+UPI.*?Amount\s+debited\s+from\s+[X*]+(\d{4,6})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ),
        Pattern(
            name = "kotak_amount_only",
            regex = Regex(
                """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
                RegexOption.IGNORE_CASE
            )
        )
    )

    internal fun extract(pattern: Pattern, match: MatchResult): Extracted? {
        return when (pattern.name) {
            "kotak_upi_sent" -> {
                val amount = parseAmountToPaise(match.groupValues[1]) ?: return null
                val last4 = match.groupValues[2].takeLast(4)
                val merchant = match.groupValues[3].trim().trimEnd('.').ifBlank { null }
                Extracted(amount = amount, merchant = merchant, balance = null, accountLast4 = last4)
            }
            "kotak_app_upi_sent" -> {
                val amount = parseAmountToPaise(match.groupValues[1]) ?: return null
                val last4 = match.groupValues[2].takeLast(4)
                Extracted(amount = amount, merchant = null, balance = null, accountLast4 = last4)
            }
            "kotak_amount_only" -> {
                val amount = parseAmountToPaise(match.groupValues[1]) ?: return null
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
