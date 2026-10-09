package com.jadennam.glucose.ui.settings

import androidx.activity.compose.LocalActivity
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jadennam.glucose.BuildConfig
import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.GlucoseRanges
import com.jadennam.glucose.domain.model.GlucoseUnit
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.ScheduleMode
import com.jadennam.glucose.domain.model.UnitSystem
import com.jadennam.glucose.domain.range.GlucoseRangeClassifier
import com.jadennam.glucose.domain.schedule.RandomScheduler
import com.jadennam.glucose.domain.units.UnitConverter
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.MainViewModel
import com.jadennam.glucose.ui.components.ChipGroup
import com.jadennam.glucose.ui.components.ConfirmDialog
import com.jadennam.glucose.ui.components.GradientButton
import com.jadennam.glucose.ui.components.Hint
import com.jadennam.glucose.ui.components.PermissionPanel
import com.jadennam.glucose.ui.components.SectionCard
import com.jadennam.glucose.ui.components.Segmented
import com.jadennam.glucose.ui.components.SwitchRow
import com.jadennam.glucose.ui.components.TimePickerDialog
import com.jadennam.glucose.ui.health.SamsungHealthSettings

@Composable
fun SettingsScreen(vm: MainViewModel, profile: Profile) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current

    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var uninstallDialog by remember { mutableStateOf(false) }
    var deleteAfterBackup by remember { mutableStateOf(false) }

    fun openUninstall() {
        context.startActivity(Intent(Intent.ACTION_DELETE, "package:${context.packageName}".toUri()))
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val thenDelete = deleteAfterBackup
            vm.writeBackup(uri) { if (thenDelete) openUninstall() }
        }
        deleteAfterBackup = false
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    fun save(s: AppSettings) = vm.saveSettings(s, toast = false)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        ProfileSection(vm, profile)
        ScheduleSection(settings, ::save)

        SectionCard("재알림·저녁 확인") {
            Text("측정 알림 후 기록이 없으면 다시 알림")
            ChipGroup(listOf(5, 10, 15, 30, 60), { "${it}분 후" }, { it == settings.remindDelayMinutes }) { save(settings.copy(remindDelayMinutes = it)) }
            SwitchRow("저녁 운동·식사 확인 알림", settings.exerciseCheckEnabled, { save(settings.copy(exerciseCheckEnabled = it)) },
                subtitle = "그날 기록이 없을 때만 알립니다")
            if (settings.exerciseCheckEnabled) {
                TimeButton("확인 시각", settings.exerciseCheckMinute) { save(settings.copy(exerciseCheckMinute = it)) }
            }
        }

        RangeSection(settings, profile.glucoseUnit) { vm.saveSettings(settings.copy(ranges = it)) }

        SectionCard("알림 안정성") { PermissionPanel(vm) }

        SamsungHealthSettings(vm)

        KakaoSection(vm, settings, ::save)

        SectionCard("백업·복원") {
            Hint("⚠️ 백업 파일에는 건강정보가 암호화되지 않은 평문(JSON)으로 저장됩니다. 파일을 다른 사람과 공유하지 마세요.")
            Hint("앱을 지우면 앱 안의 기록도 모두 지워집니다. Download·Documents 등 원하는 폴더에 백업해 두세요.")
            GradientButton("백업 파일 만들기", { backupLauncher.launch(vm.backupFileName()) }, Modifier.fillMaxWidth())
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("백업에서 복원")
            }
        }

        SectionCard("앱 삭제") {
            Hint("삭제하기 전에 백업을 권장합니다.")
            OutlinedButton(onClick = { uninstallDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("앱 삭제", color = MaterialTheme.colorScheme.error)
            }
        }

        Hint("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    restoreUri?.let { uri ->
        ConfirmDialog(
            "백업에서 복원",
            "현재 기기의 모든 기록과 설정이 백업 파일 내용으로 바뀝니다. 계속할까요?",
            "복원",
            onConfirm = { vm.restoreBackup(uri); restoreUri = null },
            onDismiss = { restoreUri = null },
        )
    }

    if (uninstallDialog) {
        AlertDialog(
            onDismissRequest = { uninstallDialog = false },
            title = { Text("앱을 삭제할까요?") },
            text = { Text("앱을 삭제하면 모든 혈당·식사·운동·몸무게·약 기록이 지워지고 되돌릴 수 없습니다. 먼저 백업 파일을 만드세요.") },
            confirmButton = {
                TextButton(onClick = {
                    uninstallDialog = false
                    deleteAfterBackup = true
                    backupLauncher.launch(vm.backupFileName())
                }) { Text("백업 후 삭제") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { uninstallDialog = false; openUninstall() }) { Text("그냥 삭제", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { uninstallDialog = false }) { Text("취소") }
                }
            },
        )
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { open = true }) { Text(Labels.minute(minute)) }
    }
    if (open) TimePickerDialog(minute, { open = false }) { onPick(it); open = false }
}

@Composable
private fun ProfileSection(vm: MainViewModel, profile: Profile) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var height by remember(profile) {
        mutableStateOf(if (profile.unitSystem == UnitSystem.US) String.format(java.util.Locale.US, "%.1f", profile.heightCm / UnitConverter.CM_PER_INCH) else String.format(java.util.Locale.US, "%.1f", profile.heightCm))
    }
    val heightCm = height.replace(',', '.').toDoubleOrNull()?.let { if (profile.unitSystem == UnitSystem.US) it * UnitConverter.CM_PER_INCH else it }?.takeIf { it in 50.0..250.0 }
    SectionCard("프로필") {
        Hint("생년월일: ${profile.birthDate} · 키: ${Labels.height(profile.heightCm, profile.unitSystem)}")
        OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            height, { height = it }, label = { Text(if (profile.unitSystem == UnitSystem.US) "키 (총 inch)" else "키 (cm)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        Text("단위 체계")
        Segmented(UnitSystem.entries, profile.unitSystem, Labels::unitSystem, { vm.saveProfile(profile.copy(unitSystem = it)) })
        Text("혈당 단위")
        Segmented(GlucoseUnit.entries, profile.glucoseUnit, Labels::glucoseUnit, { vm.saveProfile(profile.copy(glucoseUnit = it)) })
        OutlinedButton(
            onClick = { vm.saveProfile(profile.copy(name = name.trim(), heightCm = heightCm!!)) },
            enabled = name.isNotBlank() && heightCm != null,
        ) { Text("이름·키 저장") }
    }
}

@Composable
private fun ScheduleSection(s: AppSettings, save: (AppSettings) -> Unit) {
    SectionCard("측정 스케줄") {
        Segmented(ScheduleMode.entries, s.scheduleMode, { if (it == ScheduleMode.FIXED) "정시" else "랜덤" }, { save(s.copy(scheduleMode = it)) })
        when (s.scheduleMode) {
            ScheduleMode.FIXED -> {
                Hint("하루 1~3회, 켜 둔 시각에 알립니다.")
                SlotRow("아침", s.morningEnabled, s.morningMinute, { save(s.copy(morningEnabled = it)) }) { save(s.copy(morningMinute = it)) }
                SlotRow("점심", s.lunchEnabled, s.lunchMinute, { save(s.copy(lunchEnabled = it)) }) { save(s.copy(lunchMinute = it)) }
                SlotRow("저녁", s.dinnerEnabled, s.dinnerMinute, { save(s.copy(dinnerEnabled = it)) }) { save(s.copy(dinnerMinute = it)) }
            }
            ScheduleMode.RANDOM -> {
                Hint("정한 시간 범위 안에서 매일 다른 시각에 알립니다.")
                TimeButton("시작", s.randomStartMinute) { save(s.copy(randomStartMinute = it)) }
                TimeButton("끝", s.randomEndMinute) { save(s.copy(randomEndMinute = it)) }
                Text("하루 횟수")
                ChipGroup(listOf(1, 2, 3), { "${it}회" }, { it == s.randomCount }) { save(s.copy(randomCount = it)) }
                Text("알림 사이 최소 간격")
                ChipGroup(listOf(30, 60, 90, 120, 180), { if (it % 60 == 0) "${it / 60}시간" else "${it}분" }, { it == s.randomMinGapMinutes }) {
                    save(s.copy(randomMinGapMinutes = it))
                }
                if (!RandomScheduler.isFeasible(s.randomCount, s.randomStartMinute, s.randomEndMinute, s.randomMinGapMinutes)) {
                    Text("⚠️ 이 범위에는 ${s.randomCount}회를 최소 간격으로 넣을 수 없습니다. 범위를 넓히거나 횟수·간격을 줄이세요. (현재 랜덤 알림이 꺼진 상태)",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SlotRow(label: String, enabled: Boolean, minute: Int, onToggle: (Boolean) -> Unit, onTime: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SwitchRow(label, enabled, onToggle) }
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.padding(start = 8.dp)) { Text(Labels.minute(minute)) }
    }
    if (open) TimePickerDialog(minute, { open = false }) { onTime(it); open = false }
}

@Composable
private fun RangeSection(s: AppSettings, unit: GlucoseUnit, onSave: (GlucoseRanges) -> Unit) {
    val r = s.ranges
    fun show(v: Int) = UnitConverter.formatGlucose(v, unit)
    var low by remember(r, unit) { mutableStateOf(show(r.low)) }
    var targetHigh by remember(r, unit) { mutableStateOf(show(r.targetHigh)) }
    var fastingLow by remember(r, unit) { mutableStateOf(show(r.fastingLow)) }
    var fastingHigh by remember(r, unit) { mutableStateOf(show(r.fastingHigh)) }
    var veryHigh by remember(r, unit) { mutableStateOf(show(r.veryHigh)) }
    fun p(t: String) = UnitConverter.parseGlucose(t, unit)
    val parsed = listOf(p(low), p(targetHigh), p(fastingLow), p(fastingHigh), p(veryHigh))
    val candidate = if (parsed.all { it != null }) GlucoseRanges(parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!, parsed[4]!!) else null
    val valid = candidate != null && GlucoseRangeClassifier.isValid(candidate)

    SectionCard("혈당 범위 색상 (${Labels.glucoseUnit(unit)})") {
        Hint("개인 목표 범위는 의료진과 상의해 정하세요.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("저혈당 기준(미만)", low, Modifier.weight(1f)) { low = it }
            NumField("목표 상한", targetHigh, Modifier.weight(1f)) { targetHigh = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("공복 목표 하한", fastingLow, Modifier.weight(1f)) { fastingLow = it }
            NumField("공복 목표 상한", fastingHigh, Modifier.weight(1f)) { fastingHigh = it }
        }
        NumField("매우 높음 기준(이상)", veryHigh, Modifier.fillMaxWidth()) { veryHigh = it }
        if (!valid) Hint("값의 순서를 확인하세요: 저혈당 ≤ 공복 하한 < 공복 상한 ≤ 목표 상한 < 매우 높음")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSave(candidate!!) }, enabled = valid) { Text("저장") }
            TextButton(onClick = { onSave(GlucoseRanges()) }) { Text("기본값으로") }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
}

@Composable
private fun KakaoSection(vm: MainViewModel, s: AppSettings, save: (AppSettings) -> Unit) {
    val loggedIn by vm.kakaoLoggedIn.collectAsState()
    val activity = LocalActivity.current
    SectionCard("카카오톡 나에게 보내기 (선택)") {
        Hint("앱 알림이 기본입니다. 카카오톡은 보조 채널이며, 메시지에는 혈당 수치 없이 안내 문구만 들어갑니다.")
        if (!vm.kakaoConfigured) {
            Hint("이 빌드에는 카카오 앱 키가 설정되지 않아 사용할 수 없습니다. (local.properties의 KAKAO_NATIVE_APP_KEY)")
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (loggedIn) "✅ 로그인됨" else "로그인 필요", Modifier.weight(1f))
            if (loggedIn) {
                TextButton(onClick = { vm.kakaoLogout() }) { Text("로그아웃") }
            } else {
                OutlinedButton(onClick = { activity?.let { vm.kakaoLogin(it) } }) { Text("카카오 로그인") }
            }
        }
        SwitchRow("카카오톡으로도 받기", s.kakaoEnabled, { save(s.copy(kakaoEnabled = it)) }, enabled = loggedIn)
        if (s.kakaoEnabled) {
            SwitchRow("측정 시간 안내", s.kakaoOnMeasure, { save(s.copy(kakaoOnMeasure = it)) })
            SwitchRow("저녁에 식사 기록이 없을 때", s.kakaoOnMissingMeal, { save(s.copy(kakaoOnMissingMeal = it)) })
            SwitchRow("저녁에 운동 기록이 없을 때", s.kakaoOnMissingExercise, { save(s.copy(kakaoOnMissingExercise = it)) })
        }
        if (loggedIn) OutlinedButton(onClick = { vm.kakaoTest() }) { Text("테스트 메시지 보내기") }
    }
}
