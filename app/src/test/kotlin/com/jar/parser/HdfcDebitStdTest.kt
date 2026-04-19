package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcDebitStdTest {
    private val parser = HdfcParser()

    @Test fun parsesDebitWithAvailableBalance() {
        val text = "INR 750.00 debited from A/c XX5678 on 17-04-26. " +
            "Avl Bal: INR 12,345.67. UPI Ref 987654321012."
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("debit_std", r.matchedPattern)
        assertEquals(75000L, r.amount)
        assertEquals(1234567L, r.balance)
        assertEquals("5678", r.accountLast4)
        assertEquals(null, r.merchant)
        assertEquals(0.7f, r.confidence, 0.0001f)  // balance but no merchant
    }
}
