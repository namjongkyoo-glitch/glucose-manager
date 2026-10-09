package com.jadennam.glucose.ui.summary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.range.GlucoseLevel
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import com.jadennam.glucose.domain.trend.TrendBucket
import com.jadennam.glucose.domain.units.UnitConverter
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.MainViewModel
import com.jadennam.glucose.ui.MainViewModel.SummaryPeriod
import com.jadennam.glucose.ui.components.HSpace
import com.jadennam.glucose.ui.components.Hint
import com.jadennam.glucose.ui.components.LevelDot
import com.jadennam.glucose.ui.components.SectionCard
import com.jadennam.glucose.ui.components.Segmented
import com.jadennam.glucose.ui.health.HealthSummaryLines
import com.jadennam.glucose.ui.theme.LevelColors
import com.jadennam.glucose.ui.today.MedText
import kotlin.math.roundToInt

@Composable
fun SummaryScreen(vm: MainViewModel, profile: Profile) {
    val state by vm.summary.collectAsState()
    val settings by vm.settings.collectAsState()
    val health by vm.healthStatus.collectAsState()
    val unit = profile.glucoseUnit
    fun g(v: Int) = UnitConverter.formatGlucose(v, unit)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("${profile.name}님 요약", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Segmented(
            SummaryPeriod.entries,
            state?.period ?: SummaryPeriod.YESTERDAY,
            { if (it == SummaryPeriod.YESTERDAY) "어제" else "최근 7일" },
            vm::setSummaryPeriod,
        )
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Hint(
            if (s.period == SummaryPeriod.YESTERDAY) Labels.date(s.start)
            else "${Labels.date(s.start)} ~ ${Labels.date(s.endExclusive.minusDays(1))}",
        )

        SectionCard("🩸 혈당 (${Labels.glucoseUnit(unit)})") {
            val gl = s.glucose
            if (gl.count == 0) {
                Hint("이 기간에 측정 기록이 없습니다.")
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("평균", g(gl.avgMgDl!!))
                    Stat("최저~최고", "${g(gl.minMgDl!!)}~${g(gl.maxMgDl!!)}")
                    Stat("측정", "${gl.count}회")
                }
                LevelBar(gl.levelPercents)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlucoseLevel.entries.forEach { l ->
                        LevelDot(LevelColors.of(l)); HSpace(4)
                        Text("${Labels.level(l)} ${gl.levelPercents.getValue(l)}%", style = MaterialTheme.typography.labelSmall); HSpace(10)
                    }
                }
                Text("측정 상황별 평균", style = MaterialTheme.typography.labelLarge)
                MeasureContext.entries.filter { it in gl.avgByContext }.forEach { c ->
                    val v = gl.avgByContext.getValue(c)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelDot(LevelColors.of(GlucoseRangeClassifier.classify(v, c, settings.ranges))); HSpace()
                        Text("${Labels.context(c)}  ${g(v)}")
                    }
                }
            }
            if (s.period == SummaryPeriod.LAST_7_DAYS) {
                Text("날짜별 평균", style = MaterialTheme.typography.labelLarge)
                DailyBars(s.daily, settings.ranges, unit)
            }
        }

        SectionCard("🍚 식사 · 🏃 운동 (앱 기록)") {
            Text("식사 기록 ${s.mealCount}회")
            Text("운동 ${s.exerciseCount}회" + if (s.exerciseMinutes > 0) " · 총 ${s.exerciseMinutes}분" else "")
        }

        SectionCard("💊 복용 약") {
            if (s.activeMedications.isEmpty()) Hint("이 기간에 복용한 약 기록이 없습니다.")
            s.activeMedications.forEach { m -> Text("${m.name} — ${MedText.dose(m)}") }
        }

        SectionCard("📱 삼성 헬스") {
            val h = s.health
            when {
                !health.connected -> Hint("연결되지 않았습니다. 설정 → 삼성 헬스 연결에서 권한을 허용하세요.")
                h == null -> Hint("데이터를 읽지 못했습니다.")
                else -> HealthSummaryLines(h, profile.unitSystem, vm, singleDay = s.period == SummaryPeriod.YESTERDAY)
            }
        }
        Hint("기록은 두 번째 탭 '기록'에서 입력합니다.")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stacked horizontal bar of level percentages. */
@Composable
private fun LevelBar(percents: Map<GlucoseLevel, Int>) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        var x = 0f
        GlucoseLevel.entries.forEach { l ->
            val w = size.width * percents.getValue(l) / 100f
            if (w > 0f) drawRect(LevelColors.of(l), Offset(x, 0f), Size(w, size.height))
            x += w
        }
    }
}

/** One vertical bar per day showing the daily average, colored by level. */
@Composable
private fun DailyBars(days: List<TrendBucket>, ranges: GlucoseRanges, unit: GlucoseUnit) {
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val empty = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        if (days.isEmpty()) return@Canvas
        val top = 16.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val maxV = maxOf(250f, days.mapNotNull { it.avgMgDl }.maxOrNull()?.toFloat() ?: 0f)
        val slot = size.width / days.size
        val barW = slot * 0.55f
        days.forEachIndexed { i, b ->
            val cx = slot * i + slot / 2
            val style = TextStyle(fontSize = 10.sp, color = label)
            val d = measurer.measure(Labels.shortDate(b.start), style)
            drawText(d, topLeft = Offset(cx - d.size.width / 2, bottom + 3.dp.toPx()))
            val avg = b.avgMgDl
            if (avg == null) {
                drawRoundRect(empty, Offset(cx - barW / 2, bottom - 4.dp.toPx()), Size(barW, 4.dp.toPx()), CornerRadius(4f))
            } else {
                val h = (bottom - top) * (avg.toFloat() / maxV)
                val color = LevelColors.of(GlucoseRangeClassifier.classify(avg.roundToInt(), MeasureContext.AFTER_MEAL, ranges))
                drawRoundRect(color, Offset(cx - barW / 2, bottom - h), Size(barW, h), CornerRadius(8f))
                val t = measurer.measure(UnitConverter.formatGlucose(avg.roundToInt(), unit), style)
                drawText(t, topLeft = Offset(cx - t.size.width / 2, bottom - h - t.size.height))
            }
        }
    }
}
