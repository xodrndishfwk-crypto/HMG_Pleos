# StarLog 별자리 멜로디 v1 관리 노드 인수 보고

최초 작성일: 2026-11-14 KST
게시 복구 갱신일: 2026-11-15 KST
수정 재인수 갱신일: 2026-11-15 KST
상태: `HANDOFF_READY` revision 3 — M1/M2 수정 및 SQLite 시험 보완, Android SDK 환경 재검증 대기, integration 미통합

## Revision 3 — MANAGE_NODE 검토 후 수정

MANAGE_NODE revision 2 검토의 상세 보고서와 PR 댓글을 확인하고 다음을 수정했다.

- 관리 보고서: `docs/melody-v1-manage-r2` 브랜치의 `docs/team/MELODY_V1_MANAGE_NODE_REPORT.md`
- 관리 결과: https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/20#issuecomment-6879735573
- 이전 기능 SHA: `5e8044f3da2935580212e90310c5b4b9c2bfd91f`
- revision 3 기능 SHA: `3b53d380b5a246f7e620db1df1d15551237ff352`

### M1 — 실제 앱 입력 경로의 체류 시간 보존

`MelodyStudioActivity`가 동일 `SourceMode`의 `Visit` 목록을 함께 조회하도록 수정했다.
새 순수 변환기 `MelodyInputProjector`는 `MemoryStar.visitId`로 Visit을 연결하고
`endAtMs - startAtMs`를 실제 `dwellTimeMs`로 전달한다. 연결되는 동일 모드 Visit이
없으면 값을 만들거나 0으로 채우지 않고 `null`을 유지한다.

JVM 테스트와 독립 스모크에서 서로 다른 양수 체류 시간을 가진 합성 Visit이 다음 경로를
통과한 뒤에도 같은 값을 유지하는 것을 확인했다.

```text
Visit → MemoryStar → MelodyInputProjector(실제 Activity 사용)
→ MelodyStar → MelodyGenerator → MelodyNote
```

같은 시험에서 `starId`, `connectionOrder`, `createdAtMs` 보존과 REPLAY Visit을 LIVE
별자리 체류 시간에 사용하지 않는 것도 확인했다. 공통 contract와 DB schema는 변경하지 않았다.

### M2 — 미리 듣기 생명주기와 대기 작업

관리 노드가 지적한 위험을 다음 최소 구조로 보완했다.

- 저장소 I/O/생성 executor와 오디오 미리 듣기 executor를 분리했다.
- `MelodyPlaybackSession`이 세대 번호와 `Future.cancel(true)`로 최신 재생 요청만 유효하게 한다.
- 새 재생 요청은 기존 재생과 대기 요청을 중단한다.
- `onPause()`에서 대기 재생을 무효화하고 현재 `AudioTrack`을 정리한다.
- `onResume()` 이후에만 새 재생 요청을 받는다.
- PCM 생성 중 주기적으로 취소 상태를 확인하며, 무효화된 요청은 `AudioTrack`을 시작하지 않는다.
- `onDestroy()`는 playback을 닫고 I/O 큐를 `shutdownNow()`로 취소한다.
- repository는 UI thread를 막지 않는 정리 thread에서 실행 중 I/O 종료를 기다린 뒤 닫는다.
- 반복 `deactivate()`/`close()`와 중복 자원 정리가 안전하게 동작한다.

JVM 상태 전이 스모크에서 연속 요청 중 마지막 요청만 실행되고 pause/deactivate 전에
대기하던 요청이 실행되지 않는 것을 확인했다. 실제 Android AudioTrack 청취·pause/stop/
destroy/재진입 검증은 이 환경에 Android SDK/기기가 없어 `NOT_RUN`이며 HIGH_PC에 위임한다.
누수나 crash가 실제 기기에서 재현됐다고 주장하지 않는다.

### SQLite 계측시험 보완

기존 `SqliteMemoryRepositoryAndroidTest.verifyAll()`은
`StarLogAcceptanceInstrumentedTest.delegatedSqliteSuite()`에서 실제 계측 진입점으로 호출된다.
해당 스위트에 다음을 추가했다.

- base_melodies가 없는 실제 v2 합성 DB 파일 생성 후 v3 upgrade
- 기존 Visit/MemoryStar/Constellation/ConstellationMember 행 보존
- `base_melodies` 테이블 생성 확인
- 모든 `BaseMelody` 필드 JSON round-trip
- repository close/reopen 후 확정 멜로디 복원
- constellation metadata upsert 후 확정 멜로디 보존
- 같은 별자리에 다른 melody ID 저장 거부와 원본 보존
- LIVE/REPLAY 분리 상태 유지
- 기존 v1 migration 시험의 기대 DB 버전을 현재 schema version 3으로 수정

시험 코드는 실제 사용자 DB가 아닌 전용 DB 이름만 사용하며 `finally`에서 해당 합성 DB만 삭제한다.

### Revision 3 변경 파일

```text
app/src/main/java/com/example/starlog2/MelodyStudioActivity.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyInputProjector.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyPlaybackSession.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyPreviewPlayer.kt
app/src/test/java/com/example/starlog2/starlog/melody/MelodyInputProjectorTest.kt
app/src/test/java/com/example/starlog2/starlog/melody/MelodyPlaybackSessionTest.kt
app/src/androidTest/java/com/example/starlog2/StarLogAcceptanceInstrumentedTest.kt
app/src/androidTest/java/com/example/starlog2/starlog/SqliteMemoryRepositoryAndroidTest.kt
docs/team/MELODY_V1_HANDOFF.md
```

### Revision 3 검증 결과

| 검사 | 결과 |
|---|---|
| `tools/Verify-G1.ps1` | PASS, exit 0, 26 files, hash `a99ecd...7cfe48` |
| Android 독립 Kotlin 컴파일 | PASS, exit 0, Gradle 9.6 배포본의 Kotlin compiler 사용 |
| revision 3 M1/M2 합성 스모크 | PASS, exit 0, 체류시간 7000/13000ms 보존·마지막 재생만 실행·deactivate 대기 요청 취소 |
| `git diff --check` | PASS |
| 선택 JVM Gradle 시험 | NOT_RUN, launcher exit 1 — 이 환경에서 Android SDK 경로/설치물을 찾지 못해 task dependency 결정 전 중단 |
| 전체 JVM/lint/assemble/androidTest 빌드 | NOT_RUN, launcher exit 1 — 동일 SDK location 오류 |
| connected Android 계측시험 | NOT_RUN, launcher exit 1 — 동일 SDK location 오류 |
| 실제 SQLite v2→v3 계측 실행 | NOT_RUN — Android SDK/에뮬레이터 필요 |
| 1700×1184 UI/AudioTrack/내비 회귀 | NOT_RUN — Android SDK/기기 필요 |

이번 환경의 Gradle 실패는 MANAGE_NODE 환경에서 보고된 ZIP lock/daemon/ADB 장애와 다르다.
이 환경에서는 Gradle launcher가 실행됐으나 `SDK location not found`로 Android task가 시작되지 않았다.

## Revision 3 전달 식별자

```text
[HANDOFF_READY]
handoffId: H-MELODY-V1-20261114-01
revision: 3
taskId: H7-MELODY-V1
from: NAVIGATION_NODE (멜로디 v1 수정 담당)
to: MANAGE_NODE / HIGH_PC
PR: https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/20
branch: feat/starlog-melody-v1
previousCodeSHA: 5e8044f3da2935580212e90310c5b4b9c2bfd91f
codeSHA: 3b53d380b5a246f7e620db1df1d15551237ff352
baseSHA: 80657e1d765fe64b881238314d69a358335a994b
latestRemoteIntegrationSHAAtRevision: ede0b01f2cd5efcd5eae09631c5ed29d00b7d3a9
mergeBaseSHA: 80657e1d765fe64b881238314d69a358335a994b
targetBranch: integration/starlog-v1
contractVersion: starlog-contracts-1.0.0 (변경 없음)
contractHarnessHash: a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48
integrationStatus: NOT_INTEGRATED
```

## Revision 2 — 원격 게시 복구

HIGH_PC가 기능 ref와 SHA를 찾지 못한 직접 원인은 원본 개발 환경의
`feat/starlog-melody-v1` 브랜치가 로컬에만 있고 origin에 push되지 않았기 때문이다.
로컬 커밋이나 인수 정보가 사라진 것은 아니었으며 다음 객체를 모두 확인했다.

- 기준 SHA `80657e1d765fe64b881238314d69a358335a994b`
- 기능 SHA `5e8044f3da2935580212e90310c5b4b9c2bfd91f`
- 최초 인수서 SHA `12d6fde4fad831f23f05e209ad58499a86e0380c`

2026-11-15 KST에 기능 브랜치를 force 없이 origin에 일반 push했다. push 직후 실제
`git ls-remote`에서 feature ref가 `12d6fde4...`를 가리키는 것과 fetch 성공을 확인했다.
같은 시점의 최신 원격 integration은 `ede0b01f2cd5efcd5eae09631c5ed29d00b7d3a9`다.

최신 integration은 최초 기준 이후 destination navigation 변경이 추가됐지만 최초 기준
`80657e1d...`의 후손이다. 기능 브랜치와 최신 integration의 merge-base는
`80657e1d...`이며 `git merge-tree --write-tree`는 충돌 없이 결과 tree를 생성했다.
기능 브랜치를 최신 integration 위로 임의 rebase하지 않았고 integration에도 push하지 않았다.

## Revision 2 전달 식별자 (이력)

```text
[HANDOFF_READY]
handoffId: H-MELODY-V1-20261114-01
revision: 2
taskId: H7-MELODY-V1
from: CODEX_HIGH
to: MANAGE_NODE / HIGH_PC
PR: https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/20
branch: feat/starlog-melody-v1
codeSHA: 5e8044f3da2935580212e90310c5b4b9c2bfd91f
baseSHA: 80657e1d765fe64b881238314d69a358335a994b
latestRemoteIntegrationSHA: ede0b01f2cd5efcd5eae09631c5ed29d00b7d3a9
mergeBaseSHA: 80657e1d765fe64b881238314d69a358335a994b
targetBranch: integration/starlog-v1
contractVersion: starlog-contracts-1.0.0 (변경 없음)
contractHarnessHash: a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48
dataSource: 기존 SQLite LIVE/REPLAY 별자리; 가짜 별자리 입력 없음
```

## 구현 범위

주행 기억의 `MemoryStar`와 `ConstellationMember.orderIndex`를 원본 입력으로 사용해 다음 파이프라인을 구현했다.

```text
원본 별 좌표/연결 순서
→ 중심점 기준 회전
→ 선택 장음계 매핑
→ complete-link 화음 그룹
→ 전체 음열 동적 계획법 옥타브 보정
→ 기본 악보/리듬
→ 기존 악보 최대 유사도 및 자연스러움 비용
→ 5° 전체 탐색 + 최적 주변 1° 탐색
→ 확정 멜로디 SQLite 저장/복원
→ IVI Canvas 악보 UI/오프라인 PCM 미리 듣기
→ 공급자 중립 편곡 요청
```

- 원본 별 ID, 좌표, 연결 순서, 생성 시각, 체류 시간을 확정 음표까지 보존한다.
- 같은 입력·설정·비교 컬렉션이면 같은 각도와 ID를 생성한다.
- `SUCCESS`, `BEST_EFFORT`, `INSUFFICIENT_DATA`, `GENERATION_FAILED`를 구분한다.
- 편곡은 장르·리듬·악기·템포와 음 시작 시점·화음 그룹을 포함한다.
- 실제 Suno/API 호출은 하지 않았으며 “편곡 입력 준비”까지만 표시한다.

## 변경 파일

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/starlog2/GlNavigationActivity.kt
app/src/main/java/com/example/starlog2/MelodyStudioActivity.kt
app/src/main/java/com/example/starlog2/starlog/melody/ArrangementPromptBuilder.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyCanvasView.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyGenerator.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyGeometry.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyModels.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodyPreviewPlayer.kt
app/src/main/java/com/example/starlog2/starlog/melody/MelodySimilarity.kt
app/src/main/java/com/example/starlog2/starlog/storage/BaseMelodyJson.kt
app/src/main/java/com/example/starlog2/starlog/storage/MemoryRepository.kt
app/src/main/java/com/example/starlog2/starlog/storage/SqliteMemoryRepository.kt
app/src/test/java/com/example/starlog2/starlog/MemoryRepositoryTest.kt
app/src/test/java/com/example/starlog2/starlog/melody/MelodyGeneratorTest.kt
app/src/androidTest/java/com/example/starlog2/starlog/SqliteMemoryRepositoryAndroidTest.kt
docs/MELODY_IMPLEMENTATION_PLAN.md
```

사용자 소유 미추적 파일 `NAVIGATION_MANAGER_REPORT.md`는 스테이징·수정하지 않았다.

## 공개 API와 연결 예시

```kotlin
val result = MelodyGenerator(
    MelodyConfig(
        keySignature = MelodyKey.C_MAJOR,
        chordThresholdRatio = 0.25,
    ),
).generate(
    constellationId = constellation.id,
    stars = melodyStars,
    existingMelodies = (repository.listBaseMelodies() as? Outcome.Ok)?.value.orEmpty(),
    comparisonCollectionVersion = collectionVersion,
)

if (result.melody != null) {
    repository.saveBaseMelody(result.melody)
}
```

앱에서는 `GlNavigationActivity` 하단의 `별자리 멜로디 스튜디오` 버튼으로 진입한다. 스튜디오는 최신 LIVE 별자리를 우선하고 없으면 REPLAY 별자리를 읽는다. 별자리가 없을 때 임의 샘플을 만들지 않고 빈 상태를 표시한다.

## 데이터베이스 변경

- `StarLogDbSchema.CURRENT_DATABASE_VERSION`: 2 → 3
- 신규 테이블: `base_melodies`
- `constellation_id` unique 및 `constellations(id)` 외래키 사용
- v2→v3 마이그레이션은 `CREATE TABLE IF NOT EXISTS`만 사용하며 `DROP TABLE`이 없다.
- 기존 `saveConstellation`의 `CONFLICT_REPLACE`는 부모 행 교체로 확정 악보를 cascade 삭제할 수 있어 `CONFLICT_IGNORE + UPDATE` upsert로 교정했다.
- 한 별자리의 다른 ID 확정 악보 덮어쓰기는 거부한다.

## 코드·Git 컨벤션 검토

| 항목 | 결과 |
|---|---|
| 브랜치 분리 | 기능 SHA는 기준 `80657e1d...`의 직접 후손이며 최신 integration과 merge-base는 해당 기준 SHA |
| 커밋 형식 | `feat(starlog): add constellation melody studio` |
| 패키지 배치 | 계산/UI 보조는 `starlog/melody`, 저장은 기존 `starlog/storage` |
| UI 관례 | 기존 프로젝트와 동일한 programmatic Activity/Canvas 방식 |
| 테스트 배치 | 순수 계산은 `src/test`, SQLite 실구현은 `src/androidTest` |
| 공통 계약 | `contracts` 및 fixture 변경 없음 |
| 설정값 | 탐색 간격·음역·가중치·임계값은 `MelodyConfig` 중앙 관리 |
| 계산/UI 분리 | `MelodyGenerator`는 Android 의존성이 없고 UI는 결과만 렌더링 |
| Git 오염 | 미추적 사용자 파일 제외, 무관한 navigation 부모 커밋 제거 |

검토 중 수정한 결함:

1. 비연속 화음의 리듬 비교가 연결 순서 기준일 때 음수 간격을 만들 수 있어 시작 시점 기준으로 분리 정렬했다.
2. 편곡 요청에 음 시작 시점과 화음 MIDI 그룹을 추가했다.
3. 별 생성 시각·체류 시간이 확정 악보에서 사라지지 않도록 보존했다.
4. 조성·화음 임계값 등 음악 정체성 설정을 안정 ID 입력에 포함했다.
5. UI에 조성, 화음 임계값, 0° 복원을 추가했다.
6. SQLite 부모 행 `REPLACE`로 확정 악보가 삭제될 수 있는 문제를 비파괴 upsert로 수정했다.
7. 기존 내비 feature 브랜치 부모를 제거하고 integration 기준 위로 음악 커밋만 재배치했다.

## Revision 2 검증 증거 (이력)

| 검사 | 결과 |
|---|---|
| Android 독립 멜로디 Kotlin 컴파일 | PASS |
| 계약+모델+메모리 저장소 Kotlin 컴파일 | PASS |
| 생성/조성/결정성/메타데이터/편곡 스모크 | PASS, 후보 80개 |
| 메모리 저장/재시작/마이그레이션 스모크 | PASS, schema=3 |
| `tools/Verify-G1.ps1` | PASS, 26 files, hash `a99ecd...7cfe48` |
| `git diff --check` | PASS |
| origin feature 게시 및 `ls-remote` | PASS — 최초 인수서 tip `12d6fde4...` 확인 후 revision 2 갱신 |
| 최신 integration 자동 merge-tree | PASS — 결과 tree 생성, 충돌 없음 |
| Gradle JVM/lint/assemble | NOT_RUN — 이 PC에 Android SDK 경로 없음 |
| Android SQLite 계측시험 | NOT_RUN — SDK/에뮬레이터 필요 |
| 실제 차량/실차 | NOT_RUN |
| 외부 음악 생성 API | NOT_RUN — 공식 API/인증 정보 없음 |

Gradle 재현 결과:

```text
SDK location not found.
Define ANDROID_HOME or sdk.dir in local.properties.
```

## Revision 3 수신 노드 다음 행동

1. `git fetch origin` 후 원격 feature ref와 이 보고서 revision 3의 최종 handoff commit을 확인한다.
2. revision 3 code SHA `3b53d380...`가 이전 기능 SHA `5e8044f3...`의 후손이고 최신 integration과의 merge-base가 `80657e1d...`인지 확인한다.
3. Android SDK가 있는 HIGH_PC에서 아래 검사를 실행한다.

```powershell
.\tools\Verify-G1.ps1
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

4. 계측시험에서 실제 v2 합성 DB→v3 업그레이드, 기존 행 보존, JSON 왕복, close/reopen, metadata upsert, 다른 ID 거부, LIVE/REPLAY 격리를 확인한다.
5. 1700×1184 IVI에서 컨트롤 잘림, 오선·덧줄, 회전/조성/화음 임계값과 연속 재생/pause/stop/destroy/재진입을 확인한다.
6. 통과 시 integration 대상 PR로 수락한다. SDK 검증 전에는 `ACCEPTED/INTEGRATED/DONE`으로 기록하지 않는다.

## 알려진 제한과 미구현

- 외부 생성 서비스 호출, 생성 오디오 저장/삭제, WAV/MIDI 내보내기는 미구현이다.
- 실제 Suno 등 공급자의 공식 API/업로드 형식과 인증이 확인돼야 provider adapter를 구현할 수 있다.
- 현재 미리 듣기는 앱 내부 22.05 kHz mono PCM 합성음이다.
- 실제 IVI/Pleos 렌더링과 실차 오디오 검증은 완료되지 않았다.

## 수락 기준

- JVM 전체 회귀·lint·assemble 성공
- Android SQLite v3 비파괴 migration 및 melody round-trip 성공
- 빈 데이터에서 가짜 별자리 미생성
- 확정 악보 재시작 복원 및 무단 덮어쓰기 방지
- 기존 내비/StarLog 흐름 회귀 없음
- UI 검증 결과와 NOT_RUN 범위를 관리 노드가 같은 SHA에 기록
