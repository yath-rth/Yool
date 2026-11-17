package com.yatharth.yool.ui.theme

import androidx.compose.ui.graphics.Color

// Tracknix palette
val Background = Color(0xFFDBE0D8)      // soft sage — scaffold
val SecondaryBg = Color(0xFFDDE3DB)     // darker sage — sections, nav bar
val ItemsBg = Color(0xFFF8FFFF)         // cards, inputs
val Ink = Color(0xFF1A1A2E)             // text, borders, hard shadows
val Coral = Color(0xFFCB3D38)           // accent 1 — alerts, over target, binge
val Green = Color(0xFF52AB2E)           // accent 2 — success, hit target
val Amber = Color(0xFFE7C277)           // accent 3 — warnings, highlights
val DisabledBg = Color(0xFFBDB8AF)
val IconColor = Color(0xFF4A4A5A)
val Divider = Color(0xFFCCC7BE)

/** One muted colour per tracked metric (from the Tracknix category palette). */
object MetricColors {
    val Weight = Color(0xFF1B1B38)   // navy
    val Calories = Color(0xFFC4905B) // orange/brown
    val Protein = Color(0xFFB56C8A)  // muted pink
    val Steps = Color(0xFF6E87A6)    // muted blue
    val Sleep = Color(0xFF8D7AB8)    // purple
    val Mood = Color(0xFF5E9A68)     // green
    val Workout = Color(0xFF708A36)  // dark olive
    val Binge = Coral
}
