package com.yatharth.yool.ui.insights

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
fun InsightsScreen(contentPadding: PaddingValues) {
    Column(Modifier.padding(contentPadding).padding(16.dp)) {
        ScreenTitle("Insights")
        EmptyState("Coming soon", "Patterns across sleep, mood, calories and binges.")
    }
}
