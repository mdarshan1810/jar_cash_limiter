package com.jar.ui.settings

import com.jar.data.AppDatabase
import com.jar.data.InMemoryDataStore
import com.jar.data.JarRepository
import com.jar.data.buildInMemoryDb
import com.jar.settings.RolloverMode
import com.jar.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SettingsViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: JarRepository
    private lateinit var vm: SettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = buildInMemoryDb()
        settings = SettingsStore(InMemoryDataStore())
        repo = JarRepository(
            txDao = db.transactionDao(),
            unparsedDao = db.unparsedNotificationDao(),
            settingsStore = settings
        )
        vm = SettingsViewModel(settings, repo)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun setStartingAmountPersistsInPaise() = runTest {
        vm.setStartingAmountRupees(3000L)
        assertEquals(3000L * 100L, settings.flow.first().startingAmount)
    }

    @Test fun setStartingAmountRejectsNegative() = runTest {
        settings.setStartingAmount(1_000_000L)
        vm.setStartingAmountRupees(-500L)
        assertEquals(1_000_000L, settings.flow.first().startingAmount)
    }

    @Test fun setMonthlyLimitPersistsInPaise() = runTest {
        vm.setMonthlyLimitRupees(2500L)
        assertEquals(2500L * 100L, settings.flow.first().monthlyLimit)
    }

    @Test fun setPeriodStartDayAcceptsInRange() = runTest {
        vm.setPeriodStartDay(15)
        assertEquals(15, settings.flow.first().periodStartDay)
    }

    @Test fun setPeriodStartDayRejectsOutOfRange() = runTest {
        settings.setPeriodStartDay(5)
        vm.setPeriodStartDay(0)
        vm.setPeriodStartDay(29)
        vm.setPeriodStartDay(-3)
        assertEquals(5, settings.flow.first().periodStartDay)
    }

    @Test fun setRolloverModePersists() = runTest {
        vm.setRolloverMode(RolloverMode.ROLLOVER)
        assertEquals(RolloverMode.ROLLOVER, settings.flow.first().rolloverMode)
        vm.setRolloverMode(RolloverMode.RESET)
        assertEquals(RolloverMode.RESET, settings.flow.first().rolloverMode)
    }

    @Test fun relinkAccountClearsTrackedLast4() = runTest {
        settings.setTrackedAccountLast4("1234")
        vm.relinkAccount()
        assertNull(settings.flow.first().trackedAccountLast4)
    }

    @Test fun resetMonthWritesLastManualResetAt() = runTest {
        vm.resetMonth()
        assertNotNull(settings.flow.first().lastManualResetAt)
    }
}
