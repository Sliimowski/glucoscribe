package ru.samokontrol.diary.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.samokontrol.diary.data.DiaryEvent
import ru.samokontrol.diary.data.EventSource
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.data.InsulinKind
import ru.samokontrol.diary.data.MealType
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.GlucoseMetrics
import ru.samokontrol.diary.domain.GlucoseSample
import ru.samokontrol.diary.domain.GlucoseUnit

@RunWith(AndroidJUnit4::class)
class ReportExporterTest {

    @Test
    fun pdfAndCsvContainNewEventTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()

        val glucose = DiaryEvent.Glucose(
            id = 1,
            occurredAt = now - 3_600_000,
            note = "после прогулки",
            source = EventSource.MANUAL,
            mgdl = 120,
            context = MeasurementContext.BEFORE_MEAL,
        )
        val insulin = DiaryEvent.Insulin(
            id = 2,
            occurredAt = now - 1_800_000,
            source = EventSource.MANUAL,
            unitsMilli = 6500,
            kind = InsulinKind.BOLUS,
            insulinType = "Хумалог",
        )
        val carbs = DiaryEvent.Carbs(
            id = 3,
            occurredAt = now - 1_700_000,
            source = EventSource.MANUAL,
            gramsX10 = 450,
            mealType = MealType.LUNCH,
        )
        val events = listOf(glucose, insulin, carbs)
        val entries = listOf(
            GlucoseEntry(
                id = 1,
                measuredAt = glucose.occurredAt,
                mgdl = 120,
                context = MeasurementContext.BEFORE_MEAL,
                note = glucose.note,
            ),
        )
        val stats = GlucoseMetrics.calculate(
            samples = listOf(GlucoseSample(120, glucose.occurredAt)),
            periodDays = 14,
            now = now,
        )

        val pdfBytes = context.contentResolver
            .openInputStream(
                ReportExporter.exportPdf(context, events, entries, stats, GlucoseUnit.MMOLL, 14),
            )!!
            .use { it.readBytes() }
        assertTrue("PDF должен быть непустым", pdfBytes.size > 1000)

        val csv = context.contentResolver
            .openInputStream(
                ReportExporter.exportCsv(context, events, GlucoseUnit.MMOLL),
            )!!
            .use { it.readBytes().toString(StandardCharsets.UTF_8) }
        assertTrue(csv.contains("Инсулин"))
        assertTrue(csv.contains("Углеводы"))
        assertTrue(csv.contains("6,5"))
        assertTrue(csv.contains("45"))
    }
}
