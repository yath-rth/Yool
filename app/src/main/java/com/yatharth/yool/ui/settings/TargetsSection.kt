package com.yatharth.yool.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard

/** Settings section stub — implemented on feat/targets-reminders. */
@Composable
fun TargetsSection() {
    ShadowCard {
        SectionLabel("Targets")
        Text("Not set up yet.", style = MaterialTheme.typography.bodySmall)
    }
}
