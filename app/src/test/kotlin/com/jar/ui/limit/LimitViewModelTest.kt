package com.jar.ui.limit

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class LimitViewModelTest {

    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-15T04:30:00Z"), kolkata)
    private val april1 = Instant.parse("2026-03-31T18:30:00Z").toEpochMilli()

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: JarRepository
    private lateinit var vm: LimitViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = buildInMemoryDb()
        settings = SettingsStore(InMemoryDataStore())
        repo = JarRepository(
            txDao = db.transactionDao(),
            unparsedDao = db.unparsedNotificationDao(),
            settingsStore = settings,
            clock = fixedClock
        )
        vm = LimitViewModel(repository = repo, settingsStore = settings)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun tx(amount: Long, hash: String, merchant: String? = "Zomato") =
        TransactionEntity(
            amount = amount,
            merchantRaw = merchant,
            timestamp = april1 + 1_000L,
            sourceSmsHash = hash,
            parseConfidence = 1.0f,
            accountLast4 = "1234"
        )

    @Test fun defaultStateIsEmpty() = runTest {
        val s = vm.state.first()
        assertEquals(0L, s.monthlyLimitPaise)
        assertEquals(0f, s.progressFraction, 0.0001f)
        assertTrue(s.recent.isEmpty())
    }

    @Test fun settingsAndInsertsPropagateToState() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setMonthlyLimit(2_000_000L)
        settings.setPeriodStartDay(1)
        repo.insertTransaction(tx(amount = 500_000L, hash = "h1"))

        val s = vm.state.first { it.spentPaise > 0L }
        assertEquals(2_000_000L, s.monthlyLimitPaise)
        assertEquals(500_000L, s.spentPaise)
        assertEquals(1_500_000L, s.remainingPaise)
        assertFalse(s.isOverLimit)
        assertEquals(0.25f, s.progressFraction, 0.0001f)
    }

    @Test fun overLimitFlagsAndRemainingGoesNegative() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setMonthlyLimit(500_000L)
        settings.setPeriodStartDay(1)
        repo.insertTransaction(tx(amount = 600_000L, hash = "h-over"))

        val s = vm.state.first { it.isOverLimit }
        assertTrue(s.isOverLimit)
        assertEquals(-100_000L, s.remainingPaise)
    }

    @Test fun saveLimitPersistsInRupees() = runTest {
        vm.saveLimit(rupees = 1500L)
        assertEquals(1500L * 100L, settings.flow.first().monthlyLimit)
    }

    @Test fun resetMonthWritesLastManualResetAt() = runTest {
        vm.resetMonth()
        assertNotNull(settings.flow.first().lastManualResetAt)
    }

    @Test fun recentTransactionsAreNewestFirstAndMapped() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setPeriodStartDay(1)
        repo.insertTransaction(
            TransactionEntity(
                amount = 1_000L, merchantRaw = "Old", timestamp = april1 + 1_000L,
                sourceSmsHash = "h-old", parseConfidence = 1f, accountLast4 = "1234"
            )
        )
        repo.insertTransaction(
            TransactionEntity(
                amount = 2_000L, merchantRaw = "Mid", timestamp = april1 + 2_000L,
                sourceSmsHash = "h-mid", parseConfidence = 1f, accountLast4 = "1234"
            )
        )
        repo.insertTransaction(
            TransactionEntity(
                amount = 3_000L, merchantRaw = "New", timestamp = april1 + 3_000L,
                sourceSmsHash = "h-new", parseConfidence = 1f, accountLast4 = "1234"
            )
        )

        val s = vm.state.first { it.recent.size == 3 }
        assertEquals(listOf("New", "Mid", "Old"), s.recent.map { it.merchantRaw })
    }
}
