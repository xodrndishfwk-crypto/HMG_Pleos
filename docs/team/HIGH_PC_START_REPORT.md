# HIGH_PC G0 원본 준비 보고

기준: 2026-11-10 / 작업 branch: chore/sl-baseline-v1

[확실] 준비 코드 commit: 600e2fb67e2451e058bd9f11ece4136bc2c0cad3. 아래 링크 추가는 문서 전용 변경이며 테스트한 앱 코드와 구분한다.

## 협업 링크
- [G0 준비 PR](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/1)
- [총괄 작업](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/2)
- [고사양 담당](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/3)
- [내비 담당](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4)
- [StarLog 담당](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/5)

## 확인 결과
- [확실] 원격 master b4180e86e101f4e8e7196546a93c10f4d62c5453를 기준으로 원본 폴더에서 준비 branch를 생성했다.
- [확실] 이전 대화에서 구현한 Native 지도·WebGL Activity·로컬 MapLibre GL JS 5.15.0 자산이 원본에 미커밋 상태였다. 삭제·stash·복사본 생성 없이 보존해 준비 PR에 포함했다.
- [확실] master의 내비 v1 코드는 준비 branch에 이미 포함돼 있다. 중복 cherry-pick하지 않았다. jioni의 v2는 이번 PR에 적용하지 않았다.
- [확실] 지도 출처 컨트롤을 숨기던 설정을 해제했다. pitch76, zoom17.7, lookahead310m와 건물 숨김은 유지했다.

## 이 PC의 실제 검증
- [확실] Android Studio 내장 Java 경로를 사용해 Gradle을 실행했다. wrapper는 daemon JVM25 설정을 사용하며 Java source target17과 별개다.
- [확실] :app:assembleDebug :app:testDebugUnitTest :app:lintDebug 실행 성공.
- [확실] 최초 결과: 총20개 테스트(내비19 + 기본 예제1), 실패0, 오류0. lint0 errors/9 warnings.
- [확실] lint 경고는 기존 Native draw allocation, 버전 카탈로그 권장, 임시 하드코딩 UI 문구다. warning0으로 보고하지 않는다.
- [모름] 이번 준비 PR의 실제 Pleos 화면 재실행과 위치 callback은 아직 검증하지 않았다. 과거 screenshot은 이번 SHA 검증 증거가 아니다.

## 아직 준비 완료가 아닌 항목

[확실] 아래는 최초 G0 시점의 기록이다. 도어 진단과 실제 화면 재실행에 관한 미완료 사항은 뒤의 「G0 보완 검증」 결과로 갱신한다. G1은 계속 NOT_READY다.
- [확실] 공통 계약 G1은 NOT_READY다. NavigationModels는 현재 navigation 패키지에 있는 제안 모델이며, 승인된 contracts로 자동 취급하지 않는다.
- [확실] 신규 contracts/Clock/공통 fixture/직렬화 검증과 두 노트북의 수신 확인은 다음 준비 작업이다.
- [확실] 현재 지도 표식과 530m banner는 고정 UI다. 실시간 진행 및 실제 GPS 연동 완료가 아니다.
- [확실] Vehicle SDK 의존성과 CAR_DOORS 권한은 있지만 현 두 Activity에는 Vehicle 실제 호출이 없다.

## 다음 행동
1. HIGH_PC가 N/S 모델을 비교해 공통 타입 또는 명시적 adapter 계약을 확정하고 테스트·fixture를 준비한다.
2. G1 검증이 끝난 뒤 승인 코드 SHA/hash를 공지한다. 노트북은 새 공통 변경을 확인한다.
3. N v2는 별도 검토·Android 재시험 후 통합한다. S의 실제 진행과 산출물을 확인한다.
4. HIGH_PC는 경로 진행·표식·안내·방위별 별 연결, Pleos 지원 API 검증을 단계별로 수행한다.

[확실] 이 보고는 G0 준비 결과다. 앱 전체 완성이나 G1 승인·실차 검증 완료가 아니다.

## G0 보완 검증 (2026-11-10)

### 해결한 점
- [확실] MainActivity의 도어 진단을 원격 master 원본과 동일하게 복구했다. 현재 지도 화면의 「차량 SDK 진단」 버튼으로 열 수 있다.
- [확실] 이전 Native 지도는 NativeMapActivity로 이름을 옮겨 소스와 manifest 등록을 보존했다. 프로젝트 복사본을 만들지 않았다.
- [확실] WebView 본문 로딩 오류, 지도 스타일 오류, WebGL context loss, JS 오류를 오류 안내와 재시도로 연결했다.
- [확실] 지도 준비 callback이 20초 안에 오지 않으면 실패 상태와 재시도 버튼을 표시한다. 경로 요청 실패도 재시도할 수 있다.
- [확실] 지도 시도별 generation과 경로 요청 token으로 오래된 callback을 무시한다. 종료 시 timeout 제거·요청 취소·HTTP disconnect·JS bridge 제거·WebView 정리를 수행한다.
- [확실] 출처 표시 위치를 하단 정보 패널의 실제 높이에 맞춰 조정한다.

### 실행한 검증
- [확실] assembleDebug / testDebugUnitTest / lintDebug / assembleDebugAndroidTest 성공.
- [확실] JVM 단위 테스트 총23개(내비19 + 지도 수명3 + 예제1) 통과, 실패0/오류0.
- [확실] lint는 0 errors / 16 warnings. 기존·복구 UI의 문자열/카탈로그/Native draw 관련 경고이며 경고0으로 보고하지 않는다.
- [확실] Pleos emulator-5554, Connect_on_x86_64_emulator, 현재 user10에서 G0RecoveryInstrumentedTest 최종 4개 시험 모두 통과(42.595초).
  1. 존재하지 않는 본문 페이지 → 실패 안내 → 재시도 → 지도·실제 경로 복구.
  2. 준비 callback 없는 페이지 → 20초 제한 → 재시도 → 지도·실제 경로 복구.
  3. 실제 접근 불가한 스타일 URL → 오류 → 재시도 → 지도·실제 경로 복구. 카메라76도·건물 숨김·출처 DOM이 정보 패널 위에 있는 것도 검증.
  4. 차량 SDK 진단 진입 → 실제 ROW_1_LEFT 성공 callback → 버튼 재조회 성공 → 종료 후 vehicle=null 및 Activity 파괴 확인.
- [확실] 첫 스타일 오류 시험은 테스트가 Map 객체를 반환하며 JS callback timeout이 발생했다. primitive 값을 반환하도록 시험 호출을 수정한 뒤 단일 재시험과 최종 전체4개 재시험이 통과했다. 실패 이력을 숨기지 않는다.
- [확실] 에뮬레이터 SDK 조회 성공은 실차 주행 검증을 뜻하지 않는다. 고정530m·차량 표식 및 실시간 진행 연결은 다음 G1/R/I 단계의 구현 범위다.

### 사용자/팀원이 확인할 항목
- [모름] 노트북 두 사람의 실제 GitHub 계정과 각 계정의 read/push 권한은 아직 확인되지 않았다. 각자 저장소 접근·자신의 기능 branch push 가능 여부를 확인하고 담당 Issue #4/#5에 남겨야 한다.
- [확실] 이 항목은 준비 작업 완료의 외부 확인 조건이다. 코드 문제가 해결됐다고 팀 접근권한까지 완료 처리하지 않는다.
