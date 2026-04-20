package com.jar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tx: TransactionEntity): Long

    /**
     * Null-safe equality on account_last4 — SQLite's `=` returns NULL when either side is
     * NULL, so two null-last4 rows would never match. `IS` treats NULL as a value and
     * matches null-to-null, which is what dedupe needs (spec §6.5).
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount
          AND account_last4 IS :accountLast4
          AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
        """
    )
    suspend fun findDupeCandidates(
        amount: Long,
        accountLast4: String?,
        minTimestamp: Long,
        maxTimestamp: Long
    ): List<TransactionEntity>

    /**
     * Atomic check+insert. SQLite serializes write transactions, so two callers racing with
     * identical (amount, accountLast4, ±window) but different source_sms_hash cannot both
     * slip past the window-scan — the second one sees the first's row and skips.
     *
     * Returns the inserted row id, or null when the tx was dropped as a duplicate (either
     * the window scan matched, or the unique source_sms_hash index rejected the insert).
     */
    @Transaction
    suspend fun insertIfUniqueInWindow(
        tx: TransactionEntity,
        minTimestamp: Long,
        maxTimestamp: Long
    ): Long? {
        val candidates = findDupeCandidates(
            amount = tx.amount,
            accountLast4 = tx.accountLast4,
            minTimestamp = minTimestamp,
            maxTimestamp = maxTimestamp
        )
        if (candidates.isNotEmpty()) return null
        return runCatching { insert(tx) }.getOrNull()
    }

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
        """
    )
    fun observeSpentBetween(startMillis: Long, endMillis: Long): Flow<Long>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
        ORDER BY timestamp DESC
        """
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>
}
