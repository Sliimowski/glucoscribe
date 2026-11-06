package ru.samokontrol.diary.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Разбор и форматирование значений для быстрых форм.
 * Канонические единицы: инсулин — миллиединицы, углеводы — 0,1 г,
 * вес — граммы, кетоны — 0,1 ммоль/л, HbA1c — 0,1 %.
 */
object EventInput {

    private val ru: Locale = Locale.forLanguageTag("ru")

    fun parseInsulinUnits(text: String): Int? {
        val value = parseDecimal(text) ?: return null
        if (value <= 0.0 || value > 100.0) return null
        return (value * 1000).roundToInt()
    }

    fun parseCarbsGrams(text: String): Int? {
        val value = parseDecimal(text) ?: return null
        if (value <= 0.0 || value > 1000.0) return null
        return (value * 10).roundToInt()
    }

    fun parseWeightKg(text: String): Int? {
        val value = parseDecimal(text) ?: return null
        if (value < 20.0 || value > 400.0) return null
        return (value * 1000).roundToInt()
    }

    fun parseKetonesMmolL(text: String): Int? {
        val value = parseDecimal(text) ?: return null
        if (value <= 0.0 || value > 20.0) return null
        return (value * 10).roundToInt()
    }

    fun parseHba1cPercent(text: String): Int? {
        val value = parseDecimal(text) ?: return null
        if (value < 3.0 || value > 20.0) return null
        return (value * 10).roundToInt()
    }

    /** IFCC, ммоль/моль: (NGSP % − 2,15) × 10,929. */
    fun hba1cMmolMol(percentX10: Int): Int =
        ((percentX10 / 10.0 - 2.15) * 10.929).roundToInt()

    fun isValidBloodPressure(systolic: Int, diastolic: Int, pulse: Int?): Boolean =
        systolic in 50..300 &&
            diastolic in 30..200 &&
            systolic > diastolic &&
            (pulse == null || pulse in 30..250)

    fun formatInsulinUnits(unitsMilli: Int): String = "${formatDecimal(unitsMilli / 1000.0)} ЕД"

    fun formatCarbs(gramsX10: Int): String = "${formatDecimal(gramsX10 / 10.0)} г"

    fun formatWeight(weightG: Int): String = "${formatDecimal(weightG / 1000.0)} кг"

    fun formatKetones(mmolLX10: Int): String = "${formatDecimal(mmolLX10 / 10.0)} ммоль/л"

    fun formatHba1c(percentX10: Int): String = "${formatDecimal(percentX10 / 10.0)} %"

    private fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    private fun formatDecimal(value: Double): String {
        val text = String.format(ru, "%.1f", value)
        return if (text.endsWith(",0")) text.dropLast(2) else text
    }
}
