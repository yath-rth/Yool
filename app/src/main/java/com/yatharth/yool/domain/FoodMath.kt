package com.yatharth.yool.domain

import com.yatharth.yool.data.db.DayFoodTotals
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.data.db.FoodLogEntry
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.model.Meal
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import java.time.LocalDate

/** The five tracked nutrients. */
data class Nutrients(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
) {
    operator fun plus(o: Nutrients) = Nutrients(kcal + o.kcal, proteinG + o.proteinG, carbsG + o.carbsG, fatG + o.fatG, fiberG + o.fiberG)
    operator fun times(f: Double) = Nutrients(kcal * f, proteinG * f, carbsG * f, fatG * f, fiberG * f)

    companion object {
        val Zero = Nutrients()
    }
}

val Food.perServing: Nutrients get() = Nutrients(kcal, proteinG, carbsG, fatG, fiberG)
val FoodLogEntry.nutrients: Nutrients get() = Nutrients(kcal, proteinG, carbsG, fatG, fiberG)

fun Iterable<FoodLogEntry>.total(): Nutrients = fold(Nutrients.Zero) { acc, e -> acc + e.nutrients }

object FoodMath {
    /** Servings for a weight in grams; null if the food has no gram weight. */
    fun servingsForGrams(food: Food, grams: Double): Double? =
        food.servingGrams?.takeIf { it > 0 }?.let { grams / it }

    /** A new log entry for [servings] of [food], with nutrients snapshotted now. */
    fun entryFor(food: Food, date: LocalDate, meal: Meal, servings: Double, createdAt: Long = System.currentTimeMillis()): FoodLogEntry {
        val n = food.perServing * servings
        return FoodLogEntry(
            date = date, meal = meal, foodId = food.id,
            nameSnapshot = food.name, servingLabelSnapshot = food.servingLabel, servings = servings,
            kcal = n.kcal, proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG, fiberG = n.fiberG,
            createdAt = createdAt,
        )
    }

    /** Change servings of an existing entry, scaling its snapshotted nutrients proportionally. */
    fun rescale(entry: FoodLogEntry, servings: Double): FoodLogEntry {
        if (entry.servings <= 0.0) return entry.copy(servings = servings)
        val n = entry.nutrients * (servings / entry.servings)
        return entry.copy(servings = servings, kcal = n.kcal, proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG, fiberG = n.fiberG)
    }

    /** Copies of [entries] re-dated into [date]/[meal] as fresh rows (ids reset, order kept). */
    fun copyTo(entries: List<FoodLogEntry>, date: LocalDate, meal: Meal, now: Long = System.currentTimeMillis()): List<FoodLogEntry> =
        entries.mapIndexed { i, e -> e.copy(id = 0, date = date, meal = meal, createdAt = now + i) }

    /**
     * Food-log daily totals as synthetic imported values, so [DayResolver] can rank them against MFP/HC
     * with the normal source priority.
     */
    fun asImported(totals: List<DayFoodTotals>): List<ImportedValue> = totals.flatMap {
        listOf(
            ImportedValue(it.date, Metric.CALORIES, Source.FOOD_LOG, Math.round(it.kcal).toDouble(), 0),
            ImportedValue(it.date, Metric.PROTEIN_G, Source.FOOD_LOG, Math.round(it.proteinG * 10) / 10.0, 0),
        )
    }
}
