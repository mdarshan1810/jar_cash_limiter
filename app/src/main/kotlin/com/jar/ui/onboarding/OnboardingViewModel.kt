package com.jar.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jar.AppContainer
import com.jar.data.JarRepository
import com.jar.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { Welcome, Permission, Amounts, Limit, Waiting }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val startingAmountRupees: String = "",
    val periodStartDay: String = "1",
    val monthlyLimitRupees: String = "",
    val notificationAccessGranted: Boolean = false,
    val pendingAccountLast4: String? = null,
    val manualLast4: String = "",
    val amountsError: String? = null,
    val limitError: String? = null,
    val last4Error: String? = null
)

class OnboardingViewModel(
    private val settingsStore: SettingsStore,
    repository: JarRepository,
    private val permissionChecker: () -> Boolean
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        refreshNotificationAccess()
        viewModelScope.launch {
            // The first transaction picked up during onboarding (pipeline accepts any when
            // trackedAccountLast4 is null) proposes itself as the tracked account.
            repository.observeRecent(limit = 1).collect { txs ->
                val last4 = txs.firstOrNull()?.accountLast4 ?: return@collect
                if (_state.value.pendingAccountLast4 != null) return@collect
                _state.update { it.copy(pendingAccountLast4 = last4) }
                settingsStore.setTrackedAccountLast4(last4)
            }
        }
    }

    fun refreshNotificationAccess() {
        _state.update { it.copy(notificationAccessGranted = permissionChecker()) }
    }

    fun proceedFromWelcome() {
        _state.update { it.copy(step = OnboardingStep.Permission) }
    }

    fun proceedFromPermission() {
        val granted = permissionChecker()
        _state.update {
            it.copy(
                notificationAccessGranted = granted,
                step = if (granted) OnboardingStep.Amounts else it.step
            )
        }
    }

    fun updateStartingAmount(raw: String) {
        _state.update { it.copy(startingAmountRupees = raw.filter(Char::isDigit), amountsError = null) }
    }

    fun updatePeriodStartDay(raw: String) {
        _state.update { it.copy(periodStartDay = raw.filter(Char::isDigit).take(2), amountsError = null) }
    }

    fun updateMonthlyLimit(raw: String) {
        _state.update { it.copy(monthlyLimitRupees = raw.filter(Char::isDigit), limitError = null) }
    }

    fun updateManualLast4(raw: String) {
        _state.update { it.copy(manualLast4 = raw.filter(Char::isDigit).take(4), last4Error = null) }
    }

    fun saveAmountsAndAdvance() {
        val s = _state.value
        val startingRupees = s.startingAmountRupees.toLongOrNull()?.takeIf { it > 0L }
        if (startingRupees == null) {
            _state.update { it.copy(amountsError = "Enter a starting amount") }
            return
        }
        val day = s.periodStartDay.toIntOrNull()?.takeIf { it in 1..28 }
        if (day == null) {
            _state.update { it.copy(amountsError = "Period start day must be 1–28") }
            return
        }
        val startingPaise = startingRupees * 100L
        val prefilledLimitRupees = startingRupees * 80L / 100L
        viewModelScope.launch {
            settingsStore.setStartingAmount(startingPaise)
            settingsStore.setPeriodStartDay(day)
        }
        _state.update {
            it.copy(
                amountsError = null,
                monthlyLimitRupees = prefilledLimitRupees.toString(),
                step = OnboardingStep.Limit
            )
        }
    }

    fun saveLimitAndAdvance() {
        val limitRupees = _state.value.monthlyLimitRupees.toLongOrNull()?.takeIf { it >= 0L }
        if (limitRupees == null) {
            _state.update { it.copy(limitError = "Enter a monthly limit") }
            return
        }
        val limitPaise = limitRupees * 100L
        viewModelScope.launch { settingsStore.setMonthlyLimit(limitPaise) }
        _state.update { it.copy(limitError = null, step = OnboardingStep.Waiting) }
    }

    fun confirmManualLast4() {
        val s = _state.value
        if (s.manualLast4.length != 4) {
            _state.update { it.copy(last4Error = "Enter 4 digits") }
            return
        }
        viewModelScope.launch { settingsStore.setTrackedAccountLast4(s.manualLast4) }
        _state.update { it.copy(last4Error = null, pendingAccountLast4 = s.manualLast4) }
    }

    fun goBack() {
        _state.update { it.copy(step = previousStep(it.step)) }
    }

    private fun previousStep(current: OnboardingStep): OnboardingStep = when (current) {
        OnboardingStep.Welcome -> OnboardingStep.Welcome
        OnboardingStep.Permission -> OnboardingStep.Welcome
        OnboardingStep.Amounts -> OnboardingStep.Permission
        OnboardingStep.Limit -> OnboardingStep.Amounts
        OnboardingStep.Waiting -> OnboardingStep.Limit
    }

    companion object {
        fun factory(container: AppContainer, permissionChecker: () -> Boolean) =
            viewModelFactory {
                initializer {
                    OnboardingViewModel(
                        settingsStore = container.settingsStore,
                        repository = container.repository,
                        permissionChecker = permissionChecker
                    )
                }
            }
    }
}
