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
import ru.samokontrol.diary.data.DiaryEvent
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.EventInput
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

    private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru"))
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru"))
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru"))

    fun exportPdf(
        context: Context,
        events: List<DiaryEvent>,
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

        val insulin = events.filterIsInstance<DiaryEvent.Insulin>()
        if (insulin.isNotEmpty()) {
            val total = insulin.sumOf { it.unitsMilli }
            text("Инсулин: ${insulin.size} записей, суммарно ${EventInput.formatInsulinUnits(total)}")
        }
        val carbs = events.filterIsInstance<DiaryEvent.Carbs>()
        if (carbs.isNotEmpty()) {
            val total = carbs.sumOf { it.gramsX10 }
            text("Углеводы: ${carbs.size} записей, суммарно ${EventInput.formatCarbs(total)}")
        }
        y += 8f
        divider()

        // График глюкозы за период.
        text("Глюкоза за период", size = 13f, bold = true)
        val chartHeight = 190f
        newPageIfNeeded(chartHeight + 40f)
        run {
            val plotLeft = MARGIN + 34f
            val plotRight = PAGE_WIDTH - MARGIN
            val plotTop = y
            val plotBottom = y + chartHeight

            fun gy(mgdl: Float): Float =
                plotTop + (plotBottom - plotTop) * (1f - (mgdl - 40f) / (300f - 40f))

            fun gx(time: Long): Float {
                val fraction = ((time - periodStart).toFloat() / (now - periodStart).toFloat())
                    .coerceIn(0f, 1f)
                return plotLeft + (plotRight - plotLeft) * fraction
            }

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(28, 0, 150, 100)
            canvas.drawRect(plotLeft, gy(180f), plotRight, gy(70f), paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            paint.color = Color.LTGRAY
            listOf(70f, 180f, 300f).forEach { value ->
                val lineY = gy(value)
                canvas.drawLine(plotLeft, lineY, plotRight, lineY, paint)
            }

            if (entries.isNotEmpty()) {
                paint.color = Color.rgb(0, 121, 107)
                paint.strokeWidth = 1.5f
                val sorted = entries.sortedBy { it.measuredAt }
                var previous: Pair<Float, Float>? = null
                sorted.forEach { entry ->
                    val pointX = gx(entry.measuredAt)
                    val pointY = gy(entry.mgdl.toFloat().coerceIn(40f, 300f))
                    previous?.let { canvas.drawLine(it.first, it.second, pointX, pointY, paint) }
                    previous = pointX to pointY
                }

                paint.style = Paint.Style.FILL
                sorted.forEach { entry ->
                    val pointX = gx(entry.measuredAt)
                    val pointY = gy(entry.mgdl.toFloat().coerceIn(40f, 300f))
                    paint.color = when {
                        entry.mgdl < GlucoseMetrics.DEFAULT_LOW -> Color.rgb(179, 38, 30)
                        entry.mgdl > GlucoseMetrics.DEFAULT_HIGH -> Color.rgb(180, 83, 9)
                        else -> Color.rgb(0, 121, 107)
                    }
                    canvas.drawCircle(pointX, pointY, 2.5f, paint)
                }
            }

            paint.style = Paint.Style.FILL
            paint.textSize = 8f
            paint.color = Color.GRAY
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            listOf(300, 180, 70, 40).forEach { value ->
                canvas.drawText(value.toString(), MARGIN, gy(value.toFloat()) + 3f, paint)
            }
            canvas.drawText(
                dateFormat.format(Instant.ofEpochMilli(periodStart).atZone(ZoneId.systemDefault())),
                plotLeft,
                plotBottom + 12f,
                paint,
            )
            canvas.drawText(
                dateFormat.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())),
                plotRight - 46f,
                plotBottom + 12f,
                paint,
            )
            y = plotBottom + 24f
        }

        y += 4f
        divider()

        text("По контекстам", size = 13f, bold = true)
        MeasurementContext.entries.forEach { context ->
            val list = entries.filter { it.context == context }
            if (list.isNotEmpty()) {
                val mean = list.sumOf { it.mgdl }.toDouble() / list.size
                val below = list.count { it.mgdl < stats.lowMgdl } * 100.0 / list.size
                val above = list.count { it.mgdl > stats.highMgdl } * 100.0 / list.size
                text(
                    "${context.title}: ${list.size} изм., средний ${fmtGlucose(mean, unit)} ${unit.title}, " +
                        "ниже ${fmt(below)} %, выше ${fmt(above)} %",
                    size = 10f,
                )
            }
        }

        val lows = entries.filter { it.mgdl < stats.lowMgdl }.sortedByDescending { it.measuredAt }
        if (lows.isNotEmpty()) {
            y += 4f
            text("Эпизоды ниже диапазона: ${lows.size}", size = 13f, bold = true)
            lows.take(20).forEach { entry ->
                val zoned = Instant.ofEpochMilli(entry.measuredAt).atZone(ZoneId.systemDefault())
                text(
                    "${dateTimeFormat.format(zoned)} — ${Glucose.format(entry.mgdl, unit)} ${unit.title}, " +
                        entry.context.title,
                    size = 10f,
                )
            }
            if (lows.size > 20) {
                text("…и ещё ${lows.size - 20}; полный список — в CSV.", size = 9f, color = Color.GRAY)
            }
        }

        y += 8f
        text(
            "Приложение не является медицинским изделием и не заменяет консультацию врача.",
            size = 9f,
            color = Color.GRAY,
        )
        text(
            "Сформировано: ${dateTimeFormat.format(Instant.now().atZone(ZoneId.systemDefault()))}",
            size = 9f,
            color = Color.GRAY,
        )

        document.finishPage(page)
        val file = reportFile(context, "diary", "pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return uriFor(context, file)
    }

    fun exportCsv(
        context: Context,
        events: List<DiaryEvent>,
        unit: GlucoseUnit,
    ): Uri {
        val file = reportFile(context, "diary", "csv")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("Дата;Время;Тип;Значение;Единица;Детали;Заметка\n")
            events.sortedByDescending { it.occurredAt }.forEach { event ->
                val zoned = Instant.ofEpochMilli(event.occurredAt).atZone(ZoneId.systemDefault())
                val row = csvRow(event, unit)
                writer.write(
                    listOf(
                        dateFormat.format(zoned),
                        timeFormat.format(zoned),
                        row.type,
                        row.value,
                        row.unit,
                        row.details,
                        event.note,
                    ).joinToString(";") { escapeCsv(it) },
                )
                writer.write("\n")
            }
        }
        return uriFor(context, file)
    }

    private data class CsvRow(
        val type: String,
        val value: String,
        val unit: String,
        val details: String,
    )

    private fun csvRow(event: DiaryEvent, unit: GlucoseUnit): CsvRow = when (event) {
        is DiaryEvent.Glucose -> CsvRow(
            type = "Глюкоза",
            value = when (unit) {
                GlucoseUnit.MGDL -> event.mgdl.toString()
                GlucoseUnit.MMOLL -> String.format(Locale.US, "%.1f", Glucose.mgdlToMmol(event.mgdl))
                    .replace('.', ',')
            },
            unit = unit.title,
            details = event.context.title,
        )

        is DiaryEvent.Insulin -> CsvRow(
            type = "Инсулин",
            value = EventInput.formatInsulinUnits(event.unitsMilli).substringBefore(" "),
            unit = "ЕД",
            details = buildString {
                append(event.kind.title)
                if (event.insulinType.isNotBlank()) append(" · ${event.insulinType}")
            },
        )

        is DiaryEvent.Carbs -> CsvRow(
            type = "Углеводы",
            value = EventInput.formatCarbs(event.gramsX10).substringBefore(" "),
            unit = "г",
            details = event.mealType.title,
        )

        is DiaryEvent.Weight -> CsvRow(
            type = "Вес",
            value = EventInput.formatWeight(event.weightG).substringBefore(" "),
            unit = "кг",
            details = "",
        )

        is DiaryEvent.BloodPressure -> CsvRow(
            type = "Давление",
            value = "${event.systolic}/${event.diastolic}",
            unit = "мм рт. ст.",
            details = event.pulse?.let { "пульс $it" }.orEmpty(),
        )

        is DiaryEvent.Ketones -> CsvRow(
            type = "Кетоны",
            value = event.mmolLX10?.let { EventInput.formatKetones(it).substringBefore(" ") }
                ?: event.urineGrade?.toString()
                ?: "",
            unit = if (event.mmolLX10 != null) "ммоль/л" else "степень",
            details = event.urineGrade?.let { "моча: $it" }.orEmpty(),
        )

        is DiaryEvent.Hba1c -> CsvRow(
            type = "HbA1c",
            value = EventInput.formatHba1c(event.percentX10).substringBefore(" "),
            unit = "%",
            details = "${event.mmolMol} ммоль/моль",
        )
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

    private fun fmt(value: Double): String = String.format(Locale.forLanguageTag("ru"), "%.1f", value)

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
