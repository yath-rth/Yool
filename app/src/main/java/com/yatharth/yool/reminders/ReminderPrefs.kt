package com.yatharth.yool.reminders

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.reminderStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_prefs")

data class ReminderSetting(val enabled: Boolean, val time: LocalTime)

/** Enabled flag + time of day for every reminder, stored in the `reminder_prefs` DataStore. */
class ReminderPrefs(context: Context) {
    private val store = context.applicationContext.reminderStore

    private fun enabledKey(t: ReminderType) = booleanPreferencesKey("enabled_${t.id}")
    private fun minutesKey(t: ReminderType) = intPreferencesKey("minutes_${t.id}")

    private fun Preferences.toSettings(): Map<ReminderType, ReminderSetting> = ReminderType.entries.associateWith { t ->
        ReminderSetting(
            enabled = this[enabledKey(t)] ?: true,
            time = this[minutesKey(t)]?.let { LocalTime.of((it / 60).coerceIn(0, 23), (it % 60).coerceIn(0, 59)) } ?: t.defaultTime,
        )
    }

    fun observe(): Flow<Map<ReminderType, ReminderSetting>> = store.data.map { it.toSettings() }

    suspend fun current(): Map<ReminderType, ReminderSetting> = observe().first()

    suspend fun setEnabled(type: ReminderType, enabled: Boolean) {
        store.edit { it[enabledKey(type)] = enabled }
    }

    suspend fun setTime(type: ReminderType, time: LocalTime) {
        store.edit { it[minutesKey(type)] = time.hour * 60 + time.minute }
    }
}
