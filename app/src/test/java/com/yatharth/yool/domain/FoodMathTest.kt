package com.yatharth.yool.domain

import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.DayFoodTotals
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.model.Meal
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.io.backup.Backup
import com.yatharth.yool.io.backup.BackupData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FoodMathTest {
    private val d = LocalDate.of(2026, 11, 17)
    private val rice = Food(id = 7, name = "Basmati rice", servingLabel = "100 g", servingGrams = 100.0, kcal = 350.0, proteinG = 8.0, carbsG = 78.0, fatG = 1.0, fiberG = 1.5)

    @Test fun entrySnapshotsScaledNutrients() {
        val e = FoodMath.entryFor(rice, d, Meal.LUNCH, 1.5)
        assertEquals(525.0, e.kcal, 1e-9)
        assertEquals(12.0, e.proteinG, 1e-9)
        assertEquals(117.0, e.carbsG, 1e-9)
        assertEquals(2.25, e.fiberG, 1e-9)
        assertEquals("Basmati rice", e.nameSnapshot)
        assertEquals("100 g", e.servingLabelSnapshot)
        assertEquals(7L, e.foodId)
    }

    @Test fun gramsToServings() {
        assertEquals(0.6, FoodMath.servingsForGrams(rice, 60.0)!!, 1e-9)
        assertNull(FoodMath.servingsForGrams(rice.copy(servingGrams = null), 60.0))
    }

    @Test fun rescaleIsProportional() {
        val e = FoodMath.rescale(FoodMath.entryFor(rice, d, Meal.LUNCH, 2.0), 0.5)
        assertEquals(0.5, e.servings, 0.0)
        assertEquals(175.0, e.kcal, 1e-9)
        assertEquals(4.0, e.proteinG, 1e-9)
    }

    @Test fun copyResetsIdsAndKeepsOrder() {
        val src = listOf(FoodMath.entryFor(rice, d, Meal.BREAKFAST, 1.0).copy(id = 5), FoodMath.entryFor(rice, d, Meal.BREAKFAST, 2.0).copy(id = 6))
        val out = FoodMath.copyTo(src, d.plusDays(1), Meal.SNACKS, now = 1000)
        assertEquals(listOf(0L, 0L), out.map { it.id })
        assertTrue(out.all { it.date == d.plusDays(1) && it.meal == Meal.SNACKS })
        assertEquals(listOf(1.0, 2.0), out.map { it.servings })
        assertEquals(listOf(1000L, 1001L), out.map { it.createdAt })
    }

    @Test fun totalsSum() {
        val n = listOf(FoodMath.entryFor(rice, d, Meal.LUNCH, 1.0), FoodMath.entryFor(rice, d, Meal.DINNER, 1.0)).total()
        assertEquals(700.0, n.kcal, 1e-9)
        assertEquals(16.0, n.proteinG, 1e-9)
    }

    @Test fun foodLogRanksBetweenTypedAndMfp() {
        val log = FoodMath.asImported(listOf(DayFoodTotals(d, 1899.6, 120.04)))
        val mfp = ImportedValue(d, Metric.CALORIES, Source.MFP, 2500.0)
        val r = DayResolver.resolve(d, null, log + mfp)
        assertEquals(1900, r.calories)
        assertEquals(120.0, r.proteinG!!, 1e-9)
        assertEquals(Source.FOOD_LOG, r.sources[Metric.CALORIES])
        val typed = DayResolver.resolve(d, DailyEntry(d, calories = 1500), log + mfp)
        assertEquals(1500, typed.calories)
        assertEquals(Source.MANUAL, typed.sources[Metric.CALORIES])
    }

    @Test fun backupV2RoundTripsFood() {
        val entry = FoodMath.entryFor(rice, d, Meal.DINNER, 0.6).copy(createdAt = 9)
        val data = BackupData(
            emptyList(), emptyList(), emptyList(), emptyList(),
            foods = listOf(rice.copy(createdAt = 3)),
            foodLog = listOf(entry),
            savedMeals = listOf("Usual lunch" to listOf(7L to 1.5)),
        )
        val back = Backup.decode(Backup.encode(data))
        assertEquals(data.foods, back.foods)
        assertEquals(listOf(entry.copy(id = 0)), back.foodLog)
        assertEquals(data.savedMeals, back.savedMeals)
    }

    @Test fun v1FileHasNoFood() {
        val back = Backup.decode("""{"formatVersion":1,"exportedAt":"x"}""")
        assertTrue(back.foods.isEmpty() && back.foodLog.isEmpty() && back.savedMeals.isEmpty())
    }

}
