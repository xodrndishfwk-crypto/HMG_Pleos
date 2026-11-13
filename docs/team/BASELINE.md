# StarLog 승인 시작 기준

[확실] 2026-11-13 추가: 목적지 검색/경로 선택 유틸리티 PR #18 수락 및 동시성 보완. HIGH 연결 API·시험·남은 실제 서비스 선택은 [목적지 선택 검토](NAVIGATION_DESTINATION_VALIDATION.md)를 우선 확인한다. G1 계약/fixture 및 아래 최초 승인 값은 유지한다.

[확실] 최신 인수 상태(2026-11-11): N2/I1 및 S2-G1-r2 PR13/I2 PR16 통합·검증 완료. S2 r1 저장 오류는 수정·재검증했고 [최신 S2/I2 보고](STARLOG_R2_I2_VALIDATION.md)를 우선 확인한다. I2 검증 코드 merge는53434201314aaf8d22866402d0653d44bb705b36이며 이후 문서 commit과 구분한다. 전체서비스 DONE/실차 검증 완료는 아니다. 아래 G1 최초 승인 값과 [이전 r1 인수 보고](HIGH_PC_INTAKE_REPORT.md)는 이력이다. 계약/fixture는 그대로이며 현재 도구26개는 [HARNESS_MANIFEST](../g1/HARNESS_MANIFEST.json), SHA256 a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48다. Verify-G1.ps1로 원본 contract6/fixture13과 현재 도구를 함께 확인한다.

[확실] G1 공통 기반 READY · baselineRevision g1-20261110.1 · 승인 HIGH_PC · 2026-11-10 KST. 공통 코드와 입력의 준비 승인이다. Navigation v2 수락·전체 서비스 완료와 구분한다.

## 승인 값

[확실] 다음은 실제 확인한 코드·원격·시험 값이다. approvedCodeSHA는 문서 최신 commit과 달라도 되며 아래 integration merge의 조상이다.

| 항목 | 승인 값 |
|---|---|
| gate | READY_FOR_WORK |
| handoffId | H-G1-20261110-01 |
| approved BASE_SHA / approvedCodeSHA | 551079298c10c88b6e43ac212d95045b3904233b |
| code integration branch | integration/starlog-v1 |
| code integration merge SHA / 재검증 대상 | 0a827d2c62a64e5e84caf901e1809c1ba2bf094c |
| G0 prerequisite merge SHA | a2950c907abcceb535d5fa70242e9646abf848a3 |
| upstream master README 확인 SHA | 4809f24af657323ec995a0e227c7f9c279678e0d |
| contract version | starlog-contracts-1.0.0 |
| 파일별 contract/fixture/harness SHA-256 | [MANIFEST.json](../g1/MANIFEST.json), 27파일: contracts6 / fixtures13 / harness8 |
| manifest SHA-256 | f9601d5928d5615c1c6c878c58e96af1d743e86a9f24b37eb228661f85ccb62b |
| code preparation PR | [G0 #1](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/1), [G1 #10](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/10), 둘 다 integration에 병합 |
| G1 test evidence | [VALIDATION.md](../g1/VALIDATION.md), [승인 검토](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/10#issuecomment-6768560522) |
| READY 공지 위치 | [총괄 Issue #2](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/2), H-G1-20261110-01 댓글 |
| L1 issue / 현재 수신 상태 | [#4](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/4), ACK 수신·canonical migration 수락/통합 완료 |
| L2 issue / 현재 수신 상태 | [#5](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues/5), ACK 수신·S2 r2 ACCEPTED/INTEGRATED, I2 연결/합성 입력 검증 완료 |
| 두 담당자의 저장소 접근 | 2026-11-10 사용자 확인: 두 사람 모두 Push 가능 |
| 기능 수락·최종 통합 | Navigation v2/I1 및 S2 r2/I2 ACCEPTED/INTEGRATED, 전체서비스 DONE 아님 |

[확실] L1/L2의 ACK는 공지 이후 받는 실제 수신 기록이다. 미수신을 검증 완료로 꾸미지 않는다. G1 READY는 작업 시작 허용이며 담당자 수신 완료를 뜻하지 않는다. 공지 이후 문서만 반영한 integration HEAD는 위 검증 대상 merge SHA의 후손일 수 있다. 각 노드가 fetch 후 실제 HEAD와 승인 SHA의 ancestry를 다시 확인한다.

## 재현 환경

[확실] Windows NT build26200(시스템 ProductName Windows 10 Education), Android Studio JBR25.0.4, Gradle daemon JVM25, Java target17, wrapper9.6.0, AGP9.5.1. Android min34/compile37/target37, 설치된 Build Tools35.0.0/36.0.0. Vehicle2.3.2, MapLibre Native13.7.1, 로컬 GL JS5.15.0. Pleos Connect x86_64 emulator-5554/API34/user10에서 계측시험했다. 개인 SDK 경로는 커밋하지 않는다.

## 필수 명령

[확실] HIGH에서 아래 명령 exit0을 확인했다. JAVA_HOME은 각 PC의 Android Studio JBR 경로로 설정한다. Android SDK는 local.properties 또는 각 PC 환경에만 설정한다.

```powershell
git fetch origin
git merge-base --is-ancestor 551079298c10c88b6e43ac212d95045b3904233b origin/integration/starlog-v1
.\tools\Verify-G1.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
# 내비 담당 범위만 확인할 때도 공통 검사와 함께 실행
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.starlog2.contracts.*" --tests "com.example.starlog2.navigation.*"
```

[확실] 승인 code SHA에서 --rerun-tasks로 JVM33개·lint0·빌드가 통과했고 Pleos golden10+G0 recovery4가 모두 통과했다(44.889초). integration merge에서 빌드/JVM33/lint0과 golden10을 재검증했다(0.291초). 로컬 보고서는 app/build/reports/ 및 test-results/에 생성된다. 영구 결과 요약은 VALIDATION.md다. 기기별 상세 실행·기대값은 그 문서를 따른다.

[확실] SDK 없는 노트북은 Android 검사를 PASS로 쓰지 않는다. 실행 가능한 JVM 결과와 NOT_RUN 이유를 쓰고 HANDOFF의 TEST_DELEGATION으로 정확한 SHA/명령/기대값을 HIGH에 보낸다. HIGH가 검사한다.

## 노트북 다음 행동과 ACK

1. [확실] 사용자 변경을 보존하고 fetch한다. 승인 SHA가 integration 이력에 있는지와 Verify-G1 결과를 확인한다. 새 기능 브랜치는 승인 코드가 포함된 최신 integration에서 만든다. 과거 PRE-G1 브랜치를 덮어쓰거나 force push하지 않는다.
2. [확실] 자신의 Issue에 BASELINE_ACK를 남긴다: handoffId, device, approved BASE_SHA, 실제 checkout SHA, contractVersion, manifest SHA-256, ancestry/manifest 명령·exit code, 실행한 시험과 NOT_RUN, branch, nextAction.
3. [확실] L1은 NavigationModels의 공통 정의를 contracts import로 이전한다. 기존 v2·ce418b0·2438fe9 중 필요한 navigation 파일만 새 integration-target PR로 전달한다. v1은 이미 통합 이력에 있으므로 중복 cherry-pick하지 않는다. 오래된 README/docs/Gradle을 덮어쓰지 않는다.
4. [확실] L2는 contracts의 StarLogSession/MemoryRepository를 사용해 S1/S2를 구현한다. 공유 fixture는 수정하지 않는다. 저장·복원·180초·방위·LIVE/REPLAY 격리 시험을 본인 소유 경로에 추가한다.
5. [확실] 임시 NavigationContractAdapter와 중복 NavigationModels의 호환 기간은 L1 migration을 HIGH가 수락하기 전까지다. adapter 제거는 공유 경로이므로 L1이 CHANGE_REQUEST에 정확한 삭제 요청을 쓰면 HIGH가 처리한다. 중복 타입이 남은 채 최종 내비 수락하지 않는다.

## 통과 조건과 남은 단계

[확실] L1 수락에는 canonical 계약 적용, Android 테스트, 신규 lint error0, Replay 구분, timeout/cancel/429/off-route/arrival 경계와 알려진 제약 기록이 필요하다. 승인 SHA와 PR head가 달라지면 재검증한다. 병합 후 재시험 전 INTEGRATED로 쓰지 않는다.

[확실] PR #6/#7/#9는 기존 Draft 검토 기록으로 유지하며 이번 G1 준비로 병합하지 않았다. 실제 위치/heading/gear/speed 지원 P1, Activity/WebView 안내 연결 I1, 전체 주행 V1은 NOT_RUN/미구현 범위다. 기획의 stop 임계·별 수직 배치 등 미결정은 계약 준비를 빌미로 임의 확정하지 않았다.

[추정] 계약 변경은 사유·old/new·영향·migration·새 SHA/hash·검증을 BASELINE_UPDATE로 공지하고 두 담당자의 ACK를 확인한다. 기존 승인 기록은 Git/Issue에 보존한다.
