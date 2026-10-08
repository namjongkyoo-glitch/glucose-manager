@file:OptIn(ExperimentalMaterial3Api::class)

package com.jadennam.glucose.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.UnitSystem
import com.jadennam.glucose.domain.units.UnitConverter
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.MainViewModel
import com.jadennam.glucose.ui.components.GradientButton
import com.jadennam.glucose.ui.components.Hint
import com.jadennam.glucose.ui.components.PermissionPanel
import com.jadennam.glucose.ui.components.SectionCard
import com.jadennam.glucose.ui.components.Segmented
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var birthEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var unitSystem by rememberSaveable { mutableStateOf(UnitSystem.METRIC) }
    var glucoseUnit by rememberSaveable { mutableStateOf(GlucoseUnit.MG_DL) }
    var heightCm by rememberSaveable { mutableStateOf("") }
    var heightFt by rememberSaveable { mutableStateOf("") }
    var heightIn by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var showDate by rememberSaveable { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.restoreBackup(it) }
    }

    fun parsedHeight(): Double? = when (unitSystem) {
        UnitSystem.METRIC -> heightCm.toDoubleOrNull()
        UnitSystem.US -> heightFt.toIntOrNull()?.let { ft -> UnitConverter.feetInchesToCm(ft, heightIn.toDoubleOrNull() ?: 0.0) }
    }?.takeIf { it in 50.0..250.0 }

    fun parsedWeightKg(): Double? = weight.replace(',', '.').toDoubleOrNull()?.let {
        if (unitSystem == UnitSystem.US) UnitConverter.lbToKg(it) else it
    }?.takeIf { it in 20.0..300.0 }

    val formValid = name.isNotBlank() && birthEpochDay != null && parsedHeight() != null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("혈당 관리", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        when (step) {
            0 -> {
                Text("손가락 채혈 측정 일정, 기록, 식사·운동·체중·복용 약, 추세를 한곳에서 관리합니다.")
                Hint("모든 기록은 이 기기 안에만 저장됩니다.")
                GradientButton("처음 시작하기", { step = 1 }, Modifier.fillMaxWidth())
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("기존 백업에서 복원")
                }
                Hint("앱을 다시 설치했다면 이전에 저장한 백업 파일(.json)을 선택하세요.")
            }
            1 -> {
                SectionCard("기본 정보") {
                    OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(birthEpochDay?.let { "생년월일: " + LocalDate.ofEpochDay(it) } ?: "생년월일 선택")
                    }
                }
                SectionCard("단위") {
                    Segmented(UnitSystem.entries, unitSystem, Labels::unitSystem, { unitSystem = it })
                    Segmented(GlucoseUnit.entries, glucoseUnit, Labels::glucoseUnit, { glucoseUnit = it })
                    Hint("혈당 단위는 표시용입니다. 나중에 설정에서 바꿀 수 있습니다.")
                }
                SectionCard("키·몸무게") {
                    if (unitSystem == UnitSystem.METRIC) {
                        OutlinedTextField(heightCm, { heightCm = it }, label = { Text("키 (cm)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(heightFt, { heightFt = it }, label = { Text("ft") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(heightIn, { heightIn = it }, label = { Text("in") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }
                    }
                    OutlinedTextField(weight, { weight = it }, label = { Text(if (unitSystem == UnitSystem.US) "몸무게 (lb)" else "몸무게 (kg)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
                GradientButton("다음", { step = 2 }, Modifier.fillMaxWidth(), enabled = formValid)
                TextButton(onClick = { step = 0 }) { Text("이전") }
            }
            else -> {
                SectionCard("알림이 제때 오도록 설정") {
                    PermissionPanel(vm)
                }
                GradientButton("완료", {
                    val profile = Profile(name.trim(), LocalDate.ofEpochDay(birthEpochDay!!), parsedHeight()!!, unitSystem, glucoseUnit)
                    vm.completeOnboarding(profile, parsedWeightKg())
                }, Modifier.fillMaxWidth())
                TextButton(onClick = { step = 1 }) { Text("이전") }
            }
        }
    }

    if (showDate) {
        val initial = (birthEpochDay ?: LocalDate.of(1970, 1, 1).toEpochDay()) * 86_400_000L
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        birthEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}
