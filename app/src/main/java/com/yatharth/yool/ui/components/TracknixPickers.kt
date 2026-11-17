package com.yatharth.yool.ui.components

import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.SecondaryBg

/*
 * Material date/time pickers have no Tracknix equivalent, so every picker uses these colours:
 * itemsBg surface, ink text, coral selection. Pair with TxButton(fill = false) actions or TxDialog.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun txDatePickerColors(): DatePickerColors = DatePickerDefaults.colors(
    containerColor = ItemsBg,
    titleContentColor = Ink,
    headlineContentColor = Ink,
    weekdayContentColor = Ink.copy(alpha = 0.7f),
    subheadContentColor = Ink,
    navigationContentColor = Ink,
    yearContentColor = Ink,
    currentYearContentColor = Ink,
    selectedYearContainerColor = Coral,
    selectedYearContentColor = ItemsBg,
    dayContentColor = Ink,
    selectedDayContainerColor = Coral,
    selectedDayContentColor = ItemsBg,
    todayContentColor = Ink,
    todayDateBorderColor = Ink,
    dayInSelectionRangeContainerColor = Coral.copy(alpha = 0.25f),
    dayInSelectionRangeContentColor = Ink,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun txTimePickerColors(): TimePickerColors = TimePickerDefaults.colors(
    clockDialColor = SecondaryBg,
    clockDialSelectedContentColor = ItemsBg,
    clockDialUnselectedContentColor = Ink,
    selectorColor = Coral,
    containerColor = ItemsBg,
    periodSelectorBorderColor = Ink,
    periodSelectorSelectedContainerColor = Coral,
    periodSelectorUnselectedContainerColor = ItemsBg,
    periodSelectorSelectedContentColor = ItemsBg,
    periodSelectorUnselectedContentColor = Ink,
    timeSelectorSelectedContainerColor = Coral,
    timeSelectorUnselectedContainerColor = SecondaryBg,
    timeSelectorSelectedContentColor = ItemsBg,
    timeSelectorUnselectedContentColor = Ink,
)
