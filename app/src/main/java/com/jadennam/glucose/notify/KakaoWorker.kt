package com.jadennam.glucose.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jadennam.glucose.container
import java.util.concurrent.TimeUnit

/** Sends a reminder text (no health values) to the user's own KakaoTalk. */
class KakaoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        c.kakao.initIfConfigured()
        if (!c.kakao.isConfigured) return Result.failure()
        if (!c.kakao.hasToken()) {
            notifyRelogin()
            return Result.failure()
        }
        return if (c.kakao.sendToMe(text).isSuccess) {
            Result.success()
        } else if (!c.kakao.hasToken()) {
            // Refresh token expired: SDK clears tokens. Ask the user to log in again.
            notifyRelogin()
            Result.failure()
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private fun notifyRelogin() {
        applicationContext.container.notifier.show(
            Notifier.ID_KAKAO_LOGIN, Notifier.CHANNEL_SYSTEM,
            Messages.KAKAO_RELOGIN_TITLE, Messages.KAKAO_RELOGIN_BODY, Notifier.OPEN_SETTINGS,
        )
    }

    companion object {
        private const val KEY_TEXT = "text"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context, text: String) {
            val request = OneTimeWorkRequestBuilder<KakaoWorker>()
                .setInputData(workDataOf(KEY_TEXT to text))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
