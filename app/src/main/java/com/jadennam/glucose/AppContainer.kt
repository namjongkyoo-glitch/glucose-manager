package com.jadennam.glucose

import android.app.Application
import android.content.Context
import com.jadennam.glucose.data.AppDatabase
import com.jadennam.glucose.data.HealthConnectSource
import com.jadennam.glucose.data.Repository
import com.jadennam.glucose.notify.AlarmScheduler
import com.jadennam.glucose.notify.KakaoClient
import com.jadennam.glucose.notify.Notifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** System clock that follows the device time zone even if it changes while the app runs. */
private object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
}

class AppContainer(context: Context) {
    val clock: Clock = DeviceClock
    val db: AppDatabase = AppDatabase.build(context)
    val repository = Repository(db, clock)
    val notifier = Notifier(context)
    val scheduler = AlarmScheduler(context, repository, clock)
    val kakao = KakaoClient(context)
    val health = HealthConnectSource(context)
}

class GlucoseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.kakao.initIfConfigured()
    }
}

val Context.container: AppContainer get() = (applicationContext as GlucoseApp).container
