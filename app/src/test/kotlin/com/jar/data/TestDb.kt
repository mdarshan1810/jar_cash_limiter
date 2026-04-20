package com.jar.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal fun buildInMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()

/**
 * An in-memory [DataStore] backed by a [MutableStateFlow]. Real Preferences file I/O has
 * known rename flakiness on Windows under rapid test writes; this fake is reactive (emits
 * on every update) without touching disk, so JarRepository tests can exercise the settings
 * → state flow without fighting the filesystem.
 */
internal class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
