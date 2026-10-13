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
import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import com.jadennam.glucose.domain.trend.BucketActivity
import com.jadennam.glucose.domain.trend.TrendBucket
import com.jadennam.glucose.domain.units.UnitConverter
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.MainViewModel.TrendMarks
import com.jadennam.glucose.ui.theme.LevelColors
import kotlin.math.roundToInt

val MedMarkColor = Color(0xFF0EA5E9)
val MealMarkColor = Color(0xFFF59E0B)
val ExerciseMarkColor = Color(0xFF10B981)

fun exerciseIcon(t: ExerciseType): String = when (t) {
    ExerciseType.JOGGING -> "🏃"
    ExerciseType.WALKING -> "🚶"
    ExerciseType.SWIMMING -> "🏊"
    ExerciseType.GOLF -> "⛳"
    ExerciseType.GYM -> "🏋️"
    ExerciseType.OTHER -> "🤸"
}

/**
 * Measured values only: average dot (range-colored), min–max whisker and target band.
 * Below the date axis, optional marker rows show medication / meal / exercise per bucket;
 * narrow slots (month/year) use small colored dots instead of emoji.
 */
@Composable
fun TrendChart(
    buckets: List<TrendBucket>,
    activity: List<BucketActivity>,
    marks: TrendMarks,
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
    val rows = listOfNotNull(
        if (marks.meds) 0 else null,
        if (marks.meals) 1 else null,
        if (marks.exercise) 2 else null,
    )
    val rowH = 16.dp
    val plotH = 240.dp

    Canvas(
        Modifier.fillMaxWidth().height(plotH + rowH * rows.size).pointerInput(buckets) {
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
        val bottom = plotH.toPx() - 20.dp.toPx()
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
        // Selection highlight (covers marker rows too)
        selected?.takeIf { it in buckets.indices }?.let { i ->
            drawRect(lineColor.copy(alpha = 0.08f), Offset(left + step * i, top), Size(step, size.height - top))
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
        // Marker rows
        val emoji = step >= 14.dp.toPx()
        val iconStyle = TextStyle(fontSize = 10.sp)
        rows.forEachIndexed { r, kind ->
            val cy = plotH.toPx() + rowH.toPx() * r + rowH.toPx() / 2
            val (label, color) = when (kind) {
                0 -> "💊" to MedMarkColor
                1 -> "🍚" to MealMarkColor
                else -> "🏃" to ExerciseMarkColor
            }
            val l = measurer.measure(label, iconStyle)
            drawText(l, topLeft = Offset(left - l.size.width - 6.dp.toPx(), cy - l.size.height / 2))
            activity.forEachIndexed { i, a ->
                if (i > buckets.lastIndex) return@forEachIndexed
                val icon: String? = when (kind) {
                    0 -> if (a.medicationCount > 0) "💊" else null
                    1 -> if (a.mealCount > 0) "🍚" else null
                    else -> a.mainExercise?.let(::exerciseIcon)
                }
                if (icon == null) return@forEachIndexed
                if (emoji) {
                    val t = measurer.measure(icon, iconStyle)
                    drawText(t, topLeft = Offset(x(i) - t.size.width / 2, cy - t.size.height / 2))
                } else {
                    drawCircle(color, radius = minOf(step * 0.35f, 3.dp.toPx()), center = Offset(x(i), cy))
                }
            }
        }
    }
}
