package ru.samokontrol.diary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.samokontrol.diary.SamokontrolApp
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.data.InsulinKind
import ru.samokontrol.diary.data.MealType
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.GlucoseMetrics
import ru.samokontrol.diary.domain.GlucoseSample
import ru.samokontrol.diary.domain.GlucoseStats
import ru.samokontrol.diary.domain.GlucoseUnit

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SamokontrolApp).repository

    val entries = repository.entries
    val events = repository.events

    private val _unit = MutableStateFlow(GlucoseUnit.MMOLL)
    val unit = _unit.asStateFlow()

    fun setUnit(unit: GlucoseUnit) {
        _unit.value = unit
    }

    fun add(
        mgdl: Int,
        context: MeasurementContext,
        note: String,
        measuredAt: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            repository.add(
                GlucoseEntry(
                    measuredAt = measuredAt,
                    mgdl = mgdl,
                    context = context,
                    note = note.trim(),
                ),
            )
        }
    }

    fun addInsulin(
        unitsMilli: Int,
        kind: InsulinKind,
        insulinType: String,
        note: String,
        measuredAt: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            repository.addInsulin(unitsMilli, kind, insulinType, note.trim(), measuredAt)
        }
    }

    fun addCarbs(
        gramsX10: Int,
        mealType: MealType,
        note: String,
        measuredAt: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            repository.addCarbs(gramsX10, mealType, note.trim(), measuredAt)
        }
    }

    fun addWeight(
        weightG: Int,
        note: String,
        measuredAt: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            repository.addWeight(weightG, note.trim(), measuredAt)
        }
    }

    fun addBloodPressure(
        systolic: Int,
        diastolic: Int,
        pulse: Int?,
        note: String,
        measuredAt: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            repository.addBloodPressure(systolic, diastolic, pulse, note.trim(), measuredAt)
        }
    }

    fun addKetones(
        mmolLX10: Int?,
        urineGrade: Int?,
        note: String,
        measuredAt: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            repository.addKetones(mmolLX10, urineGrade, note.trim(), measuredAt)
        }
    }

    fun addHba1c(
        mmolMol: Int,
        percentX10: Int,
        note: String,
        measuredAt: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            repository.addHba1c(mmolMol, percentX10, note.trim(), measuredAt)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun entriesFor(days: Int, now: Long = System.currentTimeMillis()): List<GlucoseEntry> {
        val since = now - days * DAY_MILLIS
        return entries.value.filter { it.measuredAt >= since }
    }

    fun statsFor(days: Int, now: Long = System.currentTimeMillis()): GlucoseStats {
        val samples = entriesFor(days, now).map { GlucoseSample(it.mgdl, it.measuredAt) }
        return GlucoseMetrics.calculate(samples, periodDays = days, now = now)
    }

    companion object {
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}