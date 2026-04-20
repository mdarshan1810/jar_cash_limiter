package com.jar.data

import com.jar.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

sealed class InsertTxResult {
    data class Inserted(val id: Long) : InsertTxResult()
    data object Duplicate : InsertTxResult()
}

class JarRepository(
    private val txDao: TransactionDao,
    private val unparsedDao: UnparsedNotificationDao,
    private val settingsStore: SettingsStore,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeJarState(): Flow<JarState> = settingsStore.flow.flatMapLatest { settings ->
        val now = LocalDateTime.now(clock)
        val period = currentPeriod(now, settings, clock.zone)
        txDao.observeSpentBetween(period.startMillis, period.endMillis).map { spent ->
            JarState.from(settings, spent)
        }
    }

    fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = txDao.observeRecent(limit)

    fun observeUnparsed(): Flow<List<UnparsedNotificationEntity>> = unparsedDao.observeAll()

    /**
     * Insert with in-code dedupe per spec §6.5. Rejects any tx whose (amount, accountLast4)
     * matches a row within ±2 minutes — covers the common case of SMS + bank app posting the
     * same transaction twice. The unique index on source_sms_hash is the last-line defense
     * if this check misses (e.g., service restart replay).
     *
     * The DAO runs the check+insert inside a single Room transaction so two callers racing
     * with identical (amount, accountLast4, ±window) but different text/hash cannot both
     * win the window scan — SQLite serializes write transactions.
     */
    suspend fun insertTransaction(tx: TransactionEntity): InsertTxResult {
        val windowMs = 2.minutes.inWholeMilliseconds
        val id = txDao.insertIfUniqueInWindow(
            tx = tx,
            minTimestamp = tx.timestamp - windowMs,
            maxTimestamp = tx.timestamp + windowMs
        )
        return if (id != null) InsertTxResult.Inserted(id) else InsertTxResult.Duplicate
    }

    suspend fun insertUnparsed(notification: UnparsedNotificationEntity): Long =
        unparsedDao.insert(notification)

    suspend fun clearUnparsed() = unparsedDao.clearAll()

    suspend fun resetMonth() {
        settingsStore.setLastManualResetAt(clock.millis())
    }
}
