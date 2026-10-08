# 스킬 추천

| 적용 | 단계 | 스킬 | 사용 시점 | 이유 |
|---|---|---|---|---|
| ✅ | 기획 | `superpowers:brainstorming` | 설계안을 쓰기 전에 Q&A 재매칭 해석과 '확인 필요' 5개 항목을 사용자와 정리할 때 | Q&A 답변이 질문과 어긋나 있고 성공 기준과 제외 범위가 비어 있어서, 코딩 전에 의도를 확정해야 합니다 |
| — | 기획 | `ecc:intent-driven-development` | 추정 모델의 '95%'와 알림 신뢰성을 검증 가능한 수용 기준으로 바꿀 때 | '통계 모델 95% 이상'처럼 모호한 요구를 coverage 같은 측정 가능한 기준으로 고정해야 합니다 |
| ✅ | 설계 | `ecc:android-clean-architecture` | docs/design.md에서 domain·data·notify·ui 레이어와 의존 방향을 정할 때 | 추정·스케줄·백업 로직을 Android 의존성 없는 순수 Kotlin으로 분리해야 단위 테스트가 가능합니다 |
| ✅ | 설계 | `superpowers:writing-plans` | 설계 승인 직후 M0~M8 마일스톤을 단계별 구현 계획으로 쪼갤 때 | 기능이 많은 모바일 앱이라 마일스톤마다 검증하고 보고하는 흐름이 필요합니다 |
| — | 설계 | `ecc:architecture-decision-records` | 스택, 차트 라이브러리, 백업 암호화, applicationId, 카카오 연동 방식을 정할 때마다 기록 | applicationId와 서명키처럼 되돌릴 수 없는 결정이 있어 근거를 남겨야 합니다 |
| ✅ | 구현 | `ecc:kotlin-patterns` | 도메인 모델, 단위 변환, 리포지토리 코드를 작성할 때 | Kotlin 관용구(data class, sealed result, null safety)로 단순하고 안전한 코드를 쓰기 위해서입니다 |
| ✅ | 구현 | `ecc:compose-multiplatform-patterns` | 온보딩, 기록 프리셋 버튼, 추세 화면 등 Compose UI와 상태를 만들 때 | Jetpack Compose 상태 관리, 내비게이션, 테마 패턴을 그대로 적용할 수 있습니다 |
| — | 구현 | `ecc:kotlin-coroutines-flows` | Room Flow로 차트·오늘 화면을 갱신하고 WorkManager로 카카오 발송을 처리할 때 | DB 변경 관찰과 백그라운드 작업에서 구조적 동시성이 필요합니다 |
| ✅ | 구현 | `superpowers:test-driven-development` | 추정 모델, 랜덤 스케줄러, 단위 변환, 백업 직렬화를 구현하기 전에 실패하는 테스트부터 쓸 때 | 의료 관련 수치와 데이터 보존 로직은 테스트로 먼저 못 박아야 안전합니다 |
| ✅ | 검증 | `ecc:kotlin-testing` | 고정 시드·주입 Clock 테스트, 더미 데이터 백테스트, Room 마이그레이션 테스트를 작성할 때 | GUI와 알림은 자동 테스트가 어려워서 도메인 테스트 품질이 핵심입니다 |
| — | 검증 | `ecc:security-review` | 백업 파일 포맷, 카카오 토큰·키 처리, 로그 출력을 확정하기 전에 | 건강정보가 백업 파일이나 Logcat으로 새거나 키·키스토어가 노출되는 것을 막아야 합니다 |
| ✅ | 검증 | `superpowers:verification-before-completion` | 마일스톤 완료와 릴리스 APK 보고 직전에 testDebugUnitTest, lintDebug, assembleRelease 결과를 확인할 때 | 실기기 확인 항목과 자동 검증 결과를 구분해 정직하게 보고해야 합니다 |

## 새로 만들면 좋은 전용 스킬

- **android-apk-release** — versionCode/versionName 올리기 → CHANGELOG 갱신 → assembleRelease → 서명과 SHA-256 확인 → 릴리스 노트 초안 작성까지 한 번에 처리합니다. GitHub Releases 업로드는 확인을 받은 뒤에만 합니다. (트리거: '릴리스 APK 만들어줘', '새 버전 배포 준비' 요청 시)
- **glucose-dummy-data** — 식사·운동·시간대 패턴이 반영된 현실적인 더미 혈당·체중 데이터셋을 시드 고정으로 생성합니다. 추정 모델 백테스트와 차트 테스트, 스크린샷에 쓰고 실데이터 사용을 막습니다. (트리거: 추정 모델·차트 테스트용 데이터나 데모 데이터가 필요할 때)
- **android-alarm-device-check** — adb로 Doze 강제 진입(dumpsys deviceidle), 재부팅, 덮어설치 뒤 알람 재등록 여부와 알림 채널·정확 알람 권한 상태를 점검하는 체크리스트와 명령 모음입니다. (트리거: 알림 관련 코드를 수정한 뒤 실기기·에뮬레이터 검증을 할 때)
