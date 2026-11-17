package com.yatharth.yool.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.DuplicateFoodException
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.launch

/** Create ([foodId] == 0) or edit a library food. Calls [onDone] after save/archive/cancel. */
@Composable
fun FoodEditorScreen(
    foodId: Long,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
) {
    val repo = LocalContext.current.appContainer.foodRepository
    val scope = rememberCoroutineScope()
    var base by remember(foodId) { mutableStateOf<Food?>(null) }
    var draft by remember(foodId) { mutableStateOf(FoodDraft()) }
    var loaded by remember(foodId) { mutableStateOf(foodId == 0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(foodId) {
        if (foodId != 0L) {
            repo.getFood(foodId)?.let { base = it; draft = FoodDraft.from(it) }
            loaded = true
        }
    }

    fun edit(d: FoodDraft) { draft = d; error = null }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(if (foodId == 0L) "New food" else "Edit food", subtitle = "Nutrients are per serving")
        if (!loaded) return@Column

        ShadowCard {
            SectionLabel("Food")
            TxTextField(draft.name, { edit(draft.copy(name = it)) }, "Name", Modifier.fillMaxWidth(), maxLength = 80)
            TxTextField(draft.brand, { edit(draft.copy(brand = it)) }, "Brand (optional)", Modifier.fillMaxWidth(), maxLength = 60)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TxTextField(draft.servingLabel, { edit(draft.copy(servingLabel = it)) }, "Serving", Modifier.weight(1f), maxLength = 40)
                TxTextField(draft.servingGrams, { edit(draft.copy(servingGrams = it)) }, "Grams (opt.)", Modifier.weight(1f), unit = "g", numeric = true)
            }
        }
        ShadowCard {
            SectionLabel("Per serving")
            TxTextField(draft.kcal, { edit(draft.copy(kcal = it)) }, "Calories", Modifier.fillMaxWidth(), unit = "kcal", numeric = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TxTextField(draft.protein, { edit(draft.copy(protein = it)) }, "Protein", Modifier.weight(1f), unit = "g", numeric = true)
                TxTextField(draft.carbs, { edit(draft.copy(carbs = it)) }, "Carbs", Modifier.weight(1f), unit = "g", numeric = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TxTextField(draft.fat, { edit(draft.copy(fat = it)) }, "Fat", Modifier.weight(1f), unit = "g", numeric = true)
                TxTextField(draft.fiber, { edit(draft.copy(fiber = it)) }, "Fiber", Modifier.weight(1f), unit = "g", numeric = true)
            }
        }

        if (foodId != 0L) {
            Text(
                "Edits never change days you've already logged.",
                style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f),
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Coral) }

        TxButton(
            "Save", enabled = !busy, bg = Green, textColor = ItemsBg, modifier = Modifier.fillMaxWidth(),
            onClick = {
                val problem = draft.error()
                if (problem != null) { error = problem; return@TxButton }
                busy = true
                scope.launch {
                    try {
                        repo.saveFood(draft.toFood(base))
                        onDone()
                    } catch (e: DuplicateFoodException) {
                        error = e.message
                        busy = false
                    }
                }
            },
        )
        TxButton("Cancel", onClick = onDone, modifier = Modifier.fillMaxWidth())

        if (foodId != 0L && base != null) {
            Spacer(Modifier.height(8.dp))
            TxButton(
                "Remove food", textColor = Coral, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch { repo.removeFood(foodId); onDone() }
                },
            )
            Text(
                "Foods you've logged are archived, so past days keep their numbers.",
                style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

