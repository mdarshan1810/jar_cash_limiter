package com.jar.parser

import com.jar.parser.kotak.KotakParser
import org.junit.Assert.assertEquals
import org.junit.Test

class KotakAmountOnlyTest {

    private val parser = KotakParser()

    @Test fun fallbackExtractsAmountOnlyWithLowConfidence() {
        // Mandate / pre-debit alert — no merchant, no account info, but has Rs.X.
        val text = "Dear Customer, your standing instruction of Rs.5000 with Kotak Bank " +
            "is scheduled for 20-05-26. -Kotak Bank"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("kotak_amount_only", r.matchedPattern)
        assertEquals(500000L, r.amount)
        assertEquals(null, r.merchant)
        assertEquals(null, r.balance)
        assertEquals(0.4f, r.confidence, 0.0001f)
    }

    @Test fun returnsFailureWhenNoAmountAnywhere() {
        val text = "Dear Kotak Customer, please update your KYC at your earliest convenience."
        val r = parser.parse(text)
        assert(r is ParseResult.Failure) { "expected Failure, got $r" }
    }
}
