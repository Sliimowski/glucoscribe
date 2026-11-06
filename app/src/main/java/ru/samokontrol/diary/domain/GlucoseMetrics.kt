package ru.samokontrol.diary.domain

import kotlin.math.sqrt

/**
 * Сводные показатели за период.
 *
 * Все входные значения — mg/dL. Метрики рассчитаны по общепринятым формулам:
 *  - GMI = 3.31 + 0.02392 × средний mg/dL;
 *  - оценка HbA1c (ADAG) = (средний mg/dL + 46.7) / 28.7;
 *  - CV% = SD / средний × 100.
 */
data class GlucoseStats(
    val count: Int,
    val meanMgdl: Double,
    val sdMgdl: Double,
    val cvPercent: Double,
    val gmiPercent: Double,
    val estimatedHba1cPercent: Double,
    val timeInRangePercent: Double,
    val timeBelowPercent: Double,
    val timeAbovePercent: Double,
    val lowMgdl: Int,
    val highMgdl: Int,
) {
    val isEmpty: Boolean get() = count == 0

    companion object {
        fun empty(lowMgdl: Int = GlucoseMetrics.DEFAULT_LOW, highMgdl: Int = GlucoseMetrics.DEFAULT_HIGH) =
            GlucoseStats(
                count = 0,
                meanMgdl = 0.0,
                sdMgdl = 0.0,
                cvPercent = 0.0,
                gmiPercent = 0.0,
                estimatedHba1cPercent = 0.0,
                timeInRangePercent = 0.0,
                timeBelowPercent = 0.0,
                timeAbovePercent = 0.0,
                lowMgdl = lowMgdl,
                highMgdl = highMgdl,
            )
    }
}

object GlucoseMetrics {

    const val DEFAULT_LOW: Int = 70
    const val DEFAULT_HIGH: Int = 180

    fun calculate(
        values: List<Int>,
        lowMgdl: Int = DEFAULT_LOW,
        highMgdl: Int = DEFAULT_HIGH,
    ): GlucoseStats {
        require(lowMgdl < highMgdl) { "lowMgdl must be less than highMgdl" }
        if (values.isEmpty()) return GlucoseStats.empty(lowMgdl, highMgdl)

        val n = values.size
        val mean = values.sumOf { it.toDouble() } / n
        val variance = if (n < 2) {
            0.0
        } else {
            values.sumOf { (it - mean) * (it - mean) } / (n - 1)
        }
        val sd = sqrt(variance)
        val cv = if (mean > 0.0) sd / mean * 100.0 else 0.0
        val gmi = 3.31 + 0.02392 * mean
        val estimatedHba1c = (mean + 46.7) / 28.7

        val below = values.count { it < lowMgdl }
        val above = values.count { it > highMgdl }
        val inRange = n - below - above

        return GlucoseStats(
            count = n,
            meanMgdl = mean,
            sdMgdl = sd,
            cvPercent = cv,
            gmiPercent = gmi,
            estimatedHba1cPercent = estimatedHba1c,
            timeInRangePercent = inRange * 100.0 / n,
            timeBelowPercent = below * 100.0 / n,
            timeAbovePercent = above * 100.0 / n,
            lowMgdl = lowMgdl,
            highMgdl = highMgdl,
        )
    }
}