package com.yatharth.yool.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.yatharth.yool.data.model.FoodSource
import com.yatharth.yool.data.model.Meal
import java.time.LocalDate

/** A food in the user's library. Nutrients are **per serving** ([servingLabel], e.g. "1 cup" or "100 g"). */
@Entity(tableName = "food", indices = [Index(value = ["name", "servingLabel"], unique = true)])
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val servingLabel: String,
    /** Grams in one serving, when known — enables logging by weight. */
    val servingGrams: Double? = null,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    /** Hidden from search; kept because past log entries may reference it. */
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val source: FoodSource = FoodSource.USER,
)

/**
 * One food logged in a meal. Nutrients are the **totals for this entry** (already × servings) and are
 * snapshotted at log time, so editing or archiving the library food never rewrites history.
 */
@Entity(tableName = "food_log_entry", indices = [Index("date"), Index("foodId")])
data class FoodLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val meal: Meal,
    /** Library food this came from; null if that food no longer exists. */
    val foodId: Long?,
    val nameSnapshot: String,
    val servingLabelSnapshot: String,
    val servings: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A named group of foods that can be logged in one tap (e.g. "Usual breakfast"). */
@Entity(tableName = "saved_meal")
data class SavedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "saved_meal_item",
    foreignKeys = [
        ForeignKey(entity = SavedMeal::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Food::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mealId"), Index("foodId")],
)
data class SavedMealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodId: Long,
    val servings: Double,
)

/** Saved-meal item joined with its food. */
data class SavedMealItemWithFood(
    @Embedded val item: SavedMealItem,
    @Relation(parentColumn = "foodId", entityColumn = "id") val food: Food,
)

data class SavedMealWithItems(
    @Embedded val meal: SavedMeal,
    @Relation(entity = SavedMealItem::class, parentColumn = "id", entityColumn = "mealId")
    val items: List<SavedMealItemWithFood>,
)

/** Per-day food log sums (query result). */
data class DayFoodTotals(val date: LocalDate, val kcal: Double, val proteinG: Double)
