# StarLog 승인 시작 기준
운영 버전: three-pc-v2 · 초기 작성: 2026-11-10

[확실] 현재 이 파일은 준비 상태를 기록할 양식이다. 코드·계약 검증을 수행한 G1 승인 기록이 아니며 값은 미등록이다. 노트북은 이 파일이 READY이고 자신의 H-G1 수신검증까지 끝난 뒤 통합용 구현을 시작한다.

## 1. 현재 승인 값
| 항목 | 값 |
|---|---|
| gate | NOT_READY |
| baselineRevision | 미등록 |
| approvedCodeSHA | 미등록 |
| integration branch | integration/starlog-v1 (실제 확인 필요) |
| contract version | v1 제안, 코드 동결 전 |
| contract files/SHA-256 | 미등록 |
| fixture files/SHA-256 | 미등록 |
| code preparation PR | 미등록 |
| G1 test evidence | 미등록 |
| READY comment / handoffId | 미등록 |
| L1 received/verified | 미등록 |
| L2 received/verified | 미등록 |
| approver / time | 미등록 |

## 2. HIGH가 READY 전에 해야 할 확인
- [ ] 최신 원본과 원격을 비교하고 승인할 코드 준비 PR을 확인한다.
- [ ] contracts 실제 파일·직렬화·Clock·Fake·fixture·harness가 같은 기준에서 동작한다.
- [ ] 원격 integration에 approvedCodeSHA가 존재한다.
- [ ] 빌드·계약 테스트 명령·환경·결과가 기록되어 있다.
- [ ] 노트북 두 사람의 저장소 접근과 담당 Issue가 확인된다.
- [ ] 위 표에 미등록/자리표시자가 남아 있는 필수 항목이 없다.
- [ ] G1 Handoff 댓글·READY 공지를 만들고 BOARD에 연결한다.

[추정] approvedCodeSHA는 검증한 소스 commit이다. 그 뒤 승인 문서를 갱신한 commit SHA와 같을 필요가 없다. “이 파일 자체 최신 commit”을 무조건 코드 기준으로 쓰지 않는다.
[추정] 계약 hash는 실제 파일의 SHA-256이며 glob 문자열의 hash가 아니다. 경로와 hash를 파일별로 남기거나 승인된 manifest 파일을 연결한다.

## 3. 환경/재현 명령
[추정] HIGH가 실제 확인 후 아래 항목을 표에 추가한다: OS, Gradle wrapper/JVM, Java target, Android SDK/빌드 도구, Node 버전(사용 시), SDK 의존성 버전, 테스트 명령·exit code·결과, 부팅된 Pleos의 API/기기 정보.
[추정] 실패/미실행 검증을 삭제하고 READY로 바꾸지 않는다. 노트북의 자체 환경 확인은 별도 STARTED/BASELINE_ACK 댓글에 기록한다.

## 4. 기준 변경
[추정] 변경 사유·old/new 계약·영향 장비·migration·새 코드 SHA/hash·필수 시험을 HANDOFF의 BASELINE_UPDATE로 공지한다. 두 장비의 BASELINE_ACK가 오기 전 새/옛 계약을 섞어 최종 통합하지 않는다. 이전 기준과 수신 기록은 Git/Issue 이력에 보존한다.
