package ru.samokontrol.diary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenGlucoseParserTest {

    private fun mmol(text: String): Int? = SpokenGlucoseParser.parse(text, GlucoseUnit.MMOLL)

    private fun mgdl(text: String): Int? = SpokenGlucoseParser.parse(text, GlucoseUnit.MGDL)

    @Test
    fun `decimal with comma`() {
        assertEquals(101, mmol("5,6"))
    }

    @Test
    fun `decimal with dot`() {
        assertEquals(101, mmol("5.6"))
    }

    @Test
    fun `two digits in a row`() {
        assertEquals(101, mmol("5 6"))
    }

    @Test
    fun `digits separated by i`() {
        assertEquals(101, mmol("пять и шесть"))
    }

    @Test
    fun `word decimal form`() {
        assertEquals(117, mmol("шесть целых пять десятых"))
    }

    @Test
    fun `words with wrapper`() {
        assertEquals(101, mmol("сахар пять целых шесть"))
    }

    @Test
    fun `plain word number`() {
        assertEquals(216, mmol("двенадцать"))
    }

    @Test
    fun `hundreds compose into one number`() {
        assertEquals(123, mgdl("сто двадцать три"))
        assertEquals(120, mgdl("сто двадцать"))
    }

    @Test
    fun `mgdl integer`() {
        assertEquals(120, mgdl("120"))
    }

    @Test
    fun `mgdl small value is out of range`() {
        assertNull(mgdl("12"))
    }

    @Test
    fun `boundaries`() {
        assertEquals(18, mgdl("18"))
        assertEquals(600, mgdl("600"))
        assertNull(mgdl("601"))
        assertNull(mgdl("17"))
    }

    @Test
    fun `mmol out of range`() {
        assertNull(mmol("40"))
        assertEquals(595, mmol("33"))
    }

    @Test
    fun `empty and garbage`() {
        assertNull(mmol(""))
        assertNull(mmol("привет"))
        assertNull(mmol("5.6.7"))
    }
}