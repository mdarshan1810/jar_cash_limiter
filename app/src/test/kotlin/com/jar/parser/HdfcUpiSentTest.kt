package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class HdfcUpiSentTest {

    private val parser = HdfcParser()

    @Test fun parsesSentRsToMerchantWithAccountLast4() {
        val text = "Sent Rs.420 from HDFC Bank A/C x1234 to Zomato Limited on 19-04-26. " +
            "UPI Ref 123456789012. Not you? Call 18002586161"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("upi_sent", r.matchedPattern)
        assertEquals(42000L, r.amount)
        assertEquals("Zomato Limited", r.merchant)
        assertEquals("1234", r.accountLast4)
        assertEquals(null, r.balance)
        assertTrue("confidence >= 0.7 expected, got ${r.confidence}", r.confidence >= 0.7f)
    }
}
