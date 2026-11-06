package ru.samokontrol.diary.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.ZoneId

internal fun zoneOffsetMinutes(epochMillis: Long): Int =
    ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(epochMillis)).totalSeconds / 60

/**
 * Схема v2: общее событие + типизированные таблицы.
 *
 * v1 (таблица glucose_entries) мигрирует в events + glucose_readings без потери данных.
 */
internal class DiaryDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            migrateV1ToV2(db)
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TYPE TEXT NOT NULL,
                $COL_OCCURRED_AT INTEGER NOT NULL,
                $COL_ZONE_OFFSET_MIN INTEGER NOT NULL DEFAULT 0,
                $COL_NOTE TEXT NOT NULL DEFAULT '',
                $COL_SOURCE TEXT NOT NULL DEFAULT 'MANUAL',
                $COL_EXTERNAL_ID TEXT,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_DELETED_AT INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_time ON $TABLE_EVENTS ($COL_OCCURRED_AT)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_events_external " +
                "ON $TABLE_EVENTS ($COL_SOURCE, $COL_EXTERNAL_ID) " +
                "WHERE $COL_EXTERNAL_ID IS NOT NULL",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GLUCOSE (
                $COL_EVENT_ID INTEGER PRIMARY KEY,
                $COL_MGDL INTEGER NOT NULL,
                $COL_CONTEXT TEXT NOT NULL,
                $COL_SPECIMEN TEXT NOT NULL DEFAULT 'CAPILLARY',
                FOREIGN KEY ($COL_EVENT_ID) REFERENCES $TABLE_EVENTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_INSULIN (
                $COL_EVENT_ID INTEGER PRIMARY KEY,
                $COL_UNITS_MILLI INTEGER NOT NULL,
                $COL_INSULIN_KIND TEXT NOT NULL,
                $COL_INSULIN_TYPE TEXT NOT NULL DEFAULT '',
                FOREIGN KEY ($COL_EVENT_ID) REFERENCES $TABLE_EVENTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CARBS (
                $COL_EVENT_ID INTEGER PRIMARY KEY,
                $COL_GRAMS_X10 INTEGER NOT NULL,
                $COL_MEAL_TYPE TEXT NOT NULL,
                FOREIGN KEY ($COL_EVENT_ID) REFERENCES $TABLE_EVENTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BODY (
                $COL_EVENT_ID INTEGER PRIMARY KEY,
                $COL_WEIGHT_G INTEGER,
                $COL_HEIGHT_MM INTEGER,
                FOREIGN KEY ($COL_EVENT_ID) REFERENCES $TABLE_EVENTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BP (
                $COL_EVENT_ID INTEGER PRIMARY KEY,
                $COL_SYSTOLIC INTEGER NOT NULL,
                $COL_DIASTOLIC INTEGER NOT NULL,
                $COL_PULSE INTEGER,
                FOREIGN KEY ($COL_EVENT_ID) REFERENCES $TABLE_EVENTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_KETONES (
                $COL_EVENT_ID INTEGER PRIMARY KEY,
                $COL_MMOL_L_X10 INTEGER,
                $COL_URINE_GRADE INTEGER,
                FOREIGN KEY ($COL_EVENT_ID) REFERENCES $TABLE_EVENTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HBA1C (
                $COL_EVENT_ID INTEGER PRIMARY KEY,
                $COL_MMOL_MOL INTEGER NOT NULL,
                $COL_PERCENT_X10 INTEGER NOT NULL,
                FOREIGN KEY ($COL_EVENT_ID) REFERENCES $TABLE_EVENTS ($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    private fun migrateV1ToV2(db: SQLiteDatabase) {
        createSchema(db)
        val now = System.currentTimeMillis()

        db.rawQuery(
            "SELECT id, measured_at, mgdl, context, note FROM $LEGACY_TABLE ORDER BY id",
            null,
        ).use { cursor ->
            val timeIndex = cursor.getColumnIndexOrThrow("measured_at")
            val mgdlIndex = cursor.getColumnIndexOrThrow("mgdl")
            val contextIndex = cursor.getColumnIndexOrThrow("context")
            val noteIndex = cursor.getColumnIndexOrThrow("note")

            while (cursor.moveToNext()) {
                val measuredAt = cursor.getLong(timeIndex)
                val eventId = db.insertOrThrow(
                    TABLE_EVENTS,
                    null,
                    ContentValues().apply {
                        put(COL_TYPE, EventType.GLUCOSE.name)
                        put(COL_OCCURRED_AT, measuredAt)
                        put(COL_ZONE_OFFSET_MIN, zoneOffsetMinutes(measuredAt))
                        put(COL_NOTE, cursor.getString(noteIndex).orEmpty())
                        put(COL_SOURCE, EventSource.MANUAL.name)
                        put(COL_CREATED_AT, now)
                        put(COL_UPDATED_AT, now)
                    },
                )
                db.insertOrThrow(
                    TABLE_GLUCOSE,
                    null,
                    ContentValues().apply {
                        put(COL_EVENT_ID, eventId)
                        put(COL_MGDL, cursor.getInt(mgdlIndex))
                        put(COL_CONTEXT, cursor.getString(contextIndex))
                        put(COL_SPECIMEN, Specimen.CAPILLARY.name)
                    },
                )
            }
        }

        db.execSQL("DROP TABLE IF EXISTS $LEGACY_TABLE")
    }

    companion object {
        const val DB_NAME = "samokontrol.db"
        const val DB_VERSION = 2

        const val LEGACY_TABLE = "glucose_entries"

        const val TABLE_EVENTS = "events"
        const val COL_ID = "id"
        const val COL_TYPE = "type"
        const val COL_OCCURRED_AT = "occurred_at"
        const val COL_ZONE_OFFSET_MIN = "zone_offset_min"
        const val COL_NOTE = "note"
        const val COL_SOURCE = "source"
        const val COL_EXTERNAL_ID = "external_id"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_DELETED_AT = "deleted_at"

        const val TABLE_GLUCOSE = "glucose_readings"
        const val COL_EVENT_ID = "event_id"
        const val COL_MGDL = "mgdl"
        const val COL_CONTEXT = "context"
        const val COL_SPECIMEN = "specimen"

        const val TABLE_INSULIN = "insulin_doses"
        const val COL_UNITS_MILLI = "units_milli"
        const val COL_INSULIN_KIND = "kind"
        const val COL_INSULIN_TYPE = "insulin_type"

        const val TABLE_CARBS = "carb_entries"
        const val COL_GRAMS_X10 = "grams_x10"
        const val COL_MEAL_TYPE = "meal_type"

        const val TABLE_BODY = "body_metrics"
        const val COL_WEIGHT_G = "weight_g"
        const val COL_HEIGHT_MM = "height_mm"

        const val TABLE_BP = "bp_readings"
        const val COL_SYSTOLIC = "systolic"
        const val COL_DIASTOLIC = "diastolic"
        const val COL_PULSE = "pulse"

        const val TABLE_KETONES = "ketone_readings"
        const val COL_MMOL_L_X10 = "mmol_l_x10"
        const val COL_URINE_GRADE = "urine_grade"

        const val TABLE_HBA1C = "hba1c_results"
        const val COL_MMOL_MOL = "mmol_mol"
        const val COL_PERCENT_X10 = "percent_x10"
    }
}
