package com.yatharth.yool.ui.graphs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.ScreenTitle

/** Placeholder — implemented on feat/graphs. */
@Composable
fun GraphsScreen(contentPadding: PaddingValues) {
    Column(Modifier.padding(contentPadding).padding(16.dp)) {
        ScreenTitle("Graphs")
        EmptyState("Coming soon", "Progress graphs will live here.")
    }
}
