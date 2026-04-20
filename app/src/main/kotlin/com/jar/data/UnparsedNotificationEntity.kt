package com.jar.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unparsed_notifications")
data class UnparsedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("raw_text") val rawText: String,
    val sender: String?,
    val timestamp: Long,
    @ColumnInfo("package_name") val packageName: String
)
