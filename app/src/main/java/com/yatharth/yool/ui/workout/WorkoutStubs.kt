package com.yatharth.yool.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.TxButton

/*
 * Placeholder screens for the workout feature. Each one is replaced by its owning branch:
 *  - feat/workout-session  → ActiveWorkoutScreen, ActiveWorkoutBanner (ui/workout/session/)
 *  - feat/workout-library  → WorkoutHomeScreen, WorkoutHistoryScreen, WorkoutDetailScreen, TemplateEditorScreen,
 *                            ExerciseLibraryScreen, ExerciseDetailScreen, ExerciseEditorScreen, ExercisePickerDialog
 *                            (ui/workout/library/)
 * When implementing one, delete its stub here and keep the exact signature.
 */

@Composable
internal fun StubScreen(title: String, contentPadding: PaddingValues, onBack: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(title)
        EmptyState("Coming soon", "This screen is being built.")
        if (onBack != null) TxButton("Back", onClick = onBack)
    }
}

/**
 * The active workout (workoutId null) or a finished workout being edited (no timers, "Save" instead of
 * "Finish").
 */
@Composable
fun ActiveWorkoutScreen(workoutId: Long?, contentPadding: PaddingValues, onClose: () -> Unit, onOpenExercise: (Long) -> Unit) =
    StubScreen("Workout", contentPadding, onClose)

/** Banner above the bottom bar while a workout is active; tap to return to it. Renders nothing otherwise. */
@Composable
fun ActiveWorkoutBanner(onOpen: () -> Unit) {}

/** Settings › Workout section (rest defaults, timer sound/vibrate, keep screen on, bar weight, plates). */
@Composable
fun WorkoutSettingsSection() {}
