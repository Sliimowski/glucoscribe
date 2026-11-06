package ru.samokontrol.diary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseMetrics
import ru.samokontrol.diary.domain.GlucoseUnit
import ru.samokontrol.diary.ui.theme.Amber
import ru.samokontrol.diary.ui.theme.Danger
import ru.samokontrol.diary.ui.theme.Teal700

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

@Composable
fun DiaryScreen(
    entries: List<GlucoseEntry>,
    unit: GlucoseUnit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        EmptyDiary(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            EntryCard(entry = entry, unit = unit, onDelete = { onDelete(entry.id) })
        }
    }
}

@Composable
private fun EntryCard(
    entry: GlucoseEntry,
    unit: GlucoseUnit,
    onDelete: () -> Unit,
) {
    val color = when {
        entry.mgdl < GlucoseMetrics.DEFAULT_LOW -> Danger
        entry.mgdl > GlucoseMetrics.DEFAULT_HIGH -> Amber
        else -> Teal700
    }
    val time = remember(entry.measuredAt) {
        dateFormat.format(Instant.ofEpochMilli(entry.measuredAt).atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = Glucose.format(entry.mgdl, unit),
                    color = color,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.context.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить запись",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyDiary(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Пока нет измерений",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Нажмите «+», чтобы добавить уровень глюкозы.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AddEntryDialog(
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onConfirm: (mgdl: Int, context: MeasurementContext, note: String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var context by remember { mutableStateOf(MeasurementContext.RANDOM) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое измерение") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text("Глюкоза, ${unit.title}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
                Spacer(Modifier.height(12.dp))
                Text("Контекст", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MeasurementContext.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { option ->
                                FilterChip(
                                    selected = context == option,
                                    onClick = { context = option },
                                    label = { Text(option.title) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка (необязательно)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = Glucose.parse(value, unit)
                    if (parsed == null) {
                        error = "Введите значение в диапазоне " +
                            "${Glucose.format(Glucose.MIN_MGDL, unit)}–" +
                            "${Glucose.format(Glucose.MAX_MGDL, unit)} ${unit.title}"
                    } else {
                        onConfirm(parsed, context, note)
                    }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}