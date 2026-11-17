package com.yatharth.yool.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hcDataStore: DataStore<Preferences> by preferencesDataStore(name = "health_connect_prefs")

/** Persisted sync bookkeeping. */
class HealthConnectPrefs(context: Context) {
    private val store = context.applicationContext.hcDataStore

    val lastSyncMillis: Flow<Long?> = store.data.map { it[LAST_SYNC] }
    val backfillDone: Flow<Boolean> = store.data.map { it[BACKFILL_DONE] ?: false }

    suspend fun isBackfillDone(): Boolean = backfillDone.first()

    suspend fun recordSync(nowMillis: Long, completedBackfill: Boolean) {
        store.edit {
            it[LAST_SYNC] = nowMillis
            if (completedBackfill) it[BACKFILL_DONE] = true
        }
    }

    private companion object {
        val LAST_SYNC = longPreferencesKey("last_sync_ms")
        val BACKFILL_DONE = booleanPreferencesKey("backfill_done")
    }
}
