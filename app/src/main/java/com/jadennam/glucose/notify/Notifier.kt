package com.jadennam.glucose.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jadennam.glucose.R
import com.jadennam.glucose.ui.MainActivity

class Notifier(private val context: Context) {

    companion object {
        const val CHANNEL_MEASURE = "measure"
        const val CHANNEL_DAILY = "daily_check"
        const val CHANNEL_SYSTEM = "system"
        const val EXTRA_OPEN = "open"
        const val OPEN_GLUCOSE = "glucose"
        const val OPEN_EXERCISE = "exercise"
        const val OPEN_MEAL = "meal"
        const val OPEN_SETTINGS = "settings"

        const val ID_MEASURE = 10
        const val ID_REMIND = 11
        const val ID_EXERCISE = 20
        const val ID_MEAL = 21
        const val ID_KAKAO_LOGIN = 30
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MEASURE, "혈당 측정 알림", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "정시·랜덤 측정 알림과 재알림" },
                NotificationChannel(CHANNEL_DAILY, "하루 기록 확인", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "저녁 운동·식사 기록 확인" },
                NotificationChannel(CHANNEL_SYSTEM, "앱 안내", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "카카오 재로그인 등 안내" },
            ),
        )
    }

    fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // guarded by canNotify()
    fun show(id: Int, channel: String, title: String, text: String, open: String) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, open)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (channel == CHANNEL_MEASURE) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
