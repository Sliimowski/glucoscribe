package ru.samokontrol.diary.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseMetrics
import ru.samokontrol.diary.domain.GlucoseStats
import ru.samokontrol.diary.domain.GlucoseUnit

/**
 * Экспорт дневника в PDF и CSV. Файлы кладём в cache/reports и отдаём через FileProvider.
 * Данные не покидают устройство, пока пользователь сам не отправит файл.
 */
object ReportExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale("ru"))
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("ru"))
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))

    fun exportPdf(
        context: Context,
        entries: List<GlucoseEntry>,
        stats: GlucoseStats,
        unit: GlucoseUnit,
        days: Int,
    ): Uri {
        val document = PdfDocument()
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
        )
        var canvas = page.canvas
        var y = 56f

        fun newPageIfNeeded(required: Float = 20f) {
            if (y + required > PAGE_HEIGHT - 40f) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
                )
                canvas = page.canvas
                y = 56f
            }
        }

        fun text(
            value: String,
            size: Float = 12f,
            bold: Boolean = false,
            color: Int = Color.BLACK,
            x: Float = MARGIN,
        ) {
            newPageIfNeeded(size + 8f)
            paint.textSize = size
            paint.color = color
            paint.typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
            canvas.drawText(value, x, y, paint)
            y += size + 6f
        }

        fun divider() {
            newPageIfNeeded()
            paint.color = Color.LTGRAY
            paint.strokeWidth = 0.7f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
            y += 8f
        }

        val now = System.currentTimeMillis()
        val periodStart = now - days * 24L * 60L * 60L * 1000L
        val periodText = "${dateFormat.format(Instant.ofEpochMilli(periodStart).atZone(ZoneId.systemDefault()))} — " +
            dateFormat.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))

        text("Дневник самоконтроля", size = 20f, bold = true)
        text("Период: $periodText", size = 12f, color = Color.DKGRAY)
        text("Единицы: ${unit.title}", size = 12f, color = Color.DKGRAY)
        y += 6f
        divider()

        text("Сводка", size = 15f, bold = true)
        text("Измерений: ${stats.count}")
        text("Средний уровень: ${fmtGlucose(stats.meanMgdl, unit)} ${unit.title}")
        text("Стандартное отклонение: ${fmtGlucose(stats.sdMgdl, unit)} ${unit.title}")
        text("Коэффициент вариации (CV): ${fmt(stats.cvPercent)} %")
        text(
            "Доля измерений в диапазоне ${Glucose.format(stats.lowMgdl, unit)}–" +
                "${Glucose.format(stats.highMgdl, unit)}: ${fmt(stats.inRangePercent)} %",
        )
        text("Это не TIR по CGM: пропуски между замерами не учитываются.", size = 9f, color = Color.GRAY)
        text("Ниже диапазона: ${fmt(stats.belowPercent)} %")
        text("Тяжёлые низкие (< ${Glucose.format(GlucoseMetrics.SEVERE_LOW, unit)} ${unit.title}): ${fmt(stats.belowSeverePercent)} %")
        text("Выше диапазона: ${fmt(stats.abovePercent)} %")
        text("Тяжёлые высокие (> ${Glucose.format(GlucoseMetrics.SEVERE_HIGH, unit)} ${unit.title}): ${fmt(stats.aboveSeverePercent)} %")
        text("Дней с данными: ${stats.measuredDays} из ${stats.periodDays} (${fmt(stats.completenessPercent)} %)")
        text("GMI (оценка, не HbA1c): ${fmt(stats.gmiPercent)} %")
        text("Оценка HbA1c по ADAG (не лабораторный): ${fmt(stats.estimatedHba1cPercent)} %")
        y += 8f
        divider()

        text("Измерения", size = 15f, bold = true)
        newPageIfNeeded(30f)
        paint.textSize = 10f
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("Дата и время", MARGIN, y, paint)
        canvas.drawText("Глюкоза", 230f, y, paint)
        canvas.drawText("Контекст", 320f, y, paint)
        canvas.drawText("Заметка", 460f, y, paint)
        y += 14f
        divider()

        entries.sortedByDescending { it.measuredAt }.take(500).forEach { entry ->
            newPageIfNeeded(18f)
            val zoned = Instant.ofEpochMilli(entry.measuredAt).atZone(ZoneId.systemDefault())
            paint.textSize = 10f
            paint.color = Color.BLACK
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText(dateTimeFormat.format(zoned), MARGIN, y, paint)
            canvas.drawText(
                "${Glucose.format(entry.mgdl, unit)} ${unit.title}",
                230f,
                y,
                paint,
            )
            canvas.drawText(entry.context.title, 320f, y, paint)
            canvas.drawText(entry.note.take(24), 460f, y, paint)
            y += 16f
        }

        y += 8f
        text(
            "Приложение не является медицинским изделием и не заменяет консультацию врача.",
            size = 9f,
            color = Color.GRAY,
        )
        text("Сформировано: ${dateTimeFormat.format(Instant.now().atZone(ZoneId.systemDefault()))}", size = 9f, color = Color.GRAY)

        document.finishPage(page)
        val file = reportFile(context, "diary", "pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return uriFor(context, file)
    }

    fun exportCsv(
        context: Context,
        entries: List<GlucoseEntry>,
        unit: GlucoseUnit,
    ): Uri {
        val file = reportFile(context, "diary", "csv")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("Дата;Время;Глюкоза;Единица;Контекст;Заметка\n")
            entries.sortedByDescending { it.measuredAt }.forEach { entry ->
                val zoned = Instant.ofEpochMilli(entry.measuredAt).atZone(ZoneId.systemDefault())
                val value = when (unit) {
                    GlucoseUnit.MGDL -> entry.mgdl.toString()
                    GlucoseUnit.MMOLL -> String.format(Locale.US, "%.1f", Glucose.mgdlToMmol(entry.mgdl))
                        .replace('.', ',')
                }
                writer.write(
                    listOf(
                        dateFormat.format(zoned),
                        timeFormat.format(zoned),
                        value,
                        unit.title,
                        entry.context.title,
                        entry.note,
                    ).joinToString(";") { escapeCsv(it) },
                )
                writer.write("\n")
            }
        }
        return uriFor(context, file)
    }

    fun share(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle)
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun fmt(value: Double): String = String.format(Locale("ru"), "%.1f", value)

    private fun fmtGlucose(mgdl: Double, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MGDL -> fmt(mgdl)
        GlucoseUnit.MMOLL -> fmt(mgdl / Glucose.MGDL_PER_MMOL)
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(';') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun reportFile(context: Context, prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(Instant.now().atZone(ZoneId.systemDefault()))
        return File(dir, "${prefix}_$stamp.$extension")
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}