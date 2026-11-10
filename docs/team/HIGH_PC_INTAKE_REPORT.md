# HIGH_PC 내비·StarLog 인수 결과

[확실] 2026-11-10 검증 기록. 내비 인수와 I1을 먼저 완료한 뒤 seobice 검토를 시작했다. master에는 기능 코드를 반영하지 않았다. 사용자 미추적 tmp-guide-inspection/을 보존했다.

## 현재 판정

| 범위 | 판정 | 실제 근거 |
|---|---|---|
| N2 공통 계약 이전 | ACCEPTED / INTEGRATED | PR6, merge bc79d10dcd2cfc844d0a4809265fde9c872a37fe |
| HIGH I1 내비 화면·Replay | 통합 및 합성 Replay 검증 완료 | PR14, merge878ab78ea6a9928cdadef097609b512182b2117b |
| S1/S2 StarLog | REJECTED / CHANGES_REQUESTED | PR13의 SQLite 실제 저장 경계1개 실패, 미병합 |
| StarLog I2 | 선행 수락 대기 | S2 수정본 수신 후 진행 |
| P1 실제 센서·실차 V1 | NOT_RUN | Replay 성공과 구별 |
| 전체 서비스 DONE | 아님 | StarLog 저장 결함 및 실입력/전체 흐름 미검증 |

[확실] 위는 실행 결과다. 노드 자체 진행률100% 보고를 전체 앱 완료로 취급하지 않았다.

## Navigation 정확한 버전과 결과

[확실] 활성 전달은 PR6/jioni d55fbaa849465918810023729878316b72a90efc였으며 구현5df4a55를 포함했다. PR12는 닫힌 상태로 재개하지 않았다. HIGH cleanup bbe3464afc813a183b232d92a61b1f2fb2881c33에서 임시 adapter를 삭제하고 공통 시험을 canonical 타입으로 이전했다. 최종 PR6 head4bb54a4b2c5ce2199265f17521fedb26faafca19를 expected SHA로 병합했다.

[확실] Navigation32 + contracts10 --rerun-tasks 통과. 전체 JVM46/build/lint0 issues/AndroidTestAPK exit0. Pleos golden10+G0 recovery4는14개 통과(45.072초). integration mergebc79d10에서 build/JVM46/lint0 재확인, 계측 golden10 재시험 통과(0.278초).

[확실] I1 코드32212a724acb0f68386a9269d5a5570a8a91f43c에서 단일 reducer queue, RouteProvider·Replay/live provider 주입 경계, source/route generation과 seq guard, OFF_ROUTE 최소5초 debounce, arrival route당1회, cancel/stop/close를 구현했다. 별도 Activity HTTP/parser를 제거하고 canonical provider/GuidanceState/RenderState를 사용한다.

[확실] WebView에 [lon,lat] 경로·pose를 전달해 실제 marker, 310m 전방 카메라, maneuver 안내를 갱신한다. 고정530m와 고정 차량 표식은 제거했다. 모의 주행 시작은 REPLAY 입력이며 실제 센서라고 표시하지 않는다. heading reference가 진북이 아니면 heading을 null로 유지하고 방향 없는 표식으로 표시한다. 별 UI나 StarLog 저장은 I1에 포함하지 않았다.

[확실] I1 JVM51(내비32/공통10/integration8/example1), lint0 issues, build/AndroidTestAPK exit0. 합성 Replay의 실제 WebView 이동·안내·REPLAY·Activity 종료 정리를 포함한 Pleos15개 통과(49.79초). 최종 표시 수정 뒤 UI1개 재검사7.34초 통과. PR14 merge878ab78에서 build/JVM51/lint0 및 UI1개6.64초 통과했다. 공개 Valhalla 경로 로딩은 G0 복구시험에서도 실제 사용했지만 LIVE 주행 센서나 실차 주행을 검증한 것은 아니다.

## 계약과 시험 도구 지문

[확실] 계약 버전 starlog-contracts-1.0.0과 original docs/g1/MANIFEST.json의 SHA256 f9601d5928d5615c1c6c878c58e96af1d743e86a9f24b37eb228661f85ccb62b는 그대로다. 그 안 canonical contract6개와 fixture13개를 파일별로 재검증했다.

[확실] 요청된 adapter 삭제·공유 시험 수정은 과거27개 전체 지문과 동시에 일치할 수 없다. 원본 승인 증거를 덮지 않고 현재26개를 docs/g1/HARNESS_MANIFEST.json에 별도로 동결했다. 현재 SHA256 a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48. Verify-G1.ps1은 원본manifest 자체와 contract/fixture 불변을 먼저 확인하고 현재 파일 집합을 확인한다. 변경된 것은 도구·시험 경계이며 공통 DTO 의미는 바꾸지 않았다.

## StarLog 검토·시험과 실패

[확실] seobice9d7172e760cd117c3b82b6101629af675452a8bf의 README/보고서를 읽고, 실제 PR13 head884c7e5afde3513a6c26dc033681e156f31226df와 비교했다. 코드16개는 starlog 소유 범위만 변경했다. seobice 추가분은 README/보고서뿐이다. 보고서 기준 a5eda675...는 오기이며 실제 Git 부모 a5eda671d6b371c94bbefb46c2d0825756875e34와 승인 SHA ancestry exit0을 확인했다.

[확실] HIGH 결합 검증 SHA9f73b506be9375c11977f1b4a620b2d98fa18267은 안정 integration878ab78 + 변경하지 않은 worker884c7e5 + HIGH 계측 runner다. 별도 검토 브랜치 chore/starlog-g1-acceptance에 보존했으며 integration에는 병합하지 않았다.

[확실] build/all JVM92(StarLog41+기존51), 실패0/오류0, lint0 issues, AndroidTestAPK exit0. HIGH 계측4개는3 PASS/1 FAIL(0.415초)이다. PASS는 delegated Sqlite verifyAll, 실제 StarLogSession에 golden179999/180000 및 empty memories 입력, 실제 V1 스키마→V2 migration/row 보존/reopen/trigger로 강제한 저장실패 rollback이다.

[확실] 실패는 liveAndReplayVisitsWithSameTimestampCanBothPersist다. 별도 VisitDetector의 LIVE와 REPLAY가 동일 observedAtMs1800000000000에서 P/속도0/accuracy5로 시작하고600000ms에 qualified Visit을 만들면 mode별 dedupeKey는 다르지만 id가 모두 visit-1800000000000이다. LIVE 저장은 성공하고 REPLAY 저장은 SQLite PK 충돌로 STORAGE_FAILED/storage.save_visit_failed를 반환한다. 각 모드 별1개가 기대값이다. 정상 도메인이 생성한 결과이며 조작된 invalid DTO가 아니다.

[확실] adb exit0 및 instrumentation code-1만 보고 성공 처리하지 않았다. 실제 Tests run4/Failures1이므로 REJECTED다. I2는 S2 수락 후 진행하는 조건이므로 시작하지 않았다. worker 소유 구현을 HIGH가 몰래 수정하지 않고 정확한 재현 코드·수정 조건을 Issue5/PR13에 전달했다.

## 이어서 할 일과 증거

- [확실] [내비 수락](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4#issuecomment-6770112340), [N2 통합](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4#issuecomment-6770324060), [I1 PR14](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/14).
- [확실] [StarLog 반려·재현 및 수정 요청](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/5#issuecomment-6770460164), [PR13 검증 결과](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/13#issuecomment-6770463994).
- [확실] [HIGH 재현 시험 소스](https://github.com/xodrndishfwk-crypto/HMG_Pleos/blob/chore/starlog-g1-acceptance/app/src/androidTest/java/com/example/starlog2/StarLogAcceptanceInstrumentedTest.kt).
- [확실] LAPTOP_2가 mode를 포함한 ID 등 충돌 방지를 구현하고 동일시각/서로다른mode 저장·조회·삭제 격리를 회귀시험해야 한다. TransactionalMemoryRepository의 같은ID·다른dedupe/source 처리도 SQLite와 일치시키고 기존41개를 유지한다. 자신의 브랜치에서 소유 경로만 수정해 PR13 새 SHA/HANDOFF_READY revision을 전달한다.
- [확실] HIGH는 수정 SHA의 위4개/전체 JVM/SQLite/golden 회귀를 재시험하고 통과 후에만 수락·병합·재검증한다. 이어 I2 reducer/IO queue, checkpoint, 기억·별·명령을 연결한다.
- [확실] P1 실제 location/heading/gear/speed 및 실차/전체V1은 NOT_RUN이다. 비율 ETA·local projection·single leg·4291회 제약과 기존 Native setPadding deprecation은 유지한다.
