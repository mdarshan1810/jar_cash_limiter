package com.jar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: UnparsedNotificationEntity): Long

    @Query("SELECT * FROM unparsed_notifications ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<UnparsedNotificationEntity>>

    @Query("DELETE FROM unparsed_notifications")
    suspend fun clearAll()
}
