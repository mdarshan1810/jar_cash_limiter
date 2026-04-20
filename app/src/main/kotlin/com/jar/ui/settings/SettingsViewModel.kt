package com.jar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jar.AppContainer
import com.jar.data.JarRepository
import com.jar.settings.RolloverMode
import com.jar.settings.Settings
import com.jar.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val repository: JarRepository
) : ViewModel() {

    val state: StateFlow<Settings> = settingsStore.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = Settings.DEFAULT
    )

    fun setStartingAmountRupees(rupees: Long) {
        if (rupees < 0L) return
        viewModelScope.launch { settingsStore.setStartingAmount(rupees * 100L) }
    }

    fun setMonthlyLimitRupees(rupees: Long) {
        if (rupees < 0L) return
        viewModelScope.launch { settingsStore.setMonthlyLimit(rupees * 100L) }
    }

    fun setPeriodStartDay(day: Int) {
        if (day !in 1..28) return
        viewModelScope.launch { settingsStore.setPeriodStartDay(day) }
    }

    fun setRolloverMode(mode: RolloverMode) {
        viewModelScope.launch { settingsStore.setRolloverMode(mode) }
    }

    /**
     * Clears the tracked account, which drops the user back into onboarding's Waiting step
     * (AppRoot's trackedAccountLast4 null check). Useful when the user switches accounts or
     * typed the wrong last-4 during initial onboarding.
     */
    fun relinkAccount() {
        viewModelScope.launch { settingsStore.setTrackedAccountLast4(null) }
    }

    fun resetMonth() {
        viewModelScope.launch { repository.resetMonth() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsStore = container.settingsStore,
                    repository = container.repository
                )
            }
        }
    }
}
