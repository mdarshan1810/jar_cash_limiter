package com.jar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.jar.data.AppDatabase
import com.jar.data.JarRepository
import com.jar.notifications.BankWhitelist
import com.jar.notifications.HdfcWhitelist
import com.jar.notifications.NotificationPipeline
import com.jar.parser.BankParser
import com.jar.parser.hdfc.HdfcParser
import com.jar.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI per spec §3 #8 — Hilt is overkill for v1's size. One [AppContainer] lives on
 * [JarApp]; the NotificationListenerService and (later) activity/VMs reach in to grab
 * their dependencies.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore: SettingsStore = SettingsStore(
        PreferenceDataStoreFactory.create(
            scope = ioScope,
            produceFile = { applicationContext.preferencesDataStoreFile(SETTINGS_FILE) }
        )
    )

    val database: AppDatabase = Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java,
        DATABASE_FILE
    ).build()

    val repository: JarRepository = JarRepository(
        txDao = database.transactionDao(),
        unparsedDao = database.unparsedNotificationDao(),
        settingsStore = settingsStore
    )

    private val parsers: Map<String, BankParser> = mapOf("HDFC" to HdfcParser())
    private val whitelists: Map<String, BankWhitelist> = mapOf("HDFC" to HdfcWhitelist())

    val pipeline: NotificationPipeline = NotificationPipeline(
        parsers = parsers,
        whitelists = whitelists,
        repository = repository,
        settingsStore = settingsStore
    )

    companion object {
        private const val SETTINGS_FILE = "jar_settings"
        private const val DATABASE_FILE = "jar.db"
    }
}
