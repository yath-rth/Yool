package com.yatharth.yool.io.strong

import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import java.time.LocalDate
import kotlin.math.round

enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }

/** One usable working set. [weight] is in the file's own [unit], or null unit if the file carries no unit info. */
data class StrongSet(val date: LocalDate, val exercise: String, val weight: Double, val unit: WeightUnit?, val reps: Int)

data class StrongParseResult(
    /** Data rows in the file (excluding header). */
    val rowCount: Int,
    val sets: List<StrongSet>,
    /** Dates that had at least one non-rest-timer row. */
    val workoutDates: Set<LocalDate>,
    val exercises: Set<String>,
    /** True if the file carries its own unit info, so the unit picker is only a fallback. */
    val hasUnitInfo: Boolean,
) {
    val dateRange: ClosedRange<LocalDate>? get() = workoutDates.minOrNull()?.let { it..workoutDates.max() }

    /** Best set per (date, exercise): heaviest weight, ties broken by most reps. Weights converted to kg. */
    fun bestSets(defaultUnit: WeightUnit): List<LiftBestSet> =
        sets.map { it.copy(weight = StrongImport.toKg(it.weight, it.unit ?: defaultUnit), unit = WeightUnit.KG) }
            .groupBy { it.date to it.exercise }
            .map { (key, group) ->
                val best = group.maxWith(compareBy<StrongSet> { it.weight }.thenBy { it.reps })
                LiftBestSet(key.first, key.second, best.weight, best.reps)
            }
            .sortedWith(compareBy<LiftBestSet> { it.date }.thenBy { it.exercise })

    fun workoutValues(importedAt: Long = System.currentTimeMillis()): List<ImportedValue> =
        workoutDates.sorted().map { ImportedValue(it, Metric.WORKOUT, Source.STRONG_CSV, 1.0, importedAt) }
}

object StrongImport {
    private const val KG_PER_LB = 0.45359237
    private val DATE_RE = Regex("""^\s*(\d{4}-\d{2}-\d{2})""")

    fun toKg(weight: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) weight else round(weight * KG_PER_LB * 100) / 100

    class FormatException(message: String) : Exception(message)

    fun parse(text: String): StrongParseResult {
        val delimiter = Csv.detectDelimiter(text.removePrefix("﻿"))
        val rows = Csv.parse(text, delimiter)
        if (rows.isEmpty()) throw FormatException("The file is empty.")
        val header = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1

        val dateCol = col("date")
        val exCol = col("exercise name", "exercise")
        val repsCol = col("reps")
        val orderCol = col("set order")
        val unitCol = col("weight unit")
        val weightCol = col("weight").let {
            if (it >= 0) it else header.indexOfFirst { h -> h.startsWith("weight") && h != "weight unit" }
        }
        if (dateCol < 0 || exCol < 0 || repsCol < 0) {
            throw FormatException("This doesn't look like a Strong export (needs Date, Exercise Name and Reps columns).")
        }
        // Older exports may encode the unit in the weight column's name, e.g. "Weight (lbs)".
        val headerUnit: WeightUnit? = when {
            weightCol < 0 -> null
            header[weightCol].contains("lb") -> WeightUnit.LB
            header[weightCol].contains("kg") -> WeightUnit.KG
            else -> null
        }

        fun cell(r: List<String>, i: Int): String = if (i in r.indices) r[i].trim() else ""
        fun number(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

        val data = rows.drop(1)
        val sets = mutableListOf<StrongSet>()
        val dates = mutableSetOf<LocalDate>()
        val exercises = mutableSetOf<String>()
        for (r in data) {
            val exercise = cell(r, exCol)
            val order = cell(r, orderCol)
            if (exercise.isEmpty() || exercise.equals("Rest Timer", true) || order.equals("Rest Timer", true)) continue
            val date = DATE_RE.find(cell(r, dateCol))?.groupValues?.get(1)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            dates += date
            if (order.equals("W", true)) continue
            val reps = number(cell(r, repsCol))?.toInt() ?: continue
            if (reps <= 0) continue
            val weight = number(cell(r, weightCol)) ?: 0.0
            val unit = when (cell(r, unitCol).lowercase()) {
                "kg", "kgs" -> WeightUnit.KG
                "lb", "lbs" -> WeightUnit.LB
                else -> headerUnit
            }
            exercises += exercise
            sets += StrongSet(date, exercise, weight, unit, reps)
        }
        return StrongParseResult(data.size, sets, dates, exercises, unitCol >= 0 || headerUnit != null)
    }
}
