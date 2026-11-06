package ru.samokontrol.diary.data

enum class MeasurementContext(val title: String) {
    FASTING("Натощак"),
    BEFORE_MEAL("До еды"),
    AFTER_MEAL("После еды"),
    BEDTIME("Перед сном"),
    RANDOM("Другое"),
}

data class GlucoseEntry(
    val id: Long = 0L,
    val measuredAt: Long,
    val mgdl: Int,
    val context: MeasurementContext = MeasurementContext.RANDOM,
    val note: String = "",
)