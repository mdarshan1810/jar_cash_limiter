package com.jar.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["source_sms_hash"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    @ColumnInfo("merchant_raw") val merchantRaw: String?,
    val timestamp: Long,
    @ColumnInfo("source_sms_hash") val sourceSmsHash: String,
    @ColumnInfo("parse_confidence") val parseConfidence: Float,
    @ColumnInfo("account_last4") val accountLast4: String?
)
