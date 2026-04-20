package com.jar.data

import com.jar.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class JarRepositoryTest {

    // 2026-04-15 10:00 Kolkata; with periodStartDay=1, period = Apr 1 → May 1
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val fixedInstant: Instant = Instant.parse("2026-04-15T04:30:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, kolkata)

    // Period boundaries in epoch-ms for April 2026, Kolkata
    private val april1 = Instant.parse("2026-03-31T18:30:00Z").toEpochMilli()
    private val may1 = Instant.parse("2026-04-30T18:30:00Z").toEpochMilli()
    private val march15 = Instant.parse("2026-03-15T04:00:00Z").toEpochMilli()

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: JarRepository

    @Before fun setUp() {
        db = buildInMemoryDb()
        settings = SettingsStore(InMemoryDataStore())
        repo = JarRepository(
            txDao = db.transactionDao(),
            unparsedDao = db.unparsedNotificationDao(),
            settingsStore = settings,
            clock = clock
        )
    }

    @After fun tearDown() {
        db.close()
    }

    private fun tx(
        amount: Long,
        timestamp: Long,
        hash: String,
        last4: String? = "1234"
    ) = TransactionEntity(
        amount = amount,
        merchantRaw = "Merchant",
        timestamp = timestamp,
        sourceSmsHash = hash,
        parseConfidence = 1.0f,
        accountLast4 = last4
    )

    @Test fun defaultStateIsFullEmptyJar() = runTest {
        val state = repo.observeJarState().first()
        assertEquals(0L, state.startingAmount)
        assertEquals(0L, state.spent)
        assertEquals(0f, state.fractionRemaining, 0.0001f)
        assertFalse(state.isOverdrawn)
    }

    @Test fun settingsUpdatePropagatesToState() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setMonthlyLimit(2_000_000L)

        val state = repo.observeJarState().first()
        assertEquals(3_000_000L, state.startingAmount)
        assertEquals(2_000_000L, state.monthlyLimit)
        assertEquals(0L, state.spent)
        assertEquals(1f, state.fractionRemaining, 0.0001f)
    }

    @Test fun insertsInCurrentPeriodAccumulateSpent() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setPeriodStartDay(1)
        repo.insertTransaction(tx(amount = 50_000L, timestamp = april1 + 1000, hash = "h1"))
        repo.insertTransaction(tx(amount = 30_000L, timestamp = april1 + 2000, hash = "h2"))

        val state = repo.observeJarState().first()
        assertEquals(80_000L, state.spent)
    }

    @Test fun transactionsOutsidePeriodAreExcluded() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setPeriodStartDay(1)
        repo.insertTransaction(tx(amount = 999_999L, timestamp = march15, hash = "march"))
        repo.insertTransaction(tx(amount = 50_000L, timestamp = april1 + 1000, hash = "april"))

        val state = repo.observeJarState().first()
        assertEquals(50_000L, state.spent)
    }

    @Test fun periodEndIsExclusive() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setPeriodStartDay(1)
        // A tx at exactly may1 belongs to the NEXT period, not this one
        repo.insertTransaction(tx(amount = 1_000L, timestamp = may1, hash = "boundary"))

        val state = repo.observeJarState().first()
        assertEquals(0L, state.spent)
    }

    @Test fun overLimitFlagsWhenMonthlyLimitExceeded() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setMonthlyLimit(100_000L)
        repo.insertTransaction(tx(amount = 150_000L, timestamp = april1 + 1000, hash = "big"))

        val state = repo.observeJarState().first()
        assertTrue(state.isOverLimit)
        assertFalse(state.isOverdrawn)
    }

    @Test fun overdrawFlagsWhenSpentExceedsStarting() = runTest {
        settings.setStartingAmount(100_000L)
        repo.insertTransaction(tx(amount = 150_000L, timestamp = april1 + 1000, hash = "over"))

        val state = repo.observeJarState().first()
        assertTrue(state.isOverdrawn)
        assertEquals(0f, state.fractionRemaining, 0.0001f)
    }

    @Test fun resetMonthShiftsPeriodStartToNow() = runTest {
        settings.setStartingAmount(3_000_000L)
        settings.setPeriodStartDay(1)
        repo.insertTransaction(tx(amount = 200_000L, timestamp = april1 + 1000, hash = "pre"))

        // Reset sets lastManualResetAt = clock.millis() which is 2026-04-15 04:30 UTC
        repo.resetMonth()

        // The pre-reset tx (Apr 1) is now before the new period start → excluded
        val state = repo.observeJarState().first()
        assertEquals(0L, state.spent)
    }

    @Test fun observeRecentReturnsNewestFirst() = runTest {
        repo.insertTransaction(tx(amount = 1L, timestamp = april1 + 1000, hash = "old"))
        repo.insertTransaction(tx(amount = 2L, timestamp = april1 + 5000, hash = "mid"))
        repo.insertTransaction(tx(amount = 3L, timestamp = april1 + 9000, hash = "new"))

        val recent = repo.observeRecent(10).first()
        assertEquals(listOf(3L, 2L, 1L), recent.map { it.amount })
    }
}
