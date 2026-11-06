package ru.samokontrol.diary

import android.app.Application
import ru.samokontrol.diary.data.DiaryRepository
import ru.samokontrol.diary.data.SqliteDiaryRepository

class SamokontrolApp : Application() {
    val repository: DiaryRepository by lazy { SqliteDiaryRepository(this) }
}