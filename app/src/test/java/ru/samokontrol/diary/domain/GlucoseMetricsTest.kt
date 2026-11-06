package ru.samokontrol.diary.domain

import java.time.LocalDate
import java.time.ZoneId
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
    fun `range boundaries are inclusive`() {
        val stats = GlucoseMetrics.calculate(listOf(69, 70, 100, 180, 181))
        assertEquals(5, stats.count)
        assertEquals(60.0, stats.inRangePercent, 0.001)
        assertEquals(20.0, stats.belowPercent, 0.001)
        assertEquals(20.0, stats.abovePercent, 0.001)
    }

    @Test
    fun `severe levels are counted separately`() {
        val stats = GlucoseMetrics.calculate(listOf(53, 54, 100, 250, 251))
        assertEquals(20.0, stats.belowSeverePercent, 0.001)
        assertEquals(20.0, stats.aboveSeverePercent, 0.001)
        assertEquals(40.0, stats.belowPercent, 0.001)
        assertEquals(40.0, stats.abovePercent, 0.001)
        assertEquals(20.0, stats.inRangePercent, 0.001)
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
        assertEquals(100.0 / 3.0, stats.belowPercent, 0.001)
        assertEquals(100.0 / 3.0, stats.abovePercent, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid range is rejected`() {
        GlucoseMetrics.calculate(listOf(100), lowMgdl = 180, highMgdl = 70)
    }

    @Test
    fun `completeness counts days with measurements`() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now().atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val samples = (1..3).map { daysAgo ->
            GlucoseSample(
                mgdl = 100,
                measuredAt = LocalDate.now()
                    .minusDays(daysAgo.toLong())
                    .atTime(12, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
            )
        }

        val stats = GlucoseMetrics.calculate(samples, periodDays = 14, now = now)
        assertEquals(3, stats.measuredDays)
        assertEquals(14, stats.periodDays)
        assertEquals(3.0 * 100.0 / 14.0, stats.completenessPercent, 0.001)
    }

    @Test
    fun `samples outside the period are ignored`() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now().atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val old = GlucoseSample(
            mgdl = 100,
            measuredAt = LocalDate.now()
                .minusDays(20)
                .atTime(12, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli(),
        )

        val stats = GlucoseMetrics.calculate(listOf(old), periodDays = 14, now = now)
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.measuredDays)
        assertEquals(14, stats.periodDays)
    }
}
