# StarLog 세 컴퓨터 공유·전달 규칙
운영 버전: three-pc-v1 · 작성: 2026-11-10

[확실] 팀 환경은 고사양 PC 1대와 노트북 2대다. 이 문서는 세 사람이 각자 AI로 개발할 때 자료·진행·코드·검증 결과를 공유하는 공통 절차다.
[추정] 아래는 요청에 맞춰 정한 운영 기준이다. 계약의 제품 임계값과 미결정 UX까지 사용자 승인으로 간주하지 않는다.

## 1. 세 장비의 책임
| 식별자 | 사람과 AI의 담당 | 담당하지 않는 영역 |
|---|---|---|
| HIGH_PC | 총괄, 공통 계약·설정, WebGL 화면, Pleos 실제 입력, 통합·검증 | 노트북 담당 코드를 임의 덮어쓰기 |
| LAPTOP_1 | 내비 코어: 경로, geometry, 진행·회전·이탈·Replay | Activity, 화면, 차량 adapter, StarLog 저장 |
| LAPTOP_2 | StarLog: 체류, 저장, 방위, 정차 상태, 성단·리캡 데이터 | 경로 엔진, 화면, 차량 adapter, 공통 설정 |

[추정] HIGH_PC가 이전 논리 역할 M+R+P를 맡고, 노트북1이 N, 노트북2가 S를 맡는다. 컴퓨터마다 별도의 AI 대화를 사용한다. 고사양 PC 안에서도 화면·실센서·통합 작업은 한 AI가 차례로 진행한다.
[확실] 각 AI 대화는 다른 AI의 대화를 자동으로 공유하지 않는다. 전달할 상태는 GitHub 문서·Issue·PR에 기록한다.

## 2. 정보 위치와 읽는 순서
[추정] 운영 공지는 이 저장소의 Issues, 코드 전달은 PR, 승인 계약은 integration/starlog-v1 브랜치의 파일을 기준으로 한다. 메신저는 GitHub 링크를 알리는 용도로만 쓰고 결정 내용은 해당 Issue에 남긴다.
1. docs/COLLABORATION.md: 이 운영 규칙.
2. docs/team/BOARD.md: HIGH_PC가 만드는 현재 작업 게시판.
3. docs/team/BASELINE.md: 승인 BASE_SHA, 계약 버전/해시, 빌드 환경.
4. docs/INTERFACE_CONTRACTS.md, docs/CODE_CONVENTIONS.md: HIGH_PC가 준비 단계에서 반영할 공통 규칙.
5. 각자 배정 Issue: 범위, 인수 조건, 선행 작업, 브랜치, 현재 상태.
6. 각자 받은 실행서와 AI 지시서.

[확실] 위 파일·Issue가 모두 이미 존재하는 것은 아니다. 현재 이 문서 게시와 후속 개발 착수를 구분한다. HIGH_PC가 준비 완료를 공지하기 전에는 노트북에서 통합용 구현을 시작하지 않는다.
[추정] 인수인계 통합본과 해당 장비의 두 문서는 담당자에게 파일로 먼저 전달한다. 통합본은 프로젝트 이해용, 이 문서는 세 장비 협업 방식의 최신 기준이다. 충돌 시 운영 배치·전달 방법은 이 문서, 데이터 구조는 승인 계약이 우선한다.

## 3. 시작 준비: HIGH_PC가 먼저 할 일
- 원본 프로젝트의 사용자 변경을 보존하고 Git 상태·원격·SHA를 확인한다.
- 원격 master와 실제 최신 원본을 비교한다. WebGL 화면 등 최신 파일이 빠져 있으면 필요한 소스만 검토한 준비 PR로 integration에 반영한다. 문서만 올려진 상태를 최신 앱 코드 배포 완료로 알리지 않는다.
- integration/starlog-v1이 없으면 확인된 원격 기준점에서 만든다. 있으면 현재 작업을 확인해 유지한다.
- 공통 설계의 contracts 코드, Clock, Fake 입력, golden fixtures, 테스트 실행 환경을 준비 PR에 반영한다.
- docs/team/BASELINE.md에 정확한 BASE_SHA/계약 버전/해시/검증 명령을 적는다. 미검증 값이나 자리표시자를 READY로 공지하지 않는다.
- 총괄 Issue와 장비별 Issue를 만든다. 제목은 [TEAM] 운영 게시판, [HIGH] 기반·화면·실환경 통합, [L1] 내비 코어, [L2] StarLog 기록·방위로 제안한다. 번호를 지어내지 말고 생성 후 BOARD에 실제 URL을 적는다.
- 두 노트북 담당자에게 private 저장소 접근·push 권한을 확인시킨다. 인증 토큰을 채팅·공유 문서로 전달하지 않는다.
- 아래 준비 완료 공지를 총괄 Issue에 게시하고 두 사람에게 링크를 전달한다.

```text
[READY_FOR_WORK]
integration: integration/starlog-v1
BASE_SHA: 실제 전체 commit SHA
contract: 버전 + 파일 hash
HIGH issue: 실제 URL
L1 issue / branch: 실제 URL / feat/sl-nav-v1
L2 issue / branch: 실제 URL / feat/sl-memory-v1
공통 테스트: 실제 실행 명령과 결과
미결정/미지원: 목록
```

[추정] 현재 원격에서 integration 브랜치·G1 준비 상태는 담당자가 확인해야 한다. 이 문서를 게시했다는 이유만으로 READY 상태가 되지 않는다.

## 4. 노트북 담당자의 시작
1. 저장소 접근과 본인 Issue를 확인한다.
2. 기존 프로젝트가 있으면 그 폴더를 사용한다. 새 장비에 소스가 없을 때만 한 번 clone한다. 원본을 중복 복사해서 여러 작업본을 만들지 않는다.
3. 받은 BASE_SHA가 origin/integration/starlog-v1 이력에 포함되는지 확인한다. contracts와 fixtures 파일의 hash도 공지와 비교한다.
4. 깨끗한 작업 상태에서 승인 BASE_SHA로 본인 기능 브랜치를 만든다. 사용자 변경이 있으면 자동 stash/reset하지 않고 AI가 보존 방안을 보고한다.
5. Issue에 STARTED, BASE_SHA, contract hash, branch를 댓글로 남긴다.
6. 본인 AI에 장비별 AI 지시서와 실제 저장소/Issue/BASE_SHA를 준다.

[추정] 저장소 주소: https://github.com/xodrndishfwk-crypto/HMG_Pleos . 앱 패키지는 현재 com.example.starlog2를 유지한다.

## 5. 수정 범위
[추정] 기준 Kotlin 경로는 app/src/main/java/com/example/starlog2/다. 대응 test/androidTest 같은 패키지까지 해당 담당 소유다.
| 장비 | 수정 가능 |
|---|---|
| HIGH_PC | contracts/**, integration/**, platform/**, Activity, app/src/main/assets/map.html·starlog/**, tests/web/**, Manifest/Gradle/res 공통, CI/docs/공통 fixture |
| LAPTOP_1 | navigation/**, 대응 navigation 테스트 |
| LAPTOP_2 | starlog/**, 대응 starlog 테스트 |

[추정] 노트북에서 dependency·Manifest·계약·공통 fixture 변경이 필요하면 자신의 Issue에 CHANGE_REQUEST를 남긴다. 임의로 공유파일을 수정하지 않는다. HIGH_PC는 영향노드·계약 버전·새 SHA를 공지한다. fixture는 worker가 본인 테스트용 하위 폴더에 추가할 수 있지만 공통 golden은 HIGH 소유다.

## 6. 언제 무엇을 공유하는가
| 시점/상태 | 기록할 내용 | 받는 사람/위치 |
|---|---|---|
| STARTED | 기준 SHA·계약 hash·브랜치·첫 작업 | 본인 Issue → HIGH_PC |
| IN_PROGRESS | 완료 항목·다음 단계·현재 한계 | 작업을 끝내는 시점 또는 의미 있는 중간 결과마다 본인 Issue |
| BLOCKED | 원인·재현·영향·필요한 답·계속할 독립 작업 | 본인 Issue → HIGH_PC, 연관 노드 링크 |
| CHANGE_REQUEST | 변경 전후 계약·필요 이유·영향·migration/tests | 본인 Issue → HIGH_PC 승인 전 공통 변경 보류 |
| PR_READY | PR·SHA·tests·연결 방법·known issues | 본인 Issue → HIGH_PC |
| CHANGES_REQUESTED | 실패 입력·예상/실제·로그·수정 조건 | HIGH_PC → 원래 PR/담당 Issue |
| INTEGRATED | 병합 SHA·통합 테스트·남은 제약 | HIGH_PC → 총괄 및 담당 Issue |
| DONE | 기능/통합/실환경 완료 범위 | HIGH_PC 최종 인수 기록 후 Issue 종료 |

[추정] “작업 완료” 메시지만 보내지 않는다. 아래 형식을 사용한다.
```text
장비 / Issue:
상태:
branch / BASE_SHA / 현재 SHA:
contract version/hash:
완료한 요구 ID:
변경 파일:
실행 명령 / exit code / 결과:
연결 방법:
Mock / Replay / 실제 입력 구분:
막힘·미결정·미실행:
PR URL:
HIGH_PC가 해야 할 다음 행동:
```

## 7. 코드·PR 전달
[추정] 기능 브랜치는 feat/sl-nav-v1, feat/sl-memory-v1, HIGH 내부 작업은 chore/sl-baseline-v1, feat/sl-renderer-v1, feat/sl-platform-v1 등을 사용한다. 이미 존재하면 덮지 말고 작업 이력부터 확인한다.
- 계약 준비 반영 전 노트북 작업 시작 금지.
- 기능 PR base는 integration/starlog-v1. master에 worker 직접 병합 금지.
- 커밋은 feat(navigation), feat(starlog), fix(renderer), test(...) 등 목적별로 나눈다.
- git add . 대신 담당 변경 파일만 stage. 키·local.properties·APK·캐시 제외.
- 브랜치를 Push하고 PR에 변경·테스트·의존성·계약 hash·연결 예시를 기록한다.
- gh가 없으면 GitHub 연결 도구나 웹 PR을 사용한다. 인증 실패면 코드/로컬 commit은 보존하고 PUSH_BLOCKED로 보고한다.
- 권한 있는 AI는 담당 Issue 댓글과 본인 PR 생성까지 수행한다. main 병합·다른 사람 코드 삭제·제품 범위 변경은 권한이 아니다.

[추정] 사람의 복붙 전달에 기대지 않고 코드 자체는 GitHub PR로 주고받는다. APK/캡처는 필요할 때 PR 증거로 첨부하되 소스 전달을 대체하지 않는다. 실제 위치·사진·토큰은 공유 증거에 넣지 않는다.

## 8. 병렬·통합 순서
```text
HIGH: 최신코드·계약·fixture·READY
          ├── L1: 내비 코어 + Replay ── PR
          ├── L2: 기록·방위·저장 ───── PR
          └── HIGH: 화면 + 실센서 adapter (순차)
HIGH: L1 검증/병합 → 화면 연결 → L2 검증/병합
      → 실제 입력 연결 → 회귀/전체 검증 → 최종 인수
```
[추정] 노트북끼리는 상대 구현 완료를 기다리지 않고 공통 Fake로 개발한다. 실제 Pleos 입력이 막혀도 Replay 통합은 계속한다. 실입력 미지원은 별도 제약으로 기록한다.
[추정] HIGH_PC의 한 작업폴더에서 두 AI가 동시에 branch checkout/commit하지 않는다. 준비·화면·플랫폼 브랜치는 작업을 commit/검증한 뒤 순차 전환한다. 에뮬레이터도 HIGH 담당이 설치 대상·APK SHA·검증 시간을 관리한다.

## 9. 검토와 종료
[추정] worker 자체 단위/계약/기능 테스트 → PR → HIGH 코드 검토 → 의존 기능 통합 → 회귀 → Pleos 검증 → 적용 승인 순서다.
[추정] 예제 덧셈 테스트만 통과, UI가 뜸, Fake callback 수신은 실제 기능 완료의 증거가 아니다. 진짜 차량 callback·지도 좌표 정합·방위 경계·신호대기 제외·정차 180초·저장 복원 등 해당 시나리오를 기록한다.
[추정] 실패한 PR은 구체 재현과 함께 수정 요청한다. 수정 후 새 SHA 테스트를 다시 제출한다. 병합된 오류는 영향 확인 후 revert 커밋으로 되돌린다. force push/reset --hard로 공유 이력을 덮지 않는다.
[추정] 최종 release PR은 HIGH_PC가 확인된 기본 브랜치로 만든다. 제품 요구 충족과 미지원 기능을 구분하고 사용자 최종 전달 범위를 따른다.

## 10. 현재 게시 상태
[확실] 이 문서는 세 컴퓨터 협업 규칙을 게시하는 작업이다. 이번 게시만으로 코드 동기화, 장비별 개발 착수, Issue/기능 브랜치 생성, G1 검증, 최종 앱 통합이 실행된 것은 아니다.
