# 릴리스 절차

## 서명키 (가장 중요)
- 키스토어: `%USERPROFILE%\.android-keys\glucose-release.jks` (PKCS12, alias `glucose`, RSA 4096, 유효기간 100년)
- 비밀번호와 경로: `%USERPROFILE%\.android-keys\keystore.properties`. 저장소 밖에 있고 커밋하지 않는다.
- 인증서 SHA-256: `3f:ad:f6:a8:ff:14:5e:a8:18:88:c1:44:e0:79:0b:14:0a:45:d4:62:3c:84:1f:96:33:50:b7:62:41:c7:6e:ae`
- **키를 잃어버리면 기존 설치본 위에 업그레이드할 수 없다.** 사용자는 앱을 지우고 다시 설치해야 하고, 그때 데이터가 사라진다.

### 키스토어 백업 (지금 1회, 꼭 할 것)
1. `%USERPROFILE%\.android-keys\` 폴더(`glucose-release.jks`, `keystore.properties`) 전체를 복사한다.
2. 오프라인 저장소 2곳 이상(USB, 암호화된 외장 디스크)과 비밀번호 관리자(파일 첨부 기능)에 보관한다.
3. 복구 테스트: 다른 PC에서 `GLUCOSE_KEYSTORE_PROPERTIES` 환경변수로 그 파일을 가리키고 `assembleRelease`를 실행한다. 아래 명령으로 인증서 SHA-256이 위 값과 같은지 확인한다.

빌드 스크립트는 `GLUCOSE_KEYSTORE_PROPERTIES` 환경변수 → 저장소 루트 `keystore.properties` → `%USERPROFILE%\.android-keys\keystore.properties` 순서로 키 설정을 찾는다. 하나도 없으면 release APK는 서명되지 않는다.

## 새 버전 릴리스
1. `app/build.gradle.kts`에서 `versionCode`를 +1 하고, `versionName`(예: 1.0.1)을 올린다.
2. `CHANGELOG.md`에 변경점을 적는다.
3. Room 스키마를 바꿨다면 `AppDatabase`의 version을 올리고 Migration과 마이그레이션 테스트를 추가한다. `app/schemas/`에 새 JSON이 생성됐는지 확인한다.
4. 검증 (PowerShell):
   ```powershell
   .\gradlew.bat testDebugUnitTest lintDebug assembleRelease
   & "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
   Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
   ```
   인증서 SHA-256이 위 값과 같아야 한다.
5. 실기기에서 이전 버전 위에 덮어설치하고(`adb install -r ...`) 기록이 유지되는지 확인한다.
6. (요청받았을 때만) GitHub Releases에 `app-release.apk`를 `glucose-<versionName>.apk` 이름으로 올리고, 릴리스 노트에 SHA-256을 적는다. `site/index.html`의 다운로드 링크와 버전을 갱신한다.

## 다운로드 안내 페이지
- `site/index.html`: 한 장짜리 정적 페이지. GitHub Pages(`/site` 폴더) 또는 Cloudflare Pages에 올린다.
- 업로드와 배포는 명시적으로 요청받았을 때만 한다.

## 카카오 연동을 켤 때
1. Kakao Developers에서 앱을 만들고 **네이티브 앱 키**를 `local.properties`의 `KAKAO_NATIVE_APP_KEY=`에 넣는다.
2. 플랫폼 > Android: 패키지명 `com.jadennam.glucose`, 키 해시는 debug와 release **둘 다** 등록한다.
   - release 키 해시: 인증서 SHA-1을 base64로 바꾼 값. `keytool -exportcert -alias glucose -keystore <jks> | openssl sha1 -binary | openssl base64`로 구한다.
3. 카카오 로그인을 활성화하고, 동의항목에서 **카카오톡 메시지 전송(talk_message)**을 켠다.
4. 다시 빌드한다. 키가 비어 있으면 앱의 카카오 항목은 비활성으로 표시된다.
