package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protects the load-bearing invariant that specific patterns win over generic ones.
 * A text that matches both `upi_sent` and `amount_only` must resolve to `upi_sent` —
 * otherwise every precise extraction (merchant, last4) collapses to the 0.4-confidence
 * fallback and the pipeline routes it to unparsed_notifications instead of inserting.
 */
class HdfcPatternPrecedenceTest {

    private val parser = HdfcParser()

    @Test fun upiSentWinsOverAmountOnly() {
        val text = "Sent Rs.420 from HDFC Bank A/C x1234 to Zomato on 12/04/26"
        val result = parser.parse(text) as ParseResult.Success
        assertEquals("upi_sent", result.matchedPattern)
        assertEquals("1234", result.accountLast4)
    }

    @Test fun cardSpentWinsOverAmountOnly() {
        val text = "Rs.1500 spent on HDFC Bank Card x5678 at Amazon on 12-04-26"
        val result = parser.parse(text) as ParseResult.Success
        assertEquals("card_spent", result.matchedPattern)
    }

    @Test fun debitStdWinsOverAmountOnly() {
        val text = "INR 500.00 debited from A/c XX1234 on 12-04-26. Avl Bal: INR 10,000.00"
        val result = parser.parse(text) as ParseResult.Success
        assertEquals("debit_std", result.matchedPattern)
        assertTrue("debit_std should carry balance", result.balance != null)
    }

    @Test fun amountOnlyFiresOnlyWhenNoPrecisePatternMatches() {
        // Bare amount with no structural keywords → falls through to amount_only
        val text = "Rs.250 — tap for details"
        val result = parser.parse(text) as ParseResult.Success
        assertEquals("amount_only", result.matchedPattern)
        assertEquals(0.4f, result.confidence, 0.001f)
    }
}
