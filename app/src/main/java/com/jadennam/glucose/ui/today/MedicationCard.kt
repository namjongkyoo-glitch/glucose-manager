package com.jadennam.glucose.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.medication.RegimenItem
import com.jadennam.glucose.domain.medication.RegimenTimeline
import com.jadennam.glucose.ui.MainViewModel
import com.jadennam.glucose.ui.components.ChipGroup
import com.jadennam.glucose.ui.components.DateField
import com.jadennam.glucose.ui.components.Hint
import com.jadennam.glucose.ui.components.SectionCard
import com.jadennam.glucose.ui.components.SwitchRow
import java.time.LocalDate
import java.util.Locale

object MedText {
    fun dose(m: Medication): String {
        val units = if (m.unitsPerDose % 1.0 == 0.0) m.unitsPerDose.toInt().toString() else String.format(Locale.US, "%.1f", m.unitsPerDose)
        return "하루 ${m.timesPerDay}회 · 1회 ${units}개"
    }

    fun period(m: Medication): String = "${m.startDate} ~ ${m.endDate?.toString() ?: "현재 복용 중"}"
}

@Composable
fun MedicationCard(vm: MainViewModel, today: LocalDate) {
    val meds by vm.medications.collectAsState()
    var editing by remember { mutableStateOf<Medication?>(null) }
    var newRegimen by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val active = meds.filter { it.isActiveOn(today) }
    val groups = RegimenTimeline.groups(if (showAll) meds else active)

    SectionCard("💊 복용 약 (처방)") {
        Hint("처방이 바뀌면 '새 처방 입력'으로 새 약을 모두 입력하세요. 기존 약은 새 시작일 전날 자동 종료됩니다. 용량 판단은 반드시 의료진과 상의하세요.")
        if (groups.isEmpty()) Hint(if (showAll) "등록된 약이 없습니다." else "현재 복용 중인 약이 없습니다.")
        groups.forEach { (_, list) ->
            Text("처방 ${MedText.period(list.first())}", style = MaterialTheme.typography.labelLarge)
            list.forEach { m ->
                Column(Modifier.fillMaxWidth().clickable { editing = m }.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                    Text(m.name, fontWeight = FontWeight.SemiBold)
                    Text(MedText.dose(m) + (m.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { newRegimen = true }) { Text("+ 새 처방 입력") }
            if (meds.size > active.size || showAll) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "복용 중만 보기" else "지난 처방 포함 전체") }
            }
        }
    }

    if (newRegimen) {
        RegimenDialog(today, onDismiss = { newRegimen = false }) { items, start, end ->
            vm.applyRegimen(items, start, end) { newRegimen = false }
        }
    }

    editing?.let { m ->
        MedicationDialog(
            initial = m,
            onDismiss = { editing = null },
            onSave = { vm.saveMedication(it); editing = null },
            onDelete = if (m.id != 0L) ({ vm.deleteMedication(m.id); editing = null }) else null,
        )
    }
}

@Composable
private fun MedicationDialog(initial: Medication, onDismiss: () -> Unit, onSave: (Medication) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var times by remember { mutableIntStateOf(initial.timesPerDay) }
    var units by remember { mutableStateOf(if (initial.unitsPerDose % 1.0 == 0.0) initial.unitsPerDose.toInt().toString() else initial.unitsPerDose.toString()) }
    var start by remember { mutableStateOf(initial.startDate) }
    var ongoing by remember { mutableStateOf(initial.endDate == null) }
    var end by remember { mutableStateOf(initial.endDate ?: LocalDate.now()) }
    var note by remember { mutableStateOf(initial.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val unitsValue = units.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 20 }
    val valid = name.isNotBlank() && unitsValue != null && (ongoing || !end.isBefore(start))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "복용 약 추가" else "복용 약 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("약 이름·종류 (예: 메트포르민 500mg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("하루 복용 횟수")
                ChipGroup(listOf(1, 2, 3, 4), { "${it}회" }, { it == times }) { times = it }
                OutlinedTextField(
                    units, { units = it }, label = { Text("1회 개수 (정·알)") }, singleLine = true,
                    isError = unitsValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                DateField("시작일", start, { start = it })
                SwitchRow("현재 복용 중", ongoing, { ongoing = it })
                if (!ongoing) {
                    DateField("종료일", end, { end = it })
                    if (end.isBefore(start)) Hint("종료일이 시작일보다 빠릅니다.")
                }
                OutlinedTextField(note, { note = it }, label = { Text("메모 (선택, 예: 식후)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(if (confirmDelete) "정말 삭제" else "삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(
                    name = name.trim(), timesPerDay = times, unitsPerDose = unitsValue!!,
                    startDate = start, endDate = if (ongoing) null else end, note = note.trim().ifBlank { null },
                ))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private data class DraftLine(val name: String = "", val times: Int = 1, val units: String = "1", val note: String = "") {
    val unitsValue: Double? get() = units.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 20 }
    val valid: Boolean get() = name.isNotBlank() && unitsValue != null
}

/** New prescription with one or more drugs sharing a start date. */
@Composable
private fun RegimenDialog(today: LocalDate, onDismiss: () -> Unit, onSave: (List<RegimenItem>, LocalDate, LocalDate?) -> Unit) {
    val lines = remember { mutableStateListOf(DraftLine()) }
    var start by remember { mutableStateOf(today) }
    var ongoing by remember { mutableStateOf(true) }
    var end by remember { mutableStateOf(today) }
    val valid = lines.isNotEmpty() && lines.all { it.valid } && (ongoing || !end.isBefore(start))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 처방 입력") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("시작일", start, { start = it })
                SwitchRow("현재 복용 중", ongoing, { ongoing = it })
                if (!ongoing) {
                    DateField("종료일", end, { end = it })
                    if (end.isBefore(start)) Hint("종료일이 시작일보다 빠릅니다.")
                }
                Hint("저장하면 이 날짜에 복용 중이던 약은 ${start.minusDays(1)}에 종료됩니다.")
                lines.forEachIndexed { i, l ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("약 ${i + 1}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (lines.size > 1) TextButton(onClick = { lines.removeAt(i) }) { Text("빼기") }
                    }
                    OutlinedTextField(l.name, { lines[i] = l.copy(name = it) }, label = { Text("약 이름·종류 (예: 메트포르민 500mg)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    ChipGroup(listOf(1, 2, 3, 4), { "하루 ${it}회" }, { it == l.times }) { lines[i] = l.copy(times = it) }
                    OutlinedTextField(l.units, { lines[i] = l.copy(units = it) }, label = { Text("1회 개수 (정·알)") }, singleLine = true,
                        isError = l.unitsValue == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(l.note, { lines[i] = l.copy(note = it) }, label = { Text("메모 (선택, 예: 식후)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { lines.add(DraftLine()) }) { Text("+ 약 추가") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(lines.map { RegimenItem(it.name, it.times, it.unitsValue!!, it.note) }, start, if (ongoing) null else end)
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
