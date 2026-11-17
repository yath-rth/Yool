package com.yatharth.yool.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.domain.Nutrients
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.ScreenTitle

/** Screen title with a back arrow in front. */
@Composable
internal fun BackTitle(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.clickable(onClick = onBack).padding(end = 12.dp))
        ScreenTitle(title, Modifier.weight(1f), subtitle = subtitle)
    }
}

internal fun macroLine(n: Nutrients): String =
    "${Fmt.int(Math.round(n.kcal))} kcal · P ${Fmt.one(n.proteinG)} · C ${Fmt.one(n.carbsG)} · F ${Fmt.one(n.fatG)}"
