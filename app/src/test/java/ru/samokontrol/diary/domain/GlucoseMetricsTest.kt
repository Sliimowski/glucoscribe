package ru.samokontrol.diary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseMetricsTest {

    @Test
    fun `empty input returns empty stats`() {
        val stats = GlucoseMetrics.calculate(emptyList())
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.count)
    }

    @Test
    fun `time in range is split correctly`() {
        val stats = GlucoseMetrics.calculate(listOf(70, 100, 180, 200, 60))
        assertEquals(5, stats.count)
        assertEquals(60.0, stats.timeInRangePercent, 0.001)
        assertEquals(20.0, stats.timeBelowPercent, 0.001)
        assertEquals(20.0, stats.timeAbovePercent, 0.001)
    }

    @Test
    fun `mean and gmi follow formulas`() {
        val stats = GlucoseMetrics.calculate(listOf(70, 100, 180, 200, 60))
        assertEquals(122.0, stats.meanMgdl, 0.001)
        assertEquals(3.31 + 0.02392 * 122.0, stats.gmiPercent, 0.001)
        assertEquals((122.0 + 46.7) / 28.7, stats.estimatedHba1cPercent, 0.001)
    }

    @Test
    fun `standard deviation uses sample variance`() {
        val stats = GlucoseMetrics.calculate(listOf(70, 100, 180, 200, 60))
        assertEquals(64.187, stats.sdMgdl, 0.01)
        assertEquals(52.61, stats.cvPercent, 0.01)
    }

    @Test
    fun `single measurement has zero deviation`() {
        val stats = GlucoseMetrics.calculate(listOf(120))
        assertEquals(0.0, stats.sdMgdl, 0.0)
        assertEquals(0.0, stats.cvPercent, 0.0)
    }

    @Test
    fun `custom target range is respected`() {
        val stats = GlucoseMetrics.calculate(listOf(80, 100, 140), lowMgdl = 90, highMgdl = 130)
        assertEquals(3, stats.count)
        assertEquals(100.0 / 3.0, stats.timeBelowPercent, 0.001)
        assertEquals(100.0 / 3.0, stats.timeAbovePercent, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid range is rejected`() {
        GlucoseMetrics.calculate(listOf(100), lowMgdl = 180, highMgdl = 70)
    }
}