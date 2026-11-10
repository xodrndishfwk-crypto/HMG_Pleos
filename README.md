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

## Git 브랜치 및 협업 컨벤션

적용 기준: `master`의 `4809f24` (`docs(readme): refine git branch and staging conventions`). 이제부터 3대 장비는 아래 규칙으로 형상관리합니다.

### 1. 브랜치 규칙

- `master`: 최종 검증된 통합 결과만 유지합니다. 코드 구현을 직접 병합하거나 `force push`하지 않습니다.
- `integration/<기능>-v<버전>`: 여러 담당자의 기능을 합쳐 검증하는 통합 브랜치이며 **모든 기능 PR의 Base 브랜치**입니다. 현재 대상은 `integration/starlog-v1`입니다.
- `jioni` / `seobice`: 각각 LAPTOP_1 내비게이션과 LAPTOP_2 스타로그 도메인의 작업 기준 브랜치입니다.
- `feat/<기능>-v<버전>`: 독립 기능 개발 브랜치입니다. 예: `feat/sl-nav-v1`, `feat/sl-memory-v1`.
- `fix/<기능>-<설명>`: 확인된 결함 수정 브랜치입니다. 예: `fix/navigation-off-route`.
- `docs/<설명>` 및 `chore/<설명>`: 문서와 빌드·설정·정리 작업에 사용합니다.

### 2. 담당 경로 스테이징 규칙

`git add .`을 사용하지 않고 담당 경로만 명시적으로 스테이징합니다.

- HIGH_PC: `contracts/**`, `integration/**`, `platform/**`, `MainActivity`, WebGL/assets, `build.gradle.kts`, `AndroidManifest.xml`, `docs/**`
- LAPTOP_1 (`jioni`): `app/src/{main,test}/java/com/example/starlog2/navigation/**`
- LAPTOP_2 (`seobice`): `app/src/{main,test,androidTest}/java/com/example/starlog2/starlog/**`
- 커밋 제외: `*.jks`, `*.apk`, `local.properties`, `.gradle/`, `build/` 및 타인 담당 파일

공유 설정이나 의존성 변경이 필요하면 직접 커밋하지 않고 Manager에게 `CHANGE_REQUEST`를 먼저 남깁니다. README 수정은 이번 사용자 요청에 따른 명시적 문서 변경이며 다른 `docs/**` 파일은 함께 스테이징하지 않습니다.

### 3. 커밋과 PR 규칙

- 커밋 메시지: `<type>(<scope>): <summary>` 형식, 현재형, 50자 이내
- type: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`
- scope: `navigation`, `starlog`, `contracts`, `renderer`, `platform`, `readme`, `team`
- 새 브랜치는 `git fetch origin` 후 최신 기준 브랜치에서 생성
- 단위 테스트와 `git diff --cached --name-only` 확인 후 PR 생성
- 기능 PR은 `integration/starlog-v1`을 Base로 사용하고 HIGH 검증 전 `master`에 직접 반영하지 않음

## LAPTOP_1 Navigation v1/v2 보고서 (Manager AI용)

보고 기준: 2026-11-10 KST. 이 절은 Manager AI가 브랜치 상태를 오해하지 않고 검토·통합·실환경 검증을 이어가기 위한 실행 보고서입니다.

정식 기술 산출물은 [`docs/LAPTOP_1_NAVIGATION_DELIVERABLE.md`](docs/LAPTOP_1_NAVIGATION_DELIVERABLE.md)를 기준으로 합니다. 이 README는 빠른 인수 판단을 위한 요약입니다.

워크플로우 준수 여부와 정상화 상태는 [`docs/LAPTOP_1_WORKFLOW_AUDIT.md`](docs/LAPTOP_1_WORKFLOW_AUDIT.md)를 먼저 확인하세요. 현재 G1은 `NOT_READY`이며 v2는 `WAITING_G1_DRAFT_REVIEW`입니다.

### 현재 워크플로우 공식 진도

| 단계 | 상태 | 근거와 다음 행동 |
| --- | --- | --- |
| G0 원본 준비 | HIGH 완료 | HIGH가 원본 코드·빌드·Pleos G0를 검증함 |
| G1 공통 준비 | `NOT_READY` | 승인 BASE_SHA, contract/fixture hash와 H-G1 대기 |
| L1-1 환경·시나리오 준비 | 완료 | 저장소 접근, `jioni` 기준선, 수동 JVM 시험 확인 |
| L1-2 G1 수신·검증 | 대기 | H-G1 수신 후 `BASELINE_ACK` 필요 |
| N1 경로·진행·Replay | `PRE_G1_ARTIFACT` | 구현은 존재하지만 승인 contract 기반 수락 전 |
| N2 오류·이탈·도착 | `PRE_G1 + INDEPENDENT_FIX` | contract 비종속 Retry-After 결함 수정·회귀시험 완료 |
| HANDOFF | `HANDOFF_READY` | Issue #4와 Draft PR #7에 SHA·시험·다음 행동 기록 |
| RECEIVED / ACCEPTED | 대기 | HIGH의 수신 확인과 Android 검증 필요 |
| INTEGRATED / DONE | 미완료 | 통합·Pleos·전체 주행 검증 전에는 완료 처리 금지 |

현재 공식 진도는 **L1-1 완료, L1-2 대기**입니다. N1/N2 코드는 검토 가능한 PRE-G1 산출물이지만 G1 승인 전에는 공식 checkpoint 완료가 아닙니다.

- 작업 기준선: `jioni` / `9b6108c`
- 독립 수정 브랜치: `feat/sl-nav-v2`
- 독립 수정 SHA: `ce418b0` (`fix(navigation): prevent retry delay overflow`)
- Draft PR: [#7](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/7)
- 검증: Kotlin/JVM JUnit `OK (31 tests)`, exit code 0
- Android Gradle/lint 및 Pleos 실제 입력: `NOT_RUN`, HIGH에 `TEST_DELEGATION` 전달
- 다음 순서: H-G1 수신 → BASELINE_ACK → 승인 contract 적용 → 재시험 → HANDOFF revision → HIGH의 RECEIVED/ACCEPTED

### 1. 결론과 현재 브랜치 상태

| 구분 | 코드 기준 SHA | 상태 |
| --- | --- | --- |
| `master` | `4809f24` | Navigation v1과 최신 Git 협업 컨벤션 반영 |
| `jioni` | `9b6108c` | v1/v2 PRE-G1 산출물과 워크플로우 정상화 기록 |
| `feat/sl-nav-v2` | `ce418b0` | 독립 결함 수정, Draft PR #7 검토 대기 |
| v1 이전 master | `8de49a1` | 기존 팀 문서 기준점, 이력 보존 |

- v1은 `master`에 fast-forward로 반영했습니다. force push, reset, 기존 파일 삭제는 수행하지 않았습니다.
- v2 코드는 `jioni` 계열에만 있습니다. `master..jioni`의 핵심 코드 차이는 v2 기능 커밋 `2e94bf4`입니다.
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
