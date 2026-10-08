package com.jadennam.glucose.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jadennam.glucose.data.Repository
import com.jadennam.glucose.domain.schedule.DailyPlanner
import com.jadennam.glucose.domain.schedule.PlannedAlarm
import java.time.Clock
import java.time.Instant

/**
 * Keeps exactly one "next" alarm registered (plus an optional re-remind alarm).
 * Re-registered on boot, package update, time change, app start and settings save.
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: Repository,
    private val clock: Clock,
) {
    companion object {
        const val ACTION_ALARM = "com.jadennam.glucose.action.ALARM"
        const val ACTION_REMIND = "com.jadennam.glucose.action.REMIND"
        const val EXTRA_KIND = "kind"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        private const val RC_NEXT = 1001
        private const val RC_REMIND = 1002
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Registers the earliest alarm after [after] (defaults to now). Returns it, or null if none. */
    suspend fun scheduleNext(after: Long = clock.millis()): PlannedAlarm? {
        val settings = repository.getSettings()
        val next = DailyPlanner.next(Instant.ofEpochMilli(after), clock.zone, settings)
        val pi = pending(RC_NEXT, ACTION_ALARM, next)
        if (next == null) {
            alarmManager.cancel(pi)
            return null
        }
        set(next.triggerAt, pi)
        return next
    }

    fun scheduleRemind(originalTriggerAt: Long, delayMinutes: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_TRIGGER_AT, originalTriggerAt)
        val pi = PendingIntent.getBroadcast(context, RC_REMIND, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        set(clock.millis() + delayMinutes * 60_000L, pi)
    }

    private fun pending(requestCode: Int, action: String, alarm: PlannedAlarm?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        if (alarm != null) {
            intent.putExtra(EXTRA_KIND, alarm.kind.name)
                .putExtra(EXTRA_SLOT, alarm.slot)
                .putExtra(EXTRA_MODE, alarm.mode.name)
                .putExtra(EXTRA_TRIGGER_AT, alarm.triggerAt)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun set(triggerAt: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                return
            } catch (_: SecurityException) {
                // Permission revoked between check and call: fall through to inexact.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }
}
