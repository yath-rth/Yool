package com.yatharth.yool.ui.workout.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yatharth.yool.data.WorkoutRepository
import com.yatharth.yool.data.db.ExerciseSetRow
import com.yatharth.yool.data.db.WorkoutExerciseFull
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.domain.WorkoutSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the finish flow is. */
sealed interface FinishStage {
    /** Some sets are unchecked: complete them all, discard them, or cancel. */
    data object ChooseIncomplete : FinishStage

    /** Workout is saved; show the summary. [templateId] is set when its template's structure changed. */
    data class Summary(val summary: WorkoutSummary, val templateId: Long?, val templateName: String?) : FinishStage

    data class UpdateTemplate(val templateId: Long, val templateName: String) : FinishStage
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel(
    requestedId: Long?,
    private val repo: WorkoutRepository,
    private val prefs: WorkoutPrefs,
    private val app: Context,
) : ViewModel() {
    /** True when editing a finished workout. */
    val editing: Boolean = requestedId != null

    private val idFlow = MutableStateFlow(requestedId)
    private val mutex = Mutex()

    val workout: StateFlow<WorkoutFull?> = idFlow
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeWorkout(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<WorkoutSettings> = prefs.observe().stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutSettings())

    /** Last session's sets per exercise id, for the PREVIOUS column and placeholders. */
    val previous = MutableStateFlow<Map<Long, List<ExerciseSetRow>>>(emptyMap())

    val finishStage = MutableStateFlow<FinishStage?>(null)

    /** One-off message for the user (shown in a dialog). */
    val notice = MutableStateFlow<String?>(null)

    init {
        if (requestedId == null) viewModelScope.launch { idFlow.value = repo.startWorkout(null) }
        viewModelScope.launch {
            workout.filterNotNull().collect { w ->
                val missing = w.exercises.map { it.exercise.id }.distinct().filter { it !in previous.value }
                if (missing.isNotEmpty()) {
                    val loaded = missing.associateWith { repo.previousSets(it, w.workout.startedAt) }
                    previous.update { it + loaded }
                }
            }
        }
    }

    private fun id(): Long? = idFlow.value

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { notice.value = e.message ?: "Something went wrong." }
        }
    }

    // ── Workout info ─────────────────────────────────────────────────────────

    fun setName(name: String) = id()?.let { id -> launch { repo.updateWorkoutInfo(id, name = name) } }
    fun setNote(note: String) = id()?.let { id -> launch { repo.updateWorkoutInfo(id, note = note) } }
    fun setTimes(startedAt: Long, endedAt: Long) = id()?.let { id -> launch { repo.updateWorkoutInfo(id, startedAt = startedAt, endedAt = endedAt) } }

    // ── Exercises ────────────────────────────────────────────────────────────

    fun addExercises(ids: List<Long>) = id()?.let { w -> launch { ids.forEach { repo.addWorkoutExercise(w, it) } } }
    fun replaceExercise(weId: Long, newId: Long) = launch { repo.replaceWorkoutExercise(weId, newId) }
    fun removeExercise(weId: Long) = launch { repo.removeWorkoutExercise(weId) }
    fun moveExercise(weId: Long, toIndex: Int) = launch { repo.moveWorkoutExercise(weId, toIndex) }
    fun setExerciseNote(weId: Long, note: String) = launch { repo.setExerciseNote(weId, note) }

    /** Superset [ex] with the exercise that follows it (joining an existing group when there is one). */
    fun supersetWithNext(ex: WorkoutExerciseFull) {
        val w = workout.value ?: return
        val list = w.ordered
        val next = list.getOrNull(list.indexOfFirst { it.entry.id == ex.entry.id } + 1) ?: return
        launch {
            val group = ex.entry.supersetGroup ?: next.entry.supersetGroup ?: repo.newSupersetGroup(w.workout.id)
            repo.setSuperset(listOf(ex.entry.id, next.entry.id), group)
        }
    }

    fun removeFromSuperset(weId: Long) = launch { repo.setSuperset(listOf(weId), null) }

    /** Insert warm-up sets before the existing ones, ramping to the heaviest working weight. */
    fun addWarmups(ex: WorkoutExerciseFull) {
        val working = SessionMath.heaviestWorking(ex.sets)
        if (working == null) {
            notice.value = "Enter a working weight first, then add warm-ups."
            return
        }
        val s = settings.value
        val specs = WorkoutMath.warmupSets(working, s.barKg, s.stepKg)
        if (specs.isEmpty()) {
            notice.value = "The working weight is too light for warm-ups."
            return
        }
        launch { repo.insertSetsAtStart(ex.entry.id, specs) }
    }

    // ── Sets ─────────────────────────────────────────────────────────────────

    fun addSet(weId: Long) = launch { repo.addSet(weId) }
    fun deleteSet(setId: Long) = launch { repo.deleteSet(setId) }

    /** Read-modify-write one set from the latest stored copy, so quick successive edits don't clobber each other. */
    fun patchSet(setId: Long, transform: (WorkoutSet) -> WorkoutSet) {
        val w = id() ?: return
        launch {
            mutex.withLock {
                val s = repo.getWorkout(w)?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == setId } ?: return@withLock
                val next = transform(s)
                if (next != s) repo.updateSet(next)
            }
        }
    }

    /**
     * Check or uncheck a set. Checking fills any empty field from [prev] (the placeholder hint), saves the
     * typed values, and starts the rest timer in an active workout.
     */
    fun toggleComplete(set: WorkoutSet, ex: WorkoutExerciseFull, typed: SetValues, prev: SetValues?) {
        val w = id() ?: return
        launch {
            val now = !set.completed
            var restSet = set
            mutex.withLock {
                val stored = repo.getWorkout(w)?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == set.id } ?: return@withLock
                restSet = stored
                if (now) {
                    val v = SessionMath.forComplete(typed, prev, ex.exercise.type)
                    repo.updateSet(stored.copy(weightKg = v.weightKg, reps = v.reps, durationSec = v.durationSec, distanceM = v.distanceM))
                }
                repo.setCompleted(set.id, now)
            }
            val s = settings.value
            if (now && !editing && s.autoStartRest) {
                val sec = WorkoutMath.restFor(restSet.restSec, ex.exercise.restSec, s.defaultRestSec)
                RestTimer.start(app, sec, ex.exercise.name, s.timerSound, s.vibrate)
            }
        }
    }

    // ── Finish / discard ─────────────────────────────────────────────────────

    fun requestFinish() {
        val w = workout.value ?: return
        if (w.exercises.any { ex -> ex.sets.any { !it.completed } }) finishStage.value = FinishStage.ChooseIncomplete
        else finish(dropIncomplete = false)
    }

    fun finish(dropIncomplete: Boolean) {
        val w = id() ?: return
        finishStage.value = null
        launch {
            repo.finishWorkout(w, dropIncomplete)
            RestTimer.stop(app)
            val summary = repo.summary(w) ?: return@launch
            val full = repo.getWorkout(w)
            val template = full?.workout?.templateId?.let { repo.getTemplate(it) }
            val changed = template != null && full != null && WorkoutMath.structureChanged(template, full)
            finishStage.value = FinishStage.Summary(summary, if (changed) template?.template?.id else null, template?.template?.name)
        }
    }

    fun dismissFinish() { finishStage.value = null }

    fun askUpdateTemplate(templateId: Long, name: String) { finishStage.value = FinishStage.UpdateTemplate(templateId, name) }

    fun updateTemplate(templateId: Long, onDone: () -> Unit) {
        val w = id() ?: return onDone()
        launch {
            repo.updateTemplateFromWorkout(templateId, w)
            finishStage.value = null
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        val w = id() ?: return onDone()
        launch {
            RestTimer.stop(app)
            repo.deleteWorkout(w)
            onDone()
        }
    }
}
