package ru.samokontrol.diary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

/** Одно измерение глюкозы с временем — нужно для расчёта полноты данных. */
data class GlucoseSample(
    val mgdl: Int,
    val measuredAt: Long,
)

/**
 * Сводные показатели за период.
 *
 * Все входные значения — mg/dL. Метрики рассчитаны по общепринятым формулам:
 *  - GMI = 3.31 + 0.02392 × средний mg/dL;
 *  - оценка HbA1c (ADAG) = (средний mg/dL + 46.7) / 28.7;
 *  - CV% = SD / средний × 100.
 *
 * [inRangePercent] — это доля ИЗМЕРЕНИЙ в диапазоне, а не доля времени (TIR).
 * Для ручного дневника с 3–4 замерами в день это разные величины.
 */
data class GlucoseStats(
    val count: Int,
    val meanMgdl: Double,
    val sdMgdl: Double,
    val cvPercent: Double,
    val gmiPercent: Double,
    val estimatedHba1cPercent: Double,
    val inRangePercent: Double,
    val belowPercent: Double,
    val abovePercent: Double,
    val belowSeverePercent: Double,
    val aboveSeverePercent: Double,
    val measuredDays: Int,
    val periodDays: Int,
    val lowMgdl: Int,
    val highMgdl: Int,
) {
    val isEmpty: Boolean get() = count == 0

    /** Доля дней периода, в которые была хотя бы одна запись. */
    val completenessPercent: Double
        get() = if (periodDays <= 0) 0.0 else measuredDays * 100.0 / periodDays

    companion object {
        fun empty(lowMgdl: Int = GlucoseMetrics.DEFAULT_LOW, highMgdl: Int = GlucoseMetrics.DEFAULT_HIGH) =
            GlucoseStats(
                count = 0,
                meanMgdl = 0.0,
                sdMgdl = 0.0,
                cvPercent = 0.0,
                gmiPercent = 0.0,
                estimatedHba1cPercent = 0.0,
                inRangePercent = 0.0,
                belowPercent = 0.0,
                abovePercent = 0.0,
                belowSeverePercent = 0.0,
                aboveSeverePercent = 0.0,
                measuredDays = 0,
                periodDays = 0,
                lowMgdl = lowMgdl,
                highMgdl = highMgdl,
            )
    }
}

object GlucoseMetrics {

    const val DEFAULT_LOW: Int = 70
    const val DEFAULT_HIGH: Int = 180

    /** Уровень 2 гипогликемии: < 3,0 ммоль/л. */
    const val SEVERE_LOW: Int = 54

    /** Уровень 2 гипергликемии: > 13,9 ммоль/л. */
    const val SEVERE_HIGH: Int = 250

    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    /** Расчёт по «голым» значениям: без времени нельзя определить полноту данных. */
    fun calculate(
        values: List<Int>,
        lowMgdl: Int = DEFAULT_LOW,
        highMgdl: Int = DEFAULT_HIGH,
    ): GlucoseStats = calculateValues(
        values = values,
        measuredDays = 0,
        periodDays = 0,
        lowMgdl = lowMgdl,
        highMgdl = highMgdl,
    )

    /** Расчёт по измерениям с временем: добавляет число дней с данными и полноту периода. */
    fun calculate(
        samples: List<GlucoseSample>,
        periodDays: Int,
        now: Long = System.currentTimeMillis(),
        lowMgdl: Int = DEFAULT_LOW,
        highMgdl: Int = DEFAULT_HIGH,
    ): GlucoseStats {
        require(periodDays >= 0) { "periodDays must not be negative" }
        if (periodDays == 0) {
            return calculateValues(emptyList(), 0, 0, lowMgdl, highMgdl)
        }

        val since = now - periodDays * DAY_MILLIS
        val inPeriod = samples.filter { it.measuredAt in since..now }
        val measuredDays = inPeriod
            .map { localDate(it.measuredAt) }
            .distinct()
            .size
            .coerceAtMost(periodDays)

        return calculateValues(
            values = inPeriod.map { it.mgdl },
            measuredDays = measuredDays,
            periodDays = periodDays,
            lowMgdl = lowMgdl,
            highMgdl = highMgdl,
        )
    }

    private fun calculateValues(
        values: List<Int>,
        measuredDays: Int,
        periodDays: Int,
        lowMgdl: Int,
        highMgdl: Int,
    ): GlucoseStats {
        require(lowMgdl < highMgdl) { "lowMgdl must be less than highMgdl" }
        if (values.isEmpty()) {
            return GlucoseStats.empty(lowMgdl, highMgdl).copy(
                measuredDays = measuredDays,
                periodDays = periodDays,
            )
        }

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
        val belowSevere = values.count { it < SEVERE_LOW }
        val aboveSevere = values.count { it > SEVERE_HIGH }
        val inRange = n - below - above

        return GlucoseStats(
            count = n,
            meanMgdl = mean,
            sdMgdl = sd,
            cvPercent = cv,
            gmiPercent = gmi,
            estimatedHba1cPercent = estimatedHba1c,
            inRangePercent = inRange * 100.0 / n,
            belowPercent = below * 100.0 / n,
            abovePercent = above * 100.0 / n,
            belowSeverePercent = belowSevere * 100.0 / n,
            aboveSeverePercent = aboveSevere * 100.0 / n,
            measuredDays = measuredDays,
            periodDays = periodDays,
            lowMgdl = lowMgdl,
            highMgdl = highMgdl,
        )
    }

    private fun localDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
