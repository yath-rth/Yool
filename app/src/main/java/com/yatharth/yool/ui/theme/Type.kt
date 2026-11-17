package com.yatharth.yool.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.yatharth.yool.R

val JetBrainsMonoNL = FontFamily(
    Font(R.font.jetbrains_mono_nl_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_nl_extrabold, FontWeight.ExtraBold),
    Font(R.font.jetbrains_mono_nl_extralight, FontWeight.ExtraLight),
)

@Composable
fun adaptiveSp(baseSp: Float): TextUnit {
    val w = LocalConfiguration.current.screenWidthDp
    val scale = when {
        w < 360 -> 0.85f
        w < 400 -> 0.9f
        w < 500 -> 1.0f
        w < 600 -> 1.05f
        else -> 1.1f
    }
    return (baseSp * scale).sp
}

/**
 * Tracknix type scale: ExtraLight labels, Regular body, ExtraBold values.
 */
@Composable
fun adaptiveTypography(): Typography {
    fun style(weight: FontWeight, size: TextUnit, line: TextUnit, tracking: Float) =
        TextStyle(fontFamily = JetBrainsMonoNL, fontWeight = weight, fontSize = size, lineHeight = line, letterSpacing = tracking.sp, color = Ink)
    return Typography(
        bodySmall = style(FontWeight.Normal, adaptiveSp(12f), adaptiveSp(18f), 0.3f),
        bodyMedium = style(FontWeight.Normal, adaptiveSp(14f), adaptiveSp(22f), 0.2f),
        bodyLarge = style(FontWeight.Normal, adaptiveSp(17f), adaptiveSp(24f), 0f),
        titleLarge = style(FontWeight.ExtraBold, adaptiveSp(38f), adaptiveSp(44f), -0.5f),
        titleMedium = style(FontWeight.ExtraBold, adaptiveSp(30f), adaptiveSp(36f), -0.3f),
        titleSmall = style(FontWeight.ExtraBold, adaptiveSp(22f), adaptiveSp(28f), 0f),
        labelSmall = style(FontWeight.ExtraLight, adaptiveSp(10f), adaptiveSp(16f), 1.5f),
        labelMedium = style(FontWeight.ExtraLight, adaptiveSp(12f), adaptiveSp(18f), 1.2f),
        labelLarge = style(FontWeight.ExtraBold, adaptiveSp(20f), adaptiveSp(26f), 2f),
        headlineMedium = style(FontWeight.ExtraBold, adaptiveSp(28f), adaptiveSp(34f), -0.3f),
    )
}
