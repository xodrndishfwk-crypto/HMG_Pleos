# 목적지 선택 코드 검토와 HIGH 연결 결과

[확실] 2026-11-13. Navigation PR #18의 원 제출은 실제 검색 앱 완성본이 아니라 검색/경로 선택 경계의 부분 인수물이었다. 보고 범위는 적절했지만 동시성 보완과 HIGH 세션 연결이 필요했다. 원 head df67c99d8c4a621f6621300a144c5f0ec01eae80에서 추가 동시성 시험 8개가 실패했고, 8155b9264cb9e9dd32b37749730b66c824653ec9에서 수정했다. PR #18은 integration/starlog-v1에 564a68965c0c390aec6bdb3913af33008fddb4ec로 병합했다. master에는 병합하지 않았다.

## 수락한 범위

- [확실] provider 시작, 취소 핸들 및 소비자 callback을 내부 잠금 밖에서 호출한다. 오래된 응답 차단, 잘못된 requestId/빈 ID, provider 시작 예외, 종료 후 요청 차단을 시험했다. Navigation 단위 시험은 47개 PASS다.
- [확실] DestinationCandidate는 navigation 기능 내부 타입으로 수락했다. 동결 G1 공통 계약으로 승격하지 않았다. 공통 contract/fixture 변경 없이 Verify-G1 26 files PASS, HARNESS_MANIFEST SHA256 a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48를 유지했다.
- [확실] HIGH DestinationFlow는 검색/선택/경로 준비/안내 시작/취소/종료를 NavigationSession의 단일 reducer에 연결한다. 검색·계획 제한시간은 기본 15초이며, 위치는 현재 telemetry source의 실제 제공 값과 수신 후 경과 시간을 사용한다. 기본 허용 age는 2초다. 위치가 없거나 오래되면 오류를 반환한다.
- [확실] 계산된 Route를 adoptRoute로 재사용하므로 안내 시작 시 경로를 다시 요청하지 않는다. source 교체·pause/resume 이후 이전 계획을 시작하지 않는다. 진행 중 경로 교체가 실패하면 기존 경로와 기존 목적지를 함께 유지한다.
- [확실] stopGuidance는 경로/안내만 지우며 telemetry와 StarLog 기록 연결을 유지한다. WebView 지도에서도 경로 source/layer를 제거한다. 같은 routeId로 좌표가 바뀌어도 지도 geometry를 갱신한다.

## Star 노드: 디자인에 연결할 기능 API

[확실] Activity의 connectDestinationSearch(provider, observe)는 DestinationFlow를 반환한다. observe는 UI thread에서 호출된다. 디자인의 검색 입력·결과 목록·버튼에서 다음 명령만 연결한다. 기존 디자인 파일과 레이아웃은 HIGH가 변경하지 않았다.

```kotlin
val flow = activity.connectDestinationSearch(realSearchProvider) { state ->
    // 현재 디자인에 로딩/빈 목록/오류/경로 준비 상태 표시
}
flow.search(query)
flow.select(candidateId)
flow.startGuidance()
flow.cancelPending() // 검색·계획 취소. 진행 중 안내 종료와 구분
flow.stopGuidance()
// Activity의 NavigationSession 종료 시 flow/provider도 정리된다.
```

[확실] 상태는 IDLE, SEARCHING, RESULTS, PLANNING, READY_TO_START, GUIDING, CANCELLED, ERROR다. 빈 RESULTS는 검색 결과 없음이다. selected/plannedRoute/error를 사용하며 Route의 시간은 실시간 교통 ETA라는 의미가 아니다. provider는 호출 후 즉시 취소 핸들을 반환하고 실제 통신은 비동기로 수행해야 한다. 연결마다 새 planning provider를 사용하며 소유권은 flow에 있다.

## 아직 완료하지 않은 것

- [확실] 실제 장소 검색 provider/서비스 선택/키 설정은 없다. 사용자는 서비스 키의 의미를 질문했으며 특정 업체 선택이나 과금 승인을 하지 않았다. 가짜 검색을 실제 검색으로 연결하지 않았다.
- [확실] 디자인 화면의 검색 위젯 연결과 기존 Activity START/END 초기 경로 교체는 남아 있다. 현재 초기 경로는 기존 프로토타입 동작을 유지한다. 위 API 제공을 실제 검색 화면 완료로 해석하지 않는다.
- [확실] LIVE GPS/차량 위치, 실제 한국 경로 응답, 실제 차량 주행은 이번 합성 시험의 검증 범위가 아니다. 해당 검증은 NOT_RUN이다.
- [확실] Kakao Local은 REST API 키가 필요하다([공식 문서](https://developers.kakao.com/docs/ko/local/dev-guide)). 무료 범위와 MapLibre 조합 이용 조건을 확정한 뒤 채택한다. 키 발급만으로 무조건 유료라는 뜻은 아니다([공식 정책](https://developers.kakao.com/docs/ko/kakaomap/common)).

## 재현과 증거

[확실] 첫 전체 Pleos 실행: 21개 중 20개 통과, G0RecoveryInstrumentedTest.remoteStyleFailure_retryRestoresMapAndVisibleAttribution는 Activity 실행 45초 제한 초과로 실패했다(총 153.924초). 이 실패를 통과로 처리하지 않았으며 앱 프로세스 정리 후 복구 시험을 격리 재실행했다. 후속 결과는 아래에 기록한다.

[확실] HIGH 전체 Gradle 빌드/AndroidTest APK 빌드 성공, JVM 131개 실패 0, lint No issues found. 목적지 계측 단독 시험 1개 PASS(14.793초)를 먼저 확인했다. 전체 Pleos 회귀 및 병합 후 결과는 아래 최신 검증 기록에 덧붙인다. 시험은 fake search/route와 합성 REPLAY를 사용하지만 실제 Activity, NavigationSession, WebView 및 MapLibre source/layer를 검사한다.

```powershell
.\tools\Verify-G1.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
adb install -r --user 10 app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 10 -w -r -e class com.example.starlog2.DestinationFlowInstrumentedTest,com.example.starlog2.StarLogFlowInstrumentedTest,com.example.starlog2.StarLogAcceptanceInstrumentedTest,com.example.starlog2.NavigationReplayInstrumentedTest,com.example.starlog2.G0RecoveryInstrumentedTest,com.example.starlog2.contracts.ContractGoldenInstrumentedTest com.example.starlog2.test/androidx.test.runner.AndroidJUnitRunner
```
