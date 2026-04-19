package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals

class ConfidenceTest {

    @Test fun fullFieldsScoresOne() {
        assertEquals(1.0f, scoreConfidence(merchant = "X", balance = 200L), 0.0001f)
    }

    @Test fun amountAndMerchantScoresSevenTenths() {
        assertEquals(0.7f, scoreConfidence(merchant = "X", balance = null), 0.0001f)
    }

    @Test fun amountOnlyScoresFourTenths() {
        assertEquals(0.4f, scoreConfidence(merchant = null, balance = null), 0.0001f)
    }

    @Test fun amountAndBalanceButNoMerchantStillSevenTenths() {
        assertEquals(0.7f, scoreConfidence(merchant = null, balance = 200L), 0.0001f)
    }
}
