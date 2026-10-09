# 설계안 — 14_Blood Glucose Management (v1)

작성일 2026-10-08 · 상태: **승인됨** (사용자가 "나머지는 직접 선택해서 빌드까지" 위임)

## 0. 확인 필요 항목 — 결정

| # | 항목 | 결정 |
|---|---|---|
| 1 | Q&A 재매칭 해석 | PROMPT §8 표 그대로 확정 |
| 2 | 백업 암호화 | **평문 JSON**. 화면에 "건강정보가 평문으로 저장됩니다" 경고. `backupVersion`으로 나중에 암호화 추가 가능 |
| 3 | applicationId | **`com.jadennam.glucose`** (영구 고정) |
| 4 | 카카오 메시지 | **본인에게, 안내 문구만**. 혈당·체중 수치는 넣지 않는다 |
| 5 | 추정 표시 최소 데이터 | **14일 이상 + 측정 20회 이상**. 상황별 데이터가 3회 미만이면 그 상황만 "데이터 부족" |

## 1. 툴체인

| 항목 | 값 | 이유 |
|---|---|---|
| Gradle / AGP | 8.14.6 / 8.13.2 | AGP 9는 Kotlin 빌드 방식이 바뀌어 위험. 8.x 최신 안정판 |
| Kotlin / KSP | 2.3.21 / 2.3.12 | Room 코드 생성용 KSP |
| Compose BOM | 2026.06.01 (Compose 1.11) | Compose 1.12(BOM 2026.08+)는 AGP 9.1과 compileSdk 37을 요구해서 고정. AGP 9로 올릴 때 함께 올린다 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 | AGP 8.13이 공식 지원하는 최고 API. 37은 AGP 9 전환 때 올린다 |
| JDK | 17 | |

## 2. 새로 도입하는 라이브러리

| 라이브러리 | 이유 |
|---|---|
| Jetpack Compose (BOM) + Material3 + activity-compose | UI |
| lifecycle-viewmodel-compose / runtime-compose | 화면 상태, Flow 수집 |
| Room (runtime, ktx, compiler via KSP) | 로컬 DB, 스키마 export |
| WorkManager | 네트워크가 필요한 카카오 발송 |
| kotlinx-serialization-json | 백업 JSON (순수 Kotlin이라 JVM 단위 테스트 가능) |
| kotlinx-coroutines | Flow·suspend |
| Kakao SDK v2-user, v2-talk | 카카오 로그인 + 나에게 보내기 |
| JUnit 4 | 단위 테스트 |

**쓰지 않는 것**: 차트 라이브러리(Vico 등), Hilt, Navigation, DataStore.
- 차트: Vico, MPAndroidChart, Compose Canvas 직접 구현을 비교했다. 필요한 것은 점·선·예측밴드·점선·탭 판정 정도라서 **Canvas로 직접 그린다**(약 200줄). 의존성이 없고 밴드와 점선을 자유롭게 그릴 수 있다. Vico 3은 API 변화가 커서 유지보수 부담이 있다.
- DI는 `AppContainer`로 수동 주입한다. 화면이 4개뿐이라 Navigation 없이 상태 기반으로 전환한다. 설정은 Room 단일 행 테이블에 둔다(백업에 자연스럽게 포함).

## 3. 아키텍처

```
com.jadennam.glucose
├─ domain/      순수 Kotlin. Android import 금지
│  ├─ model/       GlucoseReading, Meal, Exercise, WeightEntry, Profile, AppSettings, enums
│  ├─ units/       UnitConverter, GlucoseFormatter
│  ├─ range/       GlucoseRangeClassifier
│  ├─ schedule/    RandomScheduler, DailyPlanner(정시·랜덤·저녁 체크 → PlannedAlarm 목록)
│  ├─ estimation/  GlucoseEstimator, Backtester
│  └─ backup/      BackupFile(직렬화 모델), BackupCodec
├─ data/        Room entity·dao·db, Repository(엔티티↔도메인 매핑, 복원 트랜잭션)
├─ notify/      AlarmScheduler, AlarmReceiver, BootReceiver, Notifier, KakaoWorker, KakaoClient
└─ ui/          theme, components, onboarding, today, trend, settings, MainActivity
```
의존 방향은 `ui → data/notify → domain` 한 방향이다. domain은 Clock(`java.time.Clock`)과 `kotlin.random.Random`을 주입받는다.

## 4. Room 스키마 v1

모든 시각은 `epoch millis(UTC)` + `zone_id`로 저장한다. 혈당은 `value_mgdl INTEGER`로 저장한다.

| 테이블 | 컬럼 |
|---|---|
| `profile` (1행, id=1) | name, birth_date(epochDay), height_cm REAL, unit_system(METRIC/US), glucose_unit(MG_DL/MMOL_L) |
| `app_settings` (1행, id=1) | schedule_mode(FIXED/RANDOM), morning/lunch/dinner_enabled·_minute, random_start/end_minute, random_count, random_min_gap_minutes, remind_delay_minutes(15), exercise_check_enabled·_minute(20:30), low(70)·target_high(180)·fasting_low(80)·fasting_high(130)·very_high(250), kakao_enabled, kakao_on_measure, kakao_on_missing_meal, kakao_on_missing_exercise, schedule_salt |
| `glucose_reading` | id, value_mgdl, measured_at, zone_id, context(FASTING/BEFORE_MEAL/AFTER_MEAL/BEDTIME), hours_after_meal?, note? |
| `meal` | id, eaten_at, zone_id, types(쉼표 구분 프리셋 코드), note? |
| `exercise` | id, performed_at, zone_id, type, duration_minutes |
| `weight_entry` | id, measured_at, zone_id, weight_kg REAL |
| `medication` | id, name, times_per_day, units_per_dose REAL, start_date(epochDay), end_date(epochDay, null = 현재 복용 중), note? |

`exportSchema = true`로 `app/schemas/`에 저장한다. 파괴적 마이그레이션은 쓰지 않는다. v2부터는 Migration과 MigrationTestHelper 테스트를 함께 추가한다.

## 5. 추정 모델

**목표**: 측정 상황 c(공복·식전·식후·취침 전)별로 다음 측정값의 점추정과 95% 예측구간을 낸다.

- 데이터: 최근 90일, 상황별 최근 60회까지. 혈당은 오른쪽으로 치우친 분포라서 로그값 `x = ln(mg/dL)`을 쓴다.
- 중심: `m_c = median(x_c)`. 점추정은 `exp(m_c)`.
- 오차: 상황별 **leave-one-out** 잔차 `r_i = |x_i − median(x_c \ x_i)|`를 모든 상황에서 모아(pooled) 쓴다.
- 구간: split-conformal 방식. 모은 잔차 n개를 정렬해 `k = ⌈(n+1)·0.95⌉`번째 값을 `q`로 쓴다. 구간은 `[exp(m_c − q), exp(m_c + q)]`다. 교환 가능성 가정 아래 coverage ≥ 95%가 이론적으로 보장된다.
- 데이터 부족: 첫 측정부터 14일 미만, 전체 20회 미만, k > n, 또는 해당 상황 측정 3회 미만이면 `Estimate.InsufficientData`를 반환한다.
- **검증**: `Backtester`가 시간순 rolling-origin으로 시점마다 그 이전 데이터만 써서 다음 실측을 예측하고, 구간 안에 들어온 비율을 계산한다. 시드를 고정한 더미 데이터(상황별 평균 차이, 요일 변동, 완만한 추세, 가끔 이상치 포함)에서 coverage ≥ 0.95를 테스트한다.
- 표시: 추세 화면의 "예상 범위" 카드에 점선 테두리와 반투명 밴드로 그려 실측과 구분하고, 면책 문구를 항상 함께 보여준다. 추정값은 DB에 저장하지 않는다.

## 6. 알림 스케줄링

- `DailyPlanner.plan(date, settings)`가 그날의 `PlannedAlarm(time, kind)` 목록을 만든다. kind는 MEASURE(slot), EXERCISE_CHECK다.
  - 정시 모드: 켜진 슬롯(아침·점심·저녁)의 시각.
  - 랜덤 모드: `RandomScheduler`가 [start, end] 범위에 count개를 뽑는다. 가능한 여유 구간 `span − (count−1)·gap`에서 균등 추출하고 정렬한 뒤 `i·gap`을 더하므로 최소 간격이 보장된다. 시드는 `schedule_salt xor epochDay`다. 그래서 재부팅 뒤에도 그날 시각이 같다.
- `AlarmScheduler`는 **다음 알람 1개만** 등록한다(현재 시각 이후 가장 이른 것, 오늘 것이 없으면 내일). 알람이 울리면 알림을 띄우고 다음 알람을 등록한다.
- 재알림: MEASURE가 울리면 `+remind_delay`분에 REMIND 알람을 별도 requestCode로 등록한다. REMIND가 울렸을 때 원래 알람 시각 이후 혈당 기록이 없으면 한 번 더 알린다.
- 저녁 체크: 오늘 운동 기록이 없으면 "오늘 운동하셨나요?" 알림을 보낸다. 탭하면 운동 입력으로 간다.
- 재등록 시점: `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`/`TIMEZONE_CHANGED`, 앱 시작, 설정 저장.
- 정확 알람: `canScheduleExactAlarms()`가 true면 `setExactAndAllowWhileIdle`, 아니면 `setAndAllowWhileIdle`(지연 가능)로 대신하고 설정 화면에 권한 안내를 띄운다. Android 13 이상은 `POST_NOTIFICATIONS`를 요청한다. 배터리 최적화 예외 안내 버튼을 둔다.

## 7. 백업 포맷

파일명은 `glucose-backup-YYYYMMDD-HHmm.json`이고 SAF `ACTION_CREATE_DOCUMENT`로 저장한다.
```json
{ "backupVersion": 1, "exportedAt": 1760000000000, "appVersionName": "1.0.0",
  "profile": {...}, "settings": {...},
  "glucoseReadings": [...], "meals": [...], "exercises": [...], "weights": [...] }
```
- `BackupCodec.decode`는 `ignoreUnknownKeys`를 켜고 `backupVersion`으로 분기한다. 지원하는 버전보다 새 파일이면 "새 버전 앱에서 만든 백업" 오류를 낸다.
- 복원은 한 트랜잭션 안에서 전체 테이블을 지우고 다시 넣는다. 복원 전에 "현재 데이터가 대체됩니다"라고 확인한다.
- 앱 삭제 메뉴는 팝업([백업하기] [그냥 삭제] [취소])을 띄운 뒤 `ACTION_DELETE package:` 화면으로 넘긴다.

## 8. 카카오 "나에게 보내기"

1. `local.properties`의 `KAKAO_NATIVE_APP_KEY`를 `BuildConfig`와 `manifestPlaceholders`(`kakao{KEY}` 스킴)로 주입한다. 키가 비어 있으면 설정 화면의 카카오 항목을 비활성으로 두고 안내만 보여준다.
2. 설정 화면에서 "카카오 로그인"을 누르면 카카오톡 로그인을 시도하고, 안 되면 카카오계정 로그인으로 넘어간다. 동의 항목은 `talk_message`다.
3. 알람이 울리고 해당 조건이 켜져 있으면 `KakaoWorker`를 큐에 넣는다(네트워크 연결 조건). Worker는 `TalkApiClient.sendDefaultMemo(TextTemplate)`를 호출한다. 문구는 안내만 보낸다. 예) "점심 혈당 측정 시간입니다", "오늘 운동 기록이 없어요".
4. 실패하면 3회까지 재시도한다. 토큰이 없거나 만료됐으면 앱 알림으로 "카카오 재로그인 필요"를 띄운다. 앱 자체 알림은 Worker보다 **먼저** 나가므로 카카오가 실패해도 영향이 없다.

## 9. 보안·로그
- 건강 값은 Logcat에 남기지 않는다(로그 코드 자체를 두지 않는다).
- `.claude/settings.json`의 deny에 `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`를 추가한다.
- 테스트는 시드를 고정한 더미 생성기만 쓴다.

## 10. 릴리스
- 서명: 저장소 밖 `%USERPROFILE%\.android-keys\glucose-release.jks`를 쓰고, 경로와 비밀번호는 `keystore.properties`(저장소 밖)에 둔다. 빌드 스크립트는 환경변수 `GLUCOSE_KEYSTORE_PROPERTIES` → 저장소 루트 `keystore.properties` → `%USERPROFILE%\.android-keys\keystore.properties` 순서로 찾는다. 하나도 없으면 release는 서명하지 않는다.
- 버전: `versionCode` 1, `versionName` 1.0.0. 변경점은 `CHANGELOG.md`에 적는다.
- 배포: GitHub Releases + `site/index.html` 안내 페이지. 업로드는 요청받았을 때만 한다. 절차는 `docs/release.md`에 있다.

## 11. 테스트 계획
| 대상 | 테스트 |
|---|---|
| UnitConverter | mg/dL↔mmol/L, cm↔ft·in, kg↔lb 왕복 오차 |
| RandomScheduler / DailyPlanner | 고정 시드 1000일: 범위·횟수·최소 간격, 불가능한 설정 거부 |
| GlucoseEstimator / Backtester | 데이터 부족 반환, 더미 백테스트 coverage ≥ 0.95 |
| BackupCodec | 왕복 동일성, v1 고정 문자열 디코딩, 미래 버전 거부, 알 수 없는 키 무시 |
| GlucoseRangeClassifier | 경계값 |
| Room 마이그레이션 | v2가 생길 때 추가 |

## 12. 설계 이후 추가·변경 (2026-10-08)
- **복용 약 기록** 추가(사용자 요청): 약 이름·종류, 하루 복용 횟수(1~4), 1회 개수(소수 허용), 시작일, 종료일 또는 "현재 복용 중", 메모. 오늘 화면의 "복용 약" 카드에서 추가하고 수정한다. 추세 지점 상세에서 그 기간과 겹치는 약을 보여주고, 차트에는 약 시작·종료 지점에 💊 점선을 그린다. 용량 계산이나 추천은 하지 않는다. 릴리스 전이라 스키마 v1에 바로 포함했다. 백업 v1에는 `medications`가 선택 필드로 들어가서, 이 필드가 없는 v1 파일도 읽힌다.
- **추세 집계**: 하루 1~3회 측정이라 1년치도 약 1000행이다. 그래서 SQL 집계 대신 순수 Kotlin `TrendAggregator`로 일·주 단위로 묶는다(단위 테스트 가능). 성능 문제가 생기면 SQL로 옮긴다.
- **OS 백업 차단**: `allowBackup=false` + `dataExtractionRules`로 클라우드 백업과 기기 간 이전을 모두 제외한다. 건강 데이터는 앱 안의 파일 백업으로만 옮긴다.

## 13. v1.1.0 — 삼성 헬스(Health Connect) 읽기 (2026-10-09)
1차 제외 범위였던 Health Connect 연동을 사용자 요청으로 추가했다. **읽기 전용**이다.
- 데이터: 걸음 수(일별 집계, `aggregateGroupByPeriod`로 폰·워치 중복 제거), 운동 세션, 수면 세션, 체중.
- 저장하지 않음: 화면을 열 때 Health Connect에서 바로 읽는다. 그래서 Room 스키마와 백업 형식은 바뀌지 않는다. 앱의 운동·몸무게 기록과 섞지 않고 "삼성 헬스"로 따로 표시한다.
- 표시: 오늘 화면 "삼성 헬스 (오늘)" 카드, 추세 지점 상세의 "삼성 헬스" 섹션, 설정의 연결·권한 섹션.
- 수면: 겹치는 세션(폰 + 워치)을 합친 뒤 **깨어난 날짜**에 귀속한다. 기간 요약은 밤당 평균이다.
- 권한: READ_STEPS / EXERCISE / SLEEP / WEIGHT. 지원되는 Health Connect 버전이면 READ_HEALTH_DATA_HISTORY도 요청한다(없으면 권한을 준 날로부터 30일 전까지만 읽힌다).
- 필수 화면: `PermissionsRationaleActivity`(Android 13 이하 rationale + Android 14 이상 VIEW_PERMISSION_USAGE alias).
- 라이브러리: `androidx.health.connect:connect-client:1.1.0` (minCompileSdk 36, AGP 8.9.1 이상이라 현재 툴체인과 호환된다).
- 실패 처리: 미설치, 권한 없음, 읽기 오류가 나면 null을 반환하고 카드에 "연결 필요"나 "데이터 없음"만 표시한다. 다른 기능에는 영향이 없다.
- 테스트: `HealthSummarizer`(수면 병합·귀속, 기간 합계·평균, 체중 최신값)를 순수 Kotlin 단위 테스트로 검증한다. 실제로 읽어 오는지는 실기기에서 확인한다.
