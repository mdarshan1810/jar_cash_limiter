package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ParseResultTest {

    @Test fun successHoldsAllFields() {
        val r = ParseResult.Success(
            amount = 50000L,
            merchant = "Zomato",
            balance = 1234500L,
            accountLast4 = "1234",
            confidence = 1.0f,
            matchedPattern = "upi_sent"
        )
        assertEquals(50000L, r.amount)
        assertEquals("Zomato", r.merchant)
        assertEquals(1234500L, r.balance)
        assertEquals("1234", r.accountLast4)
        assertEquals(1.0f, r.confidence, 0.0001f)
        assertEquals("upi_sent", r.matchedPattern)
    }

    @Test fun failureHoldsReason() {
        val r = ParseResult.Failure("no pattern matched")
        assertEquals("no pattern matched", r.reason)
    }

    @Test fun resultsAreSealedHierarchy() {
        val r: ParseResult = ParseResult.Failure("x")
        val matched = when (r) {
            is ParseResult.Success -> "success"
            is ParseResult.Failure -> "failure"
        }
        assertTrue(matched == "failure")
    }
}
