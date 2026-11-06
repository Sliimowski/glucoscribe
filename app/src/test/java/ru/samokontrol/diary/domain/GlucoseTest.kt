package ru.samokontrol.diary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlucoseTest {

    @Test
    fun `mmol to mgdl converts with clinical factor`() {
        assertEquals(99, Glucose.mmolToMgdl(5.5))
        assertEquals(180, Glucose.mmolToMgdl(10.0))
    }

    @Test
    fun `mgdl to mmol converts back`() {
        assertEquals(5.5, Glucose.mgdlToMmol(99), 0.05)
        assertEquals(9.99, Glucose.mgdlToMmol(180), 0.02)
    }

    @Test
    fun `parse accepts comma and dot decimals`() {
        assertEquals(99, Glucose.parse("5,5", GlucoseUnit.MMOLL))
        assertEquals(99, Glucose.parse("5.5", GlucoseUnit.MMOLL))
        assertEquals(100, Glucose.parse("100", GlucoseUnit.MGDL))
    }

    @Test
    fun `parse rejects values outside physiological range`() {
        assertNull(Glucose.parse("0", GlucoseUnit.MGDL))
        assertNull(Glucose.parse("999", GlucoseUnit.MGDL))
        assertNull(Glucose.parse("abc", GlucoseUnit.MGDL))
        assertNull(Glucose.parse("", GlucoseUnit.MGDL))
    }

    @Test
    fun `format uses one decimal for mmol`() {
        assertEquals("5.5", Glucose.format(99, GlucoseUnit.MMOLL))
        assertEquals("99", Glucose.format(99, GlucoseUnit.MGDL))
    }

    @Test
    fun `round trip keeps value within one mgdl`() {
        for (mgdl in 40..400 step 7) {
            val restored = Glucose.mmolToMgdl(Glucose.mgdlToMmol(mgdl))
            assertEquals(mgdl, restored)
        }
    }
}