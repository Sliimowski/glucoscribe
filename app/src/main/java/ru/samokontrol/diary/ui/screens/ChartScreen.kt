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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseUnit
import ru.samokontrol.diary.ui.DiaryViewModel
import ru.samokontrol.diary.ui.components.GlucoseChart
import kotlin.math.roundToInt

private val PERIODS = listOf(3, 7, 14, 30)

@Composable
fun ChartScreen(
    entries: List<GlucoseEntry>,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
) {
    var days by rememberSaveable { mutableIntStateOf(3) }
    val now = System.currentTimeMillis()
    val filtered = remember(entries, days) {
        entries.filter { it.measuredAt >= now - days * DiaryViewModel.DAY_MILLIS }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        if (filtered.isEmpty()) {
            Text(
                text = "За выбранный период нет измерений.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val values = filtered.map { it.mgdl }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Мин", Glucose.format(values.min(), unit), Modifier.weight(1f))
                StatBox("Средний", Glucose.format(values.average().roundToInt(), unit), Modifier.weight(1f))
                StatBox("Макс", Glucose.format(values.max(), unit), Modifier.weight(1f))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    GlucoseChart(
                        entries = filtered,
                        unit = unit,
                        days = days,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Зелёная полоса — целевой диапазон " +
                            "${Glucose.format(70, unit)}–${Glucose.format(180, unit)} ${unit.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Точек за период: ${filtered.size}. " +
                    "График строится по времени измерения; пропуски не интерполируются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}