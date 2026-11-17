package com.yatharth.yool.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.components.ScreenTitle

/** Settings is a column of independent sections; each lives in its own file. */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenFoodLibrary: () -> Unit = {},
    onOpenSavedMeals: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Settings")
        TargetsSection()
        FoodSection(onOpenFoodLibrary, onOpenSavedMeals)
        HealthConnectSection()
        RemindersSection()
        DataSection()
        Spacer(Modifier.height(24.dp))
    }
}
