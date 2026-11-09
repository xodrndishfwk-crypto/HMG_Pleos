# StarLog 공동 진행표
운영 버전: three-pc-v2 · 초기 작성: 2026-11-10

[확실] 세 사람이 작업 시작/재개 때 여기를 먼저 읽는다. 문서 게시 시점에는 개발 진행·승인 SHA·담당 Issue 증거를 아직 등록하지 않았다. 아래는 초기 작업 배치이며 실제 착수·완료를 자동 감지한 현황이 아니다.

## 바로 가기
- [전체 워크플로우](../WORKFLOW.md)
- [인수인계 양식과 수신·반려 절차](../HANDOFF.md)
- [협업 규칙](../COLLABORATION.md)
- [승인 시작 기준](BASELINE.md)
- [Issues](https://github.com/xodrndishfwk-crypto/HMG_Pleos/issues)
- [Pull requests](https://github.com/xodrndishfwk-crypto/HMG_Pleos/pulls)

## 1. 지금 누가 무엇부터 하는가
| 컴퓨터 | 현재 권장 행동 | 다음 실행을 여는 조건 |
|---|---|---|
| HIGH_PC | G0 최신 원본/원격 확인 → G1 코드·계약·fixture·Issue 준비 | G1 실제 검증 통과 후 READY_FOR_WORK |
| LAPTOP_1 | 실행서/지시서·접근권한·환경·내비 시험 시나리오 준비 | HIGH의 H-G1 수신·SHA/hash 확인 후 N1 |
| LAPTOP_2 | 실행서/지시서·접근권한·환경·체류/저장 시험 시나리오 준비 | HIGH의 H-G1 수신·SHA/hash 확인 후 S1 |

[추정] 준비 전 통합용 구현을 임의 기준으로 시작하지 않는다. 실제로 이미 수행된 작업이 있다면 HIGH가 근거 PR/SHA를 확인해 아래를 갱신한다.

## 2. 작업 현황
[추정] 상태는 HIGH가 실제 증거를 확인해서 갱신한다. “대기”는 설계상 선행 조건 미등록이며 실제 다른 장비의 작업 유무를 단정하지 않는다.
| 작업 | 담당 | 상태 | 기다리는 입력 | 전달/PR/증거 | 다음 장비·작업 |
|---|---|---|---|---|---|
| G0 코드 기준 확인 | HIGH | NOT_CONFIRMED | 최신 원본/원격 차이·권한 확인 | 미등록 | HIGH G1 |
| G1 공통 준비 | HIGH | WAITING_INPUT | G0 통과 | 미등록 | L1 N1, L2 S1, HIGH R0/P1 |
| N1 경로·진행·Replay | L1 | WAITING_INPUT | H-G1 수신/기반 검사 | 미등록 | HIGH I1 준비, L1 N2 |
| N2 내비 보강 | L1 | WAITING_INPUT | N1 기반 | 미등록 | HIGH I1 |
| S1 체류·방위·정차 | L2 | WAITING_INPUT | H-G1 수신/기반 검사 | 미등록 | HIGH I2 준비, L2 S2 |
| S2 저장·복원 | L2 | WAITING_INPUT | S1 기반 | 미등록 | HIGH DB 검증/I2 |
| R0 Fake 화면 | HIGH | WAITING_INPUT | G1 계약 | 미등록 | HIGH I1/I2 |
| P1 실제 입력 | HIGH | WAITING_INPUT | G1·실환경 슬롯 | 미등록 | HIGH V1 지원범위 |
| I1 내비 연결 | HIGH | WAITING_INPUT | R0·N checkpoint 검토 | 미등록 | HIGH V1 |
| I2 별·저장 연결 | HIGH | WAITING_INPUT | R0·S checkpoint 검토 | 미등록 | HIGH V1 |
| V1 전체 검증 | HIGH | WAITING_INPUT | I1/I2·P1 지원표 | 미등록 | HIGH REL |
| REL 최종 인수 | HIGH | WAITING_INPUT | V1·사용자 필수범위 | 미등록 | 최종 사용자 전달 |

## 3. 배정과 기준
| 항목 | 값 |
|---|---|
| 총괄 Issue | 미생성/미등록, HIGH가 실제 생성 후 URL 입력 |
| HIGH 담당 Issue/사람 | 미등록 |
| L1 담당 Issue/사람 | 미등록 |
| L2 담당 Issue/사람 | 미등록 |
| 승인 기준 | BASELINE.md, 현재 NOT_READY |
| 코드 통합 branch | integration/starlog-v1 (사용 전 존재·이력 확인) |
| 운영 현황 branch | master (이 문서 최신본) |

## 4. 인수인계 대기열
| handoffId/revision | from → to | codeSHA | 상태 | Issue/PR 댓글 | 수신자 다음 행동 |
|---|---|---|---|---|---|
| 아직 없음 | — | — | 미등록 | — | G1 준비 후 첫 H-G1 등록 |

[추정] G1 게시 후 HIGH는 장비별 수신확인을 따로 기록한다. 한 장비의 RECEIVED를 두 장비 모두 수신한 것으로 처리하지 않는다.

## 5. HIGH 장비 검증 예약
| 요청 ID | 요청 장비 | 시험/받은 codeSHA | 예약 순서/시간 | 결과 링크 |
|---|---|---|---|---|
| 아직 없음 | — | — | — | — |

[추정] 검증에는 설치한 package/serial/userId/API/서비스와 APK를 만든 codeSHA를 남긴다. 같은 worktree·에뮬레이터를 여러 AI가 동시에 변경하지 않는다.

## 6. 차단·변경·결정
| ID | 내용 | 영향 task/장비 | 담당 | 계속할 독립 작업 | 결정/해제 근거 |
|---|---|---|---|---|---|
| 초기 준비 | G0/G1 완료 증거 미등록 | N1/S1/R0/P1 | HIGH | 자료·환경·시험 시나리오 | READY와 수신확인 링크 필요 |

## 7. 갱신 방법
- [추정] 노트북은 본인 Issue/PR에 진행·전달·차단을 기록한다. BOARD 행을 각자 직접 동시에 편집하지 않는다.
- [추정] HIGH가 세션 시작/종료, 인수인계 수신, 검증 결과, 병합, 계약 변경 때 최신 Issue/PR을 읽고 BOARD를 갱신한다.
- [추정] BOARD의 값은 현재 코드·시험·댓글을 가리키는 링크 요약이다. 증거 없는 완료 체크는 하지 않는다.
- [추정] 문서 갱신 전 원격 최신 내용을 다시 읽고 충돌 시 수정사항을 보존한다. 오래된 로컬 사본으로 덮어쓰지 않는다.
- [추정] 상태: NOT_CONFIRMED, WAITING_INPUT, READY_TO_START, IN_PROGRESS, HANDOFF_READY, RECEIVED, NEEDS_INFO, CHANGES_REQUESTED, ACCEPTED, INTEGRATED, DONE, BLOCKED. 의미는 HANDOFF와 WORKFLOW를 따른다.
