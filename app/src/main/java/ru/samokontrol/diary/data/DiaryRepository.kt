package ru.samokontrol.diary.data

import kotlinx.coroutines.flow.StateFlow

interface DiaryRepository {
    val entries: StateFlow<List<GlucoseEntry>>

    suspend fun add(entry: GlucoseEntry): Long

    suspend fun delete(id: Long)
}