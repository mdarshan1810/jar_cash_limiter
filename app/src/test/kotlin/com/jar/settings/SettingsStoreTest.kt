package com.jar.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {

    @Test fun emptyPreferencesYieldsDefaults() {
        assertEquals(Settings.DEFAULT, settingsFromPreferences(mutablePreferencesOf()))
    }

    @Test fun fullyPopulatedPreferencesRoundTrip() {
        val prefs = mutablePreferencesOf().apply {
            set(SettingsKeys.startingAmount, 2_500_000L)
            set(SettingsKeys.monthlyLimit, 2_000_000L)
            set(SettingsKeys.periodStartDay, 5)
            set(SettingsKeys.rolloverMode, RolloverMode.ROLLOVER.name)
            set(SettingsKeys.trackedBank, "HDFC")
            set(SettingsKeys.trackedAccountLast4, "1234")
            set(SettingsKeys.lastManualResetAt, 1_700_000_000_000L)
        }
        val s = settingsFromPreferences(prefs)
        assertEquals(2_500_000L, s.startingAmount)
        assertEquals(2_000_000L, s.monthlyLimit)
        assertEquals(5, s.periodStartDay)
        assertEquals(RolloverMode.ROLLOVER, s.rolloverMode)
        assertEquals("HDFC", s.trackedBank)
        assertEquals("1234", s.trackedAccountLast4)
        assertEquals(1_700_000_000_000L, s.lastManualResetAt)
    }

    @Test fun unknownRolloverModeFallsBackToDefault() {
        val prefs = mutablePreferencesOf().apply {
            set(SettingsKeys.rolloverMode, "NOT_A_REAL_MODE")
        }
        assertEquals(RolloverMode.RESET, settingsFromPreferences(prefs).rolloverMode)
    }

    @Test fun missingOptionalFieldsRemainNull() {
        val prefs = mutablePreferencesOf().apply {
            set(SettingsKeys.startingAmount, 1_000L)
        }
        val s = settingsFromPreferences(prefs)
        assertEquals(null, s.trackedAccountLast4)
        assertEquals(null, s.lastManualResetAt)
    }
}
