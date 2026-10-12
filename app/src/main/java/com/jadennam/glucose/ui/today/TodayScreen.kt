package com.jadennam.glucose.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.MealType
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.UnitSystem
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import com.jadennam.glucose.domain.units.UnitConverter
import com.jadennam.glucose.notify.Notifier
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.MainViewModel
import com.jadennam.glucose.ui.components.ChipGroup
import com.jadennam.glucose.ui.components.DateField
import com.jadennam.glucose.ui.components.GradientButton
import com.jadennam.glucose.ui.components.HSpace
import com.jadennam.glucose.ui.components.Hint
import com.jadennam.glucose.ui.components.LevelDot
import com.jadennam.glucose.ui.components.SectionCard
import com.jadennam.glucose.ui.components.TimePickerDialog
import com.jadennam.glucose.ui.health.SamsungHealthTodayCard
import com.jadennam.glucose.ui.theme.LevelColors
import java.time.Instant
import java.time.LocalDate

private val DURATIONS = listOf(15, 30, 45, 60, 90, 120, 180, 240)

@Composable
fun TodayScreen(vm: MainViewModel, profile: Profile, focus: String?, consumeFocus: () -> Unit) {
    val today by vm.todayDate.collectAsState()
    val records by vm.todayRecords.collectAsState()
    val settings by vm.settings.collectAsState()
    val next by vm.nextAlarm.collectAsState()
    val glucoseFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()

    LaunchedEffect(focus) {
        when (focus) {
            Notifier.OPEN_GLUCOSE -> runCatching { glucoseFocus.requestFocus() }
            Notifier.OPEN_EXERCISE, Notifier.OPEN_MEAL -> scroll.animateScrollTo(scroll.maxValue / 2)
        }
        if (focus != null) consumeFocus()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(Labels.date(today), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Hint(next?.let { "다음 알림: " + Labels.dateTime(it.triggerAt, vm.zone()) } ?: "예정된 알림이 없습니다 (설정에서 측정 스케줄을 켜세요)")

        GlucoseCard(vm, profile, glucoseFocus)
        MealCard(vm)
        ExerciseCard(vm)
        WeightCard(vm, profile.unitSystem)
        SamsungHealthTodayCard(vm, profile.unitSystem)
        MedicationCard(vm, today)

        SectionCard("오늘 기록") {
            if (records.readings.isEmpty() && records.meals.isEmpty() && records.exercises.isEmpty()) Hint("아직 기록이 없습니다.")
            records.readings.forEach { r ->
                val level = GlucoseRangeClassifier.classify(r.valueMgDl, r.context, settings.ranges)
                RecordRow(
                    "${Labels.time(r.measuredAt, r.zoneId)}  ${UnitConverter.formatGlucose(r.valueMgDl, profile.glucoseUnit)} ${Labels.glucoseUnit(profile.glucoseUnit)} · ${Labels.contextOf(r)}",
                    dot = LevelColors.of(level),
                ) { vm.deleteReading(r.id) }
            }
            records.meals.forEach { m ->
                RecordRow("${Labels.time(m.eatenAt, m.zoneId)}  🍚 ${m.types.joinToString(", ") { Labels.meal(it) }}${m.note?.let { " · $it" } ?: ""}") { vm.deleteMeal(m.id) }
            }
            records.exercises.forEach { e ->
                RecordRow("${Labels.time(e.performedAt, e.zoneId)}  🏃 ${Labels.exercise(e.type)} ${e.durationMinutes}분") { vm.deleteExercise(e.id) }
            }
        }
    }
}

@Composable
private fun RecordRow(text: String, dot: androidx.compose.ui.graphics.Color? = null, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { LevelDot(dot); HSpace() }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { if (confirm) onDelete() else confirm = true }) { Text(if (confirm) "삭제 확인" else "삭제") }
    }
}

/**
 * Date + time of a record. Both null = "now". Picking only a date keeps the current time of day.
 * Values are epochDay / minute-of-day so they survive configuration changes.
 */
@Composable
private fun WhenChooser(vm: MainViewModel, epochDay: Long?, minute: Int?, onChange: (Long?, Int?) -> Unit) {
    var openTime by remember { mutableStateOf(false) }
    val nowZoned = Instant.ofEpochMilli(vm.now()).atZone(vm.zone())
    val nowMinute = nowZoned.hour * 60 + nowZoned.minute
    val date = epochDay?.let(LocalDate::ofEpochDay)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DateField("날짜", date ?: nowZoned.toLocalDate(), { d -> onChange(d.toEpochDay(), minute ?: nowMinute) }, Modifier.weight(1.3f))
        OutlinedButton(onClick = { openTime = true }, modifier = Modifier.weight(1f)) {
            Text(if (minute == null) "지금" else Labels.minute(minute))
        }
    }
    if (epochDay != null || minute != null) {
        TextButton(onClick = { onChange(null, null) }) { Text("지금으로 되돌리기") }
    }
    if (isFuture(vm, epochDay, minute)) Hint("⚠️ 미래 시각은 기록할 수 없습니다.")
    if (openTime) {
        TimePickerDialog(minute ?: nowMinute, { openTime = false }) { m -> onChange(epochDay, m); openTime = false }
    }
}

private fun epochAt(vm: MainViewModel, epochDay: Long?, minute: Int?): Long {
    if (epochDay == null && minute == null) return vm.now()
    val now = Instant.ofEpochMilli(vm.now()).atZone(vm.zone())
    val d = epochDay?.let(LocalDate::ofEpochDay) ?: now.toLocalDate()
    val m = minute ?: (now.hour * 60 + now.minute)
    return d.atTime(m / 60, m % 60).atZone(vm.zone()).toInstant().toEpochMilli()
}

private fun isFuture(vm: MainViewModel, epochDay: Long?, minute: Int?) = epochAt(vm, epochDay, minute) > vm.now() + 60_000

@Composable
private fun GlucoseCard(vm: MainViewModel, profile: Profile, focus: FocusRequester) {
    var value by rememberSaveable { mutableStateOf("") }
    var context by rememberSaveable { mutableStateOf(MeasureContext.FASTING) }
    var hours by rememberSaveable { mutableIntStateOf(2) }
    var day by rememberSaveable { mutableStateOf<Long?>(null) }
    var minute by rememberSaveable { mutableStateOf<Int?>(null) }
    val parsed = UnitConverter.parseGlucose(value, profile.glucoseUnit)

    SectionCard("🩸 혈당 기록") {
        OutlinedTextField(
            value, { value = it },
            label = { Text("측정값 (${Labels.glucoseUnit(profile.glucoseUnit)})") },
            singleLine = true,
            isError = value.isNotBlank() && parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        ChipGroup(MeasureContext.entries, Labels::context, { it == context }) { context = it }
        if (context == MeasureContext.AFTER_MEAL) {
            ChipGroup(listOf(1, 2, 3), { "${it}시간" }, { it == hours }) { hours = it }
        }
        WhenChooser(vm, day, minute) { d, m -> day = d; minute = m }
        GradientButton("저장", {
            parsed?.let {
                vm.addReading(it, context, if (context == MeasureContext.AFTER_MEAL) hours else null, epochAt(vm, day, minute))
                value = ""; day = null; minute = null
            }
        }, Modifier.fillMaxWidth(), enabled = parsed != null && !isFuture(vm, day, minute))
    }
}

@Composable
private fun MealCard(vm: MainViewModel) {
    var selected by rememberSaveable { mutableStateOf(listOf<MealType>()) }
    var note by rememberSaveable { mutableStateOf("") }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var day by rememberSaveable { mutableStateOf<Long?>(null) }
    var minute by rememberSaveable { mutableStateOf<Int?>(null) }

    SectionCard("🍚 식사 기록") {
        Hint("먹은 종류를 모두 누르고 저장하세요. 시각은 식사를 마친 때입니다.")
        ChipGroup(MealType.entries, Labels::meal, { it in selected }) { t ->
            selected = if (t in selected) selected - t else selected + t
        }
        if (showNote) {
            OutlinedTextField(note, { note = it }, label = { Text("직접 입력 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            TextButton(onClick = { showNote = true }) { Text("+ 직접 입력") }
        }
        WhenChooser(vm, day, minute) { d, m -> day = d; minute = m }
        GradientButton("저장", {
            vm.addMeal(selected, note, epochAt(vm, day, minute))
            selected = emptyList(); note = ""; showNote = false; day = null; minute = null
        }, Modifier.fillMaxWidth(), enabled = (selected.isNotEmpty() || note.isNotBlank()) && !isFuture(vm, day, minute))
    }
}

private const val MAX_EXERCISE_MINUTES = 600

private fun durationLabel(m: Int): String = when {
    m < 60 -> "${m}분"
    m % 60 == 0 -> "${m / 60}시간"
    else -> "${m / 60}시간 ${m % 60}분"
}

@Composable
private fun ExerciseCard(vm: MainViewModel) {
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var manual by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf<Long?>(null) }
    var minute by rememberSaveable { mutableStateOf<Int?>(null) }
    val manualMinutes = manual.trim().toIntOrNull()?.takeIf { it in 1..MAX_EXERCISE_MINUTES }
    val future = isFuture(vm, day, minute)

    fun save(m: Int) {
        vm.addExercise(type!!, m, epochAt(vm, day, minute))
        type = null; manual = ""; day = null; minute = null
    }

    SectionCard("🏃 운동 기록") {
        Hint("종류를 누른 뒤 시간을 누르면 바로 저장됩니다. 다른 날짜·시각은 먼저 바꾸세요.")
        ChipGroup(ExerciseType.entries, Labels::exercise, { it == type }) { type = it }
        if (type != null) {
            WhenChooser(vm, day, minute) { d, m -> day = d; minute = m }
            ChipGroup(DURATIONS, ::durationLabel, { false }) { m -> if (!future) save(m) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    manual, { manual = it.filter(Char::isDigit).take(3) },
                    label = { Text("직접 입력 (분, 최대 $MAX_EXERCISE_MINUTES)") },
                    singleLine = true,
                    isError = manual.isNotBlank() && manualMinutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                HSpace()
                OutlinedButton(onClick = { manualMinutes?.let(::save) }, enabled = manualMinutes != null && !future) { Text("저장") }
            }
        }
    }
}

@Composable
private fun WeightCard(vm: MainViewModel, system: UnitSystem) {
    var value by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf<Long?>(null) }
    var minute by rememberSaveable { mutableStateOf<Int?>(null) }
    val weights by vm.weights.collectAsState()
    val kg = value.replace(',', '.').toDoubleOrNull()?.let { if (system == UnitSystem.US) UnitConverter.lbToKg(it) else it }?.takeIf { it in 20.0..300.0 }
    SectionCard("⚖️ 몸무게") {
        weights.lastOrNull()?.let { Hint("최근: ${Labels.weight(it.weightKg, system)} (${Labels.dateTime(it.measuredAt, vm.zone())})") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value, { value = it },
                label = { Text(if (system == UnitSystem.US) "lb" else "kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            HSpace()
            OutlinedButton(
                onClick = { kg?.let { vm.addWeight(it, epochAt(vm, day, minute)); value = ""; day = null; minute = null } },
                enabled = kg != null && !isFuture(vm, day, minute),
            ) { Text("저장") }
        }
        WhenChooser(vm, day, minute) { d, m -> day = d; minute = m }
    }
}
