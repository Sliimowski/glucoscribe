package ru.samokontrol.diary.data

/** Тип события в дневнике. */
enum class EventType {
    GLUCOSE,
    INSULIN,
    CARBS,
    WEIGHT,
    BLOOD_PRESSURE,
    KETONES,
    HBA1C,
}

/** Откуда пришло событие. Пока всё ручное, кроме будущих импортов. */
enum class EventSource {
    MANUAL,
    METER,
    CGM,
    IMPORT,
    HEALTH_CONNECT,
}

enum class InsulinKind(val title: String) {
    BOLUS("Болюс"),
    BASAL("Базал"),
}

enum class MealType(val title: String) {
    BREAKFAST("Завтрак"),
    LUNCH("Обед"),
    DINNER("Ужин"),
    SNACK("Перекус"),
    OTHER("Другое"),
}

enum class Specimen {
    CAPILLARY,
    INTERSTITIAL,
}

/**
 * Единое событие дневника. Общие поля лежат в [id], [occurredAt], [note], [source],
 * типоспецифичные — в наследниках. В БД это таблица events + типизированная таблица.
 */
sealed interface DiaryEvent {
    val id: Long
    val occurredAt: Long
    val note: String
    val source: EventSource

    data class Glucose(
        override val id: Long,
        override val occurredAt: Long,
        override val note: String = "",
        override val source: EventSource = EventSource.MANUAL,
        val mgdl: Int,
        val context: MeasurementContext = MeasurementContext.RANDOM,
        val specimen: Specimen = Specimen.CAPILLARY,
    ) : DiaryEvent

    data class Insulin(
        override val id: Long,
        override val occurredAt: Long,
        override val note: String = "",
        override val source: EventSource = EventSource.MANUAL,
        val unitsMilli: Int,
        val kind: InsulinKind,
        val insulinType: String = "",
    ) : DiaryEvent

    data class Carbs(
        override val id: Long,
        override val occurredAt: Long,
        override val note: String = "",
        override val source: EventSource = EventSource.MANUAL,
        val gramsX10: Int,
        val mealType: MealType = MealType.OTHER,
    ) : DiaryEvent

    data class Weight(
        override val id: Long,
        override val occurredAt: Long,
        override val note: String = "",
        override val source: EventSource = EventSource.MANUAL,
        val weightG: Int,
    ) : DiaryEvent

    data class BloodPressure(
        override val id: Long,
        override val occurredAt: Long,
        override val note: String = "",
        override val source: EventSource = EventSource.MANUAL,
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int? = null,
    ) : DiaryEvent

    data class Ketones(
        override val id: Long,
        override val occurredAt: Long,
        override val note: String = "",
        override val source: EventSource = EventSource.MANUAL,
        val mmolLX10: Int? = null,
        val urineGrade: Int? = null,
    ) : DiaryEvent

    data class Hba1c(
        override val id: Long,
        override val occurredAt: Long,
        override val note: String = "",
        override val source: EventSource = EventSource.MANUAL,
        val mmolMol: Int,
        val percentX10: Int,
    ) : DiaryEvent
}
