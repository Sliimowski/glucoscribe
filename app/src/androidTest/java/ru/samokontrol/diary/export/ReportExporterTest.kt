package ru.samokontrol.diary.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
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

        val pdfUri = ReportExporter.exportPdf(context, events, entries, stats, GlucoseUnit.MMOLL, 14)
        val pdfFile = File(context.cacheDir, "test-report.pdf")
        context.contentResolver.openInputStream(pdfUri)!!.use { input ->
            pdfFile.outputStream().use { output -> input.copyTo(output) }
        }
        assertTrue("PDF должен быть непустым", pdfFile.length() > 1000)

        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue("В PDF ожидается хотя бы одна страница", renderer.pageCount >= 1)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.cacheDir, "report-page0.png").outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                }
            }
        }

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
