package com.jar.ui.jar

import com.jar.data.JarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JarViewModelTest {

    @Before fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun resetMain() {
        Dispatchers.resetMain()
    }

    private fun state(fraction: Float, spent: Long = 0L) = JarState(
        startingAmount = 1_000_000L,
        spent = spent,
        monthlyLimit = 500_000L,
        fractionRemaining = fraction,
        isOverdrawn = false,
        isOverLimit = false
    )

    @Test fun initialValueIsEmptyBeforeSourceEmits() = runTest {
        val source = MutableStateFlow(JarState.EMPTY)
        val vm = JarViewModel(source)
        assertEquals(JarState.EMPTY, vm.state.value)
    }

    @Test fun stateReflectsLatestSourceEmission() = runTest {
        val source = MutableStateFlow(state(1f))
        val vm = JarViewModel(source)

        // Subscribing triggers WhileSubscribed sharing
        val first = vm.state.first()
        assertEquals(1f, first.fractionRemaining, 0.0001f)

        source.value = state(0.3f, spent = 700_000L)
        val second = vm.state.first { it.fractionRemaining < 0.5f }
        assertEquals(700_000L, second.spent)
    }
}
