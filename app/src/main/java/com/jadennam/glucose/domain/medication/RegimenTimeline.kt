package com.jadennam.glucose.domain.medication

import com.jadennam.glucose.domain.model.Medication
import java.time.LocalDate

/** One drug line of a new prescription (regimen). */
data class RegimenItem(val name: String, val timesPerDay: Int, val unitsPerDose: Double, val note: String? = null)

sealed interface RegimenChange {
    /** [ended] are existing medications whose endDate was set to the day before the new start. */
    data class Ok(val ended: List<Medication>, val added: List<Medication>) : RegimenChange
    data class Rejected(val reason: String) : RegimenChange
}

sealed interface RegimenEdit {
    data class Ok(val updated: List<Medication>, val added: List<Medication>, val deletedIds: List<Long>) : RegimenEdit
    data class Rejected(val reason: String) : RegimenEdit
}

/**
 * A new prescription replaces whatever was being taken: medications that started earlier and were
 * still active on [start] end the day before. If a later prescription already exists, the new one
 * ends the day before that one starts (so past prescriptions can be entered out of order).
 */
object RegimenTimeline {

    fun apply(existing: List<Medication>, items: List<RegimenItem>, start: LocalDate, end: LocalDate?): RegimenChange {
        if (items.isEmpty()) return RegimenChange.Rejected("약을 1개 이상 입력하세요.")
        if (end != null && end.isBefore(start)) return RegimenChange.Rejected("종료일이 시작일보다 빠릅니다.")
        if (existing.any { it.startDate == start }) {
            return RegimenChange.Rejected("$start 에 시작하는 처방이 이미 있습니다. 기존 처방을 수정하거나 삭제하세요.")
        }

        val nextStart = existing.map { it.startDate }.filter { it.isAfter(start) }.minOrNull()
        val effectiveEnd = listOfNotNull(end, nextStart?.minusDays(1)).minOrNull()

        val ended = existing
            .filter { it.startDate.isBefore(start) && (it.endDate == null || !it.endDate.isBefore(start)) }
            .map { it.copy(endDate = start.minusDays(1)) }

        val added = items.map {
            Medication(name = it.name.trim(), timesPerDay = it.timesPerDay, unitsPerDose = it.unitsPerDose,
                startDate = start, endDate = effectiveEnd, note = it.note?.trim()?.ifBlank { null })
        }
        return RegimenChange.Ok(ended, added)
    }

    /**
     * Edits one prescription (all medications sharing [group]'s start date) as a whole.
     * [lines] pairs an existing medication id (0 = newly added drug) with its new values.
     * Start date stays fixed; [end] applies to every drug in the prescription.
     */
    fun edit(group: List<Medication>, lines: List<Pair<Long, RegimenItem>>, end: LocalDate?): RegimenEdit {
        val start = group.firstOrNull()?.startDate ?: return RegimenEdit.Rejected("수정할 처방이 없습니다.")
        if (lines.isEmpty()) return RegimenEdit.Rejected("약을 1개 이상 입력하세요. 처방을 없애려면 '처방 삭제'를 누르세요.")
        if (end != null && end.isBefore(start)) return RegimenEdit.Rejected("종료일이 시작일보다 빠릅니다.")
        val byId = group.associateBy { it.id }

        fun build(id: Long, it: RegimenItem) = Medication(id, it.name.trim(), it.timesPerDay, it.unitsPerDose, start, end, it.note?.trim()?.ifBlank { null })
        val updated = lines.filter { it.first != 0L && it.first in byId }.map { (id, item) -> build(id, item) }
        val added = lines.filter { it.first == 0L || it.first !in byId }.map { (_, item) -> build(0, item) }
        val keptIds = updated.map { it.id }.toSet()
        return RegimenEdit.Ok(updated, added, group.map { it.id }.filter { it !in keptIds })
    }

    /** Prescriptions grouped by start date, newest first. */
    fun groups(meds: List<Medication>): List<Pair<LocalDate, List<Medication>>> =
        meds.groupBy { it.startDate }.toList().sortedByDescending { it.first }
}
