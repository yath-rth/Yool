package com.yatharth.yool.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg

/** Screen title with a back arrow in front and an optional trailing slot. */
@Composable
internal fun LibBackTitle(title: String, onBack: () -> Unit, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.clickable(onClick = onBack).padding(end = 12.dp))
        ScreenTitle(title, Modifier.weight(1f), subtitle = subtitle, trailing = trailing)
    }
}

/** Bordered dialog shell used by all small dialogs here. */
@Composable
internal fun TxDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(4.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).background(ItemsBg, shape).border(2.dp, Ink, shape)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    TxDialog(onDismiss) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("Cancel", onDismiss, Modifier.weight(1f))
            TxButton(confirmLabel, onConfirm, Modifier.weight(1f), bg = if (destructive) Coral else Ink, textColor = ItemsBg)
        }
    }
}

@Composable
internal fun PromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    TxDialog(onDismiss) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge)
        TxTextField(text, { text = it }, label, Modifier.fillMaxWidth(), maxLength = 60)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("Cancel", onDismiss, Modifier.weight(1f))
            TxButton(confirmLabel, { onConfirm(text.trim()) }, Modifier.weight(1f), bg = Coral, textColor = ItemsBg, enabled = text.isNotBlank())
        }
    }
}

/** Compact tappable text action (ink, caps, ExtraBold). */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Ink) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = color,
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

@Composable
internal fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Icon(
        icon, description,
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(6.dp).size(22.dp),
        tint = if (enabled) Ink else Ink.copy(alpha = 0.25f),
    )
}
