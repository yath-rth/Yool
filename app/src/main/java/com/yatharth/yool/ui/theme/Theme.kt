package com.yatharth.yool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TracknixColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = ItemsBg,
    secondary = Green,
    tertiary = Coral,
    background = Background,
    onBackground = Ink,
    surface = ItemsBg,
    onSurface = Ink,
    surfaceVariant = SecondaryBg,
    onSurfaceVariant = IconColor,
    surfaceContainer = SecondaryBg,
    error = Coral,
    outline = Ink,
)

/** Tracknix uses one fixed palette (no dynamic colour, no dark variant yet). */
@Composable
fun YoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TracknixColorScheme,
        typography = adaptiveTypography(),
        content = content,
    )
}
