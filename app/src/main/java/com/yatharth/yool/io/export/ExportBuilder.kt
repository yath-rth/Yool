package com.yatharth.yool.io.export

import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.Streaks
import com.yatharth.yool.domain.TargetHistory
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pure formatting of the AI/tool export. No Android dependencies. */
object ExportBuilder {

    // ── CSV ──────────────────────────────────────────────────────────────────

    fun csvField(v: String?): String {
        if (v == null) return ""
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    private fun csvRow(vararg cells: String?): String = cells.joinToString(",") { csvField(it) }

    private fun num(v: Double?): String = v?.let { String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.') } ?: ""

    private fun src(d: ResolvedDay, m: Metric): String = d.sources[m]?.name?.lowercase() ?: ""

    private fun flag(b: Boolean?): String = when (b) { true -> "1"; false -> "0"; null -> "" }

    val DAILY_HEADER = csvRow(
        "date", "weight_kg", "weight_source", "calories", "calories_source", "protein_g", "protein_source",
        "steps", "steps_source", "sleep_h", "sleep_source", "workout", "workout_source",
        "binge", "binge_trigger", "binge_note", "mood", "note",
    )

    /** One row per day in [days]; days with no data at all are skipped. */
    fun dailyCsv(days: List<ResolvedDay>): String = buildString {
        append(DAILY_HEADER).append('\n')
        for (d in days) {
            if (!hasData(d)) continue
            append(
                csvRow(
                    d.date.toString(),
                    num(d.weightKg), src(d, Metric.WEIGHT_KG),
                    d.calories?.toString(), src(d, Metric.CALORIES),
                    num(d.proteinG), src(d, Metric.PROTEIN_G),
                    d.steps?.toString(), src(d, Metric.STEPS),
                    num(d.sleepHours), src(d, Metric.SLEEP_HOURS),
                    flag(d.workoutDone), src(d, Metric.WORKOUT),
                    flag(d.binge), d.bingeTrigger, d.bingeNote, d.mood?.toString(), d.note,
                )
            ).append('\n')
        }
    }

    private fun hasData(d: ResolvedDay) = d.weightKg != null || d.calories != null || d.proteinG != null ||
        d.steps != null || d.sleepHours != null || d.workoutDone != null || d.binge != null || d.mood != null ||
        !d.note.isNullOrBlank()

    fun liftsCsv(lifts: List<LiftBestSet>): String = buildString {
        append(csvRow("date", "exercise", "weight_kg", "reps")).append('\n')
        for (l in lifts.sortedWith(compareBy({ it.date }, { it.exercise }))) {
            append(csvRow(l.date.toString(), l.exercise, num(l.weightKg), l.reps.toString())).append('\n')
        }
    }

    // ── Summary ──────────────────────────────────────────────────────────────

    private fun avg(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()
    private fun f1(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
    private fun f0(v: Double?) = v?.let { String.format(Locale.US, "%.0f", it) } ?: "-"

    private fun adherence(days: List<ResolvedDay>, targets: TargetHistory, metric: TargetMetric): String {
        var eligible = 0
        var hit = 0
        for (d in days) {
            val t = targets.valueOn(metric, d.date) ?: continue
            val ok: Boolean = when (metric) {
                TargetMetric.CALORIES -> d.calories?.let { it <= t }
                TargetMetric.PROTEIN -> d.proteinG?.let { it >= t }
                TargetMetric.STEPS -> d.steps?.let { it >= t }
                TargetMetric.SLEEP_HOURS -> d.sleepHours?.let { it >= t }
                TargetMetric.WEIGHT_GOAL -> null
            } ?: continue
            eligible++
            if (ok) hit++
        }
        return if (eligible == 0) "n/a" else "${hit * 100 / eligible}% ($hit of $eligible logged days)"
    }

    fun summaryMd(
        from: LocalDate,
        to: LocalDate,
        days: List<ResolvedDay>,
        targets: TargetHistory,
        lifts: List<LiftBestSet>,
    ): String = buildString {
        val inRange = days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
        appendLine("# yool health export")
        appendLine()
        appendLine(
            "This is a personal daily health log exported from the yool Android app, intended for an AI coach. " +
                "`daily.csv` has one row per logged day: weight_kg, calories (kcal eaten), protein_g, steps, sleep_h, " +
                "workout (1 = trained), binge (1 = binge eating episode that day, 0 = none) with an optional binge_trigger tag " +
                "and binge_note, mood (1 = worst, 5 = best) and a free-text note. The `*_source` columns say where a metric " +
                "came from (manual = typed by the user, mfp = MyFitnessPal, health_connect, strong_csv, migrated). " +
                "Blank cells mean not recorded, not zero. `lifts.csv` holds the best set (heaviest weight, then most reps) of each " +
                "exercise per workout day; weight 0 means bodyweight. Targets below are the goals the user set and may change over time."
        )
        appendLine()
        appendLine("## Period")
        appendLine()
        appendLine("- $from to $to (${java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1} days, ${inRange.count { hasData(it) }} with data)")
        appendLine()

        appendLine("## Targets in force")
        appendLine()
        var anyTarget = false
        for (m in TargetMetric.entries) {
            val hist = targets.history(m)
            if (hist.isEmpty()) continue
            val inForceAtStart = targets.entryOn(m, from)
            val changes = hist.filter { it.effectiveFrom.isAfter(from) && !it.effectiveFrom.isAfter(to) }
            val seq = listOfNotNull(inForceAtStart) + changes
            if (seq.isEmpty()) continue
            anyTarget = true
            val text = seq.joinToString("; ") { t ->
                val extra = t.targetDate?.let { " (by $it)" } ?: ""
                "${num(t.value)} ${m.unit}$extra from ${t.effectiveFrom}"
            }
            appendLine("- ${m.label}: $text")
        }
        if (!anyTarget) appendLine("- No targets set.")
        appendLine()

        appendLine("## Weekly breakdown (weeks start Monday)")
        appendLine()
        appendLine("| Week of | Avg weight kg | Avg kcal | Avg protein g | Avg steps | Avg sleep h | Avg mood | Workouts | Binges |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        val weeks = inRange.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedMap()
        for ((wk, ds) in weeks) {
            appendLine(
                "| $wk | ${f1(avg(ds.mapNotNull { it.weightKg }))} | ${f0(avg(ds.mapNotNull { it.calories?.toDouble() }))} | " +
                    "${f0(avg(ds.mapNotNull { it.proteinG }))} | ${f0(avg(ds.mapNotNull { it.steps?.toDouble() }))} | " +
                    "${f1(avg(ds.mapNotNull { it.sleepHours }))} | ${f1(avg(ds.mapNotNull { it.mood?.toDouble() }))} | " +
                    "${ds.count { it.workoutDone == true }} | ${ds.count { it.binge == true }} |"
            )
        }
        appendLine()

        appendLine("## Target adherence")
        appendLine()
        appendLine("- Calories (at or under target): ${adherence(inRange, targets, TargetMetric.CALORIES)}")
        appendLine("- Protein (at or over target): ${adherence(inRange, targets, TargetMetric.PROTEIN)}")
        appendLine("- Steps (at or over target): ${adherence(inRange, targets, TargetMetric.STEPS)}")
        appendLine("- Sleep (at or over target): ${adherence(inRange, targets, TargetMetric.SLEEP_HOURS)}")
        appendLine()

        appendLine("## Weight")
        appendLine()
        val weighed = inRange.filter { it.weightKg != null }
        if (weighed.size >= 2) {
            val first = weighed.first()
            val last = weighed.last()
            val delta = last.weightKg!! - first.weightKg!!
            appendLine(
                "- ${f1(first.weightKg)} kg on ${first.date} to ${f1(last.weightKg)} kg on ${last.date}: " +
                    "${String.format(Locale.US, "%+.1f", delta)} kg"
            )
        } else if (weighed.size == 1) {
            appendLine("- Only one weigh-in: ${f1(weighed[0].weightKg)} kg on ${weighed[0].date}")
        } else appendLine("- No weigh-ins in this period.")
        appendLine()

        appendLine("## Binges")
        appendLine()
        val binges = inRange.filter { it.binge == true }
        appendLine("- Binge days: ${binges.size} of ${inRange.count { it.binge != null }} days with a check-in")
        val triggers = binges.groupingBy { it.bingeTrigger?.takeIf { t -> t.isNotBlank() } ?: "(no trigger)" }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        if (triggers.isNotEmpty()) appendLine("- Triggers: " + triggers.joinToString(", ") { "${it.key} x${it.value}" })
        appendLine("- Current binge-free streak (as of $to): ${Streaks.bingeFree(inRange, to)} days")
        appendLine()

        val workoutDays = inRange.count { it.workoutDone == true }
        val rangeLifts = lifts.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        appendLine("## Training")
        appendLine()
        appendLine("- Workout days: $workoutDays; lift records in lifts.csv: ${rangeLifts.size}")
        appendLine()

        appendLine("## Day notes")
        appendLine()
        val notes = inRange.filter { !it.note.isNullOrBlank() || !it.bingeNote.isNullOrBlank() }
        if (notes.isEmpty()) appendLine("- None.")
        for (d in notes) {
            val parts = listOfNotNull(
                d.note?.takeIf { it.isNotBlank() }?.let { "note: ${oneLine(it)}" },
                d.bingeNote?.takeIf { it.isNotBlank() }?.let { "binge note: ${oneLine(it)}" },
            )
            appendLine("- ${d.date}: ${parts.joinToString(" | ")}")
        }
    }

    private fun oneLine(s: String) = s.replace('\n', ' ').replace('\r', ' ').trim()

    // ── Zip ──────────────────────────────────────────────────────────────────

    fun zip(files: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, content) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun buildZip(from: LocalDate, to: LocalDate, days: List<ResolvedDay>, lifts: List<LiftBestSet>, targets: TargetHistory): ByteArray {
        val rangeLifts = lifts.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        return zip(
            listOf(
                "summary.md" to summaryMd(from, to, days, targets, lifts),
                "daily.csv" to dailyCsv(days),
                "lifts.csv" to liftsCsv(rangeLifts),
            )
        )
    }
}
