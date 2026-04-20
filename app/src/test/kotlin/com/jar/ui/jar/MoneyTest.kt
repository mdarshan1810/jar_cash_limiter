package com.jar.ui.jar

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test fun zero() = assertEquals("₹0", formatRupees(0L))
    @Test fun singleDigitRupees() = assertEquals("₹7", formatRupees(700L))
    @Test fun underThousand() = assertEquals("₹500", formatRupees(50_000L))
    @Test fun thousand() = assertEquals("₹1,000", formatRupees(100_000L))
    @Test fun tenThousand() = assertEquals("₹10,000", formatRupees(1_000_000L))
    @Test fun oneLakh() = assertEquals("₹1,00,000", formatRupees(10_000_000L))
    @Test fun tenLakh() = assertEquals("₹10,00,000", formatRupees(100_000_000L))
    @Test fun oneCrore() = assertEquals("₹1,00,00,000", formatRupees(1_000_000_000L))
    @Test fun negativeForOverdraw() = assertEquals("-₹1,500", formatRupees(-150_000L))
    @Test fun paiseTruncatedToWholeRupees() = assertEquals("₹42", formatRupees(4299L))
}
