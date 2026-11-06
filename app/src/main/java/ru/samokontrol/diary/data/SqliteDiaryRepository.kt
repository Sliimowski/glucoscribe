package ru.samokontrol.diary.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SqliteDiaryRepository(context: Context) : DiaryRepository {

    private val helper = DiaryDbHelper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<GlucoseEntry>>(emptyList())
    override val entries: StateFlow<List<GlucoseEntry>> = _entries.asStateFlow()

    init {
        scope.launch { loadEntries() }
    }

    override suspend fun add(entry: GlucoseEntry): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(DiaryDbHelper.COL_MEASURED_AT, entry.measuredAt)
            put(DiaryDbHelper.COL_MGDL, entry.mgdl)
            put(DiaryDbHelper.COL_CONTEXT, entry.context.name)
            put(DiaryDbHelper.COL_NOTE, entry.note)
        }
        val id = helper.writableDatabase.insertOrThrow(DiaryDbHelper.TABLE, null, values)
        _entries.value = readEntries()
        id
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            DiaryDbHelper.TABLE,
            "${DiaryDbHelper.COL_ID} = ?",
            arrayOf(id.toString()),
        )
        _entries.value = readEntries()
    }

    private suspend fun loadEntries() = withContext(Dispatchers.IO) {
        _entries.value = readEntries()
    }

    private fun readEntries(): List<GlucoseEntry> {
        val result = mutableListOf<GlucoseEntry>()
        helper.readableDatabase.query(
            DiaryDbHelper.TABLE,
            null,
            null,
            null,
            null,
            null,
            "${DiaryDbHelper.COL_MEASURED_AT} DESC",
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_ID)
            val timeIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_MEASURED_AT)
            val mgdlIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_MGDL)
            val contextIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_CONTEXT)
            val noteIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_NOTE)
            while (cursor.moveToNext()) {
                val context = runCatching {
                    MeasurementContext.valueOf(cursor.getString(contextIndex))
                }.getOrDefault(MeasurementContext.RANDOM)
                result += GlucoseEntry(
                    id = cursor.getLong(idIndex),
                    measuredAt = cursor.getLong(timeIndex),
                    mgdl = cursor.getInt(mgdlIndex),
                    context = context,
                    note = cursor.getString(noteIndex).orEmpty(),
                )
            }
        }
        return result
    }
}

private class DiaryDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MEASURED_AT INTEGER NOT NULL,
                $COL_MGDL INTEGER NOT NULL,
                $COL_CONTEXT TEXT NOT NULL,
                $COL_NOTE TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_${TABLE}_time ON $TABLE ($COL_MEASURED_AT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Первая версия схемы. Миграции добавляем по мере развития.
    }

    companion object {
        const val DB_NAME = "samokontrol.db"
        const val DB_VERSION = 1

        const val TABLE = "glucose_entries"
        const val COL_ID = "id"
        const val COL_MEASURED_AT = "measured_at"
        const val COL_MGDL = "mgdl"
        const val COL_CONTEXT = "context"
        const val COL_NOTE = "note"
    }
}