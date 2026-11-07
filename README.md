# starlog2 Pleos Connect 초기 구성

기존 `starlog2` 프로젝트 이름과 `com.example.starlog2` 패키지를 유지하면서 Pleos Vehicle SDK의 첫 도어 위치 읽기를 구현한 Android 앱입니다.

## 적용 사항

- Pleos Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
- Vehicle SDK: `ai.pleos.playground:Vehicle:2.3.2`
- 권한: `pleos.car.permission.CAR_DOORS`
- 대상 영역: `DoorValue.ROW_1_LEFT`
- 초기화 후 한 번 자동 조회하고 버튼으로 같은 세션에서 재조회
- 성공 정숫값, `null`, 동기·비동기 오류를 화면에서 구분
- Activity 종료 시 Vehicle 세션 해제

기존 프로젝트의 SDK 및 빌드 도구 설정은 유지했습니다.

| 항목 | 값 |
| --- | --- |
| namespace / applicationId | `com.example.starlog2` |
| minSdk / compileSdk / targetSdk | 34 / 37 / 37 |
| AGP / Gradle wrapper | 9.5.1 / 9.6.0 |
| Java target | 17 |

## Pleos AVD

Android Studio의 Tools → SDK Manager → SDK Update Sites에 다음 항목을 등록합니다.

```text
Name: Pleos Connect System Image
URL: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
```

현재 PC에는 `Pleos_Connect_v2.1.0` AVD가 이미 있으며 API 34와 부팅 완료 상태를 확인했습니다.

## 빌드와 실행

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:lintDebug
adb devices
.\gradlew.bat :app:installDebug
adb shell am start -n com.example.starlog2/.MainActivity
```

APK 생성 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 차량 API 연동 성공은 Pleos AVD에서 실제 성공 콜백과 반환값을 확인한 뒤 판단합니다.

## 현재 검증 결과 (2026-11-07)

- Gradle 9.6.0 / JDK 25.0.4 실행 확인
- Pleos AVD `Pleos_Connect_v2.1.0`, API 34, 부팅 완료 확인
- Gradle `:app:assembleDebug :app:lintDebug`: 성공
- APK 설치 및 `MainActivity` cold launch: 성공
- Pleos AVD 첫 도어 조회: `ROW_1_LEFT`, 원본 정숫값 `0`
- 버튼 재조회: `ROW_1_LEFT`, 원본 정숫값 `0`
- Activity 종료 후 앱·SDK 오류 로그: 없음

실제로 확인된 SDK URL은 다음과 같습니다.

```text
https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.pom
```

빠른 시작 가이드의 2.3.1 의존성이 저장소에서 확인되지 않아, 상세 SDK 설정 문서와 공식 샘플이 사용하는 2.3.2로 전환했습니다.

## 참고 자료

- [Pleos 시작 가이드](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Vehicle SDK 프로젝트 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [도메인 객체 수명 관리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)
- [Get API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/get-property)
- [Pleos 에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
