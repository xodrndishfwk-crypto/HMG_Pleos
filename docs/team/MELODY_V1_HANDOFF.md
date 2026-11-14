# StarLog 별자리 멜로디 v1 관리 노드 인수 보고

작성일: 2026-11-14 KST
상태: `HANDOFF_READY` — 기능 커밋 완료, Android SDK 환경 검증 대기, integration 미통합

## 전달 식별자

```text
[HANDOFF_READY]
handoffId: H-MELODY-V1-20261114-01
taskId: H7-MELODY-V1
from: CODEX_HIGH
to: MANAGE_NODE / HIGH_PC
branch: feat/starlog-melody-v1
codeSHA: 5e8044f3da2935580212e90310c5b4b9c2bfd91f
baseSHA: 80657e1d765fe64b881238314d69a358335a994b
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
| 브랜치 분리 | `feat/starlog-melody-v1`을 승인 integration SHA 바로 위에 생성 |
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

## 검증 증거

| 검사 | 결과 |
|---|---|
| Android 독립 멜로디 Kotlin 컴파일 | PASS |
| 계약+모델+메모리 저장소 Kotlin 컴파일 | PASS |
| 생성/조성/결정성/메타데이터/편곡 스모크 | PASS, 후보 80개 |
| 메모리 저장/재시작/마이그레이션 스모크 | PASS, schema=3 |
| `tools/Verify-G1.ps1` | PASS, 26 files, hash `a99ecd...7cfe48` |
| `git diff --check` | PASS |
| Gradle JVM/lint/assemble | NOT_RUN — 이 PC에 Android SDK 경로 없음 |
| Android SQLite 계측시험 | NOT_RUN — SDK/에뮬레이터 필요 |
| 실제 차량/실차 | NOT_RUN |
| 외부 음악 생성 API | NOT_RUN — 공식 API/인증 정보 없음 |

Gradle 재현 결과:

```text
SDK location not found.
Define ANDROID_HOME or sdk.dir in local.properties.
```

## 관리 노드 다음 행동

1. `5e8044f3da2935580212e90310c5b4b9c2bfd91f`가 `80657e1d...`의 직접 후손인지 확인한다.
2. Android SDK가 있는 HIGH_PC에서 아래 검사를 실행한다.

```powershell
.\tools\Verify-G1.ps1
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

3. 계측시험에서 v2 DB→v3 업그레이드, 기본 악보 JSON 왕복, 별자리 metadata upsert 후 악보 보존을 확인한다.
4. 1700×1184 IVI에서 컨트롤 잘림, 오선·덧줄, 회전/조성/화음 임계값, 오디오 lifecycle을 확인한다.
5. 통과 시 integration 대상 PR로 수락한다. SDK 검증 전에는 `ACCEPTED/INTEGRATED/DONE`으로 기록하지 않는다.

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
