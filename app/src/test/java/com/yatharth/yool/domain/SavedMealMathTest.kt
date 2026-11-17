package com.yatharth.yool.domain

import com.yatharth.yool.data.db.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMealMathTest {
    private val rice = Food(id = 1, name = "Basmati rice", brand = "Tilda", servingLabel = "100 g", kcal = 350.0, proteinG = 8.0, carbsG = 78.0, fatG = 1.0, fiberG = 1.5)
    private val egg = Food(id = 2, name = "Egg", servingLabel = "1 egg", kcal = 70.0, proteinG = 6.0, fatG = 5.0, archived = true)

    @Test fun totalsScaleByServings() {
        val t = SavedMealMath.total(listOf(rice to 1.5, egg to 2.0))
        assertEquals(665.0, t.kcal, 1e-9)
        assertEquals(24.0, t.proteinG, 1e-9)
        assertEquals(117.0, t.carbsG, 1e-9)
        assertEquals(11.5, t.fatG, 1e-9)
        assertEquals(2.25, t.fiberG, 1e-9)
    }

    @Test fun emptyTotalIsZero() = assertEquals(Nutrients.Zero, SavedMealMath.total(emptyList()))

    @Test fun parseServings() {
        assertEquals(1.5, SavedMealMath.parseServings(" 1,5 ")!!, 0.0)
        assertNull(SavedMealMath.parseServings(""))
        assertNull(SavedMealMath.parseServings("0"))
        assertNull(SavedMealMath.parseServings("-2"))
        assertNull(SavedMealMath.parseServings("abc"))
    }

    @Test fun formatServings() {
        assertEquals("2", SavedMealMath.formatServings(2.0))
        assertEquals("1.5", SavedMealMath.formatServings(1.5))
        assertEquals("0.33", SavedMealMath.formatServings(1.0 / 3))
    }

    @Test fun canSaveRules() {
        assertTrue(SavedMealMath.canSave("Breakfast", listOf("1")))
        assertFalse(SavedMealMath.canSave("  ", listOf("1")))
        assertFalse(SavedMealMath.canSave("B", emptyList()))
        assertFalse(SavedMealMath.canSave("B", listOf("1", "x")))
    }

    @Test fun filterFoods() {
        val all = listOf(rice, egg)
        assertEquals(listOf(rice), SavedMealMath.filterFoods(all, "", false))
        assertEquals(listOf(rice, egg), SavedMealMath.filterFoods(all, "", true))
        assertEquals(listOf(rice), SavedMealMath.filterFoods(all, "TILDA", false))
        assertEquals(listOf(rice), SavedMealMath.filterFoods(all, " rice ", true))
        assertEquals(listOf(egg), SavedMealMath.filterFoods(all, "egg", true))
        assertTrue(SavedMealMath.filterFoods(all, "egg", false).isEmpty())
    }
}
