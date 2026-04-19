package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals

class BankParserTest {

    @Test fun parserExposesBankIdAndParses() {
        val p: BankParser = object : BankParser {
            override val bankId = "TEST"
            override fun parse(text: String) =
                ParseResult.Success(
                    amount = 100L,
                    merchant = null,
                    balance = null,
                    accountLast4 = null,
                    confidence = 0.4f,
                    matchedPattern = "stub"
                )
        }
        assertEquals("TEST", p.bankId)
        val r = p.parse("anything")
        assertEquals(ParseResult.Success::class, r::class)
    }
}
