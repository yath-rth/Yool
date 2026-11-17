package com.yatharth.yool.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tracknix page background: sage fill with a 20dp grid of faint ink dots. */
fun Modifier.dotGridBackground(
    color: Color = Background,
    dotColor: Color = Ink.copy(alpha = 0.18f),
    spacing: Dp = 20.dp,
    dotRadius: Dp = 1.5.dp,
): Modifier = this.drawWithCache {
    val step = spacing.toPx()
    val diameter = dotRadius.toPx() * 2
    val width = size.width
    val height = size.height
    val points = buildList {
        var y = step / 2
        while (y < height) {
            var x = step / 2
            while (x < width) {
                add(Offset(x, y)); x += step
            }
            y += step
        }
    }
    onDrawBehind {
        drawRect(color)
        drawPoints(points, PointMode.Points, dotColor, strokeWidth = diameter, cap = StrokeCap.Round)
    }
}
