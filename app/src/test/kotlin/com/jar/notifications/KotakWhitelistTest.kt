package com.jar.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotakWhitelistTest {

    private val wl = KotakWhitelist()

    private fun raw(sender: String? = null, pkg: String = "com.example.unknown") =
        RawNotification(text = "whatever", sender = sender, packageName = pkg, postTimeMillis = 0L)

    @Test fun kotakBankAppPackageMatches() {
        assertTrue(wl.matches(raw(pkg = "com.msf.kbank.mobile")))
    }

    @Test fun kotak811AppPackageMatches() {
        assertTrue(wl.matches(raw(pkg = "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge")))
    }

    @Test fun googleMessagesPackageMatches() {
        assertTrue(wl.matches(raw(pkg = "com.google.android.apps.messaging")))
    }

    @Test fun samsungMessagesPackageMatches() {
        assertTrue(wl.matches(raw(pkg = "com.samsung.android.messaging")))
    }

    @Test fun plainKotakbSenderMatches() {
        assertTrue(wl.matches(raw(sender = "KOTAKB")))
    }

    @Test fun standardOperatorPrefixedSenderMatches() {
        // Indian telecom DLT routes prepend two-letter operator codes.
        assertTrue(wl.matches(raw(sender = "VM-KOTAKB")))
        assertTrue(wl.matches(raw(sender = "VK-KOTAKB")))
        assertTrue(wl.matches(raw(sender = "AD-KOTAKB")))
        assertTrue(wl.matches(raw(sender = "JD-KOTAKB")))
    }

    @Test fun realWorldAxKotakbSSenderMatches() {
        // Observed live: AX-KOTAKB-S (trailing -S transactional-route suffix).
        // Matcher must accept the operator-prefixed bank token even when followed by
        // an additional hyphen-suffixed route designator.
        assertTrue(wl.matches(raw(sender = "AX-KOTAKB-S")))
    }

    @Test fun caseInsensitiveSenderMatch() {
        assertTrue(wl.matches(raw(sender = "vm-kotakb")))
    }

    @Test fun shortKotakVariantMatches() {
        assertTrue(wl.matches(raw(sender = "VM-KOTAK")))
    }

    @Test fun kmblShorthandSenderMatches() {
        // KMBL = Kotak Mahindra Bank Ltd, used in some SMS routes.
        assertTrue(wl.matches(raw(sender = "VM-KMBL")))
    }

    @Test fun unrelatedSenderDoesNotMatch() {
        assertFalse(wl.matches(raw(sender = "AMAZON")))
        assertFalse(wl.matches(raw(sender = "VM-ICICIB")))
        assertFalse(wl.matches(raw(sender = "SBIBK")))
    }

    @Test fun hdfcSenderDoesNotMatch() {
        assertFalse(wl.matches(raw(sender = "VM-HDFCBK")))
        assertFalse(wl.matches(raw(sender = "AD-HDFCBK")))
    }

    @Test fun lookAlikeSendersDoNotMatch() {
        // Pure suffix collisions without the "-" separator shouldn't pass.
        assertFalse(wl.matches(raw(sender = "XKOTAKB")))
        assertFalse(wl.matches(raw(sender = "COOLKOTAKB")))
    }

    @Test fun nullSenderWithUnknownPackageDoesNotMatch() {
        assertFalse(wl.matches(raw(sender = null, pkg = "com.example.unknown")))
    }
}
