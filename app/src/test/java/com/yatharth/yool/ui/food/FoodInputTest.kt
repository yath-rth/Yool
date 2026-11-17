package com.yatharth.yool.ui.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FoodInputTest {
    @Test fun parsePositiveRejectsZeroNegativeAndGarbage() {
        assertEquals(1.5, FoodInput.parsePositive("1,5")!!, 0.0)
        assertNull(FoodInput.parsePositive("0"))
        assertNull(FoodInput.parsePositive("-2"))
        assertNull(FoodInput.parsePositive("abc"))
        assertNull(FoodInput.parsePositive(""))
    }

    @Test fun parseNonNegativeAllowsZero() {
        assertEquals(0.0, FoodInput.parseNonNegative("0")!!, 0.0)
        assertNull(FoodInput.parseNonNegative("-0.1"))
    }

    @Test fun numTrimsTrailingZeros() {
        assertEquals("1.5", FoodInput.num(1.5))
        assertEquals("100", FoodInput.num(100.0))
        assertEquals("0.33", FoodInput.num(1.0 / 3))
    }

    @Test fun gramsAndServingsSync() {
        assertEquals("150", FoodInput.gramsFromServings(100.0, "1.5"))
        assertEquals("1.5", FoodInput.servingsFromGrams(100.0, "150"))
        assertEquals("0.333", FoodInput.servingsFromGrams(300.0, "100"))
        assertEquals("", FoodInput.gramsFromServings(null, "1"))
        assertEquals("", FoodInput.gramsFromServings(100.0, "x"))
        assertEquals("", FoodInput.servingsFromGrams(0.0, "5"))
    }

    @Test fun servingsLabelFormat() {
        assertEquals("150 g", FoodInput.servingsLabel(1.5, "100 g"))
        assertEquals("5 g", FoodInput.servingsLabel(0.05, "100 g"))
        assertEquals("200 ml", FoodInput.servingsLabel(2.0, "100 ml"))
        assertEquals("1 cup", FoodInput.servingsLabel(1.0, "1 cup"))
        assertEquals("2.5 medium dosa", FoodInput.servingsLabel(2.5, "1 medium dosa"))
        assertEquals("1.5 × big bowl", FoodInput.servingsLabel(1.5, "big bowl"))
    }

    @Test fun draftValidation() {
        val ok = FoodDraft(name = "Oats", kcal = "380")
        assertNull(ok.error())
        assertNotNull(ok.copy(name = " ").error())
        assertNotNull(ok.copy(servingLabel = "").error())
        assertNotNull(ok.copy(kcal = "").error())
        assertNotNull(ok.copy(kcal = "-5").error())
        assertNotNull(ok.copy(protein = "-1").error())
        assertNotNull(ok.copy(servingGrams = "0").error())
        assertNull(ok.copy(servingGrams = "", fiber = "").error())
    }

    @Test fun draftToFoodTrimsAndParses() {
        val f = FoodDraft(name = " Oats ", brand = " ", servingLabel = "40 g", servingGrams = "40", kcal = "150,5", protein = "5").toFood()
        assertEquals("Oats", f.name)
        assertNull(f.brand)
        assertEquals(40.0, f.servingGrams!!, 0.0)
        assertEquals(150.5, f.kcal, 0.0)
        assertEquals(0.0, f.fatG, 0.0)
    }
}
