package com.jadennam.glucose.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import com.jadennam.glucose.domain.trend.TrendBucket
import com.jadennam.glucose.domain.units.UnitConverter
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.theme.LevelColors
import kotlin.math.roundToInt

val MedMarkerColor = Color(0xFF0EA5E9)

/**
 * Measured values only: average dot (range-colored), min–max whisker, target band and
 * medication start/end markers. Estimates are drawn separately and never mixed in here.
 */
@Composable
fun TrendChart(
    buckets: List<TrendBucket>,
    medications: List<Medication>,
    ranges: GlucoseRanges,
    unit: GlucoseUnit,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = LevelColors.InTarget.copy(alpha = 0.10f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val maxValue = buckets.mapNotNull { it.maxMgDl }.maxOrNull() ?: 0
    val yMin = 40f
    val yMax = maxOf(300, maxValue + 20).toFloat()

    // Medication markers: bucket index where a medication starts or ends.
    val markers: Set<Int> = buckets.indices.filter { i ->
        val b = buckets[i]
        medications.any { m ->
            (!m.startDate.isBefore(b.start) && m.startDate.isBefore(b.endExclusive)) ||
                (m.endDate != null && !m.endDate.isBefore(b.start) && m.endDate.isBefore(b.endExclusive))
        }
    }.toSet()

    Canvas(
        Modifier.fillMaxWidth().height(240.dp).pointerInput(buckets) {
            detectTapGestures { pos ->
                val left = 36.dp.toPx()
                val w = size.width - left - 8.dp.toPx()
                if (buckets.isNotEmpty() && pos.x >= left - 12.dp.toPx()) {
                    val step = w / buckets.size
                    onSelect(((pos.x - left) / step).toInt().coerceIn(0, buckets.lastIndex))
                }
            }
        },
    ) {
        val left = 36.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val w = size.width - left - 8.dp.toPx()
        val h = bottom - top
        fun y(v: Float) = bottom - (v - yMin) / (yMax - yMin) * h
        val step = if (buckets.isEmpty()) w else w / buckets.size
        fun x(i: Int) = left + step * (i + 0.5f)

        // Target band
        drawRect(bandColor, Offset(left, y(ranges.targetHigh.toFloat())), Size(w, y(ranges.low.toFloat()) - y(ranges.targetHigh.toFloat())))
        // Threshold lines + labels
        listOf(ranges.low to LevelColors.Low, ranges.targetHigh to LevelColors.High, ranges.veryHigh to LevelColors.VeryHigh).forEach { (v, c) ->
            val yy = y(v.toFloat())
            drawLine(c.copy(alpha = 0.5f), Offset(left, yy), Offset(left + w, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawText(measurer, UnitConverter.formatGlucose(v, unit), Offset(0f, yy - 7.sp.toPx()), labelStyle)
        }
        // Selection highlight
        selected?.takeIf { it in buckets.indices }?.let { i ->
            drawRect(lineColor.copy(alpha = 0.08f), Offset(left + step * i, top), Size(step, h))
        }
        // Medication markers
        markers.forEach { i ->
            drawLine(MedMarkerColor, Offset(x(i), top), Offset(x(i), bottom), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            drawText(measurer, "💊", Offset(x(i) - 6.dp.toPx(), top), TextStyle(fontSize = 10.sp))
        }
        // Average line
        val path = Path()
        var started = false
        buckets.forEachIndexed { i, b ->
            val avg = b.avgMgDl ?: return@forEachIndexed
            if (!started) { path.moveTo(x(i), y(avg.toFloat())); started = true } else path.lineTo(x(i), y(avg.toFloat()))
        }
        drawPath(path, lineColor.copy(alpha = 0.6f), style = Stroke(2.dp.toPx()))
        // Whiskers + dots
        buckets.forEachIndexed { i, b ->
            val avg = b.avgMgDl ?: return@forEachIndexed
            if (b.minMgDl != null && b.maxMgDl != null && b.maxMgDl > b.minMgDl) {
                drawLine(axisColor.copy(alpha = 0.5f), Offset(x(i), y(b.minMgDl.toFloat())), Offset(x(i), y(b.maxMgDl.toFloat())), 2.dp.toPx())
            }
            val level = GlucoseRangeClassifier.classify(avg.roundToInt(), MeasureContext.AFTER_MEAL, ranges)
            drawCircle(LevelColors.of(level), radius = if (i == selected) 6.dp.toPx() else 4.dp.toPx(), center = Offset(x(i), y(avg.toFloat())))
        }
        // X labels: first, middle, last
        if (buckets.isNotEmpty()) {
            listOf(0, buckets.size / 2, buckets.lastIndex).distinct().forEach { i ->
                val t = measurer.measure(Labels.shortDate(buckets[i].start), labelStyle)
                drawText(t, topLeft = Offset((x(i) - t.size.width / 2).coerceIn(left, size.width - t.size.width), bottom + 4.dp.toPx()))
            }
        }
    }
}
