package ru.samokontrol.diary.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.samokontrol.diary.data.InsulinKind
import ru.samokontrol.diary.data.MealType
import ru.samokontrol.diary.data.MeasurementContext
import ru.samokontrol.diary.domain.EventInput
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseUnit

enum class EntryKind(val title: String) {
    GLUCOSE("Глюкоза"),
    INSULIN("Инсулин"),
    CARBS("Еда"),
    WEIGHT("Вес"),
    BLOOD_PRESSURE("Давление"),
    KETONES("Кетоны"),
    HBA1C("HbA1c"),
}

sealed interface NewEventInput {
    data class Glucose(val mgdl: Int, val context: MeasurementContext) : NewEventInput
    data class Insulin(
        val unitsMilli: Int,
        val kind: InsulinKind,
        val insulinType: String,
    ) : NewEventInput

    data class Carbs(val gramsX10: Int, val mealType: MealType) : NewEventInput
    data class Weight(val weightG: Int) : NewEventInput
    data class BloodPressure(val systolic: Int, val diastolic: Int, val pulse: Int?) : NewEventInput
    data class Ketones(val mmolLX10: Int) : NewEventInput
    data class Hba1c(val percentX10: Int, val mmolMol: Int) : NewEventInput
}

@Composable
fun AddEventDialog(
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onConfirm: (NewEventInput, note: String) -> Unit,
) {
    var kind by remember { mutableStateOf(EntryKind.GLUCOSE) }
    var value1 by remember { mutableStateOf("") }
    var value2 by remember { mutableStateOf("") }
    var value3 by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var context by remember { mutableStateOf(MeasurementContext.RANDOM) }
    var insulinKind by remember { mutableStateOf(InsulinKind.BOLUS) }
    var mealType by remember { mutableStateOf(MealType.OTHER) }
    var error by remember { mutableStateOf<String?>(null) }

    fun switchKind(next: EntryKind) {
        kind = next
        value1 = ""
        value2 = ""
        value3 = ""
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое событие") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EntryKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { switchKind(option) },
                            label = { Text(option.title) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (kind) {
                    EntryKind.GLUCOSE -> {
                        NumberField(
                            value = value1,
                            onValueChange = { value1 = it; error = null },
                            label = "Глюкоза, ${unit.title}",
                            decimal = true,
                            isError = error != null,
                        )
                        Spacer(Modifier.height(10.dp))
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
                    }

                    EntryKind.INSULIN -> {
                        NumberField(
                            value = value1,
                            onValueChange = { value1 = it; error = null },
                            label = "Доза, ЕД",
                            decimal = true,
                            isError = error != null,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsulinKind.entries.forEach { option ->
                                FilterChip(
                                    selected = insulinKind == option,
                                    onClick = { insulinKind = option },
                                    label = { Text(option.title) },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = value2,
                            onValueChange = { value2 = it },
                            label = { Text("Тип инсулина (необязательно)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    EntryKind.CARBS -> {
                        NumberField(
                            value = value1,
                            onValueChange = { value1 = it; error = null },
                            label = "Углеводы, г",
                            decimal = true,
                            isError = error != null,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Приём пищи", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MealType.entries.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { option ->
                                        FilterChip(
                                            selected = mealType == option,
                                            onClick = { mealType = option },
                                            label = { Text(option.title) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    EntryKind.WEIGHT -> NumberField(
                        value = value1,
                        onValueChange = { value1 = it; error = null },
                        label = "Вес, кг",
                        decimal = true,
                        isError = error != null,
                    )

                    EntryKind.BLOOD_PRESSURE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                value = value1,
                                onValueChange = { value1 = it; error = null },
                                label = "Систола",
                                modifier = Modifier.weight(1f),
                                isError = error != null,
                            )
                            NumberField(
                                value = value2,
                                onValueChange = { value2 = it; error = null },
                                label = "Диастола",
                                modifier = Modifier.weight(1f),
                                isError = error != null,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        NumberField(
                            value = value3,
                            onValueChange = { value3 = it; error = null },
                            label = "Пульс (необязательно)",
                            isError = error != null,
                        )
                    }

                    EntryKind.KETONES -> NumberField(
                        value = value1,
                        onValueChange = { value1 = it; error = null },
                        label = "Кетоны, ммоль/л",
                        decimal = true,
                        isError = error != null,
                    )

                    EntryKind.HBA1C -> NumberField(
                        value = value1,
                        onValueChange = { value1 = it; error = null },
                        label = "HbA1c, %",
                        decimal = true,
                        isError = error != null,
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = buildEvent(kind, value1, value2, value3, context, insulinKind, mealType, unit)
                    if (result == null) {
                        error = errorText(kind, unit)
                    } else {
                        onConfirm(result, note)
                    }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    decimal: Boolean = false,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        singleLine = true,
        isError = isError,
    )
}

private fun buildEvent(
    kind: EntryKind,
    value1: String,
    value2: String,
    value3: String,
    context: MeasurementContext,
    insulinKind: InsulinKind,
    mealType: MealType,
    unit: GlucoseUnit,
): NewEventInput? = when (kind) {
    EntryKind.GLUCOSE -> Glucose.parse(value1, unit)?.let { NewEventInput.Glucose(it, context) }
    EntryKind.INSULIN -> EventInput.parseInsulinUnits(value1)?.let {
        NewEventInput.Insulin(it, insulinKind, value2.trim())
    }
    EntryKind.CARBS -> EventInput.parseCarbsGrams(value1)?.let {
        NewEventInput.Carbs(it, mealType)
    }
    EntryKind.WEIGHT -> EventInput.parseWeightKg(value1)?.let { NewEventInput.Weight(it) }
    EntryKind.BLOOD_PRESSURE -> {
        val systolic = value1.trim().toIntOrNull()
        val diastolic = value2.trim().toIntOrNull()
        val pulseText = value3.trim()
        val pulse = if (pulseText.isEmpty()) null else pulseText.toIntOrNull()
        val pulseValid = pulseText.isEmpty() || pulse != null
        if (systolic == null || diastolic == null || !pulseValid ||
            !EventInput.isValidBloodPressure(systolic, diastolic, pulse)
        ) {
            null
        } else {
            NewEventInput.BloodPressure(systolic, diastolic, pulse)
        }
    }
    EntryKind.KETONES -> EventInput.parseKetonesMmolL(value1)?.let { NewEventInput.Ketones(it) }
    EntryKind.HBA1C -> EventInput.parseHba1cPercent(value1)?.let {
        NewEventInput.Hba1c(it, EventInput.hba1cMmolMol(it))
    }
}

private fun errorText(kind: EntryKind, unit: GlucoseUnit): String = when (kind) {
    EntryKind.GLUCOSE ->
        "Введите значение в диапазоне ${Glucose.format(Glucose.MIN_MGDL, unit)}–" +
            "${Glucose.format(Glucose.MAX_MGDL, unit)} ${unit.title}"
    EntryKind.INSULIN -> "Введите дозу от 0,1 до 100 ЕД"
    EntryKind.CARBS -> "Введите углеводы от 0,1 до 1000 г"
    EntryKind.WEIGHT -> "Введите вес от 20 до 400 кг"
    EntryKind.BLOOD_PRESSURE -> "Проверьте давление: систола выше диастолы, пульс 30–250"
    EntryKind.KETONES -> "Введите кетоны от 0,1 до 20 ммоль/л"
    EntryKind.HBA1C -> "Введите HbA1c от 3 до 20 %"
}
