package com.yatharth.yool.domain

import com.yatharth.yool.data.db.BodyMeasurement
import com.yatharth.yool.data.model.MeasurementPart
import java.time.LocalDate

/** Latest value of one part, with the change since the previous measurement of that part. */
data class PartSummary(
    val part: MeasurementPart,
    val latest: Double,
    val latestDate: LocalDate,
    /** latest − previous; null when there's only one measurement. */
    val change: Double?,
    val count: Int,
)

object MeasurementMath {
    /** One summary per part that has data, in [MeasurementPart] order. */
    fun summaries(all: List<BodyMeasurement>): List<PartSummary> =
        all.groupBy { it.part }.map { (part, list) ->
            val sorted = list.sortedBy { it.date }
            val last = sorted.last()
            val prev = sorted.getOrNull(sorted.size - 2)
            PartSummary(part, last.value, last.date, prev?.let { last.value - it.value }, sorted.size)
        }.sortedBy { it.part.ordinal }

    /** Values typed in a form → parsed map. Blank → null (clears that part); unparsable → absent. */
    fun parseForm(fields: Map<MeasurementPart, String>): Map<MeasurementPart, Double?> =
        fields.mapNotNull { (part, text) ->
            val t = text.trim().replace(',', '.')
            when {
                t.isEmpty() -> part to null
                else -> t.toDoubleOrNull()?.takeIf { it > 0 && it < part.max }?.let { part to it }
            }
        }.toMap()

    /** "+1.2" / "−0.5" / "0.0". */
    fun signed(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        return when {
            r > 0 -> "+" + String.format(java.util.Locale.US, "%.1f", r)
            r < 0 -> "−" + String.format(java.util.Locale.US, "%.1f", -r)
            else -> "0.0"
        }
    }
}
