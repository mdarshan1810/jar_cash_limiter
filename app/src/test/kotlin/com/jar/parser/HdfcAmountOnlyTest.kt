package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcAmountOnlyTest {
    private val parser = HdfcParser()

    @Test fun fallbackExtractsAmountOnlyWithLowConfidence() {
        val text = "Dear Customer, your mandate of Rs.5000 for insurance premium is scheduled for 20-04-26. -HDFC Bank"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("amount_only", r.matchedPattern)
        assertEquals(500000L, r.amount)
        assertEquals(null, r.merchant)
        assertEquals(null, r.balance)
        assertEquals(0.4f, r.confidence, 0.0001f)
    }

    @Test fun returnsFailureWhenNoAmountAnywhere() {
        val text = "Dear Customer, please update your KYC at your earliest convenience. -HDFC Bank"
        val r = parser.parse(text)
        assert(r is ParseResult.Failure) { "expected Failure, got $r" }
    }
}
