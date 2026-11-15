# HIGH G1 검증 및 Navigation 인수 검토

[확실] 검증일 2026-11-10 KST. 원본 폴더에서 순차 작업했고 사용자 미추적 tmp-guide-inspection/을 유지했다. 프로젝트 복사본·stash·reset/force push를 사용하지 않았다.

## 확인한 버전

| 대상 | 실제 SHA |
|---|---|
| master README | 4809f24af657323ec995a0e227c7f9c279678e0d |
| 최초 integration | b4180e86e101f4e8e7196546a93c10f4d62c5453 |
| jioni | 104f9e0908a5636593ce15e0859e006f0cf57f90 |
| feat/sl-nav-v2 / Retry-After 수정 | ce418b0a1e41c89345b6108790e81599b7efcd81 |
| polyline 독립 수정 | 2438fe994e0d62557ddde806aae05bff04af4af9 |
| G0 준비 / merge | 54f0207c2996201bdc0d1efddb1d638e64600e69 / a2950c907abcceb535d5fa70242e9646abf848a3 |
| 승인 G1 코드 | 551079298c10c88b6e43ac212d95045b3904233b |
| G1 integration merge / 재검증 | 0a827d2c62a64e5e84caf901e1809c1ba2bf094c |

[확실] 표는 실제 확인한 버전이다. 원격 브랜치는 이후 담당자가 전진시킬 수 있으므로 작업 재개 시 fetch한다. upstream master README의 Git 컨벤션은 G1 브랜치에 merge해 보존했다. 기존 v1 코드는 중복 cherry-pick하지 않았다.

## 검사 결과

[확실] 다음 각 행은 별도의 정확한 버전에서 실행했다. G1에 내비 v2가 들어갔다고 혼동하지 않는다.

| 대상 | 명령 / 실제 결과 | 판정 |
|---|---|---|
| ce418b0 | :app:testDebugUnitTest --tests "com.example.starlog2.navigation.*" :app:lintDebug --rerun-tasks · JVM31, failure0/error0 · lint0 errors/2 기존 warnings · exit0 | PASS |
| 2438fe9 | 동일 navigation/lint --rerun-tasks · JVM20(Polyline5), failure0/error0 · lint0 errors/2 기존 warnings · 12s/34 tasks executed · exit0 | PASS |
| 5510792 | :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --rerun-tasks · JVM33, failure0/error0 · lint0 issues · 19s/82 tasks executed · exit0 | PASS |
| 5510792 Pleos | ContractGoldenInstrumentedTest10 + G0RecoveryInstrumentedTest4 · OK(14 tests),44.889s · adb exit0/instrumentation code -1 | PASS |
| integration 0a827d2 | 同 빌드/JVM/lint/AndroidTestAPK 명령 · JVM33, failure0/error0 · lint0 issues · 11s/exit0 | PASS |
| integration 0a827d2 Pleos | ContractGoldenInstrumentedTest · OK(10 tests),0.291s · adb exit0/instrumentation code -1 | PASS |
| 공통 지문 | Verify-G1.ps1 · 27파일 일치 · manifest 변조 거부 확인 · LF 정책 보완 후 실제 checkout 지문 재확인 · exit0 | PASS |
| Pleos 실제 위치/heading/gear/speed 주행 | 이번 Navigation/G1 검증에서 실행하지 않음 | NOT_RUN |
| Navigation v2 Activity/WebView 안내 연결 | 아직 I1 연결 전 | NOT_RUN |
| 공개 Valhalla 실요청(v2) | v2 시험은 fake HTTP 기반 | NOT_RUN |
| 실차·전체 주행 V1 | 에뮬레이터 계약시험과 구분 | NOT_RUN |

[확실] G1 JVM33 = 기존 Navigation v1 19 + MapLoadSession3 + common harness10 + example1이다. 31(v2)과 합산하지 않는다. Navigation31의 분포는 Guidance9/Geometry4/Polyline4/Replay4/Valhalla10, 독립 polyline20은 Guidance6/Geometry4/Polyline5/Replay2/Valhalla3이다. exact SHA마다 코드 구성이 달라 수가 다르다.

[확실] ce418b0/2438fe9의 lint 경고는 기존 UseTomlInstead/SetTextI18n 2개다. G0 보완을 포함한 통합에서는 둘 다 이미 해소되어 lint0이다. suppress·lint baseline으로 숨기지 않았다. Native setPadding Kotlin deprecation 메시지는 별개로 남는다.

## Pleos 재현 명령

[확실] Windows NT build26200, Studio JBR25.0.4/Gradle daemon25/Java target17, Gradle9.6.0/AGP9.5.1, SDK37·min34, emulator-5554/API34/current user10이다. fixture는 합성 데이터이며 실제 사용자의 위치·사진이 아니다. 각 장비는 adb 대상과 user를 먼저 확인해야 한다.

```powershell
adb devices
adb shell am get-current-user
adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
adb install -r --user 10 app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 10 -w -r -e class com.example.starlog2.contracts.ContractGoldenInstrumentedTest,com.example.starlog2.G0RecoveryInstrumentedTest com.example.starlog2.test/androidx.test.runner.AndroidJUnitRunner
```

[확실] adb exit0만으로 성공 처리하지 않고 OK(test count), failure0, instrumentation 최종 결과를 확인했다. G0 실제 도어 callback 성공은 에뮬레이터 도어 API 근거이며 주행 위치 지원의 근거가 아니다. 웹 지도 재시도 시험의 네트워크 성공도 내비 v2 실요청 검증과 다르다.

## 공통 계약 검토 결정

[확실] [승인 계약](../INTERFACE_CONTRACTS.md)의 contracts 패키지가 canonical이다. NavigationModels/DefaultGuidanceEngine/ValhallaRouteProvider/ReplayTelemetryProvider는 현재 navigation 자체 타입을 사용한다. HIGH는 소유 파일을 덮지 않고 명시 adapter를 추가했다. 단위 m/s·좌표·nullable·REPLAY 상태를 보존하며 v1 Route/Guidance 변환은 검사했다. 이 단계는 내비를 최종 승인한 것이 아니다.

[확실] L1의 후속 migration PR이 공통 타입을 직접 사용하도록 변경해야 한다. 중복 DTO/enum/interface와 adapter는 그 PR 수락 전에 제거한다. Outcome nested type 때문에 alias 우회 대신 contracts.Outcome을 직접 import한다. ReplayClock은 worker 전용 구현 훅으로 남길 수 있지만 HIGH의 Clock.monotonicMs를 주입해 시간 의미를 맞춰야 한다. v2의 progress backward15m/forward500m 제약·ratio ETA·single leg 한계는 유지 기록하며 실측 없는 상용 map matching이나 live traffic ETA로 표현하지 않는다.

[확실] PR #6은 jioni→integration Draft, #7은 feat/sl-nav-v2→jioni Legacy Draft, #9는 독립 polyline Draft다. 모두 이번 세션에서 병합하지 않았다. ACK와 새 contract-applied SHA/PR이 없어 Navigation v2는 ACCEPTED=false, INTEGRATED=false, DONE=false다. G0 #1 및 G1 공통 기반 #10의 병합과 구분한다.

## 인수 기록 및 다음 단계

- [확실] [세 handoff RECEIVED](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4#issuecomment-6768179753)
- [확실] [H-N2-ANDROID-02 TEST_RESULT 및 추가 수신](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4#issuecomment-6768270973)
- [확실] [H-N2-ANDROID-03 TEST_RESULT](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4#issuecomment-6768603113)
- [확실] [G1 코드 PR #10](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/10)
- [확실] [승인 기준·노트북 ACK 절차](../team/BASELINE.md)

[확실] L1 다음 행동: 승인 기준 fetch/지문 확인→Issue4 BASELINE_ACK→최신 integration 기반 새 브랜치→contracts 이전·v2와 두 독립 수정 선별 반영→navigation 파일만 PR→정확한 SHA/fixture/시험·위임 전달. 오래된 README/docs/Gradle을 덮지 않는다. L2는 Issue5 ACK 뒤 StarLog 계약으로 S1/S2를 진행한다.

[추정] 내비 수락 후 HIGH가 I1의 단일 reducer queue, [lon,lat], maneuver/camera, OFF_ROUTE debounce, lifecycle 취소를 연결하고 P1 capability를 확인한다. 그 후 Replay와 실제 입력의 전체 흐름 V1을 별도로 시험한다. ACK가 없는데 수락·최종 UI 완료로 건너뛰지 않는다.
