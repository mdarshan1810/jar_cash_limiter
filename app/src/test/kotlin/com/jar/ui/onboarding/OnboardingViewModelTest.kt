package com.jar.ui.onboarding

import com.jar.data.AppDatabase
import com.jar.data.InMemoryDataStore
import com.jar.data.JarRepository
import com.jar.data.TransactionEntity
import com.jar.data.buildInMemoryDb
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class OnboardingViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: JarRepository
    private var permissionGranted = false

    @Before fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = buildInMemoryDb()
        settings = SettingsStore(InMemoryDataStore())
        repo = JarRepository(
            txDao = db.transactionDao(),
            unparsedDao = db.unparsedNotificationDao(),
            settingsStore = settings
        )
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = OnboardingViewModel(
        settingsStore = settings,
        repository = repo,
        permissionChecker = { permissionGranted }
    )

    @Test fun startsAtWelcome() {
        assertEquals(OnboardingStep.Welcome, vm().state.value.step)
    }

    @Test fun proceedFromWelcomeAdvancesToPermission() {
        val v = vm()
        v.proceedFromWelcome()
        assertEquals(OnboardingStep.Permission, v.state.value.step)
    }

    @Test fun proceedFromPermissionGuardedByChecker() {
        val v = vm()
        v.proceedFromWelcome()
        permissionGranted = false
        v.proceedFromPermission()
        assertEquals(OnboardingStep.Permission, v.state.value.step)
        assertFalse(v.state.value.notificationAccessGranted)

        permissionGranted = true
        v.proceedFromPermission()
        assertEquals(OnboardingStep.Amounts, v.state.value.step)
        assertTrue(v.state.value.notificationAccessGranted)
    }

    @Test fun amountsValidationRejectsZeroOrBlank() {
        val v = vm()
        v.saveAmountsAndAdvance()
        assertNotNull(v.state.value.amountsError)
        assertEquals(OnboardingStep.Welcome, v.state.value.step)

        v.updateStartingAmount("0")
        v.saveAmountsAndAdvance()
        assertNotNull(v.state.value.amountsError)
    }

    @Test fun amountsValidationRejectsPeriodDayOutsideRange() {
        val v = vm()
        v.updateStartingAmount("3000")
        v.updatePeriodStartDay("31")
        v.saveAmountsAndAdvance()
        assertNotNull(v.state.value.amountsError)
    }

    @Test fun amountsSavePersistsAndPrefillsLimitAt80Pct() = runTest {
        val v = vm()
        v.updateStartingAmount("30000")
        v.updatePeriodStartDay("5")
        v.saveAmountsAndAdvance()
        assertEquals(OnboardingStep.Limit, v.state.value.step)
        assertEquals("24000", v.state.value.monthlyLimitRupees)  // 80% of 30000

        val persisted = settings.flow.first()
        assertEquals(30_000L * 100L, persisted.startingAmount)
        assertEquals(5, persisted.periodStartDay)
    }

    @Test fun limitSavePersistsAndAdvancesToWaiting() = runTest {
        val v = vm()
        v.updateMonthlyLimit("20000")
        v.saveLimitAndAdvance()
        assertEquals(OnboardingStep.Waiting, v.state.value.step)
        assertEquals(20_000L * 100L, settings.flow.first().monthlyLimit)
    }

    @Test fun inputSanitizationDropsNonDigits() {
        val v = vm()
        v.updateStartingAmount("abc3000xyz")
        assertEquals("3000", v.state.value.startingAmountRupees)

        v.updateManualLast4("12ab34ef")
        assertEquals("1234", v.state.value.manualLast4)
    }

    @Test fun manualLast4RequiresFourDigits() = runTest {
        val v = vm()
        v.updateManualLast4("12")
        v.confirmManualLast4()
        assertNotNull(v.state.value.last4Error)
        assertNull(settings.flow.first().trackedAccountLast4)

        v.updateManualLast4("1234")
        v.confirmManualLast4()
        assertNull(v.state.value.last4Error)
        assertEquals("1234", settings.flow.first().trackedAccountLast4)
        assertEquals("1234", v.state.value.pendingAccountLast4)
    }

    @Test fun firstTransactionCapturesAccountAndWritesSettings() = runTest {
        val v = vm()
        // Simulate a transaction arriving during onboarding
        repo.insertTransaction(
            TransactionEntity(
                amount = 42_000L,
                merchantRaw = "Zomato",
                timestamp = 1_700_000_000_000L,
                sourceSmsHash = "hash1",
                parseConfidence = 1.0f,
                accountLast4 = "5678"
            )
        )
        // Give the collector a tick
        val latest = v.state.first { it.pendingAccountLast4 != null }
        assertEquals("5678", latest.pendingAccountLast4)
        assertEquals("5678", settings.flow.first().trackedAccountLast4)
    }

    @Test fun goBackNavigatesOneStepPrevious() {
        val v = vm()
        v.proceedFromWelcome()
        v.goBack()
        assertEquals(OnboardingStep.Welcome, v.state.value.step)
    }

    @Test fun goBackFromWelcomeStaysOnWelcome() {
        val v = vm()
        v.goBack()
        assertEquals(OnboardingStep.Welcome, v.state.value.step)
    }
}
