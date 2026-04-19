package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class AmountTest {

    @Test fun plainRupeesNoDecimals() {
        assertEquals(50000L, parseAmountToPaise("Rs.500"))
        assertEquals(50000L, parseAmountToPaise("Rs 500"))
        assertEquals(50000L, parseAmountToPaise("₹500"))
        assertEquals(50000L, parseAmountToPaise("INR 500"))
    }

    @Test fun rupeesWithDecimals() {
        assertEquals(50025L, parseAmountToPaise("Rs.500.25"))
        assertEquals(50000L, parseAmountToPaise("INR 500.00"))
        assertEquals(12345L, parseAmountToPaise("₹123.45"))
    }

    @Test fun westernCommaGrouping() {
        assertEquals(123456L, parseAmountToPaise("Rs.1,234.56"))
        assertEquals(100000000L, parseAmountToPaise("INR 1,000,000"))
    }

    @Test fun indianCommaGrouping() {
        assertEquals(12345678L, parseAmountToPaise("Rs.1,23,456.78"))
        assertEquals(1000000000L, parseAmountToPaise("₹1,00,00,000"))
    }

    @Test fun bareNumberNoSymbol() {
        assertEquals(50000L, parseAmountToPaise("500"))
        assertEquals(50025L, parseAmountToPaise("500.25"))
    }

    @Test fun returnsNullForNonsense() {
        assertNull(parseAmountToPaise(""))
        assertNull(parseAmountToPaise("abc"))
        assertNull(parseAmountToPaise("Rs."))
        assertNull(parseAmountToPaise("1.2.3"))
    }

    @Test fun handlesWhitespace() {
        assertEquals(50000L, parseAmountToPaise("  Rs. 500  "))
        assertEquals(50000L, parseAmountToPaise("INR\t500"))
    }

    @Test fun rejectsNegativeNumbers() {
        assertNull(parseAmountToPaise("-500"))
        assertNull(parseAmountToPaise("Rs.-500"))
    }

    @Test fun paiseSingleDigitPadding() {
        assertEquals(50050L, parseAmountToPaise("Rs.500.5"))
    }
}
