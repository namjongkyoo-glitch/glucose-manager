package com.jadennam.glucose.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jadennam.glucose.container
import com.jadennam.glucose.domain.model.ScheduleMode
import com.jadennam.glucose.domain.schedule.AlarmKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    AlarmScheduler.ACTION_ALARM -> onAlarm(context, intent)
                    AlarmScheduler.ACTION_REMIND -> onRemind(context, intent)
                }
            } finally {
                // Always keep the chain alive, even if something above failed.
                runCatching {
                    val firedAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, 0L)
                    if (intent.action == AlarmScheduler.ACTION_ALARM) c.scheduler.scheduleNext(maxOf(firedAt, c.clock.millis()) + 1)
                }
                pending.finish()
            }
        }
    }

    private suspend fun onAlarm(context: Context, intent: Intent) {
        val c = context.container
        val settings = c.repository.getSettings()
        val kind = runCatching { AlarmKind.valueOf(intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: "") }.getOrNull() ?: return
        val triggerAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, c.clock.millis())
        when (kind) {
            AlarmKind.MEASURE -> {
                val mode = runCatching { ScheduleMode.valueOf(intent.getStringExtra(AlarmScheduler.EXTRA_MODE) ?: "") }.getOrDefault(settings.scheduleMode)
                val title = Messages.measureTitle(mode, intent.getIntExtra(AlarmScheduler.EXTRA_SLOT, 0))
                // App notification first; Kakao is a secondary channel and must never block it.
                c.notifier.show(Notifier.ID_MEASURE, Notifier.CHANNEL_MEASURE, title, Messages.MEASURE_BODY, Notifier.OPEN_GLUCOSE)
                if (settings.kakaoEnabled && settings.kakaoOnMeasure) KakaoWorker.enqueue(context, title)
                c.scheduler.scheduleRemind(triggerAt, settings.remindDelayMinutes)
            }
            AlarmKind.EXERCISE_CHECK -> {
                val today = LocalDate.now(c.clock)
                if (!c.repository.hasExerciseOn(today)) {
                    c.notifier.show(Notifier.ID_EXERCISE, Notifier.CHANNEL_DAILY, Messages.EXERCISE_TITLE, Messages.EXERCISE_BODY, Notifier.OPEN_EXERCISE)
                    if (settings.kakaoEnabled && settings.kakaoOnMissingExercise) KakaoWorker.enqueue(context, Messages.KAKAO_NO_EXERCISE)
                }
                if (!c.repository.hasMealOn(today)) {
                    c.notifier.show(Notifier.ID_MEAL, Notifier.CHANNEL_DAILY, Messages.MEAL_TITLE, Messages.MEAL_BODY, Notifier.OPEN_MEAL)
                    if (settings.kakaoEnabled && settings.kakaoOnMissingMeal) KakaoWorker.enqueue(context, Messages.KAKAO_NO_MEAL)
                }
            }
        }
    }

    private suspend fun onRemind(context: Context, intent: Intent) {
        val c = context.container
        val original = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, 0L)
        if (original > 0 && c.repository.countReadingsSince(original) == 0) {
            c.notifier.show(Notifier.ID_REMIND, Notifier.CHANNEL_MEASURE, Messages.REMIND_TITLE, Messages.REMIND_BODY, Notifier.OPEN_GLUCOSE)
        }
    }
}

/** Re-registers the alarm chain after reboot, app update or clock/time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { context.container.scheduler.scheduleNext() }
            } finally {
                pending.finish()
            }
        }
    }
}

/** User-facing texts. Kakao texts never contain health values. */
object Messages {
    private val SLOT_NAMES = listOf("아침", "점심", "저녁")

    fun measureTitle(mode: ScheduleMode, slot: Int): String = when (mode) {
        ScheduleMode.FIXED -> "${SLOT_NAMES.getOrElse(slot) { "" }} 혈당 측정 시간입니다".trim()
        ScheduleMode.RANDOM -> "혈당 측정 시간입니다"
    }

    const val MEASURE_BODY = "측정 후 앱에 기록해 주세요."
    const val REMIND_TITLE = "아직 혈당 기록이 없어요"
    const val REMIND_BODY = "지금 측정하고 기록해 주세요."
    const val EXERCISE_TITLE = "오늘 운동하셨나요?"
    const val EXERCISE_BODY = "운동했다면 종류와 시간을 눌러 기록해 주세요."
    const val MEAL_TITLE = "오늘 식사 기록이 없어요"
    const val MEAL_BODY = "식사 종류를 눌러 간단히 기록해 주세요."
    const val KAKAO_NO_EXERCISE = "오늘 운동 기록이 없어요"
    const val KAKAO_NO_MEAL = "오늘 식사 기록이 없어요"
    const val KAKAO_RELOGIN_TITLE = "카카오 재로그인이 필요합니다"
    const val KAKAO_RELOGIN_BODY = "설정 > 카카오톡에서 다시 로그인해 주세요. 앱 알림은 계속 동작합니다."
}
