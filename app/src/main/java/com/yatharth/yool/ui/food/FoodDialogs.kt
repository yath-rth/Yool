package com.yatharth.yool.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.theme.Ink

/** Tracknix-styled modal: a ShadowCard in a dialog window, scrollable for the keyboard. */
@Composable
fun TxDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 20.dp)) {
            ShadowCard {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Ink)
                    content()
                }
            }
        }
    }
}
