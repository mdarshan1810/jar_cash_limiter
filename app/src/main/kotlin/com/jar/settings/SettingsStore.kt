package com.jar.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object SettingsKeys {
    val startingAmount = longPreferencesKey("starting_amount")
    val periodStartDay = intPreferencesKey("period_start_day")
    val monthlyLimit = longPreferencesKey("monthly_limit")
    val rolloverMode = stringPreferencesKey("rollover_mode")
    val trackedBank = stringPreferencesKey("tracked_bank")
    val trackedAccountLast4 = stringPreferencesKey("tracked_account_last4")
    val lastManualResetAt = longPreferencesKey("last_manual_reset_at")
}

internal fun settingsFromPreferences(prefs: Preferences): Settings = Settings(
    startingAmount = prefs[SettingsKeys.startingAmount] ?: Settings.DEFAULT.startingAmount,
    periodStartDay = prefs[SettingsKeys.periodStartDay] ?: Settings.DEFAULT.periodStartDay,
    monthlyLimit = prefs[SettingsKeys.monthlyLimit] ?: Settings.DEFAULT.monthlyLimit,
    rolloverMode = prefs[SettingsKeys.rolloverMode]?.let(::parseRolloverMode)
        ?: Settings.DEFAULT.rolloverMode,
    trackedBank = prefs[SettingsKeys.trackedBank] ?: Settings.DEFAULT.trackedBank,
    trackedAccountLast4 = prefs[SettingsKeys.trackedAccountLast4],
    lastManualResetAt = prefs[SettingsKeys.lastManualResetAt]
)

private fun parseRolloverMode(raw: String): RolloverMode =
    runCatching { RolloverMode.valueOf(raw) }.getOrDefault(Settings.DEFAULT.rolloverMode)

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val flow: Flow<Settings> = dataStore.data.map(::settingsFromPreferences)

    suspend fun setStartingAmount(value: Long) = mutate { it[SettingsKeys.startingAmount] = value }
    suspend fun setMonthlyLimit(value: Long) = mutate { it[SettingsKeys.monthlyLimit] = value }
    suspend fun setPeriodStartDay(value: Int) = mutate { it[SettingsKeys.periodStartDay] = value }
    suspend fun setRolloverMode(value: RolloverMode) = mutate {
        it[SettingsKeys.rolloverMode] = value.name
    }
    suspend fun setTrackedBank(value: String) = mutate { it[SettingsKeys.trackedBank] = value }
    suspend fun setTrackedAccountLast4(value: String?) = mutate {
        if (value == null) it.remove(SettingsKeys.trackedAccountLast4)
        else it[SettingsKeys.trackedAccountLast4] = value
    }
    suspend fun setLastManualResetAt(value: Long?) = mutate {
        if (value == null) it.remove(SettingsKeys.lastManualResetAt)
        else it[SettingsKeys.lastManualResetAt] = value
    }

    private suspend inline fun mutate(crossinline block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }
}
