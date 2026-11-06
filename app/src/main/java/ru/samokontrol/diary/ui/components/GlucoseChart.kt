package ru.samokontrol.diary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.samokontrol.diary.data.GlucoseEntry
import ru.samokontrol.diary.domain.Glucose
import ru.samokontrol.diary.domain.GlucoseUnit

private const val Y_MIN = 40f
private const val Y_MAX = 300f

@Composable
fun GlucoseChart(
    entries: List<GlucoseEntry>,
    unit: GlucoseUnit,
    days: Int,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val sorted = remember(entries) { entries.sortedBy { it.measuredAt } }
    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        val left = 46.dp.toPx()
        val right = 10.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = 26.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom

        fun yFor(mgdl: Float): Float =
            top + plotHeight * (1f - (mgdl - Y_MIN) / (Y_MAX - Y_MIN))

        fun xFor(time: Long): Float {
            val now = System.currentTimeMillis()
            val start = now - days * 24L * 60L * 60L * 1000L
            val fraction = ((time - start).toFloat() / (now - start).toFloat()).coerceIn(0f, 1f)
            return left + plotWidth * fraction
        }

        // Целевая зона 70–180 mg/dL.
        val bandTop = yFor(HIGH_THRESHOLD.toFloat())
        val bandBottom = yFor(LOW_THRESHOLD.toFloat())
        drawRect(
            color = bandColor,
            topLeft = Offset(left, bandTop),
            size = Size(plotWidth, bandBottom - bandTop),
        )

        // Горизонтальная сетка.
        listOf(70f, 180f, 300f).forEach { value ->
            val y = yFor(value)
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(left + plotWidth, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        textPaint.color = labelColor.toArgb()
        textPaint.textSize = 10.sp.toPx()
        drawIntoCanvas { canvas ->
            listOf(300, 180, 70, 40).forEach { value ->
                val y = yFor(value.toFloat()) + 4.dp.toPx()
                canvas.nativeCanvas.drawText(
                    Glucose.format(value, unit),
                    left - 8.dp.toPx(),
                    y,
                    textPaint,
                )
            }
            canvas.nativeCanvas.drawText(
                "−${days} дн.",
                left,
                size.height - 4.dp.toPx(),
                textPaint,
            )
            canvas.nativeCanvas.drawText(
                "сейчас",
                left + plotWidth - 42.dp.toPx(),
                size.height - 4.dp.toPx(),
                textPaint,
            )
        }

        if (sorted.isEmpty()) return@Canvas

        val path = Path()
        sorted.forEachIndexed { index, entry ->
            val x = xFor(entry.measuredAt)
            val y = yFor(entry.mgdl.toFloat().coerceIn(Y_MIN, Y_MAX))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        sorted.forEach { entry ->
            val x = xFor(entry.measuredAt)
            val y = yFor(entry.mgdl.toFloat().coerceIn(Y_MIN, Y_MAX))
            val pointColor = when {
                entry.mgdl < LOW_THRESHOLD -> Color(0xFFB3261E)
                entry.mgdl > HIGH_THRESHOLD -> Color(0xFFB45309)
                else -> lineColor
            }
            drawCircle(color = pointColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}

private const val LOW_THRESHOLD = 70
private const val HIGH_THRESHOLD = 180