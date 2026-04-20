package com.jar.data

import com.jar.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DedupeTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: JarRepository

    private val baseTime = 1_700_000_000_000L  // some 2023-ish epoch ms
    private val twoMinutesMs = 2L * 60 * 1000

    @Before fun setUp() {
        db = buildInMemoryDb()
        repo = JarRepository(
            txDao = db.transactionDao(),
            unparsedDao = db.unparsedNotificationDao(),
            settingsStore = SettingsStore(InMemoryDataStore())
        )
    }

    @After fun tearDown() {
        db.close()
    }

    private fun tx(
        amount: Long = 42000L,
        timestamp: Long = baseTime,
        hash: String = "hash-$timestamp",
        last4: String? = "1234"
    ) = TransactionEntity(
        amount = amount,
        merchantRaw = "Zomato",
        timestamp = timestamp,
        sourceSmsHash = hash,
        parseConfidence = 1.0f,
        accountLast4 = last4
    )

    @Test fun firstInsertSucceeds() = runTest {
        val result = repo.insertTransaction(tx())
        assertTrue(result is InsertTxResult.Inserted)
    }

    @Test fun tripleFireWithinTwoMinutesOnlyKeepsFirst() = runTest {
        val first = repo.insertTransaction(tx(timestamp = baseTime, hash = "h1"))
        val second = repo.insertTransaction(tx(timestamp = baseTime + 30_000, hash = "h2"))
        val third = repo.insertTransaction(tx(timestamp = baseTime + 90_000, hash = "h3"))

        assertTrue(first is InsertTxResult.Inserted)
        assertEquals(InsertTxResult.Duplicate, second)
        assertEquals(InsertTxResult.Duplicate, third)
    }

    @Test fun outsideTwoMinuteWindowIsNotDedupe() = runTest {
        repo.insertTransaction(tx(timestamp = baseTime, hash = "h1"))
        val later = repo.insertTransaction(tx(timestamp = baseTime + twoMinutesMs + 1, hash = "h2"))
        assertTrue(later is InsertTxResult.Inserted)
    }

    @Test fun exactlyAtWindowEdgeIsStillDedupe() = runTest {
        repo.insertTransaction(tx(timestamp = baseTime, hash = "h1"))
        // Edge: BETWEEN is inclusive, so ±exactly-2min is caught
        val edge = repo.insertTransaction(tx(timestamp = baseTime + twoMinutesMs, hash = "h2"))
        assertEquals(InsertTxResult.Duplicate, edge)
    }

    @Test fun differentLast4IsNotDedupe() = runTest {
        repo.insertTransaction(tx(last4 = "1234", hash = "h1"))
        val other = repo.insertTransaction(tx(last4 = "5678", hash = "h2"))
        assertTrue(other is InsertTxResult.Inserted)
    }

    @Test fun bothNullLast4MatchEachOther() = runTest {
        // Spec §6.5: SQLite `=` never matches NULL to NULL; DAO uses `IS` so two null-last4
        // rows (parser couldn't extract account) still dedupe against each other.
        val first = repo.insertTransaction(tx(last4 = null, hash = "h1"))
        val second = repo.insertTransaction(tx(last4 = null, hash = "h2", timestamp = baseTime + 10_000))
        assertTrue(first is InsertTxResult.Inserted)
        assertEquals(InsertTxResult.Duplicate, second)
    }

    @Test fun nullLast4DoesNotMatchPopulatedLast4() = runTest {
        repo.insertTransaction(tx(last4 = "1234", hash = "h1"))
        val nullOne = repo.insertTransaction(tx(last4 = null, hash = "h2", timestamp = baseTime + 10_000))
        assertTrue(nullOne is InsertTxResult.Inserted)
    }

    @Test fun differentAmountIsNotDedupe() = runTest {
        repo.insertTransaction(tx(amount = 42000L, hash = "h1"))
        val other = repo.insertTransaction(tx(amount = 42100L, hash = "h2"))
        assertTrue(other is InsertTxResult.Inserted)
    }

    @Test fun concurrentInsertsWithDifferentHashesYieldSingleRow() = runBlocking {
        // Two notifications for the same payment (SMS vs bank app) can arrive in the same
        // millisecond with different text → different source_sms_hash. The in-code window
        // dedupe has to hold under concurrent callers — before the DAO @Transaction fix,
        // both callers' findDupeCandidates reads could observe an empty table before either
        // insert landed, and both would persist. Stress this across many iterations to catch
        // the race consistently rather than by luck.
        repeat(20) { iter ->
            db.clearAllTables()
            val ts = baseTime + iter * 1_000_000L
            val tx1 = tx(timestamp = ts, hash = "iter-$iter-a", last4 = "1234")
            val tx2 = tx(timestamp = ts + 5_000L, hash = "iter-$iter-b", last4 = "1234")

            val results = coroutineScope {
                listOf(
                    async(Dispatchers.Default) { repo.insertTransaction(tx1) },
                    async(Dispatchers.Default) { repo.insertTransaction(tx2) }
                ).awaitAll()
            }

            val inserted = results.count { it is InsertTxResult.Inserted }
            val dupes = results.count { it === InsertTxResult.Duplicate }
            assertEquals("iter=$iter expected exactly one Inserted", 1, inserted)
            assertEquals("iter=$iter expected exactly one Duplicate", 1, dupes)
            assertEquals(
                "iter=$iter DB should contain one row",
                1,
                repo.observeRecent(10).first().size
            )
        }
    }

    @Test fun uniqueHashIndexRejectsReplayOutsideWindow() = runTest {
        // Same hash, but timestamps far apart so window dedupe does NOT catch it.
        // The unique index on source_sms_hash is the last-line defense.
        val first = repo.insertTransaction(tx(timestamp = baseTime, hash = "same-hash", amount = 100))
        val replay = repo.insertTransaction(tx(timestamp = baseTime + 10 * twoMinutesMs, hash = "same-hash", amount = 200))
        assertTrue(first is InsertTxResult.Inserted)
        assertEquals(InsertTxResult.Duplicate, replay)
    }
}
