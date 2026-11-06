package ru.samokontrol.diary.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Разбор фразы с показанием глюкозы, распознанной голосом.
 *
 * Понимает цифры («5,6»), числительные («шесть целых пять десятых»),
 * разговорные формы («пять и шесть») и слова-обёртки («сахар», «уровень»).
 * Всегда возвращает канонические mg/dL либо null. Окончательное решение
 * принимает человек в диалоге подтверждения, поэтому спорные варианты
 * разбираются по первому подходящему значению.
 */
object SpokenGlucoseParser {

    fun parse(text: String, unit: GlucoseUnit): Int? {
        val groups = extractNumbers(normalize(text))
        if (groups.isEmpty()) return null

        val first = groups.first()
        val candidates = mutableListOf<Double>()

        if (first % 1.0 != 0.0) candidates += first

        if (groups.size >= 2 && groups[1] % 1.0 == 0.0) {
            val second = groups[1]
            val fraction = when {
                second < 10 -> second / 10.0
                second < 100 -> second / 100.0
                else -> null
            }
            if (fraction != null) candidates += first + fraction
        }

        candidates += first

        // «сто двадцать» уже склеивается в одно число, но если распознавание
        // вернуло цифры по одной («1 2 0»), пробуем собрать их подряд.
        if (unit == GlucoseUnit.MGDL && groups.size > 1) {
            groups.joinToString("") { it.roundToInt().toString() }
                .toDoubleOrNull()
                ?.let { candidates += it }
        }

        for (candidate in candidates) {
            val textValue = when (unit) {
                GlucoseUnit.MGDL -> candidate.roundToInt().toString()
                GlucoseUnit.MMOLL -> String.format(Locale.US, "%.2f", candidate)
            }
            Glucose.parse(textValue, unit)?.let { return it }
        }
        return null
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace('ё', 'е')
            .replace(',', '.')
            .replace(Regex("[^а-я0-9.\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractNumbers(text: String): List<Double> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<Double>()
        var sum = 0.0
        var last = 0.0
        var pending = false

        fun flush() {
            if (pending) {
                result += sum
                sum = 0.0
                last = 0.0
                pending = false
            }
        }

        text.split(' ').forEach { token ->
            val wordValue = NUMBER_WORDS[token]
            val digitValue = if (token.matches(DIGITS)) token.toDoubleOrNull() else null
            when {
                wordValue != null -> {
                    // Числительные собираются, пока разряды убывают:
                    // «сто двадцать три» — одно число, «пять шесть» — два.
                    if (pending && wordValue >= last) flush()
                    sum += wordValue
                    last = wordValue
                    pending = true
                }
                digitValue != null -> {
                    flush()
                    result += digitValue
                }
                else -> flush()
            }
        }
        flush()
        return result
    }

    private val DIGITS = Regex("\\d+(\\.\\d+)?")

    private val NUMBER_WORDS: Map<String, Double> = buildMap {
        fun put(value: Int, vararg forms: String) {
            forms.forEach { put(it, value.toDouble()) }
        }
        put(0, "ноль", "нуль")
        put(1, "один", "одна", "одну", "одного")
        put(2, "два", "две", "двух")
        put(3, "три", "трех")
        put(4, "четыре", "четырех")
        put(5, "пять", "пяти")
        put(6, "шесть", "шести")
        put(7, "семь", "семи")
        put(8, "восемь", "восьми")
        put(9, "девять", "девяти")
        put(10, "десять", "десяти")
        put(11, "одиннадцать")
        put(12, "двенадцать")
        put(13, "тринадцать")
        put(14, "четырнадцать")
        put(15, "пятнадцать")
        put(16, "шестнадцать")
        put(17, "семнадцать")
        put(18, "восемнадцать")
        put(19, "девятнадцать")
        put(20, "двадцать")
        put(30, "тридцать")
        put(40, "сорок")
        put(50, "пятьдесят")
        put(60, "шестьдесят")
        put(70, "семьдесят")
        put(80, "восемьдесят")
        put(90, "девяносто")
        put(100, "сто", "сотня")
        put(200, "двести")
        put(300, "триста")
        put(400, "четыреста")
        put(500, "пятьсот")
        put(600, "шестьсот")
        put(700, "семьсот")
        put(800, "восемьсот")
        put(900, "девятьсот")
        put(1000, "тысяча")
    }
}