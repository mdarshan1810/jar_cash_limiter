package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcCardSpentTest {
    private val parser = HdfcParser()

    @Test fun parsesCardSpentAtMerchant() {
        val text = "Rs.1,250.00 spent on HDFC Bank Card x9876 at Amazon Pay on 18-04-26. " +
            "Avl Lmt Rs.87,500.00. Not you? SMS BLOCK to 567676"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("card_spent", r.matchedPattern)
        assertEquals(125000L, r.amount)
        assertEquals("Amazon Pay", r.merchant)
        assertEquals("9876", r.accountLast4)
    }
}
