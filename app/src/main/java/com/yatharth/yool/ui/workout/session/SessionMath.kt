package com.yatharth.yool.ui.workout.session

import com.yatharth.yool.data.db.ExerciseSetRow
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.domain.WorkoutMath
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A running rest countdown. [endAt] is epoch millis. */
data class RestState(
    val endAt: Long,
    val totalSec: Int,
    val label: String,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
) {
    fun remainingSec(now: Long): Int = ceil((endAt - now) / 1000.0).toInt().coerceAtLeast(0)

    fun progress(now: Long): Float =
        if (totalSec <= 0) 1f else (1f - remainingSec(now).toFloat() / totalSec).coerceIn(0f, 1f)

    /** Shift the end by [deltaSec]; the total grows/shrinks with it (never below what is left). */
    fun adjusted(deltaSec: Int, now: Long): RestState {
        val next = copy(endAt = endAt + deltaSec * 1000L)
        val remaining = next.remainingSec(now)
        return next.copy(totalSec = (totalSec + deltaSec).coerceAtLeast(remaining).coerceAtLeast(1))
    }

    companion object {
        fun start(seconds: Int, label: String, now: Long, sound: Boolean = true, vibrate: Boolean = true) =
            RestState(now + seconds * 1000L, seconds, label, sound, vibrate)
    }
}

/** The editable value fields of a set. */
data class SetValues(val weightKg: Double? = null, val reps: Int? = null, val durationSec: Int? = null, val distanceM: Double? = null) {
    companion object {
        fun of(s: WorkoutSet) = SetValues(s.weightKg, s.reps, s.durationSec, s.distanceM)
        fun of(r: ExerciseSetRow) = SetValues(r.weightKg, r.reps, r.durationSec, r.distanceM)
    }
}

object SessionMath {
    /**
     * Values to store when a set is checked: each field the exercise type uses falls back to the previous
     * session's value (the placeholder hint) when left empty; unused fields stay null.
     */
    fun forComplete(current: SetValues, prev: SetValues?, type: ExerciseType): SetValues = SetValues(
        weightKg = if (type.usesWeight) current.weightKg ?: prev?.weightKg else null,
        reps = if (type.usesReps) current.reps ?: prev?.reps else null,
        durationSec = if (type.usesDuration) current.durationSec ?: prev?.durationSec else null,
        distanceM = if (type.usesDistance) current.distanceM ?: prev?.distanceM else null,
    )

    /** "50 × 8", "12", "30:00", "5 km · 30:00"; "—" when there is nothing. */
    fun valuesLabel(type: ExerciseType, v: SetValues): String {
        val parts = mutableListOf<String>()
        if (type.usesWeight || type.usesReps) {
            val w = v.weightKg?.takeIf { type.usesWeight }?.let { WorkoutMath.kg(it) }
            val r = v.reps?.takeIf { type.usesReps }?.toString()
            when {
                w != null && r != null -> parts += "$w × $r"
                w != null -> parts += w
                r != null -> parts += r
            }
        }
        if (type.usesDistance) v.distanceM?.let { parts += "${WorkoutMath.kg(it / 1000)} km" }
        if (type.usesDuration) v.durationSec?.let { parts += durationText(it) }
        return parts.joinToString(" · ").ifEmpty { "—" }
    }

    /** Labels for the SET column: normal sets are numbered 1.., others show W/D/F. */
    fun setLabels(types: List<SetType>): List<String> {
        var n = 0
        return types.map { t -> if (t == SetType.NORMAL) (++n).toString() else t.short }
    }

    /** "90", "1:30" or "1:02:03" to seconds; blank/invalid gives null. */
    fun parseDuration(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val parts = t.split(':')
        if (parts.size > 3) return null
        var total = 0
        for (p in parts) total = total * 60 + (p.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null)
        return total
    }

    /** Seconds to "m:ss" (or "h:mm:ss"). */
    fun durationText(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        return if (s >= 3600) "${s / 3600}:${(s % 3600 / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
        else WorkoutMath.formatClock(s)
    }

    /** Live workout clock: "5:09" / "1:05:09". */
    fun elapsedClock(ms: Long): String = durationText((ms / 1000).coerceAtLeast(0).toInt())

    /** Weight of the heaviest non-warm-up set that has a weight, or null. */
    fun heaviestWorking(sets: List<WorkoutSet>): Double? =
        sets.filter { it.type != SetType.WARMUP }.mapNotNull { it.weightKg }.filter { it > 0 }.maxOrNull()

    /** RPE choices 6..10 in 0.5 steps. */
    val RpeOptions: List<Double> = (0..8).map { 6.0 + it * 0.5 }

    fun rpeText(rpe: Double): String =
        if (rpe == rpe.roundToInt().toDouble()) rpe.roundToInt().toString() else "%.1f".format(java.util.Locale.US, rpe)
}
