package com.yatharth.yool.ui.workout.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yatharth.yool.domain.WorkoutMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workoutStore: DataStore<Preferences> by preferencesDataStore(name = "workout_prefs")

/** User settings for the workout session (rest timer, plates, screen). */
data class WorkoutSettings(
    val defaultRestSec: Int = 120,
    val autoStartRest: Boolean = true,
    val timerSound: Boolean = true,
    val vibrate: Boolean = true,
    val keepScreenOn: Boolean = true,
    val barKg: Double = 20.0,
    val plates: List<Double> = DefaultPlates,
    val stepKg: Double = 2.5,
) {
    companion object {
        val DefaultPlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

        /** "25, 20, 1.25" -> [25.0, 20.0, 1.25] (positive, distinct, heaviest first). */
        fun parsePlates(text: String): List<Double> =
            text.split(',', ' ', ';').mapNotNull { it.trim().toDoubleOrNull() }
                .filter { it > 0 }.distinct().sortedDescending()

        fun formatPlates(plates: List<Double>): String = plates.joinToString(", ") { WorkoutMath.kg(it) }
    }
}

/** Workout settings in the `workout_prefs` DataStore. */
class WorkoutPrefs(context: Context) {
    private val store = context.applicationContext.workoutStore

    private val rest = intPreferencesKey("default_rest_sec")
    private val auto = booleanPreferencesKey("auto_start_rest")
    private val sound = booleanPreferencesKey("timer_sound")
    private val vibrate = booleanPreferencesKey("vibrate")
    private val keepOn = booleanPreferencesKey("keep_screen_on")
    private val bar = doublePreferencesKey("bar_kg")
    private val plates = stringPreferencesKey("plates")
    private val step = doublePreferencesKey("step_kg")

    fun observe(): Flow<WorkoutSettings> = store.data.map { p ->
        val d = WorkoutSettings()
        WorkoutSettings(
            defaultRestSec = p[rest] ?: d.defaultRestSec,
            autoStartRest = p[auto] ?: d.autoStartRest,
            timerSound = p[sound] ?: d.timerSound,
            vibrate = p[vibrate] ?: d.vibrate,
            keepScreenOn = p[keepOn] ?: d.keepScreenOn,
            barKg = p[bar] ?: d.barKg,
            plates = p[plates]?.let(WorkoutSettings::parsePlates)?.takeIf { it.isNotEmpty() } ?: d.plates,
            stepKg = p[step] ?: d.stepKg,
        )
    }

    suspend fun current(): WorkoutSettings = observe().first()

    suspend fun update(transform: (WorkoutSettings) -> WorkoutSettings) {
        val next = transform(current())
        store.edit {
            it[rest] = next.defaultRestSec.coerceIn(5, 3600)
            it[auto] = next.autoStartRest
            it[sound] = next.timerSound
            it[vibrate] = next.vibrate
            it[keepOn] = next.keepScreenOn
            it[bar] = next.barKg.coerceAtLeast(0.0)
            it[plates] = WorkoutSettings.formatPlates(next.plates)
            it[step] = next.stepKg.takeIf { s -> s > 0 } ?: 2.5
        }
    }
}
