package ru.samokontrol.diary.data

import kotlinx.coroutines.flow.StateFlow

interface DiaryRepository {
    /** Только глюкоза — для графика, отчёта и голосового ввода. */
    val entries: StateFlow<List<GlucoseEntry>>

    /** Все события дневника в обратном хронологическом порядке. */
    val events: StateFlow<List<DiaryEvent>>

    suspend fun add(entry: GlucoseEntry): Long

    suspend fun addInsulin(
        unitsMilli: Int,
        kind: InsulinKind,
        insulinType: String,
        note: String,
        occurredAt: Long,
    ): Long

    suspend fun addCarbs(
        gramsX10: Int,
        mealType: MealType,
        note: String,
        occurredAt: Long,
    ): Long

    suspend fun addWeight(
        weightG: Int,
        note: String,
        occurredAt: Long,
    ): Long

    suspend fun addBloodPressure(
        systolic: Int,
        diastolic: Int,
        pulse: Int?,
        note: String,
        occurredAt: Long,
    ): Long

    suspend fun addKetones(
        mmolLX10: Int?,
        urineGrade: Int?,
        note: String,
        occurredAt: Long,
    ): Long

    suspend fun addHba1c(
        mmolMol: Int,
        percentX10: Int,
        note: String,
        occurredAt: Long,
    ): Long

    suspend fun delete(id: Long)
}
