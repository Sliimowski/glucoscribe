package ru.samokontrol.diary.domain

import java.util.Locale
import kotlin.math.roundToInt

enum class GlucoseUnit(val title: String) {
    MMOLL("ммоль/л"),
    MGDL("мг/дл"),
}

/**
 * Пересчёт и разбор показателей глюкозы.
 *
 * Каноническая единица хранения — целые mg/dL: без плавающей точки и потерь
 * при многократных конвертациях. В UI показываем то, что выбрал пользователь.
 */
object Glucose {

    const val MGDL_PER_MMOL: Double = 18.0182

    const val MIN_MGDL: Int = 18
    const val MAX_MGDL: Int = 600

    fun mgdlToMmol(mgdl: Int): Double = mgdl / MGDL_PER_MMOL

    fun mmolToMgdl(mmol: Double): Int = (mmol * MGDL_PER_MMOL).roundToInt()

    fun format(mgdl: Int, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MGDL -> mgdl.toString()
        GlucoseUnit.MMOLL -> String.format(Locale.US, "%.1f", mgdlToMmol(mgdl))
    }

    fun formatWithUnit(mgdl: Int, unit: GlucoseUnit): String =
        "${format(mgdl, unit)} ${unit.title}"

    fun unitSuffix(unit: GlucoseUnit): String = unit.title

    /** Возвращает mg/dL или null, если значение не число или вне допустимого диапазона. */
    fun parse(text: String, unit: GlucoseUnit): Int? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        val mgdl = when (unit) {
            GlucoseUnit.MGDL -> value.roundToInt()
            GlucoseUnit.MMOLL -> mmolToMgdl(value)
        }
        return mgdl.takeIf { it in MIN_MGDL..MAX_MGDL }
    }

    fun rangeFor(unit: GlucoseUnit, lowMgdl: Int, highMgdl: Int): Pair<Double, Double> =
        when (unit) {
            GlucoseUnit.MGDL -> lowMgdl.toDouble() to highMgdl.toDouble()
            GlucoseUnit.MMOLL -> mgdlToMmol(lowMgdl) to mgdlToMmol(highMgdl)
        }
}