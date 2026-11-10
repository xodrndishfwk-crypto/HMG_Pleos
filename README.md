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

## jioni 내비게이션 코어 인수인계 (Manager 노드)

### 작업 상태와 기준

| 항목 | 내용 |
| --- | --- |
| 담당 | LAPTOP_1 / Navigation core |
| 브랜치 | `jioni` |
| 기준 master | `8de49a1` |
| 구현 커밋 | `4f34ee2` (`feat(navigation): implement route guidance and replay core`) |
| Manager v1 인수 기준 | `b4180e8` (v1 코드·문서 고정점) |
| LAPTOP_1 v2 커밋 | `2e94bf4` (`feat(navigation): harden route and progress handling`) |
| 현재 상태 | v1 Manager integration pending / v2 reliability hardening complete |
| 소유 경로 | `app/src/main/java/com/example/starlog2/navigation/**` 및 대응 unit test |

이 구현은 Activity, Manifest, Gradle, 기존 Pleos Vehicle 코드를 변경하지 않습니다. 현재 저장소의 `docs/team/BASELINE.md`는 `NOT_READY`이고 공통 contract 코드가 아직 없으므로, 제안된 v1 타입을 `com.example.starlog2.navigation` 안에 함께 구현했습니다. Manager는 통합 전에 공통 contract의 실제 타입과 비교하여 중복 DTO를 제거하거나 adapter를 두어야 합니다.

### v1과 v2 구분

- Manager가 먼저 통합할 v1 기준은 `b4180e8`입니다. 경로·geometry·guidance·Replay의 기본 동작과 Manager 인수 문서가 포함됩니다.
- LAPTOP_1 v2는 `2e94bf4`부터 시작합니다. Manager의 v1 통합을 방해하지 않도록 공통 contract나 Activity는 수정하지 않고 navigation 내부의 신뢰성과 결정적 시험을 보강했습니다.
- v2에서 다중 leg, UI, WebView protocol 변경, 공통 contract 확정은 수행하지 않습니다. 해당 변경은 Manager 승인과 새 contract version이 필요합니다.

v2에서 완료한 항목은 다음과 같습니다.

- HTTP connection factory, retry sleep, wall clock을 주입 가능하게 해 공개 서버 없이 RouteProvider 경계를 검증.
- `Retry-After`의 초 형식과 RFC 1123 HTTP-date 형식을 지원하고 60초를 초과하는 자동 대기는 거부.
- HTTP 400/422→`INVALID_INPUT`, 401/403→`PERMISSION_DENIED`, 404→`NO_DATA`, 5xx→retryable `NETWORK_UNAVAILABLE` 매핑.
- cancel-before-run과 latest-request-wins를 결정적 executor로 검증하여 이전 callback이 소비자에게 전달되지 않음을 확인.
- `maximumBacktrackM`와 `maximumForwardJumpM`을 추가해 긴 선분에서도 GPS 순간 이동으로 진행량이 크게 되감기거나 앞서가지 않도록 제한.
- 평행 복귀 경로, Replay pause/resume, 중복 replay timestamp 거부 시험 추가.

### 구현된 기능

- `Polyline6`: polyline6 decode, 잘린 문자열·잘못된 문자·좌표 overflow·빈/1점 geometry·5만 점 초과 방어.
- `NavigationGeometry`: Haversine 거리, true-north bearing, 359°→1° wrap smoothing, 경로 보간, 기본 310m lookahead bearing.
- `ValhallaRouteProvider`: 단일 비동기 요청, 이전 요청 취소 및 late reply 차단, connect 10초/read 15초 timeout, 최대 2MB 응답, HTTP 429 `Retry-After` 1회 재시도.
- Valhalla 응답의 `trip.summary.length` km→m 변환, 초 단위 유지, polyline6 shape와 maneuver index 검증.
- v1에서 여러 leg가 오면 첫 leg만 조용히 사용하지 않고 `UNSUPPORTED_ROUTE`로 반환.
- `DefaultGuidanceEngine`: 현재 위치의 경로 선분 투영, `snappedPoint`/`alongM`/remaining distance·seconds, 다음 maneuver와 거리 계산.
- loop·평행도로에서 먼 선분으로 갑자기 이동하지 않도록 이전 진행 선분 기준 탐색 window 적용.
- 정확도 30m 이하 fix에서 경로 이탈 40m 초과가 3회 연속이면 `OFF_ROUTE`; 낮은 정확도는 이탈 카운터에 포함하지 않음.
- 마지막 along distance와 endpoint distance가 각각 20m 이하이면 `ARRIVED`; route 교체 시 진행·도착·이탈 상태 초기화.
- `ReplayTelemetryProvider`: fixture 기반 `Telemetry` 생성, `pause`/`resume`/`seek`/`reset`/`stop`, 동일 입력에 동일 seq/state 제공.

초기 임계값은 모두 `GuidanceConfig`로 노출되어 있습니다.

```kotlin
GuidanceConfig(
    deviationThresholdM = 40.0,
    deviationFixCount = 3,
    maximumAccuracyM = 30.0,
    arrivalAlongM = 20.0,
    arrivalEndpointM = 20.0,
    maximumBacktrackM = 15.0,
    maximumForwardJumpM = 500.0,
)
```

### Manager 연결 예시

`RouteProvider` callback과 telemetry callback은 worker thread에서 들어올 수 있습니다. Manager가 소유한 단일 reducer queue에서 `setRoute`와 `update`를 직렬화해야 합니다.

```kotlin
val routeProvider = ValhallaRouteProvider(clientId = "starlog-android")
val guidanceEngine: GuidanceEngine = DefaultGuidanceEngine()

val cancelHandle = routeProvider.request(
    RouteRequest(
        requestId = "route-42",
        start = LatLon(37.497952, 127.027619),
        end = LatLon(37.504501, 127.048962),
    ),
) { outcome ->
    reducerExecutor.execute {
        when (outcome) {
            is Outcome.Ok -> guidanceEngine.setRoute(outcome.value)
            is Outcome.Err -> renderNavigationError(outcome.error)
        }
    }
}

fun onTelemetry(sample: Telemetry) {
    reducerExecutor.execute {
        val guidanceState = guidanceEngine.update(sample)
        renderGuidance(guidanceState)
    }
}

// Activity/session 종료 또는 새 요청으로 교체할 때
cancelHandle.cancel()
routeProvider.close()
```

Replay도 같은 `Telemetry` 입력 경로에 연결합니다. Replay 결과를 실차 입력으로 표시하지 말고 `sourceMode == SourceMode.REPLAY`를 RenderState까지 유지해야 합니다.

```kotlin
val replay = ReplayTelemetryProvider(frames = recordedFrames)
replay.start(
    onSample = ::onTelemetry,
    onError = ::renderNavigationError,
)
```

### 통합 시 Manager가 해야 할 작업

1. 공통 contract가 확정되면 `NavigationModels.kt`의 타입을 공통 타입으로 교체하거나 명시적 adapter를 추가합니다.
2. Vehicle/live location adapter와 Replay가 모두 같은 reducer queue로 `Telemetry`를 전달하게 합니다.
3. `GuidanceState`를 전체 RenderState의 `guidance`에 넣고, route geometry는 웹 좌표 순서 `[lon, lat]`로 변환합니다.
4. `OFF_ROUTE` 수신 시 Manager가 최소 5초 debounce 후 새 route 요청을 만들며, 매 telemetry tick마다 API를 호출하지 않습니다.
5. Activity/session 수명에 맞춰 `CancelHandle.cancel()`, `ReplayTelemetryProvider.stop()`, `ValhallaRouteProvider.close()`를 호출합니다.
6. Pleos 실제 위치 입력과 WebView를 연결한 뒤 route 좌표 정합, maneuver 전후, 도착 1회, 이탈 후 재탐색을 통합 시험합니다.

### 검증 결과와 현재 한계

- Java 25의 Kotlin/JVM 컴파일: 성공.
- navigation JUnit 4 테스트: `OK (30 tests)`.
- 검증 시나리오: polyline 오류, 거리·방위 wrap, 90도 회전, remaining, low accuracy/no fix, 3회 이탈, 도착 유지, route 교체, 장거리 전후 GPS 순간 이동, 평행 복귀 경로, Replay 결정성·pause/resume, Valhalla km→m·단일 leg·maneuver index·429·timeout·cancel·latest reply·HTTP 오류 매핑.
- 이 노트북에는 Android SDK 위치가 없어 `:app:testDebugUnitTest`와 `:app:lintDebug`는 이번 변경에서 `NOT_RUN`입니다. Android SDK가 있는 Manager PC에서 재실행해야 합니다.
- 공개 Valhalla 서버를 사용하는 live route 요청과 Pleos 실차/에뮬레이터 위치 입력은 `NOT_RUN`입니다.
- remaining seconds는 경로 비용을 진행 거리 비율로 나눈 추정치이며 live traffic ETA가 아닙니다.
- 경로 투영은 제한된 window의 local projection으로 상용 map matching과 동일하지 않습니다.
- 자동 429 재시도는 `Retry-After`가 1~60초일 때 한 번만 수행합니다.

Manager PC 권장 검증 명령은 다음과 같습니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.starlog2.navigation.*"
.\gradlew.bat :app:lintDebug
```

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
