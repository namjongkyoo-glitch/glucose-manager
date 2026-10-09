@file:OptIn(ExperimentalMaterial3Api::class)

package com.jadennam.glucose.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadennam.glucose.data.PeriodDetail
import com.jadennam.glucose.domain.estimation.Estimate
import com.jadennam.glucose.domain.health.HealthPeriodSummary
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import com.jadennam.glucose.domain.trend.TrendBucket
import com.jadennam.glucose.domain.trend.TrendPeriod
import com.jadennam.glucose.domain.units.UnitConverter
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.MainViewModel
import com.jadennam.glucose.ui.components.DisclaimerBanner
import com.jadennam.glucose.ui.components.HSpace
import com.jadennam.glucose.ui.components.Hint
import com.jadennam.glucose.ui.components.LevelDot
import com.jadennam.glucose.ui.components.SectionCard
import com.jadennam.glucose.ui.components.Segmented
import com.jadennam.glucose.ui.health.HealthSummaryLines
import com.jadennam.glucose.ui.theme.LevelColors
import com.jadennam.glucose.ui.today.MedText
import java.util.Locale

@Composable
fun TrendScreen(vm: MainViewModel, profile: Profile) {
    val trend by vm.trend.collectAsState()
    val settings by vm.settings.collectAsState()
    val estimates by vm.estimates.collectAsState()
    var selected by remember(trend.period) { mutableStateOf<Int?>(null) }
    var detail by remember { mutableStateOf<Pair<TrendBucket, PeriodDetail>?>(null) }
    var health by remember { mutableStateOf<HealthPeriodSummary?>(null) }
    val healthStatus by vm.healthStatus.collectAsState()
    val unit = profile.glucoseUnit

    LaunchedEffect(selected, trend.buckets) {
        val b = selected?.let { trend.buckets.getOrNull(it) }
        health = b?.let { vm.healthSummary(it.start, it.endExclusive) }
        detail = b?.let { it to vm.periodDetail(it) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("추세", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Segmented(TrendPeriod.entries, trend.period, { when (it) { TrendPeriod.WEEK -> "주간"; TrendPeriod.MONTH -> "월간"; TrendPeriod.YEAR -> "연간" } }, vm::setPeriod)

        SectionCard("실측 혈당 (${Labels.glucoseUnit(unit)})") {
            TrendChart(trend.buckets, trend.medications, settings.ranges, unit, selected) { selected = it }
            Legend()
            Hint(if (trend.period == TrendPeriod.YEAR) "점 = 주 평균, 세로선 = 최저~최고. 점을 누르면 그 주의 상세가 나옵니다." else "점 = 일 평균, 세로선 = 최저~최고. 점을 누르면 그날의 상세가 나옵니다.")
            val counted = trend.buckets.filter { it.count > 0 }
            if (counted.isNotEmpty()) {
                val avg = counted.sumOf { it.avgMgDl!! * it.count } / counted.sumOf { it.count }
                Text("기간 평균 ${UnitConverter.formatGlucose(Math.round(avg).toInt(), unit)} · 측정 ${counted.sumOf { it.count }}회", fontWeight = FontWeight.SemiBold)
            }
        }

        EstimateCard(estimates, unit, settings.ranges)
    }

    detail?.let { (bucket, d) ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            DetailSheet(vm, bucket, d, profile, settings.ranges, health, healthStatus.connected)
        }
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(LevelColors.Low to "낮음", LevelColors.InTarget to "목표", LevelColors.High to "높음", LevelColors.VeryHigh to "매우 높음", MedMarkerColor to "약 시작·종료")
            .forEach { (c, t) -> LevelDot(c); HSpace(4); Text(t, style = MaterialTheme.typography.labelSmall); HSpace(10) }
    }
}

@Composable
private fun DetailSheet(
    vm: MainViewModel, b: TrendBucket, d: PeriodDetail, profile: Profile, ranges: GlucoseRanges,
    health: HealthPeriodSummary?, healthConnected: Boolean,
) {
    val unit = profile.glucoseUnit
    val title = if (b.endExclusive == b.start.plusDays(1)) Labels.date(b.start) else "${Labels.date(b.start)} ~ ${Labels.date(b.endExclusive.minusDays(1))}"
    val meds by vm.medications.collectAsState()
    val activeMeds = meds.filter { it.overlaps(b.start, b.endExclusive) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("측정 ${d.readings.size}회" + (b.avgMgDl?.let { " · 평균 ${UnitConverter.formatGlucose(Math.round(it).toInt(), unit)} ${Labels.glucoseUnit(unit)}" } ?: ""))
        d.weight?.let { Text("몸무게: ${Labels.weight(it.weightKg, profile.unitSystem)} (${Labels.dateTime(it.measuredAt, vm.zone())} 기록)") }
        HorizontalDivider()
        Text("🩸 측정값", fontWeight = FontWeight.SemiBold)
        if (d.readings.isEmpty()) Hint("없음")
        d.readings.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelDot(LevelColors.of(GlucoseRangeClassifier.classify(r.valueMgDl, r.context, ranges))); HSpace()
                Text("${Labels.dateTime(r.measuredAt, vm.zone())}  ${UnitConverter.formatGlucose(r.valueMgDl, unit)} · ${Labels.contextOf(r)}")
            }
        }
        Text("🍚 식사", fontWeight = FontWeight.SemiBold)
        if (d.meals.isEmpty()) Hint("없음")
        d.meals.forEach { m -> Text("${Labels.dateTime(m.eatenAt, vm.zone())}  ${m.types.joinToString(", ") { Labels.meal(it) }}${m.note?.let { " · $it" } ?: ""}") }
        Text("🏃 운동", fontWeight = FontWeight.SemiBold)
        if (d.exercises.isEmpty()) Hint("없음")
        d.exercises.forEach { e -> Text("${Labels.dateTime(e.performedAt, vm.zone())}  ${Labels.exercise(e.type)} ${e.durationMinutes}분") }
        Text("💊 복용 약", fontWeight = FontWeight.SemiBold)
        if (activeMeds.isEmpty()) Hint("없음")
        activeMeds.forEach { m -> Text("${m.name} — ${MedText.dose(m)} (${MedText.period(m)})") }
        HorizontalDivider()
        Text("📱 삼성 헬스", fontWeight = FontWeight.SemiBold)
        when {
            !healthConnected -> Hint("연결되지 않음 (설정 → 삼성 헬스 연결)")
            health == null -> Hint("데이터 없음")
            else -> HealthSummaryLines(health, profile.unitSystem, vm, singleDay = b.endExclusive == b.start.plusDays(1))
        }
    }
}

/** Estimates: dashed outline + translucent band, visually distinct from measured dots. */
@Composable
private fun EstimateCard(estimates: Map<MeasureContext, Estimate>, unit: GlucoseUnit, ranges: GlucoseRanges) {
    SectionCard("예상 범위 (추정)") {
        DisclaimerBanner()
        Hint("최근 기록으로 계산한 다음 측정의 95% 예측구간입니다. 실측값이 아닙니다.")
        val available = estimates.values.filterIsInstance<Estimate.Available>()
        if (available.isEmpty()) {
            Text("데이터 부족", fontWeight = FontWeight.SemiBold)
            Hint("측정 기록이 14일 이상, 20회 이상 쌓이면 표시됩니다.")
        }
        MeasureContext.entries.forEach { ctx ->
            when (val e = estimates[ctx]) {
                is Estimate.Available -> EstimateRow(e, unit, ranges)
                else -> if (available.isNotEmpty()) Text("${Labels.context(ctx)}: 데이터 부족", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EstimateRow(e: Estimate.Available, unit: GlucoseUnit, ranges: GlucoseRanges) {
    val fmt = { v: Int -> UnitConverter.formatGlucose(v, unit) }
    val band = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Column {
        Text(
            "${Labels.context(e.context)}  예상 ${fmt(e.pointMgDl)} (${fmt(e.lowerMgDl)}–${fmt(e.upperMgDl)}) ${Labels.glucoseUnit(unit)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val lo = 40f; val hi = 320f
            fun x(v: Int) = ((v.coerceIn(lo.toInt(), hi.toInt()) - lo) / (hi - lo)) * size.width
            drawRoundRect(outline.copy(alpha = 0.25f), Offset(0f, size.height / 2 - 2), Size(size.width, 4f), CornerRadius(2f))
            drawRect(LevelColors.InTarget.copy(alpha = 0.25f), Offset(x(ranges.low), size.height / 2 - 2), Size(x(ranges.targetHigh) - x(ranges.low), 4f))
            val left = x(e.lowerMgDl); val right = x(e.upperMgDl)
            drawRoundRect(band.copy(alpha = 0.18f), Offset(left, 1f), Size(right - left, size.height - 2), CornerRadius(8f))
            drawRoundRect(band, Offset(left, 1f), Size(right - left, size.height - 2), CornerRadius(8f),
                style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            drawLine(band, Offset(x(e.pointMgDl), 0f), Offset(x(e.pointMgDl), size.height), 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
        }
        Text(String.format(Locale.KOREAN, "최근 %d회 기반", e.sampleCount), style = MaterialTheme.typography.labelSmall)
    }
}
