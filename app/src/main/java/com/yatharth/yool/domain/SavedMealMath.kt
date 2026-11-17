package com.yatharth.yool.domain

import com.yatharth.yool.data.db.Food
import com.yatharth.yool.data.db.SavedMealWithItems

/** Pure helpers for saved meals and the food library list. */
object SavedMealMath {
    fun total(items: List<Pair<Food, Double>>): Nutrients =
        items.fold(Nutrients.Zero) { acc, (food, servings) -> acc + food.perServing * servings }

    fun total(meal: SavedMealWithItems): Nutrients = total(meal.items.map { it.food to it.item.servings })

    /** Parse a typed servings value; null when blank, non-numeric or not positive. */
    fun parseServings(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    fun formatServings(v: Double): String =
        if (v == Math.rint(v) && v < 1e9) v.toLong().toString()
        else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    fun canSave(name: String, servingsTexts: List<String>): Boolean =
        name.isNotBlank() && servingsTexts.isNotEmpty() && servingsTexts.all { parseServings(it) != null }

    /** Library filter: case-insensitive match on name or brand; hides archived unless [showArchived]. */
    fun filterFoods(foods: List<Food>, query: String, showArchived: Boolean): List<Food> {
        val q = query.trim()
        return foods.filter { f ->
            (showArchived || !f.archived) &&
                (q.isEmpty() || f.name.contains(q, ignoreCase = true) || (f.brand?.contains(q, ignoreCase = true) == true))
        }
    }
}
