# StarLog AI가 수행할 인수인계
운영 버전: three-pc-v3 · 갱신: 2026-11-10

[추정] **이 문서는 사람이 빈 양식을 작성하라는 문서가 아니다.** 개발·시험 결과를 주고받을 때 담당 AI가 아래 절차와 상세 양식을 사용한다. 사람은 전체 흐름을 [WORKFLOW](WORKFLOW.md)에서 보고, 자기 AI를 시작/재개하면 된다.

## 1. 미리 정해 둔 전달 지점
| 언제 | 보내는 AI → 받는 AI | 보내는 AI가 준비할 것 | 받는 AI가 해야 할 일 |
|---|---|---|---|
| 공통 준비 G1 완료 | HIGH → L1/L2 | 코드 기준·공통 형식·시험 입력·담당 작업·실행 명령 | 내려받기·기반 검사 후 각자 개발 시작 |
| 내비 N1/N2 전달 | L1 → HIGH | 내비 코드·PR·공개 API 사용 예시·경로/Replay 입력·테스트 | 검토·지도 연결·주행 안내 검증·결과 회신 |
| 기록/방위 S1/S2 전달 | L2 → HIGH | 계산/저장 코드·PR·사용 예시·정차/방위/복원 테스트 | 검토·별/액자/저장 연결·결과 회신 |
| Pleos 시험 대행 | L1 또는 L2 → HIGH | 시험 코드·명령·기대값·시험할 코드 버전 | 실제 기기에서 실행하고 결과 회신 |
| 연결·시험 실패 | HIGH → 원래 담당 AI | 실패 입력·실행환경·기대/실제·수정 조건 | 원래 PR에서 수정·재시험·재전달 |
| 공통 형식 변경 | HIGH → L1/L2 | 바뀐 타입·영향·새 코드 기준·필수 재시험 | 자기 구현에 반영·시험 후 확인 회신 |
| 세션 종료/새 대화 | 현재 AI → 다음 담당 AI | 현재 코드·완료/미완료·막힌 입력·다음 실행 | 실제 GitHub 기록을 확인하고 이어서 작업 |
| 최종 인수 | HIGH → 사용자/팀 | 최종 코드/앱·검증·실행 안내·남은 제약 | 합의 범위와 결과 확인 |

## 2. AI에 적용되는 수행 지시
1. [추정] 작업 시작/재개 때 GitHub docs/README.md, WORKFLOW.md, 자기 담당 Issue의 최신 기록을 읽는다.
2. [추정] 자기 단계가 끝나면 코드 PR과 실제 테스트 결과를 작성하고 받는 쪽의 다음 행동까지 남긴다.
3. [추정] 다른 장비 결과를 기다리는 작업에 도달하면 해당 PR/Issue를 조회한다. 입력이 있으면 확인·검증·연결까지 진행한다.
4. [추정] 입력이 없으면 무엇이 필요한지 자기 Issue에 기록하고 독립 작업을 계속한다. 사람에게 빈 양식을 대신 쓰게 하지 않는다.
5. [추정] 받은 결과가 실패하면 원래 담당 작업에 재현 정보를 남긴다. 수정한 AI는 새 코드 버전·재시험 결과를 회신한다.
6. [추정] 인증/권한/물리 기기 연결/제품 선택처럼 사람이 필요한 부분만 구체적으로 요청한다.
7. [확실] GitHub에 읽기/쓰기 가능한 도구와 실행 중인 AI가 필요하다. 이 문서 자체가 컴퓨터 간 자동 실행·상시 감시를 설치하지 않는다.

<details>
<summary>AI용 상세 기록 형식 펼치기 — 사람이 직접 채울 필요 없음</summary>

[추정] 아래 양식의 작성 주체는 각 담당 AI다.

[추정] 전달할 때 이 문서의 양식을 해당 작업 Issue 댓글에 복사한다. 코드는 PR, 상세 결과는 그 PR에 기록하고 댓글에는 링크·정확한 SHA·다음 행동을 남긴다. 내용 없는 “끝남” 메시지로 다음 장비를 시작시키지 않는다.
[확실] 이동 순서는 [WORKFLOW](WORKFLOW.md), 장비별 경로는 [BOARD](team/BOARD.md), 책임은 [COLLABORATION](COLLABORATION.md)을 따른다.

## 1. 한 번의 전달이 끝나는 조건
1. [추정] 보내는 장비가 HANDOFF_READY 댓글과 PR을 올린다.
2. [추정] 받는 장비가 내용을 읽고 RECEIVED 또는 NEEDS_INFO를 답한다.
3. [추정] 받은 코드 SHA·계약을 확인하고 명시된 시험을 수행한다.
4. [추정] 통과하면 ACCEPTED 또는 INTEGRATED, 실패하면 REJECTED와 재현 정보를 답한다.
5. [추정] HIGH AI가 총괄 Issue의 실제 진행·증거·다음 행동을 갱신한다. 수신 확인만으로 검증 통과가 되지 않는다.

[추정] 다른 담당에게 알림이 자동 전달된다고 가정하지 않는다. 실제 GitHub 계정이 확인된 경우 담당을 mention하고, 팀 메신저를 쓰면 해당 Issue 댓글 링크를 사람이 알려준다. AI가 권한 없이 외부 메시지를 보내지 않는다.

## 2. 전달 묶음의 필수 항목
| 항목 | 빠지면 받는 쪽이 할 일 |
|---|---|
| 인수인계 ID/작업 ID/송신·수신 장비 | 누구의 어떤 결과인지 NEEDS_INFO |
| PR URL·branch·정확한 code SHA | 코드 재현이 불가능하므로 인수 보류 |
| 기준 BASE_SHA·계약 버전/hash | 호환 기준 확인 전 적용 보류 |
| 구현 범위·변경파일 | 소유권·기능 범위 확인 요청 |
| 공개 API와 연결 예시 | 호출 가능한 함수·타입 정보 요청 |
| 테스트 명령·결과·환경·시험 code SHA | 시험 증거 확인, 누락 시험은 NOT_RUN |
| Mock/Replay/실데이터 구분 | 실제 연동 완료 인정 보류 |
| 미실행 시험·알려진 제약·DB 변경 | 인수 범위와 위임 시험 명확히 요청 |
| 받는 장비가 할 다음 행동/통과 기준 | 다음 단계 착수 보류·행동 확인 |

[추정] 문서·PR·테스트 로그에 정밀 위치·사진·토큰을 넣지 않는다. 재현 데이터는 합성 또는 익명화 fixture로 남긴다.

## 3. 기능 코드 전달 양식
```text
[HANDOFF_READY]
handoffId: H-N1-01 (고유 ID, 변경 시 revision 추가)
taskId: N1
from: LAPTOP_1
to: HIGH_PC
issue:
PR:
branch:
codeSHA: 실제 전체 commit SHA
baseSHA:
contractVersion / contractHash:
requirements:
changedFiles:
implemented:
notImplemented / knownIssues:
input -> output:
publicAPI / 연결 예시:
fixtures:
tests: 명령 / 환경 / codeSHA / exit code / 결과
dataSource: Mock | Replay | LIVE (섞인 경우 항목별 구분)
delegatedTests: NOT_RUN 목록 또는 없음
databaseMigration: 내용 또는 없음
receiverNextAction: 어떤 파일에서 무엇을 연결하고 무슨 시험을 할지
acceptanceCriteria:
```

[추정] 같은 PR의 다음 checkpoint는 새 codeSHA와 revision을 써서 전달한다. 예전 인수 결과는 예전 SHA에만 유효하다.

## 4. 받는 장비의 응답
```text
[RECEIVED]
handoffId:
receiver:
receivedCodeSHA:
contractChecked:
validationPlan:
nextAction:
validationSlot: 예정 순서/시간 또는 미정과 이유
```

```text
[NEEDS_INFO]
handoffId:
missing:
whyNeeded:
senderAction:
receiverIndependentWork:
```

```text
[ACCEPTED] 또는 [INTEGRATED]
handoffId:
reviewedCodeSHA:
integrationSHA: 실제 병합/조립 SHA, 아직 병합 안 했으면 null
checks: 명령 / 환경 / 결과
acceptedScope:
remainingLimitations:
nextTask / nextComputer:
evidenceURLs:
```

[추정] ACCEPTED는 지정 checkpoint의 검토·시험 통과다. INTEGRATED는 실제 통합 branch에 연결·검증한 상태다. 최종 DONE은 전체 인수 조건 통과 뒤 HIGH가 기록한다.

## 5. 반려와 재전달
```text
[REJECTED]
handoffId:
testedCodeSHA:
contractVersion:
reproduction: 입력 fixture + 실행 명령 + 환경
expected:
actual:
errorCode / anonymizedLog:
requiredFix:
retestCriteria:
nextOwner:
affectedTasks:
independentWorkThatCanContinue:
```

[추정] 담당자는 같은 PR에 수정 commit을 추가하고 테스트를 재실행한 뒤 HANDOFF_READY revision을 보낸다. HIGH가 대신 다른 노트북 소유 코드를 무단 수정하지 않는다.

## 6. 노트북 → HIGH 시험 대행 요청
```text
[TEST_DELEGATION]
handoffId: H-S2-DB-01
from: LAPTOP_2
to: HIGH_PC
taskId: S2
issue / PR / codeSHA:
testFiles:
requiredEnvironment: Pleos 또는 일반 Android / API 수준 / 필요한 서비스
runCommand: 설치된 toolchain에서 그대로 실행할 명령
fixture / setup:
expectedResult:
resetPolicy: 합성 테스트 DB만, 실제 사용자 데이터 삭제 금지
currentResult: NOT_RUN
whatBlocksUntilResult:
independentWork:
```

[추정] HIGH 회신은 TEST_RESULT로 같은 ID, 실제 실행 codeSHA, 기기/API/서비스, 명령, exit code, 기대/실제, PASS/FAIL/NOT_RUN, 증거 링크를 남긴다. 권한/환경 부재는 NOT_RUN이지 기능 PASS가 아니다.

## 7. HIGH → 노트북 공통 변경 전달
```text
[BASELINE_UPDATE]
changeId:
reason:
oldVersion / newVersion:
newApprovedCodeSHA:
contractHash:
changedTypes / fixtures:
impactL1:
impactL2:
HIGHActions:
migration:
requiredChecks:
compatibilityWindow:
resumeCriteria:
```

[추정] L1/L2는 BASELINE_ACK로 적용한 SHA/hash·자신의 branch SHA·계약 테스트 결과·계속할 task를 답한다. 제품 임계 변경은 사용자 확인 기록까지 연결한다.

## 8. 사람/AI 대화가 바뀔 때
```text
[SESSION_RESUME]
computer / task / issue:
currentBranch / currentCodeSHA:
contractVersion / hash:
workingTree: clean 또는 사용자/작업 변경 파일 목록
completed:
inProgress:
waitingFor: handoffId + 담당 컴퓨터 + 필요한 결과
lastHandoffURL:
commandsAlreadyRun / results:
nextExactAction:
doNotRepeat:
```

[추정] 새 담당 AI는 실제 git status·Issue·PR을 재조회해서 이 기록과 일치하는지 확인한다. 예전 대화 내용을 기억한다고 가정하지 않는다.

## 9. 증거와 완료 구분
[추정] RECEIVED → ACCEPTED → INTEGRATED → DONE을 별도로 기록한다. PR 생성·노드 테스트·Pleos replay·실제 callback·실차 주행은 각기 다른 검증이다. “실차 미검증”을 전체 개발 실패나 실차 성공으로 바꾸지 않는다.

</details>
