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

private val DURATIONS = listOf(15, 30, 45, 60, 90)

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

        GlucoseCard(vm, profile, today, glucoseFocus)
        MealCard(vm, today)
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

/** Minute-of-day picker that defaults to "now"; see [epochAt]. */
@Composable
private fun TimeChooser(vm: MainViewModel, minute: Int?, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (minute == null) "시각: 지금" else "시각: ${Labels.minute(minute)}", Modifier.weight(1f))
        TextButton(onClick = { open = true }) { Text("변경") }
        if (minute != null) TextButton(onClick = { onPick(null) }) { Text("지금으로") }
    }
    if (open) {
        val nowMinute = Instant.ofEpochMilli(vm.now()).atZone(vm.zone()).let { it.hour * 60 + it.minute }
        TimePickerDialog(minute ?: nowMinute, { open = false }) { onPick(it); open = false }
    }
}

private fun epochAt(vm: MainViewModel, date: LocalDate, minute: Int?): Long =
    if (minute == null) vm.now() else date.atTime(minute / 60, minute % 60).atZone(vm.zone()).toInstant().toEpochMilli()

@Composable
private fun GlucoseCard(vm: MainViewModel, profile: Profile, date: LocalDate, focus: FocusRequester) {
    var value by rememberSaveable { mutableStateOf("") }
    var context by rememberSaveable { mutableStateOf(MeasureContext.FASTING) }
    var hours by rememberSaveable { mutableIntStateOf(2) }
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
        TimeChooser(vm, minute) { minute = it }
        GradientButton("저장", {
            parsed?.let {
                vm.addReading(it, context, if (context == MeasureContext.AFTER_MEAL) hours else null, epochAt(vm, date, minute))
                value = ""; minute = null
            }
        }, Modifier.fillMaxWidth(), enabled = parsed != null)
    }
}

@Composable
private fun MealCard(vm: MainViewModel, date: LocalDate) {
    var selected by rememberSaveable { mutableStateOf(listOf<MealType>()) }
    var note by rememberSaveable { mutableStateOf("") }
    var showNote by rememberSaveable { mutableStateOf(false) }
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
        TimeChooser(vm, minute) { minute = it }
        GradientButton("저장", {
            vm.addMeal(selected, note, epochAt(vm, date, minute))
            selected = emptyList(); note = ""; showNote = false; minute = null
        }, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty() || note.isNotBlank())
    }
}

@Composable
private fun ExerciseCard(vm: MainViewModel) {
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    SectionCard("🏃 운동 기록") {
        Hint("종류를 누른 뒤 시간을 누르면 바로 저장됩니다.")
        ChipGroup(ExerciseType.entries, Labels::exercise, { it == type }) { type = it }
        if (type != null) {
            ChipGroup(DURATIONS, { "${it}분" }, { false }) { m ->
                vm.addExercise(type!!, m)
                type = null
            }
        }
    }
}

@Composable
private fun WeightCard(vm: MainViewModel, system: UnitSystem) {
    var value by rememberSaveable { mutableStateOf("") }
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
            OutlinedButton(onClick = { kg?.let { vm.addWeight(it); value = "" } }, enabled = kg != null) { Text("저장") }
        }
    }
}
