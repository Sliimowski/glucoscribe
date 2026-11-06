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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.samokontrol.diary.data.DiaryEvent
import ru.samokontrol.diary.domain.EventInput
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseMetrics
import ru.samokontrol.diary.domain.GlucoseUnit
import ru.samokontrol.diary.ui.theme.Amber
import ru.samokontrol.diary.ui.theme.Danger
import ru.samokontrol.diary.ui.theme.Teal700

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))

@Composable
fun DiaryScreen(
    events: List<DiaryEvent>,
    unit: GlucoseUnit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) {
        EmptyDiary(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.id }) { event ->
            EventCard(event = event, unit = unit, onDelete = { onDelete(event.id) })
        }
    }
}

@Composable
private fun EventCard(
    event: DiaryEvent,
    unit: GlucoseUnit,
    onDelete: () -> Unit,
) {
    val visual = eventVisual(event, unit)
    val time = remember(event.occurredAt) {
        dateFormat.format(Instant.ofEpochMilli(event.occurredAt).atZone(ZoneId.systemDefault()))
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
                    .background(visual.color.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = visual.primary,
                    color = visual.color,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(visual.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (event.note.isNotBlank()) {
                    Text(
                        text = event.note,
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

private data class EventVisual(
    val primary: String,
    val title: String,
    val color: Color,
)

private fun eventVisual(event: DiaryEvent, unit: GlucoseUnit): EventVisual = when (event) {
    is DiaryEvent.Glucose -> EventVisual(
        primary = Glucose.format(event.mgdl, unit),
        title = event.context.title,
        color = when {
            event.mgdl < GlucoseMetrics.DEFAULT_LOW -> Danger
            event.mgdl > GlucoseMetrics.DEFAULT_HIGH -> Amber
            else -> Teal700
        },
    )

    is DiaryEvent.Insulin -> EventVisual(
        primary = EventInput.formatInsulinUnits(event.unitsMilli),
        title = buildString {
            append(event.kind.title)
            if (event.insulinType.isNotBlank()) append(" · ${event.insulinType}")
        },
        color = Teal700,
    )

    is DiaryEvent.Carbs -> EventVisual(
        primary = EventInput.formatCarbs(event.gramsX10),
        title = event.mealType.title,
        color = Teal700,
    )

    is DiaryEvent.Weight -> EventVisual(
        primary = EventInput.formatWeight(event.weightG),
        title = "Вес",
        color = Teal700,
    )

    is DiaryEvent.BloodPressure -> EventVisual(
        primary = "${event.systolic}/${event.diastolic}",
        title = buildString {
            append("Давление")
            event.pulse?.let { append(" · пульс $it") }
        },
        color = Teal700,
    )

    is DiaryEvent.Ketones -> EventVisual(
        primary = event.mmolLX10?.let { EventInput.formatKetones(it) }
            ?: event.urineGrade?.let { "Моча: $it" }
            ?: "—",
        title = "Кетоны",
        color = when {
            (event.mmolLX10 ?: 0) >= 30 -> Danger
            else -> Teal700
        },
    )

    is DiaryEvent.Hba1c -> EventVisual(
        primary = EventInput.formatHba1c(event.percentX10),
        title = "HbA1c · ${event.mmolMol} ммоль/моль",
        color = Teal700,
    )
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
            text = "Пока нет записей",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Нажмите «+», чтобы добавить глюкозу, инсулин, еду, вес, давление, кетоны или HbA1c.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
