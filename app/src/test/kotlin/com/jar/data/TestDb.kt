package com.jar.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

internal fun buildInMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()
