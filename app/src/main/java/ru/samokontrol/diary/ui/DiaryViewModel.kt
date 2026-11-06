package ru.samokontrol.diary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.samokontrol.diary.SamokontrolApp
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.GlucoseMetrics
import ru.samokontrol.diary.domain.GlucoseStats
import ru.samokontrol.diary.domain.GlucoseUnit

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SamokontrolApp).repository

    val entries = repository.entries

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

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun entriesFor(days: Int, now: Long = System.currentTimeMillis()): List<GlucoseEntry> {
        val since = now - days * DAY_MILLIS
        return entries.value.filter { it.measuredAt >= since }
    }

    fun statsFor(days: Int, now: Long = System.currentTimeMillis()): GlucoseStats {
        val values = entriesFor(days, now).map { it.mgdl }
        return GlucoseMetrics.calculate(values)
    }

    companion object {
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}