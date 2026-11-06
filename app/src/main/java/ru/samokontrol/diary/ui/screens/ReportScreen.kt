package ru.samokontrol.diary.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseStats
import ru.samokontrol.diary.domain.GlucoseUnit
import ru.samokontrol.diary.export.ReportExporter
import ru.samokontrol.diary.ui.DiaryViewModel
import ru.samokontrol.diary.ui.theme.Amber
import ru.samokontrol.diary.ui.theme.Danger
import ru.samokontrol.diary.ui.theme.Teal700

private val PERIODS = listOf(14, 30, 90)

@Composable
fun ReportScreen(
    entries: List<GlucoseEntry>,
    unit: GlucoseUnit,
    statsFor: (Int) -> GlucoseStats,
    modifier: Modifier = Modifier,
) {
    var days by rememberSaveable { mutableIntStateOf(14) }
    var message by remember { mutableStateOf<String?>(null) }
    val stats = remember(entries, days) { statsFor(days) }
    val periodEntries = remember(entries, days) {
        val now = System.currentTimeMillis()
        entries.filter { it.measuredAt >= now - days * DiaryViewModel.DAY_MILLIS }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun export(pdf: Boolean) {
        message = null
        scope.launch {
            runCatching {
                val uri = withContext(Dispatchers.IO) {
                    if (pdf) {
                        ReportExporter.exportPdf(context, periodEntries, stats, unit, days)
                    } else {
                        ReportExporter.exportCsv(context, periodEntries, unit)
                    }
                }
                ReportExporter.share(
                    context = context,
                    uri = uri,
                    mimeType = if (pdf) "application/pdf" else "text/csv",
                    chooserTitle = "Отправить отчёт",
                )
            }.onFailure { message = "Не удалось сформировать файл: ${it.message}" }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PERIODS.forEach { period ->
                FilterChip(
                    selected = days == period,
                    onClick = { days = period },
                    label = { Text("$period дн.") },
                )
            }
        }

        if (stats.isEmpty) {
            Text(
                text = "Нет данных за выбранный период. Добавьте измерения в дневнике.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "В целевом диапазоне",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${pct(stats.timeInRangePercent)} %",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Teal700,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (stats.timeInRangePercent / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MiniMetric("Ниже", "${pct(stats.timeBelowPercent)} %", Danger)
                        MiniMetric("Выше", "${pct(stats.timeAbovePercent)} %", Amber)
                        MiniMetric("Точек", stats.count.toString(), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            MetricRow("Средний уровень", "${Glucose.format(stats.meanMgdl.toInt(), unit)} ${unit.title}")
            MetricRow("Стандартное отклонение", "${Glucose.format(stats.sdMgdl.toInt(), unit)} ${unit.title}")
            MetricRow("Коэффициент вариации (CV)", "${pct(stats.cvPercent)} %")
            MetricRow("GMI", "${pct(stats.gmiPercent)} %")
            MetricRow("Оценка HbA1c (ADAG)", "${pct(stats.estimatedHba1cPercent)} %")

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { export(pdf = true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = periodEntries.isNotEmpty(),
            ) {
                Text("Экспорт PDF")
            }
            OutlinedButton(
                onClick = { export(pdf = false) },
                modifier = Modifier.fillMaxWidth(),
                enabled = periodEntries.isNotEmpty(),
            ) {
                Text("Экспорт CSV")
            }
            message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = "Приложение ведёт дневник и не является медицинским изделием. " +
                    "Оно не рассчитывает дозы и не заменяет консультацию врача.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MiniMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

private fun pct(value: Double): String = String.format(Locale("ru"), "%.1f", value)