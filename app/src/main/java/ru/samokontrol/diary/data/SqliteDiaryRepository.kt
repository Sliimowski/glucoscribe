package ru.samokontrol.diary.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

    private val _events = MutableStateFlow<List<DiaryEvent>>(emptyList())
    override val events: StateFlow<List<DiaryEvent>> = _events.asStateFlow()

    private val _entries = MutableStateFlow<List<GlucoseEntry>>(emptyList())
    override val entries: StateFlow<List<GlucoseEntry>> = _entries.asStateFlow()

    init {
        scope.launch { reload() }
    }

    override suspend fun add(entry: GlucoseEntry): Long = withContext(Dispatchers.IO) {
        val id = insertEvent(
            type = EventType.GLUCOSE,
            occurredAt = entry.measuredAt,
            note = entry.note,
        ) { db, eventId ->
            db.insertOrThrow(
                DiaryDbHelper.TABLE_GLUCOSE,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_EVENT_ID, eventId)
                    put(DiaryDbHelper.COL_MGDL, entry.mgdl)
                    put(DiaryDbHelper.COL_CONTEXT, entry.context.name)
                    put(DiaryDbHelper.COL_SPECIMEN, Specimen.CAPILLARY.name)
                },
            )
        }
        reload()
        id
    }

    override suspend fun addInsulin(
        unitsMilli: Int,
        kind: InsulinKind,
        insulinType: String,
        note: String,
        occurredAt: Long,
    ): Long = withContext(Dispatchers.IO) {
        val id = insertEvent(EventType.INSULIN, occurredAt, note) { db, eventId ->
            db.insertOrThrow(
                DiaryDbHelper.TABLE_INSULIN,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_EVENT_ID, eventId)
                    put(DiaryDbHelper.COL_UNITS_MILLI, unitsMilli)
                    put(DiaryDbHelper.COL_INSULIN_KIND, kind.name)
                    put(DiaryDbHelper.COL_INSULIN_TYPE, insulinType)
                },
            )
        }
        reload()
        id
    }

    override suspend fun addCarbs(
        gramsX10: Int,
        mealType: MealType,
        note: String,
        occurredAt: Long,
    ): Long = withContext(Dispatchers.IO) {
        val id = insertEvent(EventType.CARBS, occurredAt, note) { db, eventId ->
            db.insertOrThrow(
                DiaryDbHelper.TABLE_CARBS,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_EVENT_ID, eventId)
                    put(DiaryDbHelper.COL_GRAMS_X10, gramsX10)
                    put(DiaryDbHelper.COL_MEAL_TYPE, mealType.name)
                },
            )
        }
        reload()
        id
    }

    override suspend fun addWeight(
        weightG: Int,
        note: String,
        occurredAt: Long,
    ): Long = withContext(Dispatchers.IO) {
        val id = insertEvent(EventType.WEIGHT, occurredAt, note) { db, eventId ->
            db.insertOrThrow(
                DiaryDbHelper.TABLE_BODY,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_EVENT_ID, eventId)
                    put(DiaryDbHelper.COL_WEIGHT_G, weightG)
                },
            )
        }
        reload()
        id
    }

    override suspend fun addBloodPressure(
        systolic: Int,
        diastolic: Int,
        pulse: Int?,
        note: String,
        occurredAt: Long,
    ): Long = withContext(Dispatchers.IO) {
        val id = insertEvent(EventType.BLOOD_PRESSURE, occurredAt, note) { db, eventId ->
            db.insertOrThrow(
                DiaryDbHelper.TABLE_BP,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_EVENT_ID, eventId)
                    put(DiaryDbHelper.COL_SYSTOLIC, systolic)
                    put(DiaryDbHelper.COL_DIASTOLIC, diastolic)
                    pulse?.let { put(DiaryDbHelper.COL_PULSE, it) }
                },
            )
        }
        reload()
        id
    }

    override suspend fun addKetones(
        mmolLX10: Int?,
        urineGrade: Int?,
        note: String,
        occurredAt: Long,
    ): Long = withContext(Dispatchers.IO) {
        val id = insertEvent(EventType.KETONES, occurredAt, note) { db, eventId ->
            db.insertOrThrow(
                DiaryDbHelper.TABLE_KETONES,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_EVENT_ID, eventId)
                    mmolLX10?.let { put(DiaryDbHelper.COL_MMOL_L_X10, it) }
                    urineGrade?.let { put(DiaryDbHelper.COL_URINE_GRADE, it) }
                },
            )
        }
        reload()
        id
    }

    override suspend fun addHba1c(
        mmolMol: Int,
        percentX10: Int,
        note: String,
        occurredAt: Long,
    ): Long = withContext(Dispatchers.IO) {
        val id = insertEvent(EventType.HBA1C, occurredAt, note) { db, eventId ->
            db.insertOrThrow(
                DiaryDbHelper.TABLE_HBA1C,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_EVENT_ID, eventId)
                    put(DiaryDbHelper.COL_MMOL_MOL, mmolMol)
                    put(DiaryDbHelper.COL_PERCENT_X10, percentX10)
                },
            )
        }
        reload()
        id
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            DiaryDbHelper.TABLE_EVENTS,
            ContentValues().apply { put(DiaryDbHelper.COL_DELETED_AT, System.currentTimeMillis()) },
            "${DiaryDbHelper.COL_ID} = ?",
            arrayOf(id.toString()),
        )
        reload()
    }

    private fun insertEvent(
        type: EventType,
        occurredAt: Long,
        note: String,
        source: EventSource = EventSource.MANUAL,
        fillTyped: (SQLiteDatabase, Long) -> Unit,
    ): Long {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val eventId = db.insertOrThrow(
                DiaryDbHelper.TABLE_EVENTS,
                null,
                ContentValues().apply {
                    put(DiaryDbHelper.COL_TYPE, type.name)
                    put(DiaryDbHelper.COL_OCCURRED_AT, occurredAt)
                    put(DiaryDbHelper.COL_ZONE_OFFSET_MIN, zoneOffsetMinutes(occurredAt))
                    put(DiaryDbHelper.COL_NOTE, note)
                    put(DiaryDbHelper.COL_SOURCE, source.name)
                    put(DiaryDbHelper.COL_CREATED_AT, now)
                    put(DiaryDbHelper.COL_UPDATED_AT, now)
                },
            )
            fillTyped(db, eventId)
            db.setTransactionSuccessful()
            return eventId
        } finally {
            db.endTransaction()
        }
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        val all = readEvents()
        _events.value = all
        _entries.value = all
            .filterIsInstance<DiaryEvent.Glucose>()
            .map {
                GlucoseEntry(
                    id = it.id,
                    measuredAt = it.occurredAt,
                    mgdl = it.mgdl,
                    context = it.context,
                    note = it.note,
                )
            }
    }

    private fun readEvents(): List<DiaryEvent> {
        val db = helper.readableDatabase
        val rows = mutableListOf<EventRow>()
        db.query(
            DiaryDbHelper.TABLE_EVENTS,
            arrayOf(
                DiaryDbHelper.COL_ID,
                DiaryDbHelper.COL_TYPE,
                DiaryDbHelper.COL_OCCURRED_AT,
                DiaryDbHelper.COL_NOTE,
                DiaryDbHelper.COL_SOURCE,
            ),
            "${DiaryDbHelper.COL_DELETED_AT} IS NULL",
            null,
            null,
            null,
            "${DiaryDbHelper.COL_OCCURRED_AT} DESC, ${DiaryDbHelper.COL_ID} DESC",
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_ID)
            val typeIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_TYPE)
            val timeIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_OCCURRED_AT)
            val noteIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_NOTE)
            val sourceIndex = cursor.getColumnIndexOrThrow(DiaryDbHelper.COL_SOURCE)
            while (cursor.moveToNext()) {
                rows += EventRow(
                    id = cursor.getLong(idIndex),
                    type = runCatching { EventType.valueOf(cursor.getString(typeIndex)) }
                        .getOrDefault(EventType.GLUCOSE),
                    occurredAt = cursor.getLong(timeIndex),
                    note = cursor.getString(noteIndex).orEmpty(),
                    source = runCatching { EventSource.valueOf(cursor.getString(sourceIndex)) }
                        .getOrDefault(EventSource.MANUAL),
                )
            }
        }

        val glucose = readGlucose(db)
        val insulin = readInsulin(db)
        val carbs = readCarbs(db)
        val weight = readWeight(db)
        val bloodPressure = readBloodPressure(db)
        val ketones = readKetones(db)
        val hba1c = readHba1c(db)

        return rows.mapNotNull { row ->
            when (row.type) {
                EventType.GLUCOSE -> glucose[row.id]?.let {
                    DiaryEvent.Glucose(
                        id = row.id,
                        occurredAt = row.occurredAt,
                        note = row.note,
                        source = row.source,
                        mgdl = it.mgdl,
                        context = it.context,
                        specimen = it.specimen,
                    )
                }
                EventType.INSULIN -> insulin[row.id]?.let {
                    DiaryEvent.Insulin(
                        id = row.id,
                        occurredAt = row.occurredAt,
                        note = row.note,
                        source = row.source,
                        unitsMilli = it.unitsMilli,
                        kind = it.kind,
                        insulinType = it.insulinType,
                    )
                }
                EventType.CARBS -> carbs[row.id]?.let {
                    DiaryEvent.Carbs(
                        id = row.id,
                        occurredAt = row.occurredAt,
                        note = row.note,
                        source = row.source,
                        gramsX10 = it.gramsX10,
                        mealType = it.mealType,
                    )
                }
                EventType.WEIGHT -> weight[row.id]?.let {
                    DiaryEvent.Weight(
                        id = row.id,
                        occurredAt = row.occurredAt,
                        note = row.note,
                        source = row.source,
                        weightG = it,
                    )
                }
                EventType.BLOOD_PRESSURE -> bloodPressure[row.id]?.let {
                    DiaryEvent.BloodPressure(
                        id = row.id,
                        occurredAt = row.occurredAt,
                        note = row.note,
                        source = row.source,
                        systolic = it.systolic,
                        diastolic = it.diastolic,
                        pulse = it.pulse,
                    )
                }
                EventType.KETONES -> ketones[row.id]?.let {
                    DiaryEvent.Ketones(
                        id = row.id,
                        occurredAt = row.occurredAt,
                        note = row.note,
                        source = row.source,
                        mmolLX10 = it.mmolLX10,
                        urineGrade = it.urineGrade,
                    )
                }
                EventType.HBA1C -> hba1c[row.id]?.let {
                    DiaryEvent.Hba1c(
                        id = row.id,
                        occurredAt = row.occurredAt,
                        note = row.note,
                        source = row.source,
                        mmolMol = it.mmolMol,
                        percentX10 = it.percentX10,
                    )
                }
            }
        }
    }

    private fun readGlucose(db: SQLiteDatabase): Map<Long, GlucoseRow> {
        val result = mutableMapOf<Long, GlucoseRow>()
        db.rawQuery(
            "SELECT g.event_id, g.mgdl, g.context, g.specimen FROM ${DiaryDbHelper.TABLE_GLUCOSE} g " +
                "JOIN ${DiaryDbHelper.TABLE_EVENTS} e ON e.id = g.event_id " +
                "WHERE e.deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = GlucoseRow(
                    mgdl = cursor.getInt(1),
                    context = runCatching { MeasurementContext.valueOf(cursor.getString(2)) }
                        .getOrDefault(MeasurementContext.RANDOM),
                    specimen = runCatching { Specimen.valueOf(cursor.getString(3)) }
                        .getOrDefault(Specimen.CAPILLARY),
                )
            }
        }
        return result
    }

    private fun readInsulin(db: SQLiteDatabase): Map<Long, InsulinRow> {
        val result = mutableMapOf<Long, InsulinRow>()
        db.rawQuery(
            "SELECT i.event_id, i.units_milli, i.kind, i.insulin_type " +
                "FROM ${DiaryDbHelper.TABLE_INSULIN} i " +
                "JOIN ${DiaryDbHelper.TABLE_EVENTS} e ON e.id = i.event_id " +
                "WHERE e.deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = InsulinRow(
                    unitsMilli = cursor.getInt(1),
                    kind = runCatching { InsulinKind.valueOf(cursor.getString(2)) }
                        .getOrDefault(InsulinKind.BOLUS),
                    insulinType = cursor.getString(3).orEmpty(),
                )
            }
        }
        return result
    }

    private fun readCarbs(db: SQLiteDatabase): Map<Long, CarbsRow> {
        val result = mutableMapOf<Long, CarbsRow>()
        db.rawQuery(
            "SELECT c.event_id, c.grams_x10, c.meal_type " +
                "FROM ${DiaryDbHelper.TABLE_CARBS} c " +
                "JOIN ${DiaryDbHelper.TABLE_EVENTS} e ON e.id = c.event_id " +
                "WHERE e.deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = CarbsRow(
                    gramsX10 = cursor.getInt(1),
                    mealType = runCatching { MealType.valueOf(cursor.getString(2)) }
                        .getOrDefault(MealType.OTHER),
                )
            }
        }
        return result
    }

    private fun readWeight(db: SQLiteDatabase): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        db.rawQuery(
            "SELECT b.event_id, b.weight_g FROM ${DiaryDbHelper.TABLE_BODY} b " +
                "JOIN ${DiaryDbHelper.TABLE_EVENTS} e ON e.id = b.event_id " +
                "WHERE e.deleted_at IS NULL AND b.weight_g IS NOT NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = cursor.getInt(1)
            }
        }
        return result
    }

    private fun readBloodPressure(db: SQLiteDatabase): Map<Long, BloodPressureRow> {
        val result = mutableMapOf<Long, BloodPressureRow>()
        db.rawQuery(
            "SELECT b.event_id, b.systolic, b.diastolic, b.pulse " +
                "FROM ${DiaryDbHelper.TABLE_BP} b " +
                "JOIN ${DiaryDbHelper.TABLE_EVENTS} e ON e.id = b.event_id " +
                "WHERE e.deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = BloodPressureRow(
                    systolic = cursor.getInt(1),
                    diastolic = cursor.getInt(2),
                    pulse = if (cursor.isNull(3)) null else cursor.getInt(3),
                )
            }
        }
        return result
    }

    private fun readKetones(db: SQLiteDatabase): Map<Long, KetonesRow> {
        val result = mutableMapOf<Long, KetonesRow>()
        db.rawQuery(
            "SELECT k.event_id, k.mmol_l_x10, k.urine_grade " +
                "FROM ${DiaryDbHelper.TABLE_KETONES} k " +
                "JOIN ${DiaryDbHelper.TABLE_EVENTS} e ON e.id = k.event_id " +
                "WHERE e.deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = KetonesRow(
                    mmolLX10 = if (cursor.isNull(1)) null else cursor.getInt(1),
                    urineGrade = if (cursor.isNull(2)) null else cursor.getInt(2),
                )
            }
        }
        return result
    }

    private fun readHba1c(db: SQLiteDatabase): Map<Long, Hba1cRow> {
        val result = mutableMapOf<Long, Hba1cRow>()
        db.rawQuery(
            "SELECT h.event_id, h.mmol_mol, h.percent_x10 FROM ${DiaryDbHelper.TABLE_HBA1C} h " +
                "JOIN ${DiaryDbHelper.TABLE_EVENTS} e ON e.id = h.event_id " +
                "WHERE e.deleted_at IS NULL",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = Hba1cRow(
                    mmolMol = cursor.getInt(1),
                    percentX10 = cursor.getInt(2),
                )
            }
        }
        return result
    }

    private data class EventRow(
        val id: Long,
        val type: EventType,
        val occurredAt: Long,
        val note: String,
        val source: EventSource,
    )

    private data class GlucoseRow(
        val mgdl: Int,
        val context: MeasurementContext,
        val specimen: Specimen,
    )

    private data class InsulinRow(
        val unitsMilli: Int,
        val kind: InsulinKind,
        val insulinType: String,
    )

    private data class CarbsRow(
        val gramsX10: Int,
        val mealType: MealType,
    )

    private data class BloodPressureRow(
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int?,
    )

    private data class KetonesRow(
        val mmolLX10: Int?,
        val urineGrade: Int?,
    )

    private data class Hba1cRow(
        val mmolMol: Int,
        val percentX10: Int,
    )
}
