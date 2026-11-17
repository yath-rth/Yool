package com.yatharth.yool.domain

import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import java.time.LocalDate

/** A day with every metric merged from all sources. This is what UI, graphs, insights and export consume. */
data class ResolvedDay(
    val date: LocalDate,
    val weightKg: Double? = null,
    val calories: Int? = null,
    val proteinG: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val workoutDone: Boolean? = null,
    val binge: Boolean? = null,
    val bingeTrigger: String? = null,
    val bingeNote: String? = null,
    val mood: Int? = null,
    val note: String? = null,
    /** Which source won for each metric that has a value. */
    val sources: Map<Metric, Source> = emptyMap(),
) {
    val checkInDone: Boolean get() = binge != null && mood != null
}

object DayResolver {

    /** Merge one day. [imported] may contain values for other days; they are ignored. */
    fun resolve(date: LocalDate, entry: DailyEntry?, imported: List<ImportedValue>): ResolvedDay {
        val sources = mutableMapOf<Metric, Source>()
        val candidates = imported.filter { it.date == date }.groupBy { it.metric }

        fun pick(metric: Metric, manual: Double?): Double? {
            if (manual != null) {
                sources[metric] = Source.MANUAL; return manual
            }
            val best = candidates[metric]?.minWithOrNull(compareBy<ImportedValue> { it.source.priority }.thenByDescending { it.importedAt })
                ?: return null
            sources[metric] = best.source
            return best.value
        }

        val workout = pick(Metric.WORKOUT, entry?.workoutDone?.let { if (it) 1.0 else 0.0 })

        return ResolvedDay(
            date = date,
            weightKg = pick(Metric.WEIGHT_KG, entry?.weightKg),
            calories = pick(Metric.CALORIES, entry?.calories?.toDouble())?.toInt(),
            proteinG = pick(Metric.PROTEIN_G, entry?.proteinG),
            steps = pick(Metric.STEPS, entry?.steps?.toDouble())?.toInt(),
            sleepHours = pick(Metric.SLEEP_HOURS, entry?.sleepHours),
            workoutDone = workout?.let { it > 0.0 },
            binge = entry?.binge,
            bingeTrigger = entry?.bingeTrigger,
            bingeNote = entry?.bingeNote,
            mood = entry?.mood,
            note = entry?.note,
            sources = sources,
        )
    }

    /** Resolve every day in [from]..[to] inclusive (days with no data are included, all-null). */
    fun resolveRange(
        from: LocalDate,
        to: LocalDate,
        entries: List<DailyEntry>,
        imported: List<ImportedValue>,
    ): List<ResolvedDay> {
        val byDate = entries.associateBy { it.date }
        val importedByDate = imported.groupBy { it.date }
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .map { d -> resolve(d, byDate[d], importedByDate[d].orEmpty()) }
            .toList()
    }
}
