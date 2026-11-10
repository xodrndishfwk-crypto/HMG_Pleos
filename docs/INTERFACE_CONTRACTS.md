# StarLog 공통 계약 1.0.0

[확실] 승인 API의 원본은 `app/src/main/java/com/example/starlog2/contracts/*.kt`다. 파일별 SHA-256은 [manifest](g1/MANIFEST.json), 시작 코드·실제 통합 버전·시험 결과는 [BASELINE](team/BASELINE.md)에서 확인한다. 이 문서는 계약 설명이며 G1의 READY 여부는 BASELINE과 H-G1 공지를 따른다.

## 타입과 경계

- [확실] NavigationContracts: LatLon, Telemetry, TimedValue, LocationFix, SourceMode, Gear, Quality, Reference, AppError, Outcome, CancelHandle, RouteRequest/Route/Maneuver, GuidanceState 및 provider/engine 인터페이스.
- [확실] StarLogContracts: Visit, MemoryStar, SkyItem, ExperienceState/Command, StarLogUpdate, SessionCheckpoint, MemoryRepository, StarLogSession. 도메인 구현은 L2 소유다.
- [확실] RenderContracts: RenderRoute/Pose, CameraConfig, MemoryCard, RenderState, RenderEnvelope. ContractJson은 native JSON 경계이며 현재 화면이 이 프로토콜을 소비한다는 뜻은 아니다.
- [확실] Clock: wallTimeMs는 기록 날짜, monotonicMs는 기간 계산용이다. AndroidClock은 elapsedRealtime을 사용한다. 공통 JVM harness의 FakeClock은 wall 역행과 179999→180000ms를 시험한다.
- [확실] TelemetryCursor는 동일 provider generation에서 seq 증가·monotonic 비역행을 검사한다. provider/source 변경은 거부한다. 새 구독 세대에서만 reset/새 인스턴스를 사용한다. reducer queue 연결은 I1 범위다.

## 숫자·없음·직렬화

- [확실] 거리 m, 기간 s, 속도 m/s, 기록 UTC epoch ms다. lat/lon 객체와 웹 좌표 `[lon,lat]`를 구별한다. 모든 Double은 유한값이며 좌표 범위를 검사한다.
- [확실] heading은 [0,360), relativeBearing은 [-180,180)다. TRUE_NORTH/MAGNETIC/UNKNOWN을 구분한다. 자북을 진북으로 임의 해석하지 않는다.
- [확실] wire의 정수는 0..9007199254740991이며 seq/시간/age가 대상이다. index는 Int 범위다. enum 대소문자를 구분하고 unknown enum·숫자 문자열·필수 필드 누락은 거부한다.
- [확실] nullable 필드도 JSON에 명시적 null로 포함한다. VALID에는 값이 있어야 하며 UNAVAILABLE에는 null만 허용한다. unknown speed를 0으로 대체하지 않는다. STALE에는 마지막 값 또는 null을 허용한다.
- [확실] Route는 2..50000점, 음수 거리/시간 거부, maneuver index 연속·geometry index 범위 검사를 적용한다. bridge JSON은 UTF-8 1MiB 초과를 거부한다.
- [확실] RenderEnvelope는 version=1/type=renderState다. snapshot은 route/pose/guidance/memory/error를 포함하고 없으면 null이다. 웹 이벤트는 ready/ack/userAction/error만 허용한다. SELECT_MEMORY에는 starId가 필요하다.

## 실행 책임

- [확실] 공통 타입은 Android 의존성이 없다. ContractJson만 Android의 org.json을 사용하며 실제 Pleos 계측시험으로 확인한다. 순수 JVM 시험에서 Android stub JSON을 호출하지 않는다.
- [추정] provider callback은 worker에서 올 수 있다. HIGH가 단일 reducer queue, cancel/stop/close, generation token, OFF_ROUTE 재요청 debounce를 I1에서 조립한다. 계약 코드만으로 이 연결이 완료되지 않는다.
- [추정] MemoryRepository는 blocking IO로 사용하며 UI thread에서 호출하지 않는다. finish/restore는 이미 체류 기준을 충족한 방문만 반환하고 앱 종료를 ignition off로 만들지 않는다. 저장 중복 방지는 dedupeKey upsert로 L2가 구현한다.
- [추정] 공유 stop fixture의 P/N 180초·속도0.3m/s·age2초는 기존 설계의 시험용 제안 기준이다. 제품 요구로 새 확정한 값이 아니며 L2 기능시험과 사용자 결정은 별도다. 순수 형식검사 통과가 액자 기능 완성을 뜻하지 않는다.

## PRE-G1 내비 이전 결정

[확실] canonical 타입은 contracts 패키지다. HIGH는 worker 소유 navigation 파일을 덮어쓰지 않고 NavigationContractAdapter에 기존 v1→공통 Route/Telemetry/Guidance 변환만 추가했다. PRE-G1 산출물 보존과 G1 준비 사이의 임시 경계다.

[확실] 호환 기간은 H-G1 게시부터 L1의 첫 contract migration PR을 HIGH가 수락하기 전까지다. 최종 내비 ACCEPTED 조건은 NavigationModels.kt의 중복 DTO/interface/enum을 제거하고 모든 N 구현·시험에서 contracts를 직접 사용하며 임시 adapter와 해당 adapter 시험도 제거하는 것이다. 타입 alias로 nested Outcome.Ok/Err를 우회하지 말고 실제 contracts.Outcome을 import한다. callback·단위·null·source 의미 변경은 CHANGE_REQUEST로 전달한다.

[확실] PR #6/#7은 PRE-G1 참고 기록이며 아직 수락·통합한 코드가 아니다. 새 기능 PR의 base는 integration/starlog-v1이다. 과거 README/docs를 덮어쓰지 않고 navigation 소유 파일만 전달한다.

## 공통 시험 입력

[확실] `fixtures/contracts/`는 HIGH가 관리하며 worker는 읽기만 한다. route valid/invalid, driving, stop 경계, stale/unknown speed, heading wrap, empty memory, render full/null, bridge errors, invalid telemetry, geometry를 포함한다. geometry의 1m·bearing0.1도는 합성 입력의 허용 오차이며 실제 GPS 정확도 보증이 아니다. `ContractGoldenInstrumentedTest`와 `ContractHarnessTest`가 이 기준의 형식·경계·시간·취소를 검사한다. 도메인 구현과 실주행 검증은 각 담당자의 추가 시험이 필요하다.
