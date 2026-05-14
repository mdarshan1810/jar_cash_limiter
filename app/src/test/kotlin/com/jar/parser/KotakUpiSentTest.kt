package com.jar.parser

import com.jar.parser.kotak.KotakParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotakUpiSentTest {

    private val parser = KotakParser()

    @Test fun parsesRealWorldKotakUpiSentToHandle() {
        // Verbatim shape from a live AX-KOTAKB-S sender.
        val text = "Sent Rs.75.00 from Kotak Bank AC X9885 to paytm.s1zwuhv@pty " +
            "on 11-05-26.UPI Ref 613172441609. Not you, https://kotak.com/KBANKT/Fraud"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("kotak_upi_sent", r.matchedPattern)
        assertEquals(7500L, r.amount)
        assertEquals("paytm.s1zwuhv@pty", r.merchant)
        assertEquals("9885", r.accountLast4)
        assertEquals(null, r.balance)
        assertTrue("confidence >= 0.7 expected, got ${r.confidence}", r.confidence >= 0.7f)
    }

    @Test fun parsesUpiSentWithLowercaseXAccountPrefix() {
        // Defensive: future Kotak revisions might use lowercase x like HDFC.
        val text = "Sent Rs.420 from Kotak Bank AC x1234 to merchant@upi on 12-05-26.UPI Ref 999"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("kotak_upi_sent", r.matchedPattern)
        assertEquals("1234", r.accountLast4)
    }

    @Test fun parsesUpiSentWithoutDotAfterDate() {
        // Some Kotak SMS use a space before "UPI Ref" instead of joining with a period.
        val text = "Sent Rs.99.50 from Kotak Bank AC X5678 to test@ybl on 13-05-26 UPI Ref 123"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("kotak_upi_sent", r.matchedPattern)
        assertEquals(9950L, r.amount)
        assertEquals("test@ybl", r.merchant)
    }
}
