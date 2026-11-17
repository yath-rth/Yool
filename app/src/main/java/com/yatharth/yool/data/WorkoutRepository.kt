package com.yatharth.yool.data

import androidx.room.withTransaction
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.ExerciseSetRow
import com.yatharth.yool.data.db.TemplateExercise
import com.yatharth.yool.data.db.TemplateFolder
import com.yatharth.yool.data.db.TemplateFull
import com.yatharth.yool.data.db.TemplateSet
import com.yatharth.yool.data.db.Workout
import com.yatharth.yool.data.db.WorkoutExercise
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.db.WorkoutTemplate
import com.yatharth.yool.data.db.YoolDatabase
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.domain.ExerciseSpec
import com.yatharth.yool.domain.SetSpec
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.domain.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exercise library, templates and the workout log (including the single active workout, which is a
 * workout row with endedAt == null). Every editing op works on active and finished workouts alike,
 * so the same screen edits past workouts.
 */
class WorkoutRepository(private val db: YoolDatabase, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private val dao = db.workoutDao()

    // ── Exercises ────────────────────────────────────────────────────────────

    fun observeExercises(includeArchived: Boolean = false): Flow<List<Exercise>> = dao.observeExercises(includeArchived)
    fun observeExercise(id: Long): Flow<Exercise?> = dao.observeExercise(id)
    suspend fun getExercise(id: Long): Exercise? = dao.getExercise(id)

    /** Insert (id 0) or update. Throws [DuplicateExerciseException] when another exercise has the name. */
    suspend fun saveExercise(exercise: Exercise): Long {
        val clean = exercise.copy(name = exercise.name.trim(), note = exercise.note?.trim()?.ifEmpty { null })
        require(clean.name.isNotEmpty()) { "Name is required." }
        val clash = dao.findExercise(clean.name)
        if (clash != null && clash.id != clean.id) throw DuplicateExerciseException(clean.name)
        return if (clean.id == 0L) dao.insertExercise(clean) else clean.id.also { dao.updateExercise(clean) }
    }

    /** Exercises used anywhere are archived; unused ones are deleted. */
    suspend fun removeExercise(id: Long) {
        val e = dao.getExercise(id) ?: return
        if (dao.exerciseUseCount(id) > 0) dao.updateExercise(e.copy(archived = true)) else dao.deleteExercise(e)
    }

    suspend fun setExerciseArchived(id: Long, archived: Boolean) {
        dao.getExercise(id)?.let { dao.updateExercise(it.copy(archived = archived)) }
    }

    /** Completed sets of an exercise in finished workouts, oldest first. Feed to [WorkoutMath.records]/[WorkoutMath.sessions]. */
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseSetRow>> = dao.observeExerciseSets(exerciseId)

    /** "Previous" sets for [exerciseId]: its last finished session before [beforeStartedAt]. */
    suspend fun previousSets(exerciseId: Long, beforeStartedAt: Long = Long.MAX_VALUE): List<ExerciseSetRow> =
        dao.previousSets(exerciseId, beforeStartedAt)

    // ── Folders & templates ──────────────────────────────────────────────────

    fun observeFolders(): Flow<List<TemplateFolder>> = dao.observeFolders()
    suspend fun saveFolder(folder: TemplateFolder): Long =
        if (folder.id == 0L) dao.insertFolder(folder.copy(name = folder.name.trim())) else folder.id.also { dao.updateFolder(folder.copy(name = folder.name.trim())) }
    /** Templates in the folder move to "no folder". */
    suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    fun observeTemplates(): Flow<List<TemplateFull>> = dao.observeTemplates()
    fun observeTemplate(id: Long): Flow<TemplateFull?> = dao.observeTemplate(id)
    suspend fun getTemplate(id: Long): TemplateFull? = dao.getTemplate(id)

    /** Create (id 0) or replace a template's metadata and contents. Returns its id. */
    suspend fun saveTemplate(id: Long, name: String, exercises: List<ExerciseSpec>, folderId: Long? = null, note: String? = null): Long =
        db.withTransaction {
            val clean = name.trim().ifEmpty { "Template" }
            val templateId = if (id == 0L) {
                dao.insertTemplate(WorkoutTemplate(name = clean, folderId = folderId, note = note?.trim()?.ifEmpty { null }, position = dao.nextTemplatePosition()))
            } else {
                val existing = dao.getTemplate(id)?.template ?: error("Template $id not found")
                dao.updateTemplate(existing.copy(name = clean, folderId = folderId, note = note?.trim()?.ifEmpty { null }))
                id
            }
            writeTemplateContents(templateId, exercises)
            templateId
        }

    private suspend fun writeTemplateContents(templateId: Long, exercises: List<ExerciseSpec>) {
        dao.clearTemplateExercises(templateId)
        exercises.forEachIndexed { i, spec ->
            val teId = dao.insertTemplateExercise(
                TemplateExercise(templateId = templateId, exerciseId = spec.exerciseId, position = i, supersetGroup = spec.supersetGroup, note = spec.note),
            )
            dao.insertTemplateSets(spec.sets.mapIndexed { j, s ->
                TemplateSet(
                    templateExerciseId = teId, position = j, type = s.type,
                    weightKg = s.weightKg, reps = s.reps, durationSec = s.durationSec, distanceM = s.distanceM, restSec = s.restSec,
                )
            })
        }
    }

    suspend fun renameTemplate(id: Long, name: String) {
        dao.getTemplate(id)?.template?.let { dao.updateTemplate(it.copy(name = name.trim().ifEmpty { it.name })) }
    }

    suspend fun moveTemplateToFolder(id: Long, folderId: Long?) {
        dao.getTemplate(id)?.template?.let { dao.updateTemplate(it.copy(folderId = folderId)) }
    }

    /** Reorder templates: [orderedIds] gets positions 0..n. */
    suspend fun reorderTemplates(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { i, id -> dao.getTemplate(id)?.template?.let { dao.updateTemplate(it.copy(position = i)) } }
    }

    suspend fun duplicateTemplate(id: Long): Long {
        val t = dao.getTemplate(id) ?: return 0
        return saveTemplate(0, "${t.template.name} (copy)", WorkoutMath.specsOf(t), t.template.folderId, t.template.note)
    }

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    /** Save a workout's structure (and values) as a new template. */
    suspend fun saveWorkoutAsTemplate(workoutId: Long, name: String): Long {
        val w = dao.getWorkoutFull(workoutId) ?: return 0
        return saveTemplate(0, name, WorkoutMath.specsOf(w, completedOnly = w.workout.endedAt != null))
    }

    /** Overwrite a template's contents with the workout's exercises and sets (Strong's "Update template"). */
    suspend fun updateTemplateFromWorkout(templateId: Long, workoutId: Long) {
        val w = dao.getWorkoutFull(workoutId) ?: return
        val t = dao.getTemplate(templateId) ?: return
        db.withTransaction { writeTemplateContents(t.template.id, WorkoutMath.specsOf(w, completedOnly = true)) }
    }

    // ── Workouts: lifecycle ──────────────────────────────────────────────────

    fun observeActive(): Flow<WorkoutFull?> = dao.observeActive()
    fun observeWorkout(id: Long): Flow<WorkoutFull?> = dao.observeWorkout(id)
    suspend fun getWorkout(id: Long): WorkoutFull? = dao.getWorkoutFull(id)
    fun observeHistory(limit: Int = 1000): Flow<List<WorkoutFull>> = dao.observeHistory(limit)
    fun observeWorkoutsBetween(from: LocalDate, to: LocalDate): Flow<List<WorkoutFull>> = dao.observeWorkoutsBetween(from, to)

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    /**
     * Start a workout (empty, or from [templateId] with its exercises and target sets pre-filled).
     * If a workout is already active, returns its id instead of starting another.
     */
    suspend fun startWorkout(templateId: Long? = null, now: Long = System.currentTimeMillis()): Long = db.withTransaction {
        dao.getActive()?.let { return@withTransaction it.id }
        val template = templateId?.let { dao.getTemplate(it) }
        val name = template?.template?.name ?: WorkoutMath.defaultName(Instant.ofEpochMilli(now).atZone(zone()).hour)
        val id = dao.insertWorkout(Workout(name = name, date = dateOf(now), startedAt = now, templateId = template?.template?.id))
        template?.let {
            dao.markTemplateUsed(it.template.id, now)
            it.ordered.forEachIndexed { i, te ->
                val weId = dao.insertWorkoutExercise(
                    WorkoutExercise(workoutId = id, exerciseId = te.exercise.id, position = i, supersetGroup = te.entry.supersetGroup, note = te.entry.note),
                )
                dao.insertSets(te.orderedSets.mapIndexed { j, s ->
                    WorkoutSet(
                        workoutExerciseId = weId, position = j, type = s.type,
                        weightKg = s.weightKg, reps = s.reps, durationSec = s.durationSec, distanceM = s.distanceM, restSec = s.restSec,
                    )
                })
            }
        }
        id
    }

    /** Start a new workout repeating a past one (structure and values, nothing completed). */
    suspend fun repeatWorkout(workoutId: Long, now: Long = System.currentTimeMillis()): Long = db.withTransaction {
        dao.getActive()?.let { return@withTransaction it.id }
        val src = dao.getWorkoutFull(workoutId) ?: return@withTransaction startWorkout(null, now)
        val id = dao.insertWorkout(Workout(name = src.workout.name, date = dateOf(now), startedAt = now, templateId = src.workout.templateId))
        WorkoutMath.specsOf(src, completedOnly = true).forEachIndexed { i, spec -> insertExercise(id, i, spec) }
        id
    }

    private suspend fun insertExercise(workoutId: Long, position: Int, spec: ExerciseSpec): Long {
        val weId = dao.insertWorkoutExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = spec.exerciseId, position = position, supersetGroup = spec.supersetGroup, note = spec.note),
        )
        dao.insertSets(spec.sets.mapIndexed { j, s ->
            WorkoutSet(
                workoutExerciseId = weId, position = j, type = s.type,
                weightKg = s.weightKg, reps = s.reps, durationSec = s.durationSec, distanceM = s.distanceM, restSec = s.restSec,
            )
        })
        return weId
    }

    suspend fun updateWorkoutInfo(workoutId: Long, name: String? = null, note: String? = null, startedAt: Long? = null, endedAt: Long? = null) {
        val w = dao.getWorkout(workoutId) ?: return
        val start = startedAt ?: w.startedAt
        dao.updateWorkout(
            w.copy(
                name = name?.trim()?.ifEmpty { w.name } ?: w.name,
                note = if (note != null) note.trim().ifEmpty { null } else w.note,
                startedAt = start,
                date = dateOf(start),
                endedAt = endedAt ?: w.endedAt,
            ),
        )
    }

    /**
     * Finish the workout. With [dropIncomplete], unchecked sets are deleted and exercises left with no
     * sets are removed (Strong's "discard incomplete sets"); otherwise they are marked complete.
     */
    suspend fun finishWorkout(workoutId: Long, dropIncomplete: Boolean = true, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val w = dao.getWorkoutFull(workoutId) ?: return@withTransaction
        for (ex in w.exercises) {
            val incomplete = ex.sets.filter { !it.completed }
            if (dropIncomplete) incomplete.forEach { dao.deleteSet(it.id) }
            else dao.updateSets(incomplete.map { it.copy(completed = true, completedAt = now) })
            if (dropIncomplete && incomplete.size == ex.sets.size) dao.deleteWorkoutExercise(ex.entry.id)
            else renumberSets(ex.entry.id)
        }
        renumberExercises(workoutId)
        dao.updateWorkout(w.workout.copy(endedAt = now))
    }

    /** Delete a workout (discarding the active one, or removing one from history). */
    suspend fun deleteWorkout(workoutId: Long) = dao.deleteWorkout(workoutId)

    /** Summary with PRs, comparing against all earlier finished workouts. */
    suspend fun summary(workoutId: Long): WorkoutSummary? {
        val w = dao.getWorkoutFull(workoutId) ?: return null
        val history = w.exercises.map { it.exercise.id }.distinct().associateWith { dao.observeExerciseSets(it).first() }
        return WorkoutMath.summary(w, history)
    }

    // ── Workouts: editing exercises ──────────────────────────────────────────

    /**
     * Append an exercise. Set count copies its last session (min 1, warm-ups excluded) with empty values,
     * so the "Previous" column lines up. Returns the workout-exercise id.
     */
    suspend fun addWorkoutExercise(workoutId: Long, exerciseId: Long): Long = db.withTransaction {
        val position = dao.workoutExercises(workoutId).size
        val w = dao.getWorkout(workoutId)
        val prev = dao.previousSets(exerciseId, w?.startedAt ?: Long.MAX_VALUE).filter { it.type != SetType.WARMUP }
        val sets = List(prev.size.coerceIn(1, 10)) { SetSpec() }
        insertExercise(workoutId, position, ExerciseSpec(exerciseId, sets))
    }

    suspend fun removeWorkoutExercise(workoutExerciseId: Long) = db.withTransaction {
        val we = dao.getWorkoutExercise(workoutExerciseId) ?: return@withTransaction
        dao.deleteWorkoutExercise(workoutExerciseId)
        renumberExercises(we.workoutId)
    }

    /** Swap the exercise but keep its sets' structure; values and completion are cleared. */
    suspend fun replaceWorkoutExercise(workoutExerciseId: Long, newExerciseId: Long) = db.withTransaction {
        val we = dao.getWorkoutExercise(workoutExerciseId) ?: return@withTransaction
        dao.updateWorkoutExercise(we.copy(exerciseId = newExerciseId, note = null))
        dao.updateSets(dao.sets(workoutExerciseId).map {
            it.copy(weightKg = null, reps = null, durationSec = null, distanceM = null, rpe = null, completed = false, completedAt = null)
        })
    }

    /** Move an exercise to [toIndex] within its workout. */
    suspend fun moveWorkoutExercise(workoutExerciseId: Long, toIndex: Int) = db.withTransaction {
        val we = dao.getWorkoutExercise(workoutExerciseId) ?: return@withTransaction
        val list = dao.workoutExercises(we.workoutId).toMutableList()
        val from = list.indexOfFirst { it.id == workoutExerciseId }
        if (from < 0) return@withTransaction
        list.add(toIndex.coerceIn(0, list.lastIndex), list.removeAt(from))
        dao.updateWorkoutExercises(list.mapIndexed { i, e -> e.copy(position = i) })
    }

    /** Put the given exercises into one superset (or remove them from any superset when [group] is null). */
    suspend fun setSuperset(workoutExerciseIds: List<Long>, group: Int?) = db.withTransaction {
        val list = workoutExerciseIds.mapNotNull { dao.getWorkoutExercise(it) }
        dao.updateWorkoutExercises(list.map { it.copy(supersetGroup = group) })
    }

    /** A superset group number not used in [workoutId]. */
    suspend fun newSupersetGroup(workoutId: Long): Int = (dao.workoutExercises(workoutId).mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1

    suspend fun setExerciseNote(workoutExerciseId: Long, note: String?) {
        dao.getWorkoutExercise(workoutExerciseId)?.let { dao.updateWorkoutExercise(it.copy(note = note?.trim()?.ifEmpty { null })) }
    }

    // ── Workouts: editing sets ───────────────────────────────────────────────

    /** Append a set copying the last set's type-less values (Strong behaviour). Returns its id. */
    suspend fun addSet(workoutExerciseId: Long, type: SetType = SetType.NORMAL): Long = db.withTransaction {
        val sets = dao.sets(workoutExerciseId)
        val last = sets.lastOrNull()
        dao.insertSet(
            WorkoutSet(
                workoutExerciseId = workoutExerciseId, position = sets.size, type = type,
                weightKg = last?.weightKg, reps = last?.reps, durationSec = last?.durationSec, distanceM = last?.distanceM, restSec = last?.restSec,
            ),
        )
    }

    /** Insert sets (e.g. generated warm-ups) at the start of an exercise. */
    suspend fun insertSetsAtStart(workoutExerciseId: Long, specs: List<SetSpec>) = db.withTransaction {
        val existing = dao.sets(workoutExerciseId)
        dao.updateSets(existing.map { it.copy(position = it.position + specs.size) })
        dao.insertSets(specs.mapIndexed { i, s ->
            WorkoutSet(
                workoutExerciseId = workoutExerciseId, position = i, type = s.type,
                weightKg = s.weightKg, reps = s.reps, durationSec = s.durationSec, distanceM = s.distanceM, restSec = s.restSec,
            )
        })
        renumberSets(workoutExerciseId)
    }

    /** Save edited values of a set (weight, reps, type, rpe, rest…). */
    suspend fun updateSet(set: WorkoutSet) = dao.updateSet(set)

    /** Check/uncheck a set. Returns the updated set. */
    suspend fun setCompleted(setId: Long, completed: Boolean, now: Long = System.currentTimeMillis()): WorkoutSet? {
        val s = dao.getSet(setId) ?: return null
        val updated = s.copy(completed = completed, completedAt = if (completed) now else null)
        dao.updateSet(updated)
        return updated
    }

    suspend fun deleteSet(setId: Long) = db.withTransaction {
        val s = dao.getSet(setId) ?: return@withTransaction
        dao.deleteSet(setId)
        renumberSets(s.workoutExerciseId)
    }

    private suspend fun renumberSets(workoutExerciseId: Long) {
        val sets = dao.sets(workoutExerciseId)
        val fixed = sets.mapIndexedNotNull { i, s -> if (s.position != i) s.copy(position = i) else null }
        if (fixed.isNotEmpty()) dao.updateSets(fixed)
    }

    private suspend fun renumberExercises(workoutId: Long) {
        val list = dao.workoutExercises(workoutId)
        val fixed = list.mapIndexedNotNull { i, e -> if (e.position != i) e.copy(position = i) else null }
        if (fixed.isNotEmpty()) dao.updateWorkoutExercises(fixed)
    }

    // ── Import / backup ──────────────────────────────────────────────────────

    suspend fun allExercises() = dao.allExercises()
    suspend fun allFolders() = dao.allFolders()
    suspend fun allTemplates() = dao.allTemplates()
    suspend fun allWorkouts() = dao.allWorkouts()

    /** Templates as name-referenced specs (for backup). */
    suspend fun templatesForBackup(): List<ImportedTemplate> {
        val folders = dao.allFolders().associate { it.id to it.name }
        return dao.allTemplates().map { t ->
            ImportedTemplate(
                t.template.name, t.template.folderId?.let(folders::get), t.template.note,
                t.ordered.zip(WorkoutMath.specsOf(t)).map { (te, spec) -> te.exercise.name to spec },
            )
        }
    }

    /** Finished workouts with exercises referenced by name (for backup). */
    suspend fun workoutsForBackup(): List<ImportedWorkout> = dao.allWorkouts().map { w ->
        ImportedWorkout(w.workout, w.ordered.map { ImportedWorkoutExercise(it.exercise.name, it.orderedSets, it.entry.note, it.entry.supersetGroup) })
    }

    /** Restore everything workout-related from a backup (merge; never duplicates). Returns workouts added. */
    suspend fun restore(exercises: List<Exercise>, templates: List<ImportedTemplate>, workouts: List<ImportedWorkout>): Int {
        restoreExercises(exercises)
        val byName = exercises.associateBy { it.name.lowercase() }
        fun fallback(name: String) = byName[name.lowercase()] ?: Exercise(name = name, type = ExerciseType.MACHINE, bodyPart = BodyPart.OTHER)
        val added = importWorkouts(workouts, ::fallback)
        // Templates may reference exercises that appear nowhere else.
        for (t in templates) for ((name, _) in t.exercises) {
            if (dao.findExercise(name) == null) dao.insertExercise(fallback(name).copy(id = 0, name = name))
        }
        restoreTemplates(templates)
        return added
    }

    /**
     * Merge workouts (import or restore). Exercises are matched by name (case-insensitive) and created
     * when missing using [exerciseFor]. Workouts whose startedAt already exists are skipped, so repeating
     * an import never duplicates. Returns how many workouts were added.
     */
    suspend fun importWorkouts(workouts: List<ImportedWorkout>, exerciseFor: (String) -> Exercise): Int = db.withTransaction {
        val ids = HashMap<String, Long>()
        suspend fun exerciseId(name: String): Long = ids.getOrPut(name.lowercase()) {
            dao.findExercise(name)?.id ?: dao.insertExercise(exerciseFor(name).copy(id = 0, name = name))
        }
        val existing = dao.allStartTimes().toHashSet()
        var added = 0
        for (w in workouts) {
            if (w.workout.startedAt in existing) continue
            existing += w.workout.startedAt
            val id = dao.insertWorkout(w.workout.copy(id = 0, templateId = null, date = dateOf(w.workout.startedAt)))
            w.exercises.forEachIndexed { i, ex ->
                insertExercise(id, i, ExerciseSpec(exerciseId(ex.name), emptyList(), ex.supersetGroup, ex.note))
                    .let { weId ->
                        dao.insertSets(ex.sets.mapIndexed { j, s -> s.copy(id = 0, workoutExerciseId = weId, position = j) })
                    }
            }
            added++
        }
        added
    }

    /** Restore exercises (by name; existing ones are kept as they are). */
    suspend fun restoreExercises(exercises: List<Exercise>) = db.withTransaction {
        for (e in exercises) if (dao.findExercise(e.name) == null) dao.insertExercise(e.copy(id = 0))
    }

    /** Restore templates by name (skips names that exist). Exercise names must already exist. */
    suspend fun restoreTemplates(templates: List<ImportedTemplate>) = db.withTransaction {
        val folders = dao.allFolders().associateBy { it.name.lowercase() }.toMutableMap()
        for (t in templates) {
            if (dao.findTemplate(t.name) != null) continue
            val folderId = t.folder?.let { name ->
                folders.getOrPut(name.lowercase()) { TemplateFolder(dao.insertFolder(TemplateFolder(name = name, position = folders.size)), name) }.id
            }
            val specs = t.exercises.mapNotNull { (name, spec) -> dao.findExercise(name)?.let { spec.copy(exerciseId = it.id) } }
            saveTemplate(0, t.name, specs, folderId, t.note)
        }
    }

    /**
     * Create templates named [names] from the latest finished workout with that name, skipping names that
     * already have a template or no workouts. Returns the names created.
     */
    suspend fun seedTemplatesFromHistory(names: List<String>): List<String> {
        val created = mutableListOf<String>()
        for (name in names) {
            if (dao.findTemplate(name) != null) continue
            val latest = dao.latestWorkoutNamed(name) ?: continue
            val full = dao.getWorkoutFull(latest.id) ?: continue
            saveTemplate(0, name, WorkoutMath.specsOf(full, completedOnly = true))
            created += name
        }
        return created
    }
}

/** A workout to import: [workout] ids are ignored; exercises are referenced by name. */
data class ImportedWorkout(val workout: Workout, val exercises: List<ImportedWorkoutExercise>)

data class ImportedWorkoutExercise(val name: String, val sets: List<WorkoutSet>, val note: String? = null, val supersetGroup: Int? = null)

/** A template to restore; exercises are (exercise name, spec with exerciseId ignored). */
data class ImportedTemplate(val name: String, val folder: String?, val note: String?, val exercises: List<Pair<String, ExerciseSpec>>)

class DuplicateExerciseException(name: String) : Exception("\"$name\" is already in your exercises.")
