package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcUpiSlashTest {
    private val parser = HdfcParser()

    @Test fun parsesUpiSlashFormat() {
        val text = "UPI txn of Rs.299.00 debited. UPI/427054887123/SWIGGY/swiggy@ybl on 16-04-26. -HDFC Bank"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("upi_slash", r.matchedPattern)
        assertEquals(29900L, r.amount)
        assertEquals("SWIGGY", r.merchant)
        assertEquals(null, r.accountLast4)
        assertEquals(0.7f, r.confidence, 0.0001f)
    }
}
