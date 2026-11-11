# StarLog S2-G1-r2 인수 및 I2 검증 결과

[확실] HIGH_PC 검증일: 2026-11-11 KST. S2-G1-r1의 SQLite 동일시각 LIVE/REPLAY 저장 실패를 재현한 기존 시험을 그대로 사용해 r2 수정을 확인했다. PR13과 후속 I2 PR16은 integration/starlog-v1에 정상 병합했다. master에는 이번 기능을 반영하지 않았다.

## 승인·통합 버전

| 구분 | 정확한 값 |
|---|---|
| LAPTOP_2 report | S2-G1-r2 / seobice6a0d00d8793581cec5353df5380f21ca4c36d4d7 |
| 검토·수락한 PR13 head | c71ef17394466d35d36e41577624333ac7615b69 |
| S2 HIGH 수락 시험 SHA | bc14786eed81ea318dc9cdfd80745996fda5d384, worker 코드 동일 + HIGH runner1개 |
| PR13 integration merge | ce4394647ce2c02f1f908120aa59b24986165be2 |
| 검토·수락한 I2 PR16 head | 0ac172a6da856fe01504b8cbfb2ad5b085c14119 |
| I2 integration merge / 재검증 | 53434201314aaf8d22866402d0653d44bb705b36 |
| 계약 버전 / 승인 BASE_SHA | starlog-contracts-1.0.0 / 551079298c10c88b6e43ac212d95045b3904233b |
| original MANIFEST SHA256 | f9601d5928d5615c1c6c878c58e96af1d743e86a9f24b37eb228661f85ccb62b |
| current HARNESS_MANIFEST SHA256 | a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48 |

[확실] 위 값은 실제 Git/PR/시험 결과다. 원본 contract6개·fixture13개는 변경하지 않았고 현재26개 지문을 Verify-G1.ps1로 확인했다. 이후 문서만 추가한 commit은 검증 코드 merge의 후손이며 앱 코드 기준과 구분한다. 원격 integration은 착수 시3a4c146(PR9 merge)로 진전했지만 cfbc6ba 대비 tree 변경0도 확인했다.

## S2 수정 인수

[확실] VisitDetector가 source별 visit-live-/visit-replay- ID를 생성하며, 같은 ID·다른 dedupe/source 충돌에서는 in-memory/SQLite 모두 STORAGE_FAILED로 롤백한다. 기존 dedupe upsert·자료 보존 및 격리 시험을 검토했다. worker 변경16개는 starlog 소유 경로에 한정됐으며 HIGH가 worker 코드를 덮어쓰지 않았다.

[확실] PR13 정확한 head에서 전체 JVM95(StarLog44+기존51), 실패0/오류0, build/lint0 issues/AndroidTestAPK exit0. 처음 에뮬레이터가 꺼져 있어 계측 NOT_RUN을 확인하고 기존 Pleos AVD를 wipe 없이 부팅했다. HIGH runner 결합bc14786에서 StarLogAcceptanceInstrumentedTest OK4(1.023초), instrumentation code-1이었다. PASS 항목은 SQLite 위임 스위트, 실제 도메인의 golden179999/180000 및 빈 기억, 실제 V1→V2 row 보존·reopen·write rollback, 동일시각 LIVE/REPLAY 독립 저장이다. 이전 반려 사유가 해결됐음을 확인해 ACCEPTED 후 PR13을 병합했다.

[확실] PR13 mergece43946에서 전체 JVM95/build/lint0/시험APK exit0을 재확인했다. 최종 I2 결합에서도 동일4개가 전부 통과했다. S2 core/storage는 ACCEPTED/INTEGRATED이며 r1 반려 기록은 이전 보고와 Issue 이력에 보존한다.

## I2 구현

- [확실] NavigationSession의 단일 reducer queue에서 StarLogSessionImpl을 호출하고 별도 단일 IO queue에서 repository를 호출한다. Activity UI thread에서 DB를 열거나 조회하지 않는다.
- [확실] completedVisits를 upsert하고 source별 listStars 캐시를 갱신해 RenderState.experience/skyItems/memory로 연결한다. toMemoryCard의 null 제목·사진을 그대로 유지한다. 저장 실패는 오류로 표시하고 다음 tick에서 미저장 방문을 재시도한다.
- [확실] 웹 SELECT_MEMORY/OPEN_RECAP/CLOSE_RECAP을 엄격한 JSON 경계·renderer generation·messageId 중복 방지 후 reducer로 전달한다. Kotlin이 최신 입력 age와 정차를 재검사하고 INVALID_STATE/INVALID_INPUT을 화면 오류에 매핑한다. 기억 오류에 경로 서버 재요청 버튼을 노출하지 않는다.
- [확실] Activity pause/close에서 provider를 중지하고 checkpoint와 남은 방문을 IO queue에 저장한다. close는 이전 upsert 진행 중에도 최신 end 시각을 별도 flush한 뒤 repository를 닫는다. 시작/복귀 시 source별 checkpoint를 복구하고 같은 dedupeKey로 upsert하며 비활성 시간을 정차 시간에 더하지 않는다.
- [확실] 1초 간격 freshness 검사로 2초를 초과한 무입력도 DRIVING_SAFE로 복귀시킨다. 앱의 실제 연결 config는 maxSampleGapMs2000을 적용하며 S 노드의 공통 계약·기본 모델·공유 fixture는 변경하지 않는다.
- [확실] 실제 WebView에 방위별 별, 기억 카드, 리캡 열기/닫기를 연결했다. 주행 중 별 선택은 비활성화되고 이동 시 기억 화면을 숨긴다. 제목은 textContent로 출력하며 임의 photoRef URL을 직접 로드하지 않는다.
- [확실] LIVE/REPLAY의 조회·상태·checkpoint를 분리하며 이전 source의 늦은 IO 결과를 현재 화면에 적용하지 않는다. 기존 네이티브/GL 지도·도어 진단·pitch76/건물 숨김·310m 전방 중심을 보존했다.
- [확실] 실제 위치 기록 SQLite 파일은 클라우드 자동 백업·device transfer 대상에서 제외했다. 사용자 데이터 삭제·프로젝트 복사·reset/force push/자동 stash는 하지 않았고 tmp-guide-inspection/을 보존했다.

## 최종 시험과 재현

[확실] Windows/Studio JBR25.0.4/Gradle daemon25/Java target17/Gradle9.6.0/AGP9.5.1/compile37/min34, Pleos x86_64 API34/user10에서 실행했다.

| 대상 | 결과 |
|---|---|
| I2 최종 head 전체 JVM | 103개(StarLog44 + 기존51 + I2 8), failure0/error0 |
| assembleDebug / testDebugUnitTest / lintDebug / assembleDebugAndroidTest | exit0, lint0 issues |
| 최종 head Pleos 전체 계측 | OK20,92.232초, instrumentation code-1 |
| I2 integration merge5343420 | build/JVM103/lint0/시험APK exit0 |
| merge 핵심 I2 화면1 + S2 수락4 재검증 | OK5,31.38초, instrumentation code-1 |
| Verify-G1.ps1 | PASS26, 승인 hash 일치 |
| P1 실제 차량 location/heading/gear/speed | NOT_RUN |
| 실차/전체서비스 V1 | NOT_RUN |

[확실] 계측20개는 I2 실제 WebView/bridge/IO/SQLite/Activity 재생성1 + S2 수락4 + 내비 Replay1 + G0 복구/도어4 + 계약 golden10이다. 정차180초·체류600초는 합성 telemetry와 주입된 가상 clock으로 경계를 검증했으며 실제180초/600초 주행을 기다린 시험은 아니다. Android SQLite·WebView·지도 엔진과 도어 callback은 실제 에뮬레이터에서 실행했다. 시험 DB는 전용 이름을 사용하고 정리했다.

```powershell
.\tools\Verify-G1.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
adb install -r --user 10 app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 10 -w -r -e class com.example.starlog2.StarLogFlowInstrumentedTest,com.example.starlog2.StarLogAcceptanceInstrumentedTest,com.example.starlog2.NavigationReplayInstrumentedTest,com.example.starlog2.G0RecoveryInstrumentedTest,com.example.starlog2.contracts.ContractGoldenInstrumentedTest com.example.starlog2.test/androidx.test.runner.AndroidJUnitRunner
```

## 시험 중 발견·해결한 점

[확실] 최초 전체20개에서 기존 시험의 controller 교체 시 웹 lastRenderSeq 잔류/연쇄 Activity launch timeout2건을 기록했다. 시험이 상태 처리기를 교체할 때 renderer generation도 초기화하도록 수정했고 N/G0 5개가 통과했다(73.949초). 다음 전체20개에서는 공개 경로 응답에 의존하던 G0 초기 경로 준비 timeout1건이 있었다. UI/복구 시험의 RouteProvider를 공통 fixture로 DI하여 외부 경로 응답과 화면 동작을 분리했고 최종20개를 통과했다. 정확한 외부 서버 실패 원인을 단정하지 않는다.

[확실] 최종 UI 회귀의 경로는 mock, telemetry는 synthetic REPLAY다. 실제 지도 스타일/WebView/MapLibre와 SQLite는 그대로 실행한다. 앱의 기본 RouteProvider는 Valhalla이며 테스트 후 factory를 복원한다. 최종20개 통과가 공개 Valhalla 서버의 가용성·교통 ETA·실차 센서 검증을 뜻하지 않는다. 이전 실패를 PASS로 덮거나 테스트를 제외하지 않았다.

[확실] 종료 직전 이전 upsert가 진행 중일 때 마지막 end 시각이 빠지는 경계를 보완하고 회귀시험했다. 저장·경험 오류가 route retry로 잘못 연결되지 않게 수정했다. 모든 후속 변경도 명시적인 HIGH 소유 파일만 commit/push했다.

## 범위와 다음 단계

[확실] S2-G1-r2 인수와 I2의 저장·상태·웹 명령·checkpoint 연결은 ACCEPTED/INTEGRATED다. 전체 서비스 DONE은 아니다. 다음 HIGH 작업은 P1 실제 센서 capability와 V1 실제/Replay 전체 흐름 검증이다. 노트북은 새 계약을 독자 변경하지 않고 최신 담당 Issue에서 후속 결함·작업을 확인한다.

[확실] 별의 수직 위치는 임시 렌더 배치이며 실제 천문 고도/최종 디자인이 아니다. 비어 있지 않은 photoRef의 실제 사진 가져오기·미리보기, 기획 전체 성단/여행 리캡 완성은 이번 I2 범위에 포함하지 않았다. 기존 ratio ETA/local projection/single-leg/4291회 및 Native setPadding deprecation도 별도 제약으로 남는다.

- [PR13 S2 수정 인수](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/13)
- [S2 ACCEPTED 기록](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/5#issuecomment-6789548563)
- [S2 INTEGRATED 기록](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/5#issuecomment-6789917503)
- [PR16 I2 코드·최종 검증](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/16)
