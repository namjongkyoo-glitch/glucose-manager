# 14_Blood Glucose Management (혈당 관리)

연속혈당측정기(CGM)를 쓰지 않는 당뇨인을 위한 **개인용 Android 혈당 관리 앱**입니다.
측정 일정 알림, 혈당·식사·운동·몸무게 기록, 주·월·연 추세, 통계 기반 추정 혈당을 한 앱에서 간단히 다룹니다.

> ⚠️ 이 앱이 보여주는 **추정 혈당은 참고용 예상값이며 의료적 판단에 사용할 수 없습니다.** 약이나 인슐린 조절은 반드시 실측값과 의료진 상담을 바탕으로 하세요.

## 주요 기능
- **첫 실행 설정**: 이름, 생년월일, 키, 몸무게, 단위(한국 cm·kg / 미국 ft·in·lb), 혈당 단위(mg/dL / mmol/L). 기존 백업이 있으면 바로 복원할 수 있습니다.
- **측정 스케줄·알림**: 하루 1~3회 정시 알림 또는 지정한 시간 범위 안의 랜덤 알림. 놓치면 재알림합니다.
- **간편 기록**: 혈당과 측정 상황, 식사 완료 시각·종류(프리셋 버튼), 운동(조깅·산책·수영·골프·헬스 등), 몸무게.
- **복용 약**: 약 이름·종류, 하루 횟수, 1회 개수, 시작일~종료일(또는 현재 복용 중). 추세 상세에서 함께 확인합니다.
- **저녁 운동 체크 알림**
- **추세 차트**: 주간·월간·연간. 지점을 누르면 그날(주)의 식사·운동·측정 횟수·몸무게·복용 약을 보여줍니다.
- **추정 혈당**: 개인 기록을 바탕으로 한 통계 모델 추정값과 95% 예측구간을 실측과 구분해 표시합니다.
- **혈당 범위 색상**: 기본 저혈당 < 70, 목표 70–180, 높음 > 180, 매우 높음 ≥ 250 mg/dL. 설정에서 바꿀 수 있습니다.
- **백업·복원**: 원하는 폴더에 백업 파일을 저장하고, 재설치 후 복원합니다. 앱의 "앱 삭제" 메뉴에서는 백업 권유 후 삭제로 넘어갑니다.
- **카카오톡 나에게 보내기(선택)**: 측정·식사·운동 확인 안내를 본인 카카오톡으로 받습니다(혈당 수치는 보내지 않음).

## 설치 (사용자)
1. 다운로드 페이지(배포 후 링크 추가 예정)에서 최신 `app-release.apk`를 받습니다.
2. 처음 설치할 때는 브라우저나 파일 앱에 "출처를 알 수 없는 앱 설치"를 허용합니다.
3. 첫 실행 때 알림 권한과 정확한 알람 권한을 허용하고, 배터리 최적화 예외를 설정합니다(알림 누락 방지).
4. **업그레이드**: 새 APK를 받아 그대로 설치하면 덮어설치되고 기록은 유지됩니다.
5. **앱을 지우기 전에 반드시 앱 안에서 백업**하세요. 앱 내부 저장소는 삭제할 때 함께 지워집니다.

## 개발 환경
- JDK 17, Android SDK(command-line tools 또는 Android Studio). SDK 위치: `%LOCALAPPDATA%\Android\Sdk`
- `local.properties.example`을 복사해 `local.properties`를 만들고 `sdk.dir`와 `KAKAO_NATIVE_APP_KEY`를 입력합니다.
- 릴리스 서명: `keystore.properties.example`을 참고해 `keystore.properties`를 만듭니다. 키스토어는 저장소 밖에 보관하고 따로 백업합니다.

```powershell
.\gradlew.bat testDebugUnitTest      # 단위 테스트
.\gradlew.bat lintDebug              # 린트
.\gradlew.bat assembleDebug          # 디버그 APK
.\gradlew.bat assembleRelease        # 서명된 릴리스 APK
adb install -r app\build\outputs\apk\release\app-release.apk
```

## 기술 스택
Kotlin 2.3 · Jetpack Compose(Material3, 차트는 Canvas 직접 구현) · Room · AlarmManager / WorkManager · kotlinx.serialization · 카카오 Android SDK(선택 기능) · JUnit
AGP 8.13 · Gradle 8.14 · compileSdk/targetSdk 36 · minSdk 26 · applicationId `com.jadennam.glucose`

## 폴더 구조
```
14_Blood Glucose Management/
├─ app/
│  ├─ src/main/java/com/jadennam/glucose/
│  │  ├─ domain/        # 순수 Kotlin: model, estimation, schedule, units, range, trend, backup
│  │  ├─ data/          # Room entity·dao·db, Repository(복원 트랜잭션 포함)
│  │  ├─ notify/        # 알람 스케줄러, BroadcastReceiver, 카카오 발송 Worker
│  │  └─ ui/            # onboarding, today(기록·복용 약), trend(차트·추정), settings, components, theme
│  ├─ src/test/         # 도메인 단위 테스트 (더미 데이터)
│  └─ schemas/          # Room 스키마 export
├─ docs/
│  ├─ design.md         # 설계안 (승인본)
│  └─ release.md        # 서명·버전·배포 절차, 키스토어 백업
├─ site/                # APK 다운로드 안내 페이지
├─ CHANGELOG.md
├─ local.properties.example
├─ keystore.properties.example
├─ README.md
└─ CLAUDE.md
```

## 나중 계획
- 개인 서버를 통한 사용자 정보·백업 보관과 재설치 후 본인 확인 복원
- 앱 내 새 버전 확인
