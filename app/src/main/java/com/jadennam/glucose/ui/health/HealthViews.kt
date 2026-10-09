package com.jadennam.glucose.ui.health

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.jadennam.glucose.data.HealthAvailability
import com.jadennam.glucose.data.HealthConnectSource
import com.jadennam.glucose.domain.health.HealthPeriodSummary
import com.jadennam.glucose.domain.health.HealthSummarizer
import com.jadennam.glucose.domain.model.UnitSystem
import com.jadennam.glucose.ui.Labels
import com.jadennam.glucose.ui.MainViewModel
import com.jadennam.glucose.ui.components.GradientButton
import com.jadennam.glucose.ui.components.Hint
import com.jadennam.glucose.ui.components.SectionCard
import java.text.NumberFormat
import java.util.Locale

private fun steps(n: Long) = NumberFormat.getIntegerInstance(Locale.KOREA).format(n) + "보"

/** Lines describing a Samsung Health summary; [singleDay] switches wording between day and period. */
@Composable
fun HealthSummaryLines(s: HealthPeriodSummary, unitSystem: UnitSystem, vm: MainViewModel, singleDay: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            s.totalSteps == null -> Text("👣 걸음 수: 기록 없음")
            singleDay -> Text("👣 걸음 수: ${steps(s.totalSteps)}")
            else -> Text("👣 걸음 수: 합계 ${steps(s.totalSteps)} · 하루 평균 ${steps(s.avgDailySteps ?: 0)}")
        }
        when {
            s.avgSleepMinutes == null -> Text("🛌 수면: 기록 없음")
            singleDay -> Text("🛌 수면(전날 밤): ${HealthSummarizer.formatMinutes(s.avgSleepMinutes)}")
            else -> Text("🛌 수면: 평균 ${HealthSummarizer.formatMinutes(s.avgSleepMinutes)} (${s.sleepNights}일)")
        }
        if (s.exercises.isEmpty()) Text("🏃 운동: 기록 없음")
        s.exercises.forEach { e ->
            Text("🏃 ${Labels.dateTime(e.start, vm.zone())}  ${e.title?.takeIf { it.isNotBlank() } ?: e.typeLabel} ${e.minutes}분")
        }
        s.latestWeight?.let { Text("⚖️ 체중: ${Labels.weight(it.kg, unitSystem)} (${Labels.dateTime(it.time, vm.zone())})") }
    }
}

@Composable
fun SamsungHealthTodayCard(vm: MainViewModel, unitSystem: UnitSystem) {
    val status by vm.healthStatus.collectAsState()
    val today by vm.healthToday.collectAsState()
    SectionCard("📱 삼성 헬스 (오늘)") {
        val s = today
        when {
            !status.connected -> Hint("연결되지 않았습니다. 설정 → 삼성 헬스 연결에서 권한을 허용하세요.")
            s == null -> Hint("데이터를 읽지 못했습니다. 삼성 헬스의 Health Connect 동기화를 확인하세요.")
            else -> HealthSummaryLines(s, unitSystem, vm, singleDay = true)
        }
    }
}

@Composable
fun SamsungHealthSettings(vm: MainViewModel) {
    val status by vm.healthStatus.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshHealth()
    }
    fun open(intent: Intent) = try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }

    SectionCard("📱 삼성 헬스 연결 (읽기 전용)") {
        Hint("걸음 수·운동·수면·체중을 Health Connect를 통해 읽어 옵니다. 앱 DB에 복사하거나 밖으로 보내지 않습니다.")
        when (status.availability) {
            HealthAvailability.NOT_INSTALLED, HealthAvailability.UPDATE_REQUIRED -> {
                Text(if (status.availability == HealthAvailability.NOT_INSTALLED) "Health Connect가 설치되어 있지 않습니다." else "Health Connect 업데이트가 필요합니다.")
                OutlinedButton(onClick = {
                    open(Intent(Intent.ACTION_VIEW, "market://details?id=${HealthConnectSource.PROVIDER_PACKAGE}".toUri()).setPackage("com.android.vending"))
                }) { Text("Play 스토어에서 받기") }
            }
            HealthAvailability.AVAILABLE -> {
                Text(
                    when {
                        status.missing.isEmpty() -> "✅ 연결됨 (모든 권한 허용)"
                        status.connected -> "⚠️ 일부 권한만 허용됨"
                        else -> "연결 필요"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                if (status.missing.isNotEmpty()) {
                    GradientButton("권한 허용하기", { permissionLauncher.launch(status.requested) }, Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { open(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }) { Text("Health Connect 설정 열기") }
            }
        }
        Text("삼성 헬스 쪽 설정 (1회)", style = MaterialTheme.typography.labelLarge)
        Hint("삼성 헬스 앱 → 설정 → Health Connect → 걸음 수·운동·수면·체중을 Health Connect와 공유하도록 켜세요.")
        Hint("과거 기록 권한을 허용하면 30일 이전 자료도 추세에 표시됩니다.")
    }
}
