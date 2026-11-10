# LAPTOP_1 Navigation Core v1/v2 기술 산출물

문서 상태: Manager 검토용  
작성 기준일: 2026-11-10 KST  
저장소: `xodrndishfwk-crypto/HMG_Pleos`  
담당: LAPTOP_1 / Navigation core  
코드 패키지: `com.example.starlog2.navigation`

## 1. 산출물 요약

LAPTOP_1은 경로 요청, polyline6 geometry, 주행 진행, 회전 안내, 경로 이탈, 도착 판정과 Replay telemetry를 구현했습니다. v1은 `master`에 반영됐고, v2는 `jioni`에서 네트워크 경계와 진행량 안정성을 추가로 보강했습니다.

| 구분 | 코드 기준 | 상태 |
| --- | --- | --- |
| v1 | `master` / `b4180e8` | 반영 완료, Manager 통합·실환경 연결 대기 |
| v2 | `jioni` / `2e94bf4` | 코드 완료, Manager 검토 전 |
| 최신 보고 문서 | 최신 `origin/jioni` | README와 본 문서 |
| v1 이전 기준 | `8de49a1` | 기존 master 이력으로 보존 |

v1은 force push나 reset 없이 기존 master에서 fast-forward로 반영했습니다. v2 코드는 master에 포함되지 않습니다.

## 2. 범위

### 포함

- Valhalla route 요청과 단일 leg 응답 처리
- polyline6 decode와 방어적 입력 검증
- 거리, bearing, 보간과 lookahead 계산
- route 선분 투영과 진행량 계산
- remaining distance/seconds와 다음 maneuver 계산
- 저정확도/no-fix 처리, 이탈과 도착 판정
- Replay telemetry 생성과 재생 제어
- timeout, 취소, latest-request-wins와 429 처리
- 순수 Kotlin/JVM 단위 테스트

### 제외

- Activity와 WebView 연결
- Pleos 실차 위치·속도·gear adapter
- 공통 contract 확정과 package 이동
- 여러 Valhalla leg flattening
- 상용 map matching 및 live traffic ETA
- route 재요청 debounce 실행
- UI와 RenderState 직렬화

제외 범위는 Manager/HIGH_PC 소유이거나 공통 contract 승인이 필요한 작업입니다.

## 3. 커밋과 반영 위치

| 커밋 | 유형 | 설명 | master | jioni |
| --- | --- | --- | --- | --- |
| `4f34ee2` | `feat(navigation)` | v1 Navigation 코어와 19개 테스트 | 포함 | 포함 |
| `aec01ec` | `docs(readme)` | Git 브랜치 컨벤션 | 포함 | 포함 |
| `b4180e8` | `docs(readme)` | v1 Manager 인수인계 | 포함 | 포함 |
| `2e94bf4` | `feat(navigation)` | v2 신뢰성·진행 안정성, 총 30개 테스트 | 제외 | 포함 |
| `ecae96d` 이후 | `docs(readme)` | v2 진행도와 Manager 보고서 | 제외 | 포함 |

Manager는 v1을 다시 cherry-pick하지 않습니다. v2 적용이 승인되면 코드 커밋 `2e94bf4`를 검토 대상으로 사용하고 문서 커밋은 코드 반영과 분리할 수 있습니다.

## 4. 구성과 데이터 흐름

```text
RouteRequest
    ↓
ValhallaRouteProvider ── 실패 → Outcome.Err(AppError)
    ↓ Outcome.Ok(Route)
GuidanceEngine.setRoute
    ↓
Telemetry(LIVE 또는 REPLAY)
    ↓
GuidanceEngine.update
    ↓
GuidanceState
    ↓
Manager reducer → RenderState → WebView
```

`RouteProvider`와 telemetry callback은 worker thread에서 실행될 수 있습니다. Manager는 하나의 reducer queue에서 `setRoute`, `update`, 오류 처리와 RenderState 생성을 직렬화해야 합니다.

## 5. 주요 구성요소

| 파일 | 책임 |
| --- | --- |
| `NavigationModels.kt` | v1 제안 DTO와 provider/engine 인터페이스 |
| `Polyline6.kt` | polyline6 decode와 크기·문자·범위 검증 |
| `NavigationGeometry.kt` | 거리, bearing, smoothing, 선분 투영, 보간, lookahead |
| `DefaultGuidanceEngine.kt` | 진행, maneuver, 이탈, 도착 상태 계산 |
| `ValhallaRouteProvider.kt` | HTTP 요청, retry, 취소, 응답 검증 |
| `ReplayTelemetryProvider.kt` | 결정적 Replay sample과 재생 제어 |
| `SimpleJson.kt` | Android 프레임워크에 의존하지 않는 제한적 JSON parser |

대응 테스트는 `app/src/test/java/com/example/starlog2/navigation/**`에 있습니다.

## 6. 공개 API

| API | 입력 | 출력/효과 |
| --- | --- | --- |
| `RouteProvider.request` | `RouteRequest` | `Outcome<Route>` callback과 `CancelHandle` |
| `GuidanceEngine.setRoute` | `Route` | 기존 진행·도착·이탈 상태 초기화 |
| `GuidanceEngine.update` | `Telemetry` | `GuidanceState` |
| `GuidanceEngine.clear` | 없음 | 현재 route와 진행 상태 제거 |
| `ReplayTelemetryProvider.start` | sample/error callback | Replay worker 시작, 중복 start 방지 |
| `pause` / `resume` | 없음 | monotonic 기준 재생 중단·재개 |
| `seek` / `reset` | position 또는 0 | cursor와 seq 결정성 유지 |
| `stop` | 없음 | worker와 callback 해제 |

### 연결 예시

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

Replay와 live adapter는 동일한 `onTelemetry` 경로를 사용하고 `SourceMode`를 유지합니다.

## 7. 기능 명세

### Route와 Valhalla

- endpoint 기본값: `https://valhalla1.openstreetmap.de/route`
- method/content type: `POST`, `application/json`
- costing/units/language: `auto`, `kilometers`, `ko-KR`
- connect/read timeout: 10초/15초
- response 제한: 2MB
- geometry 제한: 2~50,000 points
- v1 route: 단일 leg만 허용
- summary length: km에서 m로 변환
- duration: seconds 유지
- maneuver begin/end shape index를 검증
- 새 요청은 이전 요청을 취소하고 늦은 callback을 무시

### Guidance

- 유효 fix를 route 선분에 투영해 `snappedPoint`와 `alongM` 계산
- geometry 진행 비율로 route distance와 duration의 remaining 계산
- 다음 maneuver index, instruction과 거리 계산
- route 교체 시 projection window와 모든 상태 초기화
- 도착 후 상태는 다음 route 또는 clear까지 `ARRIVED` 유지

### Replay

- fixture offset 기준으로 seq와 monotonic timestamp 생성
- 동일 fixture와 seek/reset 순서에 동일한 sample 생성
- 위치가 없으면 `Quality.UNAVAILABLE`
- Replay 결과의 `SourceMode.REPLAY` 유지
- 동일 offset 중복은 모호한 순서를 방지하기 위해 거부

## 8. 설정값

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `deviationThresholdM` | 40m | route 이탈 거리 |
| `deviationFixCount` | 3 | OFF_ROUTE 전 연속 유효 fix 수 |
| `maximumAccuracyM` | 30m | 이탈 카운터에 포함할 최대 정확도 |
| `arrivalAlongM` | 20m | route 끝까지 남은 허용 거리 |
| `arrivalEndpointM` | 20m | 실제 endpoint까지 허용 거리 |
| `backwardSegmentWindow` | 2 | 이전 진행점 기준 뒤쪽 탐색 segment 수 |
| `forwardSegmentWindow` | 8 | 이전 진행점 기준 앞쪽 탐색 segment 수 |
| `maximumBacktrackM` | 15m | v2 진행량 후퇴 제한 |
| `maximumForwardJumpM` | 500m | v2 진행량 전진 점프 제한 |

이 값은 초기 제안값입니다. 제품 요구로 확정하려면 Manager가 contract/config 버전과 실차 시험 결과를 기록해야 합니다.

## 9. 오류 정책

| 조건 | AppError code | retryable |
| --- | --- | --- |
| connect/read timeout | `NETWORK_TIMEOUT` | true |
| HTTP 400/422 | `INVALID_INPUT` | false |
| HTTP 401/403 | `PERMISSION_DENIED` | false |
| HTTP 404 | `NO_DATA` | false |
| HTTP 429 | `RATE_LIMITED` | true |
| HTTP 5xx/IO | `NETWORK_UNAVAILABLE` | true |
| invalid JSON/shape/index/body | `INVALID_RESPONSE` | false |
| 여러 leg | `UNSUPPORTED_ROUTE` | false |

`Retry-After`는 정수 초 또는 RFC 1123 HTTP-date를 지원합니다. 1~60초일 때 한 번만 자동 재시도하며 무한 재시도하지 않습니다.

## 10. 요구사항 추적표

| ID | 요구사항 | 구현 | 대표 검증 |
| --- | --- | --- | --- |
| NAV-01 | polyline6 안전 decode | `Polyline6` | valid/truncated/invalid/point-limit |
| NAV-02 | 거리·방위·lookahead | `NavigationGeometry` | cardinal bearing, wrap, right turn |
| NAV-03 | Valhalla 단일 leg route | `ValhallaRouteProvider` | km→m, maneuver index, multi-leg reject |
| NAV-04 | 진행과 maneuver 안내 | `DefaultGuidanceEngine` | 90도 회전, remaining |
| NAV-05 | low accuracy/no-fix | `DefaultGuidanceEngine` | counter 제외, `NO_FIX` |
| NAV-06 | OFF_ROUTE | `DefaultGuidanceEngine` | 40m 초과 3회 |
| NAV-07 | ARRIVED와 route 교체 | `DefaultGuidanceEngine` | 도착 유지, 교체 초기화 |
| NAV-08 | loop·평행도로 점프 방지 | projection window/v2 jump limit | parallel return, 전후 teleport |
| NAV-09 | 결정적 Replay | `ReplayTelemetryProvider` | reset 동일성, seek, pause/resume |
| NAV-10 | timeout·cancel·latest request | `ValhallaRouteProvider` | fake connection/executor |
| NAV-11 | 429 정책 | `ValhallaRouteProvider` | seconds/date retry-after |

## 11. 검증 결과

| 대상 | 결과 | 비고 |
| --- | --- | --- |
| v1 `b4180e8` Kotlin/JVM compile | PASS | Java 25 |
| v1 JUnit 4 | `OK (19 tests)` | master fast-forward 후 재검증 |
| v2 `2e94bf4` Kotlin/JVM compile | PASS | Java 25 |
| v2 JUnit 4 | `OK (30 tests)` | 기존 19 + 경계 11 |
| Android Gradle unit test | NOT_RUN | 이 장비에 Android SDK 없음 |
| `:app:lintDebug` | NOT_RUN | 이 장비에 Android SDK 없음 |
| 공개 Valhalla live route | NOT_RUN | fake/recorded boundary 우선 |
| Pleos 실제 입력과 WebView | NOT_RUN | Manager 환경 필요 |

Manager 환경의 필수 재검증 명령:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.starlog2.navigation.*"
.\gradlew.bat :app:lintDebug
```

## 12. 통합 및 인수 조건

### Manager 작업

1. `NavigationModels.kt`와 승인된 공통 contract를 비교합니다.
2. 공통 타입으로 교체하거나 명시적 adapter를 작성합니다.
3. provider와 telemetry callback을 단일 reducer queue로 직렬화합니다.
4. route 좌표를 WebView에 `[lon, lat]`로 전달합니다.
5. `OFF_ROUTE` 이후 route 재요청에 최소 5초 debounce를 적용합니다.
6. Activity/session 종료 시 cancel, stop, close를 호출합니다.
7. Android unit/lint 및 Pleos 실환경 시나리오를 실행합니다.
8. 적용 SHA와 contract hash를 Issue/PR에 기록합니다.

### 인수 통과 기준

- [ ] 공통 contract 중복 DTO가 없음
- [ ] navigation JVM/Android unit test 통과
- [ ] lint 통과 또는 기존/신규 이슈 구분 기록
- [ ] Replay와 live source mode가 화면까지 유지
- [ ] route geometry의 lon/lat 순서 확인
- [ ] 90도 maneuver 전후 안내 확인
- [ ] low accuracy/no-fix가 이탈로 오판되지 않음
- [ ] 이탈 3회 후 재탐색 debounce 확인
- [ ] endpoint 20m 도착과 route 교체 초기화 확인
- [ ] timeout, cancel, 429와 late reply 확인
- [ ] 실제 Valhalla/Pleos 미실행 항목 결과 기록

## 13. 알려진 한계와 위험

- remaining seconds는 거리 비율 추정이며 live traffic ETA가 아닙니다.
- projection window는 경량 local matching이며 상용 map matching이 아닙니다.
- sparse telemetry가 500m 이상 전진하면 v2 기본 jump limit에 걸릴 수 있으므로 실차 주기 기준 조정이 필요합니다.
- 다중 leg는 명시적으로 거부합니다.
- 공통 contract가 확정되지 않아 Navigation 내부에 제안 DTO가 있습니다.
- Android SDK, live Valhalla와 Pleos 실환경 검증은 아직 수행되지 않았습니다.

## 14. 배포와 롤백

- v1은 `master`의 `b4180e8`에 존재하므로 다시 cherry-pick하지 않습니다.
- v2 적용 전 `2e94bf4`의 diff와 공통 error/config 정책을 검토합니다.
- v2에 문제가 있으면 force push/reset 대신 해당 merge 또는 cherry-pick을 `git revert`합니다.
- `jioni`의 README 보고 커밋은 기능 코드와 분리해 선택할 수 있습니다.

## 15. 최종 상태

LAPTOP_1 기준으로 v1 코드는 master 반영 완료, v2 코드는 jioni에서 구현·JVM 검증 완료입니다. 전체 앱 통합 완료는 Manager가 공통 contract, Android build/lint, 실제 위치와 WebView 연결을 검증한 뒤에만 선언합니다.
