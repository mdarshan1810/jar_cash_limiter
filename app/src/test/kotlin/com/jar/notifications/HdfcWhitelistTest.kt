package com.jar.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdfcWhitelistTest {

    private val wl = HdfcWhitelist()

    private fun raw(sender: String? = null, pkg: String = "com.example.unknown") =
        RawNotification(text = "whatever", sender = sender, packageName = pkg, postTimeMillis = 0L)

    @Test fun hdfcBankAppPackageMatches() {
        assertTrue(wl.matches(raw(pkg = "com.snapwork.hdfc")))
    }

    @Test fun googleMessagesPackageMatches() {
        assertTrue(wl.matches(raw(pkg = "com.google.android.apps.messaging")))
    }

    @Test fun samsungMessagesPackageMatches() {
        assertTrue(wl.matches(raw(pkg = "com.samsung.android.messaging")))
    }

    @Test fun plainHdfcbkSenderMatches() {
        assertTrue(wl.matches(raw(sender = "HDFCBK")))
    }

    @Test fun vmPrefixedSenderMatches() {
        // Indian telecom route prefix — VM-, VK-, AD-, JD- are all common
        assertTrue(wl.matches(raw(sender = "VM-HDFCBK")))
        assertTrue(wl.matches(raw(sender = "VK-HDFCBK")))
        assertTrue(wl.matches(raw(sender = "AD-HDFCBK")))
        assertTrue(wl.matches(raw(sender = "JD-HDFCBK")))
    }

    @Test fun caseInsensitiveSenderMatch() {
        assertTrue(wl.matches(raw(sender = "vm-hdfcbk")))
    }

    @Test fun shortHdfcVariantMatches() {
        assertTrue(wl.matches(raw(sender = "VM-HDFC")))
    }

    @Test fun unrelatedSenderDoesNotMatch() {
        assertFalse(wl.matches(raw(sender = "AMAZON")))
        assertFalse(wl.matches(raw(sender = "VM-ICICIB")))
        assertFalse(wl.matches(raw(sender = "SBIBK")))
    }

    @Test fun whatsappPackageWithBankyLookingTextDoesNotMatch() {
        assertFalse(wl.matches(raw(pkg = "com.whatsapp")))
    }

    @Test fun nullSenderWithUnknownPackageDoesNotMatch() {
        assertFalse(wl.matches(raw(sender = null, pkg = "com.example.unknown")))
    }
}
