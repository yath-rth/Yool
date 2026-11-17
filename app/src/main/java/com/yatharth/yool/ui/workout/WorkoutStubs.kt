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

/** Workout tab home: start empty, templates (with folders), recent history. */
@Composable
fun WorkoutHomeScreen(
    contentPadding: PaddingValues,
    /** Called after a workout was started (or an active one exists) — opens the active workout. */
    onOpenActive: () -> Unit,
    onEditTemplate: (templateId: Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWorkout: (workoutId: Long) -> Unit,
    onOpenExercises: () -> Unit,
) = StubScreen("Workout", contentPadding)

/** Finished workouts, newest first. */
@Composable
fun WorkoutHistoryScreen(contentPadding: PaddingValues, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) =
    StubScreen("History", contentPadding, onBack)

/** One finished workout: sets, volume, PRs; edit / delete / save as template / start again. */
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (workoutId: Long) -> Unit,
    onOpenActive: () -> Unit,
    onOpenExercise: (exerciseId: Long) -> Unit,
) = StubScreen("Workout", contentPadding, onBack)

/** Create (templateId 0) or edit a template. */
@Composable
fun TemplateEditorScreen(templateId: Long, contentPadding: PaddingValues, onDone: () -> Unit) =
    StubScreen("Template", contentPadding, onDone)

@Composable
fun ExerciseLibraryScreen(contentPadding: PaddingValues, onBack: () -> Unit, onOpenExercise: (Long) -> Unit, onNewExercise: () -> Unit) =
    StubScreen("Exercises", contentPadding, onBack)

/** History / Records / Charts for one exercise. */
@Composable
fun ExerciseDetailScreen(exerciseId: Long, contentPadding: PaddingValues, onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenWorkout: (Long) -> Unit) =
    StubScreen("Exercise", contentPadding, onBack)

/** Create (exerciseId 0) or edit an exercise. */
@Composable
fun ExerciseEditorScreen(exerciseId: Long, contentPadding: PaddingValues, onDone: () -> Unit) =
    StubScreen("Exercise", contentPadding, onDone)

/**
 * Full-screen dialog to pick exercises (search, body-part filter, create new inline).
 * [multiple] allows picking several (adding to a workout/template); otherwise one (replace).
 */
@Composable
fun ExercisePickerDialog(
    title: String,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onPicked: (exerciseIds: List<Long>) -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) { StubScreen(title, PaddingValues(0.dp), onDismiss) }
}
