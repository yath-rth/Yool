package com.yatharth.yool.ui.graphs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatharth.yool.domain.DatePoint
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.Stats
import com.yatharth.yool.ui.theme.Amber
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.JetBrainsMonoNL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/*
 * Small custom Canvas charts. Vico 3 was evaluated but step-function targets, per-bar colouring against a
 * moving target and the calendar heatmap are all simpler (and more predictable) to draw directly.
 */

data class BarDatum(val date: LocalDate, val value: Double, val color: Color)

data class LineSeries(
    val points: List<DatePoint>,
    val color: Color,
    val strokeDp: Float = 2f,
    val dots: Boolean = false,
    val dotColor: ((DatePoint) -> Color)? = null,
    val dotRadiusDp: Float = 3f,
)

private val axisFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val axisFmtLong = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

private fun niceStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    val nice = when {
        n <= 1.0 -> 1.0
        n <= 2.0 -> 2.0
        n <= 2.5 -> 2.5
        n <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/**
 * Date-based chart: bars, lines and a step-function target line share one x axis ([from]..[to] inclusive)
 * and one y axis. Bars start at zero; line-only charts fit their data.
 */
@Composable
fun TimeChart(
    from: LocalDate,
    to: LocalDate,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    bars: List<BarDatum> = emptyList(),
    barSpanDays: Int = 1,
    lines: List<LineSeries> = emptyList(),
    stepLine: List<DatePoint> = emptyList(),
    stepColor: Color = Ink,
    yMin: Double? = null,
    yMax: Double? = null,
    highlight: DatePoint? = null,
    yFormat: (Double) -> String = { v -> if (abs(v) >= 100) "%.0f".format(Locale.US, v) else "%.1f".format(Locale.US, v) },
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = JetBrainsMonoNL, fontWeight = FontWeight.Normal, fontSize = 9.sp, color = Ink.copy(alpha = 0.6f))
    Canvas(modifier.fillMaxWidth().height(height)) {
        val values = bars.map { it.value } + lines.flatMap { l -> l.points.map { it.value } } + stepLine.map { it.value }
        if (values.isEmpty()) return@Canvas
        val barsOnly = bars.isNotEmpty()
        var lo = yMin ?: if (barsOnly) 0.0 else values.min()
        var hi = yMax ?: values.max()
        if (yMin == null && !barsOnly) {
            val pad = max((hi - lo) * 0.1, 0.5)
            lo -= pad; hi += pad
        }
        if (hi - lo < 1e-9) hi = lo + 1.0
        val step = niceStep((hi - lo) / 4.0)
        if (yMin == null) lo = floor(lo / step) * step
        if (yMax == null) hi = ceil(hi / step) * step
        if (hi - lo < 1e-9) hi = lo + step

        val ticks = generateSequence(lo) { it + step }.takeWhile { it <= hi + step * 0.001 }.toList()
        val tickLayouts = ticks.map { measurer.measure(yFormat(it), labelStyle) }
        val leftPad = (tickLayouts.maxOfOrNull { it.size.width } ?: 0) + 8.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val rightPad = 8.dp.toPx()
        val plotL = leftPad
        val plotR = size.width - rightPad
        val plotT = topPad
        val plotB = size.height - bottomPad
        val plotW = plotR - plotL
        val plotH = plotB - plotT
        val totalDays = (Stats.daysBetween(from, to) + 1).toFloat().coerceAtLeast(1f)

        fun yOf(v: Double) = plotB - ((v - lo) / (hi - lo)).toFloat() * plotH
        fun xFrac(d: LocalDate) = Stats.daysBetween(from, d).toFloat() / totalDays
        fun xCenter(d: LocalDate) = plotL + (xFrac(d) + 0.5f / totalDays) * plotW

        // grid + y labels
        ticks.forEachIndexed { i, t ->
            val y = yOf(t)
            drawLine(Ink.copy(alpha = if (i == 0) 0.45f else 0.10f), Offset(plotL, y), Offset(plotR, y), 1.dp.toPx())
            val tl = tickLayouts[i]
            drawText(tl, topLeft = Offset(plotL - 6.dp.toPx() - tl.size.width, y - tl.size.height / 2f))
        }
        // x labels
        val fractions = listOf(0f, 1f / 3f, 2f / 3f, 1f)
        fractions.forEachIndexed { i, f ->
            val date = from.plusDays(((totalDays - 1) * f).toLong())
            val tl = measurer.measure(date.format(if (totalDays > 300) axisFmtLong else axisFmt), labelStyle)
            val cx = plotL + (xFrac(date) + 0.5f / totalDays) * plotW
            val x = when (i) {
                0 -> plotL
                fractions.lastIndex -> plotR - tl.size.width
                else -> cx - tl.size.width / 2f
            }
            drawText(tl, topLeft = Offset(x, plotB + 4.dp.toPx()))
        }

        clipRect(plotL - 6.dp.toPx(), plotT - 6.dp.toPx(), plotR + 6.dp.toPx(), plotB + 1f) {
            // bars
            bars.forEach { b ->
                val slot = plotW / totalDays * barSpanDays
                val w = max(slot * 0.72f, 1.5.dp.toPx().coerceAtMost(slot))
                val center = plotL + (xFrac(b.date) * plotW) + slot / 2f
                val top = yOf(b.value)
                drawRect(b.color, Offset(center - w / 2f, top), Size(w, plotB - top))
            }
            // step target
            if (stepLine.isNotEmpty()) {
                val path = Path()
                var prevY = yOf(stepLine.first().value)
                path.moveTo(plotL, prevY)
                stepLine.drop(1).forEach { p ->
                    val x = plotL + xFrac(p.date) * plotW
                    path.lineTo(x, prevY)
                    prevY = yOf(p.value)
                    path.lineTo(x, prevY)
                }
                path.lineTo(plotR, prevY)
                drawPath(
                    path, stepColor,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())), join = StrokeJoin.Round),
                )
            }
            // lines
            lines.forEach { l ->
                val pts = l.points.sortedBy { it.date }
                if (pts.size > 1) {
                    val path = Path()
                    pts.forEachIndexed { i, p ->
                        val x = xCenter(p.date)
                        val y = yOf(p.value)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, l.color, style = Stroke(l.strokeDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (l.dots || pts.size == 1) {
                    pts.forEach { p ->
                        val c = Offset(xCenter(p.date), yOf(p.value))
                        drawCircle(Ink, l.dotRadiusDp.dp.toPx() + 1.dp.toPx(), c)
                        drawCircle(l.dotColor?.invoke(p) ?: l.color, l.dotRadiusDp.dp.toPx(), c)
                    }
                }
            }
        }
        // highlight (PR) drawn unclipped so the ring is never cut
        highlight?.let { p ->
            val c = Offset(xCenter(p.date), yOf(p.value))
            drawCircle(Ink, 8.dp.toPx(), c)
            drawCircle(Amber, 6.5.dp.toPx(), c)
            val tl = measurer.measure("PR", labelStyle.copy(fontWeight = FontWeight.ExtraBold, color = Ink))
            val tx = (c.x - tl.size.width / 2f).coerceIn(plotL, plotR - tl.size.width)
            drawText(tl, topLeft = Offset(tx, max(0f, c.y - 11.dp.toPx() - tl.size.height)))
        }
    }
}

/** Legend dot + label row. */
@Composable
fun LegendItem(color: Color, text: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dashed) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { Box(Modifier.width(5.dp).height(2.dp).background(color)) }
            }
        } else Box(Modifier.size(8.dp).background(color))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

/** Horizontal proportion bar with label and value text, for distributions and breakdowns. */
@Composable
fun HBarRow(
    label: String,
    fraction: Float,
    valueText: String,
    color: Color,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 84.dp,
    valueWidth: Dp = 52.dp,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(labelWidth), maxLines = 1)
        Box(Modifier.weight(1f).height(14.dp).background(Ink.copy(alpha = 0.07f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
        Text(valueText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(valueWidth))
    }
}

/**
 * Calendar heatmap: columns are Monday-start weeks, rows Mon..Sun.
 * Green = no binge, coral = binge, faint = not logged. Scrolls horizontally when wider than the card.
 */
@Composable
fun BingeHeatmap(days: List<ResolvedDay>, from: LocalDate, to: LocalDate, modifier: Modifier = Modifier) {
    val byDate = days.associateBy { it.date }
    val firstWeek = Stats.weekStart(from)
    val weeks = (Stats.daysBetween(firstWeek, to) / 7 + 1).toInt()
    val cell = 14.dp
    val gap = 3.dp
    val scroll = rememberScrollState()
    LaunchedEffect(weeks) { scroll.scrollTo(scroll.maxValue) }
    val measurer = rememberTextMeasurer()
    val monthStyle = TextStyle(fontFamily = JetBrainsMonoNL, fontWeight = FontWeight.Normal, fontSize = 9.sp, color = Ink.copy(alpha = 0.6f))
    val width = (cell + gap) * weeks
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.height(14.dp))
            listOf("M", "", "W", "", "F", "", "S").forEach {
                Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Canvas(Modifier.width(width).height(14.dp + (cell + gap) * 7)) {
                val c = cell.toPx()
                val g = gap.toPx()
                val top = 14.dp.toPx()
                var lastMonth = -1
                for (w in 0 until weeks) {
                    val weekDate = firstWeek.plusWeeks(w.toLong())
                    if (weekDate.monthValue != lastMonth && weekDate.dayOfMonth <= 7) {
                        lastMonth = weekDate.monthValue
                        val tl = measurer.measure(weekDate.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), monthStyle)
                        drawText(tl, topLeft = Offset(w * (c + g), 0f))
                    }
                    for (r in 0 until 7) {
                        val date = weekDate.plusDays(r.toLong())
                        if (date.isBefore(from) || date.isAfter(to)) continue
                        val b = byDate[date]?.binge
                        val color = when (b) {
                            true -> Coral
                            false -> Green
                            null -> Ink.copy(alpha = 0.08f)
                        }
                        val tl = Offset(w * (c + g), top + r * (c + g))
                        drawRect(color, tl, Size(c, c))
                        if (b != null) drawRect(Ink, tl, Size(c, c), style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
    }
}
