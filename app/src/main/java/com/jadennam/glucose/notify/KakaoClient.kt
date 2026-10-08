package com.jadennam.glucose.notify

import android.content.Context
import com.jadennam.glucose.BuildConfig
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.talk.TalkApiClient
import com.kakao.sdk.template.model.Link
import com.kakao.sdk.template.model.TextTemplate
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Kakao "send to me" (memo) via the official SDK. Never logs tokens or message contents. */
class KakaoClient(private val context: Context) {

    val isConfigured: Boolean get() = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()
    private var initialized = false

    fun initIfConfigured() {
        if (isConfigured && !initialized) {
            KakaoSdk.init(context, BuildConfig.KAKAO_NATIVE_APP_KEY)
            initialized = true
        }
    }

    fun hasToken(): Boolean = initialized && AuthApiClient.instance.hasToken()

    /** Must be called with an Activity context. Tries KakaoTalk app login first, then account login. */
    suspend fun login(activityContext: Context): Result<Unit> {
        if (!initialized) return Result.failure(IllegalStateException("카카오 앱 키가 설정되지 않았습니다."))
        val users = UserApiClient.instance
        val viaTalk: Result<Unit>? = if (users.isKakaoTalkLoginAvailable(activityContext)) {
            suspendCancellableCoroutine<Result<Unit>> { cont ->
                users.loginWithKakaoTalk(activityContext) { token: OAuthToken?, error: Throwable? ->
                    cont.resume(if (token != null) Result.success(Unit) else Result.failure(error ?: Exception("로그인 실패")))
                }
            }
        } else null
        if (viaTalk?.isSuccess == true) return viaTalk
        return suspendCancellableCoroutine { cont ->
            users.loginWithKakaoAccount(activityContext) { token: OAuthToken?, error: Throwable? ->
                cont.resume(if (token != null) Result.success(Unit) else Result.failure(error ?: Exception("로그인 실패")))
            }
        }
    }

    suspend fun logout(): Result<Unit> {
        if (!initialized) return Result.success(Unit)
        return suspendCancellableCoroutine { cont ->
            UserApiClient.instance.logout { error -> cont.resume(if (error == null) Result.success(Unit) else Result.failure(error)) }
        }
    }

    suspend fun sendToMe(text: String): Result<Unit> {
        if (!initialized) return Result.failure(IllegalStateException("not configured"))
        val template = TextTemplate(text = "[혈당 관리] $text", link = Link())
        return suspendCancellableCoroutine { cont ->
            TalkApiClient.instance.sendDefaultMemo(template) { error ->
                cont.resume(if (error == null) Result.success(Unit) else Result.failure(error))
            }
        }
    }
}
