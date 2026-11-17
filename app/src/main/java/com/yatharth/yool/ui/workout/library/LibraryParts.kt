package com.yatharth.yool.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.theme.Ink

/** Compact tappable text action (ink, caps, ExtraBold) for inline text links. */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = color,
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

/** Tiny inline icon for dense rows. Standalone buttons use TxIconButton instead. */
@Composable
internal fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Icon(
        icon, description,
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(6.dp).size(22.dp),
        tint = if (enabled) Ink else Ink.copy(alpha = 0.25f),
    )
}
