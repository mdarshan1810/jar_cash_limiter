package com.jar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
