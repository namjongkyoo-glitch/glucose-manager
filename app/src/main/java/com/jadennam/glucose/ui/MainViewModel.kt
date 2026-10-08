package com.jadennam.glucose.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jadennam.glucose.BuildConfig
import com.jadennam.glucose.container
import com.jadennam.glucose.data.DayRecords
import com.jadennam.glucose.data.PeriodDetail
import com.jadennam.glucose.domain.backup.BackupCodec
import com.jadennam.glucose.domain.backup.BackupFormatException
import com.jadennam.glucose.domain.estimation.Estimate
import com.jadennam.glucose.domain.estimation.GlucoseEstimator
import com.jadennam.glucose.domain.model.AppSettings
import com.jadennam.glucose.domain.model.Exercise
import com.jadennam.glucose.domain.model.ExerciseType
import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.Meal
import com.jadennam.glucose.domain.model.MealType
import com.jadennam.glucose.domain.model.MeasureContext
import com.jadennam.glucose.domain.model.Medication
import com.jadennam.glucose.domain.model.Profile
import com.jadennam.glucose.domain.model.WeightEntry
import com.jadennam.glucose.domain.schedule.PlannedAlarm
import com.jadennam.glucose.domain.trend.TrendAggregator
import com.jadennam.glucose.domain.trend.TrendBucket
import com.jadennam.glucose.domain.trend.TrendPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface ProfileState {
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Ready(val profile: Profile) : ProfileState
}

data class TrendState(
    val period: TrendPeriod = TrendPeriod.WEEK,
    val buckets: List<TrendBucket> = emptyList(),
    val medications: List<Medication> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val repo = c.repository
    private val estimator = GlucoseEstimator()

    private val _messages = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _messages.asStateFlow()
    fun consumeMessage() { _messages.value = null }
    private fun say(text: String) { _messages.value = text }

    val profileState: StateFlow<ProfileState> = repo.profile
        .map { p -> if (p == null) ProfileState.Missing else ProfileState.Ready(p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileState.Loading)

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val today = MutableStateFlow(LocalDate.now(c.clock))
    val todayDate: StateFlow<LocalDate> = today.asStateFlow()
    val todayRecords: StateFlow<DayRecords> = today.flatMapLatest { repo.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayRecords(emptyList(), emptyList(), emptyList()))

    val medications: StateFlow<List<Medication>> = repo.medications.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val weights: StateFlow<List<WeightEntry>> = repo.allWeights.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val estimates: StateFlow<Map<MeasureContext, Estimate>> = repo.allReadings
        .map { estimator.estimateAll(it, c.clock.millis()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val period = MutableStateFlow(TrendPeriod.WEEK)
    val trend: StateFlow<TrendState> = combine(repo.allReadings, period, today, repo.medications) { r, p, t, meds ->
        TrendState(p, TrendAggregator.buckets(r, p, t), meds)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendState())

    private val _nextAlarm = MutableStateFlow<PlannedAlarm?>(null)
    val nextAlarm: StateFlow<PlannedAlarm?> = _nextAlarm.asStateFlow()

    private val _kakaoLoggedIn = MutableStateFlow(false)
    val kakaoLoggedIn: StateFlow<Boolean> = _kakaoLoggedIn.asStateFlow()
    val kakaoConfigured: Boolean get() = c.kakao.isConfigured

    fun canScheduleExact() = c.scheduler.canScheduleExact()
    fun canNotify() = c.notifier.canNotify()
    fun zone() = repo.zone()
    fun now() = repo.now()

    /** Called on start/resume: date roll-over, alarm chain, Kakao status. */
    fun refresh() {
        today.value = LocalDate.now(c.clock)
        _kakaoLoggedIn.value = c.kakao.hasToken()
        reschedule()
    }

    private fun reschedule() = viewModelScope.launch(Dispatchers.IO) {
        if (repo.getProfile() != null) _nextAlarm.value = c.scheduler.scheduleNext()
    }

    fun setPeriod(p: TrendPeriod) { period.value = p }

    // --- Onboarding / profile / settings ---
    fun completeOnboarding(profile: Profile, weightKg: Double?) = viewModelScope.launch(Dispatchers.IO) {
        repo.getSettings() // creates defaults with a per-install salt
        repo.saveProfile(profile)
        weightKg?.let { repo.addWeight(WeightEntry(measuredAt = now(), zoneId = zone().id, weightKg = it)) }
        _nextAlarm.value = c.scheduler.scheduleNext()
    }

    fun saveProfile(profile: Profile) = viewModelScope.launch(Dispatchers.IO) { repo.saveProfile(profile); say("저장했습니다") }

    fun saveSettings(s: AppSettings, toast: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {
        repo.saveSettings(s)
        _nextAlarm.value = c.scheduler.scheduleNext()
        if (toast) say("저장했습니다")
    }

    // --- Records ---
    fun addReading(valueMgDl: Int, context: MeasureContext, hoursAfterMeal: Int?, at: Long) = viewModelScope.launch(Dispatchers.IO) {
        repo.addReading(GlucoseReading(valueMgDl = valueMgDl, measuredAt = at, zoneId = zone().id, context = context, hoursAfterMeal = hoursAfterMeal))
        c.notifier.cancel(com.jadennam.glucose.notify.Notifier.ID_MEASURE)
        c.notifier.cancel(com.jadennam.glucose.notify.Notifier.ID_REMIND)
        say("혈당을 기록했습니다")
    }

    fun addMeal(types: List<MealType>, note: String?, at: Long) = viewModelScope.launch(Dispatchers.IO) {
        repo.addMeal(Meal(eatenAt = at, zoneId = zone().id, types = types, note = note?.takeIf { it.isNotBlank() }))
        c.notifier.cancel(com.jadennam.glucose.notify.Notifier.ID_MEAL)
        say("식사를 기록했습니다")
    }

    fun addExercise(type: ExerciseType, minutes: Int) = viewModelScope.launch(Dispatchers.IO) {
        repo.addExercise(Exercise(performedAt = now(), zoneId = zone().id, type = type, durationMinutes = minutes))
        c.notifier.cancel(com.jadennam.glucose.notify.Notifier.ID_EXERCISE)
        say("운동을 기록했습니다")
    }

    fun addWeight(kg: Double) = viewModelScope.launch(Dispatchers.IO) {
        repo.addWeight(WeightEntry(measuredAt = now(), zoneId = zone().id, weightKg = kg))
        say("몸무게를 기록했습니다")
    }

    fun deleteReading(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteReading(id) }
    fun deleteMeal(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteMeal(id) }
    fun deleteExercise(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteExercise(id) }
    fun deleteWeight(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteWeight(id) }

    fun saveMedication(m: Medication) = viewModelScope.launch(Dispatchers.IO) { repo.saveMedication(m); say("복용 약을 저장했습니다") }
    fun deleteMedication(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteMedication(id) }

    suspend fun periodDetail(bucket: TrendBucket): PeriodDetail =
        withContext(Dispatchers.IO) { repo.periodDetail(bucket.start, bucket.endExclusive) }

    // --- Backup ---
    fun backupFileName(): String =
        "glucose-backup-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(java.time.LocalDateTime.now(c.clock)) + ".json"

    fun writeBackup(uri: Uri, onDone: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val ok = runCatching {
            val text = BackupCodec.encode(repo.exportBackup(), now(), BuildConfig.VERSION_NAME)
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
        say(if (ok) "백업 파일을 저장했습니다" else "백업에 실패했습니다")
        if (ok) withContext(Dispatchers.Main) { onDone() }
    }

    fun restoreBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            repo.restore(BackupCodec.decode(text))
            _nextAlarm.value = c.scheduler.scheduleNext()
            say("복원했습니다")
        } catch (e: BackupFormatException) {
            say(e.message ?: "복원에 실패했습니다")
        } catch (e: Exception) {
            say("파일을 읽을 수 없습니다")
        }
    }

    // --- Kakao ---
    fun kakaoLogin(activity: android.app.Activity) = viewModelScope.launch {
        val r = c.kakao.login(activity)
        _kakaoLoggedIn.value = c.kakao.hasToken()
        say(if (r.isSuccess) "카카오 로그인 완료" else "카카오 로그인 실패")
    }

    fun kakaoLogout() = viewModelScope.launch {
        c.kakao.logout()
        _kakaoLoggedIn.value = c.kakao.hasToken()
    }

    fun kakaoTest() = viewModelScope.launch {
        val r = c.kakao.sendToMe("테스트 메시지입니다. 알림 연결이 정상입니다.")
        say(if (r.isSuccess) "카카오톡으로 보냈습니다" else "보내기 실패: 로그인과 동의 항목을 확인하세요")
    }
}
