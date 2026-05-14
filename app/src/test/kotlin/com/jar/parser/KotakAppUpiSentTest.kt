package com.jar.parser

import com.jar.parser.kotak.KotakParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the in-app notification format the Kotak banking app posts directly (separate
 * from the bank's SMS). Observed live shape:
 *   title: "₹1.00 sent via UPI"
 *   body:  "Amount debited from XX2582. Check out details."
 * `JarNotificationListener.toRawNotification` joins these with " · ", so the parser sees
 * the concatenated form below. Ordering matters: this pattern must precede
 * kotak_amount_only so it wins on amount + last4 (otherwise the fallback grabs amount only).
 */
class KotakAppUpiSentTest {

    private val parser = KotakParser()

    @Test fun parsesInAppUpiSentNotification() {
        val text = "₹1.00 sent via UPI · Amount debited from XX2582. Check out details. " +
            "· Amount debited from XX2582. Check out details."
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("kotak_app_upi_sent", r.matchedPattern)
        assertEquals(100L, r.amount)
        assertEquals("2582", r.accountLast4)
        assertEquals(null, r.merchant)
        assertEquals(null, r.balance)
        // amount + accountLast4 alone doesn't bump confidence — scoreConfidence keys off
        // merchant/balance presence only. The account_last4 still drives the pipeline's
        // account-match gate, so a 0.4-confidence row with the right last4 is acceptable.
        assertTrue("expected confidence 0.4, got ${r.confidence}", r.confidence >= 0.4f)
    }

    @Test fun parsesLargerAmountInAppNotification() {
        val text = "₹1,250.50 sent via UPI · Amount debited from XX9999."
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("kotak_app_upi_sent", r.matchedPattern)
        assertEquals(125050L, r.amount)
        assertEquals("9999", r.accountLast4)
    }

    @Test fun appPatternBeatsAmountOnlyFallback() {
        // Without the new pattern, kotak_amount_only would catch ₹100 first and lose the
        // account info. The named pattern's higher specificity (and earlier position) must
        // win so account routing works correctly downstream.
        val text = "₹100 sent via UPI · Amount debited from XX1234."
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("kotak_app_upi_sent", r.matchedPattern)
        assertEquals("1234", r.accountLast4)
    }
}
