# 14_Blood glucose management

상위 `088_AI/CLAUDE.md` 규칙을 따른다. 목적·기능은 @README.md, 설계는 `docs/design.md`, 릴리스 절차는 `docs/release.md`에 있다.
CGM 없이 쓰는 개인용 혈당 기록·알림·추세 Android 앱(Kotlin + Jetpack Compose, Room, 로컬 백업, APK 직접 배포).

## 명령 (PowerShell 기준. Claude Code(Git Bash)에서는 `./gradlew`)
- 단위 테스트(관련 파일 먼저): `.\gradlew.bat testDebugUnitTest --tests "*GlucoseEstimatorTest"`
- 전체 단위 테스트: `.\gradlew.bat testDebugUnitTest`
- 린트: `.\gradlew.bat lintDebug`
- 디버그 빌드: `.\gradlew.bat assembleDebug`
- 릴리스 빌드(서명): `.\gradlew.bat assembleRelease` → `app/build/outputs/apk/release/` (`keystore.properties` 필요)
- 실기기 설치(덮어쓰기): `adb install -r app\build\outputs\apk\release\app-release.apk`
- 필요 환경: JDK 17, Android SDK(`local.properties`의 `sdk.dir`)

## 지켜야 할 계약
- **applicationId와 서명키는 한 번 정하면 바꾸지 않는다.** 둘 중 하나라도 바뀌면 덮어설치가 깨지고 사용자 데이터가 사라진다.
- 릴리스마다 `versionCode`는 반드시 올리고, `versionName`과 `CHANGELOG.md`를 함께 갱신한다.
- 혈당은 DB에 항상 **mg/dL 정수**로 저장한다. mmol/L(÷18.0)과 미국 단위 변환은 표시 레이어에서만 한다.
- 시각은 epoch millis(UTC) + 기록 당시 zoneId로 저장한다.
- 추정값은 DB에 실측값으로 저장하지 않는다. 화면에서는 점선이나 밴드로 실측과 구분하고, 면책 문구 "참고용 예상값이며 의료적 판단에 사용할 수 없습니다"를 항상 함께 보여준다.
- 추정 모델은 데이터가 최소 기준에 못 미치면 값을 내지 않고 "데이터 부족"을 반환한다.
- Room: `exportSchema = true`, `app/schemas/`는 커밋한다. 스키마를 바꾸면 Migration과 마이그레이션 테스트를 함께 추가한다. `fallbackToDestructiveMigration` 금지.
- 백업 파일에는 `backupVersion` 필드를 둔다. 복원은 이전 버전 포맷도 읽을 수 있어야 한다.
- 도메인 로직(estimation, schedule, units, backup)은 Android 의존성이 없는 순수 Kotlin으로 쓴다. Clock과 Random은 주입받는다.
- 카카오 발송이 실패해도 앱 자체 알림은 반드시 나간다(카카오는 보조 채널).

## 함정
- 앱 전용 저장소(`filesDir`, `getExternalFilesDir`)는 **삭제 시 함께 지워진다.** 백업은 반드시 SAF(`ACTION_CREATE_DOCUMENT`)로 사용자가 고른 위치에 저장한다.
- Android는 시스템에서 앱을 지우는 순간을 감지할 수 없다. 삭제 전 경고는 앱 안의 "앱 삭제" 메뉴 → 백업 팝업 → `ACTION_DELETE` 흐름에서만 가능하다.
- 알람은 재부팅·앱 업데이트·강제 종료 때 사라진다. `BOOT_COMPLETED`와 `MY_PACKAGE_REPLACED`에서 다시 등록한다.
- Android 13 이상은 `POST_NOTIFICATIONS` 런타임 권한이 필요하다. Android 14 이상은 `SCHEDULE_EXACT_ALARM`이 기본 거부라서 `canScheduleExactAlarms()`로 확인하고 설정 화면으로 안내한다.
- Doze와 제조사 배터리 최적화(특히 삼성) 때문에 알림이 지연될 수 있다. 배터리 최적화 예외 안내 화면을 두고 실기기에서 확인한다.
- 카카오 키 해시는 debug와 release 키스토어가 다르다. 카카오 디벨로퍼스에 둘 다 등록해야 한다.
- 카카오 refresh token이 만료되면 재로그인을 안내한다. 이때 토큰 값을 로그에 남기지 않는다.
- 랜덤 알림 테스트는 시드를 고정한 Random으로만 한다. 실제 시간을 기다리는 테스트는 만들지 않는다.
- AGP 8.13은 compileSdk 36까지만 지원한다. Compose BOM 2026.08 이상(Compose 1.12)과 androidx 최신판 일부는 AGP 9.1 + compileSdk 37을 요구해서 빌드가 깨진다. 버전을 올릴 때는 AGP 9 전환과 함께 올린다.
- 리소스 폴더를 옮긴 뒤 AAPT `resource not found`가 나면 `.\gradlew.bat clean` 후 다시 빌드한다.
- 릴리스 서명키는 `%USERPROFILE%\.android-keys\`에 있다. 절차와 인증서 지문은 `docs/release.md`를 본다.

## 비밀·파일
- `local.properties`(sdk.dir, `KAKAO_NATIVE_APP_KEY`), `keystore.properties`, `*.jks`, `*.keystore`는 커밋하지 않고 열지도 않는다. 참조는 `local.properties.example`과 `keystore.properties.example`로만 한다.
- `.claude/settings.json`의 deny 목록에 위 파일 읽기 차단을 추가한다.
- `.gitignore`에는 루트 기본 항목에 `.gradle/`, `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `*.apk`, `app/build/`를 더한다.

## 금지
- 인슐린·약 용량 계산이나 추천, "정상입니다" 같은 진단성 문구.
- 실제 혈당·체중 데이터를 테스트·fixture·스크린샷·커밋에 넣는 것(더미 생성기만 사용).
- Release 빌드에서 건강 값을 Logcat에 남기는 것.
- GitHub Releases 업로드나 다운로드 페이지 배포를 명시적 요청 없이 하는 것.
- UIVerse HTML/CSS를 그대로 복사하는 것(스타일만 참고해 Compose로 구현).
- 승인 없이 새 라이브러리를 추가하는 것(차트, 직렬화, 카카오 SDK 포함). 설계안에서 승인받은 것만 쓴다.

<!-- planner:skills -->
## 스킬 사용
해당 상황이 되면 아래 스킬을 먼저 호출한다.
- 기획: `superpowers:brainstorming` — 설계안을 쓰기 전에 Q&A 재매칭 해석과 '확인 필요' 5개 항목을 사용자와 정리할 때
- 설계: `ecc:android-clean-architecture` — docs/design.md에서 domain·data·notify·ui 레이어와 의존 방향을 정할 때
- 설계: `superpowers:writing-plans` — 설계 승인 직후 M0~M8 마일스톤을 단계별 구현 계획으로 쪼갤 때
- 구현: `ecc:kotlin-patterns` — 도메인 모델, 단위 변환, 리포지토리 코드를 작성할 때
- 구현: `ecc:compose-multiplatform-patterns` — 온보딩, 기록 프리셋 버튼, 추세 화면 등 Compose UI와 상태를 만들 때
- 구현: `superpowers:test-driven-development` — 추정 모델, 랜덤 스케줄러, 단위 변환, 백업 직렬화를 구현하기 전에 실패하는 테스트부터 쓸 때
- 검증: `ecc:kotlin-testing` — 고정 시드·주입 Clock 테스트, 더미 데이터 백테스트, Room 마이그레이션 테스트를 작성할 때
- 검증: `superpowers:verification-before-completion` — 마일스톤 완료와 릴리스 APK 보고 직전에 testDebugUnitTest, lintDebug, assembleRelease 결과를 확인할 때
