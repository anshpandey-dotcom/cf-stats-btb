package com.fleetmc.verity.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.unlockDataStore by preferencesDataStore(name = "verity_unlocks")

class UnlockStore(context: Context) {

    private val dataStore = context.applicationContext.unlockDataStore

    val unlockedIds: Flow<Set<String>> = dataStore.data.map { it[UNLOCKED_KEY].orEmpty() }

    suspend fun unlock(id: String) {
        dataStore.edit { preferences ->
            preferences[UNLOCKED_KEY] = preferences[UNLOCKED_KEY].orEmpty() + id
        }
    }

    private companion object {
        val UNLOCKED_KEY = stringSetPreferencesKey("unlocked_ids")
    }
}
