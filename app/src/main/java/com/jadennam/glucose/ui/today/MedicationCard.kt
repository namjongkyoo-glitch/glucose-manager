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
    var editingGroup by remember { mutableStateOf<List<Medication>?>(null) }
    var newRegimen by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val active = meds.filter { it.isActiveOn(today) }
    val groups = RegimenTimeline.groups(if (showAll) meds else active)

    SectionCard("💊 복용 약 (처방)") {
        Hint("처방이 바뀌면 '새 처방 입력'으로 새 약을 모두 입력하세요. 기존 약은 새 시작일 전날 자동 종료됩니다. 같은 처방에 약을 더하거나 고치려면 '처방 수정'을 누르세요. 용량 판단은 반드시 의료진과 상의하세요.")
        if (groups.isEmpty()) Hint(if (showAll) "등록된 약이 없습니다." else "현재 복용 중인 약이 없습니다.")
        groups.forEach { (start, _) ->
            // Edit always works on the full prescription, even when only some drugs are shown.
            val fullGroup = meds.filter { it.startDate == start }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("처방 ${MedText.period(fullGroup.first())} · ${fullGroup.size}종", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { editingGroup = fullGroup }) { Text("처방 수정") }
            }
            fullGroup.forEach { m ->
                Column(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp)) {
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
        RegimenDialog(
            editing = null, today = today, onDismiss = { newRegimen = false }, onDelete = null,
        ) { lines, start, end ->
            vm.applyRegimen(lines.map { it.second }, start, end) { newRegimen = false }
        }
    }

    editingGroup?.let { group ->
        RegimenDialog(
            editing = group, today = today, onDismiss = { editingGroup = null },
            onDelete = { vm.deleteRegimen(group) { editingGroup = null } },
        ) { lines, _, end ->
            vm.editRegimen(group, lines, end) { editingGroup = null }
        }
    }
}

/** [id] = 0 for a newly added drug line. */
private data class DraftLine(val id: Long = 0, val name: String = "", val times: Int = 1, val units: String = "1", val note: String = "") {
    val unitsValue: Double? get() = units.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 20 }
    val valid: Boolean get() = name.isNotBlank() && unitsValue != null

    companion object {
        fun of(m: Medication) = DraftLine(
            m.id, m.name, m.timesPerDay,
            if (m.unitsPerDose % 1.0 == 0.0) m.unitsPerDose.toInt().toString() else m.unitsPerDose.toString(),
            m.note ?: "",
        )
    }
}

/**
 * New prescription ([editing] == null) or whole-prescription edit. In edit mode the start date is
 * fixed (delete and re-enter to move it); the end date applies to every drug.
 */
@Composable
private fun RegimenDialog(
    editing: List<Medication>?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (List<Pair<Long, RegimenItem>>, LocalDate, LocalDate?) -> Unit,
) {
    val lines = remember { mutableStateListOf<DraftLine>().apply { if (editing.isNullOrEmpty()) add(DraftLine()) else addAll(editing.map(DraftLine::of)) } }
    var start by remember { mutableStateOf(editing?.firstOrNull()?.startDate ?: today) }
    val initialEnd = editing?.firstOrNull()?.endDate
    var ongoing by remember { mutableStateOf(initialEnd == null) }
    var end by remember { mutableStateOf(initialEnd ?: today) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = lines.isNotEmpty() && lines.all { it.valid } && (ongoing || !end.isBefore(start))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "새 처방 입력" else "처방 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing == null) {
                    DateField("시작일", start, { start = it })
                } else {
                    Text("시작일: $start", fontWeight = FontWeight.SemiBold)
                    Hint("시작일을 바꾸려면 처방을 삭제하고 새로 입력하세요.")
                }
                SwitchRow("현재 복용 중", ongoing, { ongoing = it })
                if (!ongoing) {
                    DateField("종료일", end, { end = it })
                    if (end.isBefore(start)) Hint("종료일이 시작일보다 빠릅니다.")
                }
                if (editing == null) Hint("저장하면 이 날짜에 복용 중이던 약은 ${start.minusDays(1)}에 종료됩니다.")
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
                    OutlinedTextField(l.note, { lines[i] = l.copy(note = it) }, label = { Text("메모·설명 (선택, 예: 식후 / 혈당약)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { lines.add(DraftLine()) }) { Text("+ 약 추가") }
                if (onDelete != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(if (confirmDelete) "정말 처방 삭제" else "처방 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(lines.map { it.id to RegimenItem(it.name, it.times, it.unitsValue!!, it.note) }, start, if (ongoing) null else end)
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
