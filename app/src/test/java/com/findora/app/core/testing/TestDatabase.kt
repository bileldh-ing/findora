package com.findora.app.core.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.findora.app.core.data.local.FindoraDatabase

fun inMemoryDatabase(): FindoraDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FindoraDatabase::class.java)
        .allowMainThreadQueries()
        .build()
