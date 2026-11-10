# LAPTOP_1 Navigation G1 작업 보고서

## 1. 보고 기준

| 항목 | 값 |
| --- | --- |
| 보고 버전 | `N2-G1-r1` |
| 작성일 | 2026-11-10 KST |
| 송신 / 수신 | LAPTOP_1 / HIGH_PC Manager Node |
| 작업 브랜치 | `jioni` |
| 구현 SHA | `5df4a55c2e95972650d055309f2aa8ecfd6963c0` |
| integration 기준 SHA | `a5eda671d6b371c94bbefb46c2d0825756875e34` |
| 승인 BASE_SHA | `551079298c10c88b6e43ac212d95045b3904233b` |
| 계약 버전 | `starlog-contracts-1.0.0` |
| manifest SHA-256 | `f9601d5928d5615c1c6c878c58e96af1d743e86a9f24b37eb228661f85ccb62b` |
| PR | [#6](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/6) |
| 담당 Issue | [#4](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4) |

## 2. 진행률과 현재 상태

- LAPTOP_1 담당 구현 진행률: **100%**. G1 기준 확인, BASELINE_ACK, canonical 계약 이전, v2 및 독립 수정 반영, 시험 코드, PR, HANDOFF_READY까지 완료했습니다.
- 통합 인수 진행률: **4/6 단계(67%)**. `기준 확인 → 구현 → 시험 코드 → PR/인수인계`를 완료했고, `HIGH 공유 코드 정리·Android 검증 → integration 병합 및 재시험`이 남았습니다.
- 현재 상태는 `HANDOFF_READY`입니다. HIGH의 검토·시험·병합 전이므로 `ACCEPTED`, `INTEGRATED`, `DONE`으로 표시하지 않습니다.

## 3. 수행한 작업

1. 최신 `origin/integration/starlog-v1`에서 새 기능 브랜치를 만들었습니다.
2. 승인 SHA의 ancestry와 G1 manifest 27개 지문을 확인했습니다.
3. `NavigationModels.kt`의 중복 DTO, enum, interface를 삭제했습니다.
4. Navigation 구현과 시험이 `com.example.starlog2.contracts`의 canonical 타입과 `Outcome.Ok/Err`를 직접 사용하도록 변경했습니다.
5. 기존 Navigation v2의 진행량 역행·과도한 전진·평행 복귀 경로 방어를 반영했습니다.
6. timeout, cancel, latest-request-wins, HTTP 오류, 429 1회 재시도, off-route, arrival, Replay 구분 회귀 동작을 유지했습니다.
7. `ce418b0`의 Retry-After 극단값 overflow 방어와 `2438fe9`의 polyline high-bit overflow 방어를 반영했습니다.
8. `jioni`를 head로 사용하는 integration 대상 PR #6과 Issue #4에 HANDOFF_READY, CHANGE_REQUEST, TEST_DELEGATION을 기록했습니다.

## 4. 변경 범위

구현 변경은 `app/src/main/java/com/example/starlog2/navigation/**`, 시험 변경은 `app/src/test/java/com/example/starlog2/navigation/**`입니다. 핵심 변경은 다음과 같습니다.

- `NavigationModels.kt` 삭제 및 `NavigationErrors.kt` 추가
- `DefaultGuidanceEngine.kt`: canonical 계약 및 진행량 안정화
- `ValhallaRouteProvider.kt`: canonical 계약, timeout/cancel/429/HTTP 오류, Retry-After overflow 방어
- `Polyline6.kt`: high-bit overflow 방어
- `ReplayTelemetryProvider.kt`, `NavigationGeometry.kt`: canonical 계약 직접 사용
- Navigation 단위 시험 32개: v2 회귀 31개 + polyline overflow 회귀 1개

공유 contracts, Gradle, Manifest, fixture는 기능 커밋에서 직접 수정하지 않았습니다.

## 5. 검증 결과

| 검사 | 결과 |
| --- | --- |
| `git merge-base --is-ancestor <approved SHA> HEAD` | exit 0 |
| `.\tools\Verify-G1.ps1` | exit 0, manifest 27 files PASS |
| manifest SHA-256 | 승인값과 일치 |
| `git diff --check` | exit 0 |
| Navigation 시험 정의 | 32개 |
| Gradle Navigation/lint | `NOT_RUN`; SDK location not found, exit 1 at configuration |
| 공개 Valhalla, Pleos live telemetry, WebView 안내, 실차/전체 주행 | `NOT_RUN` |

실행하지 못한 검사를 PASS로 표시하지 않았습니다. LAPTOP_1에는 `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties`와 Android SDK가 없습니다.

## 6. HIGH_PC 변경 요청

canonical 이전으로 더 이상 필요하지 않은 HIGH 소유 공유 코드를 PR 검토와 함께 정리해야 합니다.

1. `app/src/main/java/com/example/starlog2/integration/NavigationContractAdapter.kt` 삭제
2. `ContractHarnessTest.kt`의 adapter 및 legacy Navigation 타입 시험을 canonical 직접 시험으로 이전
3. `ContractGoldenInstrumentedTest.kt`의 `com.example.starlog2.navigation.*` legacy 참조를 canonical contracts로 이전
4. 위 변경을 포함한 정확한 SHA에서 assemble, contracts 및 Navigation 32개 단위 시험, lint, androidTest, golden/recovery 시험 실행

## 7. Manager Node 다음 행동

1. PR #6의 `jioni` head SHA와 계약 버전/hash를 확인합니다.
2. 위 CHANGE_REQUEST를 HIGH 소유 경로에 반영합니다.
3. Android SDK 및 Pleos API34 환경에서 TEST_DELEGATION 명령을 실행합니다.
4. 실패하면 재현 입력, 기대/실제, 정확한 SHA를 Issue #4에 `REJECTED`로 기록합니다.
5. 통과한 경우에만 `ACCEPTED`를 기록하고 integration에 병합·재시험한 뒤 `INTEGRATED`로 전환합니다.
6. 이후 I1 reducer queue, `[lon, lat]`, maneuver, OFF_ROUTE debounce, lifecycle 취소를 연결합니다.

## 8. 알려진 제약

- remaining seconds는 geometry 진행 비율 기반 추정치이며 live traffic ETA가 아닙니다.
- 제한된 window의 local projection은 상용 map matching이 아닙니다.
- 다중 leg는 `UNSUPPORTED_ROUTE`입니다.
- 429 자동 재시도는 유효한 Retry-After 1~60초에 한 번만 수행합니다.
- Replay 입력은 `SourceMode.REPLAY`로 유지해야 하며 LIVE로 표시하지 않습니다.
