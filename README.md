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

## Git 브랜치 컨벤션

- `master`: 검증된 통합 결과만 유지하는 기본 브랜치입니다. 직접 개발하거나 force push하지 않습니다.
- `jioni`: 이 노트북에서 작업하는 개인 개발 브랜치입니다. 현재 내비게이션 코어 구현은 이 브랜치에서 진행합니다.
- `integration/<기능>-v<버전>`: 여러 담당자의 기능을 합쳐 검증하는 통합 브랜치입니다. 예: `integration/starlog-v1`.
- `feat/<기능>-v<버전>`: 독립 기능 개발 브랜치입니다. 예: `feat/sl-nav-v1`, `feat/sl-memory-v1`.
- `fix/<기능>-<설명>`: 확인된 결함 수정 브랜치입니다. 예: `fix/navigation-off-route`.
- `docs/<설명>` 및 `chore/<설명>`: 문서와 빌드·설정·정리 작업에 사용합니다.

브랜치는 최신 기준 브랜치에서 만들고, 커밋 메시지는 `feat(navigation): ...`, `fix(navigation): ...`, `test(navigation): ...`, `docs(readme): ...`처럼 `<type>(<scope>): <summary>` 형식을 사용합니다. 기능 브랜치는 PR로 통합하며 `master`에는 직접 병합하지 않습니다.

## LAPTOP_1 Navigation v1/v2 보고서 (Manager AI용)

보고 기준: 2026-11-10 KST. 이 절은 Manager AI가 브랜치 상태를 오해하지 않고 검토·통합·실환경 검증을 이어가기 위한 실행 보고서입니다.

정식 기술 산출물은 [`docs/LAPTOP_1_NAVIGATION_DELIVERABLE.md`](docs/LAPTOP_1_NAVIGATION_DELIVERABLE.md)를 기준으로 합니다. 이 README는 빠른 인수 판단을 위한 요약입니다.

### 1. 결론과 현재 브랜치 상태

| 구분 | 코드 기준 SHA | 상태 |
| --- | --- | --- |
| `master` | `b4180e8` | LAPTOP_1 Navigation v1 반영 완료 |
| `jioni` | `2e94bf4` | v1 전체 + Navigation v2 코드 보강 완료, 최신 `origin/jioni`에 본 보고서 포함 |
| v1 이전 master | `8de49a1` | 기존 팀 문서 기준점, 이력 보존 |

- v1은 `master`에 fast-forward로 반영했습니다. force push, reset, 기존 파일 삭제는 수행하지 않았습니다.
- v2 코드는 `jioni`에만 있습니다. `master..jioni`의 코드 차이는 v2 기능 커밋 `2e94bf4`이며 나머지는 v2 보고용 README 커밋입니다.
- 현재 작업 영역은 Navigation 패키지와 대응 테스트입니다. Activity, Manifest, Gradle, WebView, Pleos Vehicle 코드는 변경하지 않았습니다.
- `docs/team/BASELINE.md`의 공통 contract gate는 아직 `NOT_READY`입니다. Navigation 내부 DTO를 승인된 공통 contract로 오인하지 마세요.

### 2. 커밋 지도

| 버전 | 커밋 | 목적 | 반영 위치 |
| --- | --- | --- | --- |
| v1 | `4f34ee2` | route, geometry, guidance, Replay 코어와 19개 테스트 | `master`, `jioni` |
| v1 문서 | `aec01ec` | Git 브랜치·커밋 컨벤션 | `master`, `jioni` |
| v1 인수 | `b4180e8` | Manager 연결 방법과 v1 한계 | `master`, `jioni` |
| v2 | `2e94bf4` | 네트워크 경계와 진행량 안정성 보강, 총 30개 테스트 | `jioni`만 |
| v2 문서 | `ecae96d` 이후 README 커밋 | v1/v2 구분, 검증 결과와 본 Manager 보고서 | `jioni`만 |

Manager가 v1을 다시 cherry-pick하면 중복 충돌이 발생할 수 있습니다. v1은 이미 `master`에 있으므로 재적용하지 마세요. v2 채택 시 코드 커밋 `2e94bf4`를 검토·적용하며, 이후 README 커밋은 보고 문서이므로 `master` 반영 대상에서 분리할 수 있습니다.

### 3. v1 구현 결과

| 구성요소 | 구현 내용 |
| --- | --- |
| `Polyline6` | decode, truncation·invalid character·overflow·빈/1점 geometry·5만 점 초과 방어 |
| `NavigationGeometry` | Haversine 거리, true-north bearing, 359°→1° smoothing, 보간, 기본 310m lookahead |
| `ValhallaRouteProvider` | POST 요청, connect 10초/read 15초 timeout, 2MB 제한, 취소, latest-request-wins, 429 1회 재시도 |
| Valhalla parser | summary km→m, seconds 유지, polyline6와 maneuver index 검증, 다중 leg는 `UNSUPPORTED_ROUTE` |
| `DefaultGuidanceEngine` | route 투영, `snappedPoint`, `alongM`, remaining, 다음 maneuver, 이탈·도착 상태 |
| `ReplayTelemetryProvider` | 결정적 seq/state, `pause`·`resume`·`seek`·`reset`·`stop`, `SourceMode.REPLAY` 유지 |

v1 기본 판정값은 정확도 30m 이하 fix, 이탈 40m 초과 3회, 도착 remaining/endpoint 각각 20m 이하입니다. 낮은 정확도와 no-fix는 이탈 카운터에 포함하지 않으며 route 교체 시 진행·이탈·도착 상태를 초기화합니다.

### 4. v2 변경점과 호환성

v2는 새로운 화면 기능이 아니라 v1 Navigation의 신뢰성과 시험 가능성을 강화합니다.

- `ValhallaRouteProvider`에 connection factory, retry sleeper, wall clock 주입 지점을 추가했습니다. 기본 인자가 있으므로 기존 `ValhallaRouteProvider(clientId)` 호출은 그대로 동작합니다.
- `Retry-After`의 정수 초와 RFC 1123 HTTP-date를 지원합니다. 자동 대기는 1~60초이며 1회만 재시도합니다.
- HTTP 400/422는 `INVALID_INPUT`, 401/403은 `PERMISSION_DENIED`, 404는 `NO_DATA`, 5xx는 retryable `NETWORK_UNAVAILABLE`로 매핑합니다.
- cancel-before-run과 latest-request-wins를 네트워크 없이 결정적으로 검증합니다. 취소되거나 구세대인 callback은 소비자에게 전달하지 않습니다.
- `GuidanceConfig`에 `maximumBacktrackM=15m`, `maximumForwardJumpM=500m`를 추가했습니다. 기본값이 있어 기존 생성 코드는 소스 호환됩니다.
- 긴 단일 선분, GPS 전후 순간 이동, loop·평행 복귀 경로에서도 진행량이 크게 점프하지 않도록 제한합니다.
- Replay pause/resume과 동일 timestamp 중복 거부 시험을 추가했습니다.

v2는 다중 leg 지원, UI, WebView protocol, Activity, 공통 contract, 실제 센서 adapter를 변경하지 않습니다. 이 범위를 확장하려면 Manager가 contract version과 영향 범위를 먼저 승인해야 합니다.

### 5. 공개 API와 Manager 연결 원칙

주요 API는 `RouteProvider.request`, `GuidanceEngine.setRoute/update/clear`, `ReplayTelemetryProvider.start/stop/pause/resume/seek/reset`입니다. `RouteProvider`와 telemetry callback은 worker thread에서 호출될 수 있으므로 Manager의 단일 reducer queue에서 모든 상태 변경을 직렬화해야 합니다.

```kotlin
val routeProvider = ValhallaRouteProvider(clientId = "starlog-android")
val guidanceEngine: GuidanceEngine = DefaultGuidanceEngine()

val cancelHandle = routeProvider.request(routeRequest) { outcome ->
    reducerExecutor.execute {
        when (outcome) {
            is Outcome.Ok -> guidanceEngine.setRoute(outcome.value)
            is Outcome.Err -> renderNavigationError(outcome.error)
        }
    }
}

fun onTelemetry(sample: Telemetry) {
    reducerExecutor.execute {
        renderGuidance(guidanceEngine.update(sample))
    }
}
```

Replay와 live adapter는 동일한 `onTelemetry` 경로를 사용하되 `SourceMode`를 바꾸지 않습니다. 웹 좌표는 반드시 `[lon, lat]`로 변환하고, Kotlin `LatLon`은 `lat/lon` 순서를 유지합니다.

### 6. Manager AI 필수 작업

1. `NavigationModels.kt`를 승인된 공통 contract와 비교합니다. 동일 DTO를 여러 패키지에 유지하지 말고 공통 타입 교체 또는 명시적 adapter 중 하나를 선택합니다.
2. `RouteProvider` callback, live telemetry, Replay를 단일 reducer queue로 직렬화합니다.
3. `GuidanceState`를 RenderState에 연결하고 route geometry를 `[lon, lat]`로 변환합니다.
4. `OFF_ROUTE` 이후 재탐색은 Manager가 최소 5초 debounce합니다. telemetry tick마다 route API를 호출하지 않습니다.
5. route 교체·Activity 종료 시 `CancelHandle.cancel()`, Replay 종료 시 `stop()`, session 종료 시 `ValhallaRouteProvider.close()`를 호출합니다.
6. v2를 채택할 경우 `2e94bf4`의 API 기본값과 HTTP 오류 매핑이 공통 error 정책에 맞는지 검토합니다.
7. Android SDK와 Pleos 환경에서 아래 검증 명령 및 실환경 시나리오를 실행하고 결과 SHA를 기록합니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.starlog2.navigation.*"
.\gradlew.bat :app:lintDebug
```

### 7. 검증 증거

| 대상 | 환경/명령 | 결과 |
| --- | --- | --- |
| v1 `b4180e8` | Java 25, Kotlin/JVM 수동 compile + JUnit 4 | `OK (19 tests)` |
| v2 `2e94bf4` | Java 25, Kotlin/JVM 수동 compile + JUnit 4 | `OK (30 tests)` |
| Android Gradle unit test | Android SDK 미설치 | `NOT_RUN` |
| `:app:lintDebug` | Android SDK 미설치 | `NOT_RUN` |
| 공개 Valhalla live 요청 | recorded/fake boundary 우선 | `NOT_RUN` |
| Pleos 실제 위치·WebView 연결 | Manager 소유 환경 필요 | `NOT_RUN` |

30개 테스트는 polyline 오류, 거리·방위 wrap, 90도 회전, remaining, low accuracy/no-fix, 3회 이탈, 도착 유지, route 교체, 전후 GPS 순간 이동, 평행 복귀 경로, Replay 결정성·pause/resume, Valhalla km→m·단일 leg·maneuver index·429·timeout·cancel·latest request·HTTP 오류 매핑을 포함합니다.

### 8. 알려진 한계와 금지 사항

- remaining seconds는 거리 비례 추정치이며 live traffic ETA가 아닙니다.
- route projection은 제한된 window의 local projection이며 상용 map matching이 아닙니다.
- 다중 leg는 지원하지 않고 `UNSUPPORTED_ROUTE`를 반환합니다.
- 공개 API를 매 telemetry tick마다 호출하지 않습니다.
- Replay 결과를 live 측정값으로 표시하지 않습니다.
- Manager 검증 전 v2를 `master`에 직접 반영하거나 v1을 중복 cherry-pick하지 않습니다.
- 실제 통합·실환경 검증이 끝나기 전 전체 Navigation 완료로 보고하지 않습니다.

Manager 완료 보고에는 적용한 SHA, contract version/hash, adapter 또는 타입 교체 내역, 테스트 명령과 exit code, live/Replay 구분, Pleos 실환경 결과, known issues를 포함해야 합니다.

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
