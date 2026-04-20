package com.jar.ui.limit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jar.AppContainer
import com.jar.data.JarRepository
import com.jar.data.TransactionEntity
import com.jar.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecentTransactionUi(
    val id: Long,
    val amountPaise: Long,
    val merchantRaw: String?,
    val timestamp: Long
)

data class LimitScreenState(
    val monthlyLimitPaise: Long,
    val spentPaise: Long,
    val remainingPaise: Long,
    val isOverLimit: Boolean,
    val progressFraction: Float,
    val recent: List<RecentTransactionUi>
) {
    companion object {
        val EMPTY = LimitScreenState(
            monthlyLimitPaise = 0L,
            spentPaise = 0L,
            remainingPaise = 0L,
            isOverLimit = false,
            progressFraction = 0f,
            recent = emptyList()
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LimitViewModel(
    private val repository: JarRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val state: StateFlow<LimitScreenState> = combine(
        repository.observeJarState(),
        repository.observeRecent(RECENT_LIMIT)
    ) { jarState, recent ->
        LimitScreenState(
            monthlyLimitPaise = jarState.monthlyLimit,
            spentPaise = jarState.spent,
            remainingPaise = jarState.monthlyLimit - jarState.spent,
            isOverLimit = jarState.isOverLimit,
            progressFraction = if (jarState.monthlyLimit > 0L) {
                (jarState.spent.toFloat() / jarState.monthlyLimit.toFloat()).coerceAtLeast(0f)
            } else 0f,
            recent = recent.map(TransactionEntity::toUi)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LimitScreenState.EMPTY
    )

    fun saveLimit(rupees: Long) {
        viewModelScope.launch { settingsStore.setMonthlyLimit(rupees * 100L) }
    }

    fun resetMonth() {
        viewModelScope.launch { repository.resetMonth() }
    }

    companion object {
        private const val RECENT_LIMIT = 10
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                LimitViewModel(
                    repository = container.repository,
                    settingsStore = container.settingsStore
                )
            }
        }
    }
}

private fun TransactionEntity.toUi(): RecentTransactionUi = RecentTransactionUi(
    id = id,
    amountPaise = amount,
    merchantRaw = merchantRaw,
    timestamp = timestamp
)
