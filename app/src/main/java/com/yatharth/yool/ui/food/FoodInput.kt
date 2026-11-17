package com.yatharth.yool.ui.food

import com.yatharth.yool.data.db.Food
import com.yatharth.yool.ui.Fmt
import java.util.Locale

/** Pure text/number helpers for the food screens (servings <-> grams sync, validation, labels). */
object FoodInput {
    /** Number > 0, or null. Accepts "1,5" and "1.5". */
    fun parsePositive(s: String): Double? = Fmt.parseDouble(s)?.takeIf { it > 0 && it.isFinite() }

    /** Number >= 0, or null. */
    fun parseNonNegative(s: String): Double? = Fmt.parseDouble(s)?.takeIf { it >= 0 && it.isFinite() }

    /** Compact number text: up to [decimals] places, no trailing zeros ("1.5", "100"). */
    fun num(v: Double, decimals: Int = 2): String {
        val s = String.format(Locale.US, "%.${decimals}f", v)
        return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
    }

    /** Grams text for a servings text, given grams per serving; "" if either is invalid. */
    fun gramsFromServings(servingGrams: Double?, servingsText: String): String {
        val g = servingGrams?.takeIf { it > 0 } ?: return ""
        val s = parsePositive(servingsText) ?: return ""
        return num(g * s, 1)
    }

    /** Servings text for a grams text, given grams per serving; "" if either is invalid. */
    fun servingsFromGrams(servingGrams: Double?, gramsText: String): String {
        val g = servingGrams?.takeIf { it > 0 } ?: return ""
        val grams = parsePositive(gramsText) ?: return ""
        return num(grams / g, 3)
    }

    /** "1.5 × 100 g" */
    fun servingsLabel(servings: Double, servingLabel: String): String = "${num(servings)} × $servingLabel"

    fun kcal(v: Double): String = Fmt.int(Math.round(v))
    fun grams(v: Double): String = num(v, 1)
}

/** Editor form state with validation. */
data class FoodDraft(
    val name: String = "",
    val brand: String = "",
    val servingLabel: String = "1 serving",
    val servingGrams: String = "",
    val kcal: String = "",
    val protein: String = "0",
    val carbs: String = "0",
    val fat: String = "0",
    val fiber: String = "0",
) {
    /** First problem found, or null when the draft can be saved. */
    fun error(): String? {
        if (name.isBlank()) return "Name is required."
        if (servingLabel.isBlank()) return "Serving label is required."
        if (servingGrams.isNotBlank() && FoodInput.parsePositive(servingGrams) == null) return "Serving grams must be above 0."
        if (kcal.isBlank()) return "Calories are required."
        if (FoodInput.parseNonNegative(kcal) == null) return "Calories can't be negative."
        for ((label, v) in listOf("Protein" to protein, "Carbs" to carbs, "Fat" to fat, "Fiber" to fiber)) {
            if (v.isNotBlank() && FoodInput.parseNonNegative(v) == null) return "$label can't be negative."
        }
        return null
    }

    /** Builds a [Food] on top of [base] (keeps id, archived, createdAt, source). Call only when [error] is null. */
    fun toFood(base: Food? = null): Food {
        fun n(s: String) = FoodInput.parseNonNegative(s) ?: 0.0
        return (base ?: Food(name = "", servingLabel = "", kcal = 0.0)).copy(
            name = name.trim(),
            brand = brand.trim().ifEmpty { null },
            servingLabel = servingLabel.trim(),
            servingGrams = FoodInput.parsePositive(servingGrams),
            kcal = n(kcal), proteinG = n(protein), carbsG = n(carbs), fatG = n(fat), fiberG = n(fiber),
        )
    }

    companion object {
        fun from(f: Food) = FoodDraft(
            name = f.name, brand = f.brand.orEmpty(), servingLabel = f.servingLabel,
            servingGrams = f.servingGrams?.let { FoodInput.num(it, 1) } ?: "",
            kcal = FoodInput.num(f.kcal, 1), protein = FoodInput.num(f.proteinG, 1), carbs = FoodInput.num(f.carbsG, 1),
            fat = FoodInput.num(f.fatG, 1), fiber = FoodInput.num(f.fiberG, 1),
        )
    }
}
