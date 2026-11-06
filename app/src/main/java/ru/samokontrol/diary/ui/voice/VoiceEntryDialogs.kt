package ru.samokontrol.diary.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseUnit

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))

@Composable
fun VoiceListeningDialog(
    partialText: String,
    onDevice: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Слушаю…") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = if (partialText.isBlank()) {
                            "Назовите показатель, например «пять и шесть»"
                        } else {
                            partialText
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (onDevice) {
                        "Распознавание на устройстве, аудио не уходит в сеть."
                    } else {
                        "Системное распознавание может использовать интернет."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}

@Composable
fun VoiceConfirmDialog(
    unit: GlucoseUnit,
    initialValue: String,
    recognizedText: String,
    measuredAt: Long,
    onDismiss: () -> Unit,
    onConfirm: (mgdl: Int, context: MeasurementContext, note: String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    var note by remember { mutableStateOf("") }
    var context by remember { mutableStateOf(MeasurementContext.RANDOM) }
    var error by remember { mutableStateOf<String?>(null) }

    val time = remember(measuredAt) {
        timeFormat.format(Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault()))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проверьте значение") },
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Распознано: «$recognizedText»",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Время записи: сейчас, $time",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun VoiceFailureDialog(
    title: String,
    message: String,
    recognizedText: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (!recognizedText.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Распознано: «$recognizedText»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (canRetry) {
                TextButton(onClick = onRetry) { Text("Ещё раз") }
            } else {
                TextButton(onClick = onManual) { Text("Ввести вручную") }
            }
        },
        dismissButton = {
            Row {
                if (canRetry) {
                    TextButton(onClick = onManual) { Text("Ввести вручную") }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}