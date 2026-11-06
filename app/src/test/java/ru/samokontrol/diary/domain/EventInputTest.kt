package ru.samokontrol.diary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventInputTest {

    @Test
    fun `insulin accepts comma and dot`() {
        assertEquals(6500, EventInput.parseInsulinUnits("6,5"))
        assertEquals(6500, EventInput.parseInsulinUnits("6.5"))
        assertEquals(500, EventInput.parseInsulinUnits("0,5"))
    }

    @Test
    fun `insulin rejects zero and out of range`() {
        assertNull(EventInput.parseInsulinUnits("0"))
        assertNull(EventInput.parseInsulinUnits("-1"))
        assertNull(EventInput.parseInsulinUnits("101"))
        assertNull(EventInput.parseInsulinUnits("abc"))
    }

    @Test
    fun `carbs are stored in tenths of gram`() {
        assertEquals(455, EventInput.parseCarbsGrams("45,5"))
        assertEquals(450, EventInput.parseCarbsGrams("45"))
        assertNull(EventInput.parseCarbsGrams("0"))
    }

    @Test
    fun `weight is stored in grams`() {
        assertEquals(72500, EventInput.parseWeightKg("72,5"))
        assertNull(EventInput.parseWeightKg("10"))
        assertNull(EventInput.parseWeightKg("401"))
    }

    @Test
    fun `ketones are stored in tenths`() {
        assertEquals(8, EventInput.parseKetonesMmolL("0,8"))
        assertNull(EventInput.parseKetonesMmolL("0"))
        assertNull(EventInput.parseKetonesMmolL("21"))
    }

    @Test
    fun `hba1c converts to ifcc`() {
        assertEquals(65, EventInput.parseHba1cPercent("6,5"))
        assertEquals(48, EventInput.hba1cMmolMol(65))
        assertNull(EventInput.parseHba1cPercent("2,9"))
        assertNull(EventInput.parseHba1cPercent("20,1"))
    }

    @Test
    fun `blood pressure validation`() {
        assertTrue(EventInput.isValidBloodPressure(120, 80, 70))
        assertTrue(EventInput.isValidBloodPressure(120, 80, null))
        assertFalse(EventInput.isValidBloodPressure(80, 120, 70))
        assertFalse(EventInput.isValidBloodPressure(120, 80, 10))
        assertFalse(EventInput.isValidBloodPressure(40, 20, null))
    }

    @Test
    fun `formatting keeps one decimal and trims zero`() {
        assertEquals("6,5 ЕД", EventInput.formatInsulinUnits(6500))
        assertEquals("6 ЕД", EventInput.formatInsulinUnits(6000))
        assertEquals("45,5 г", EventInput.formatCarbs(455))
        assertEquals("72,5 кг", EventInput.formatWeight(72500))
        assertEquals("0,8 ммоль/л", EventInput.formatKetones(8))
        assertEquals("6,5 %", EventInput.formatHba1c(65))
    }
}
