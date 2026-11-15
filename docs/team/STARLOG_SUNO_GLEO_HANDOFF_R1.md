# StarLog Suno/Gleo demo handoff R1

## Status and identity

- status: `IMPLEMENTED / LOCAL_TESTED / HANDOFF_READY`
- live Suno status: `NOT_RUN / NOT_VERIFIED`
- Android demo status: `NOT_RUN / NOT_DEMO_READY`
- integration status: `NOT_INTEGRATED`
- owner: `NAVIGATION_NODE`
- receiver: `MANAGE_NODE / HIGH_PC`
- branch: `feat/starlog-suno-demo`
- base SHA: `eda7eabb3cc1ddf858ec00fc02d4f269268dcc0b`
- Z1 SHA: `d79f96ec99bb93597082e42199cf57bc724a86dd`
- feature code SHA: `4178787c797b4a873f861543e307f9eda8b8fd34`
- dependency: Draft PR #20 and its unresolved U1 Canvas display defect remain separate.

## Implemented flow

The existing melody studio now uses an explicit `constellationId` and `sourceMode`, supports an
explicit synthetic REPLAY seed, preserves the confirmed base melody, submits an idempotent music
job through the local gateway, restores/polls an existing job after re-entry, downloads a validated
WAV/MP3 result, and plays the saved file without mixing it with the internal PCM preview.

The database schema is v4. `ai_music_jobs` is separate from `base_melodies`; the v2-to-current
migration is non-destructive. Re-saving the same confirmed base melody uses `UPDATE`, avoiding the
foreign-key cascade caused by SQLite `REPLACE` and preserving generated results.

Z1 is resolved as numeric coordinate equivalence: `-0.0` and `+0.0` are the same persisted point.
Stable IDs, JSON, existing confirmed melodies, and the schema are not rewritten for signed zero.

## Gleo Interface

Official Pleos guidance checked on 2026-11-15 requires an app schema, Android action filters,
`ACTION_PARAM` parsing, and a callback broadcast. The implementation adds:

- `assets/schema.json` with the maximum three Gleo actions.
- `OPEN_MELODY_STUDIO`: opens the selected/latest recorded constellation.
- `PREPARE_CONSTELLATION_MUSIC`: opens arrangement settings but does not submit a potentially paid
  generation request without on-screen user confirmation.
- `PLAY_CONSTELLATION_MUSIC`: plays the most recent locally saved generated result and returns a
  silent success callback so Gleo speech does not overlap the song.
- Callback action: `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`.
- Invalid parameters, missing constellations, unconfirmed melodies, and missing files return
  `fail` or `asking`; no fixed success response is used.

Official references:

- <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts>
- <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/app-capability>
- <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/action_callback>
- <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/examples>
- <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema>

Actual Gleo system execution remains `NOT_RUN` until MANAGE_NODE uses the Pleos Connect emulator or
Devbox where the Gleo app scans and approves `schema.json`.

## Suno provider boundary

The Android app contains no Suno secret. `backend/suno-gateway` is a dependency-free Node 20+
gateway with an idempotent request/job/result protocol. Its explicit `mock` provider creates a real,
deterministic WAV from the confirmed melody for UI and lifecycle verification; it is not represented
as Suno output.

The public Suno Platform and MCP pages do not expose the authenticated REST authorization,
endpoint/schema, rate limits, credit policy, or media-retention contract needed to implement the
official adapter. `MUSIC_PROVIDER=suno-platform` therefore fails closed. Live generation requires
authorized Suno Platform account documentation and an approved credit ceiling; do not substitute an
unofficial reseller.

References checked on 2026-11-15:

- <https://platform.suno.com/>
- <https://suno.com/mcp>

## Local verification at feature code SHA

| Check | Result | Evidence |
|---|---|---|
| `tools/Verify-G1.ps1` | PASS | 26 files; manifest SHA `a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48` |
| Independent Kotlin/JUnit | PASS | 31 tests, exit 0; melody generator/projector/playback, memory repository, Z1, coordinator, demo seed, Gleo contract |
| `npm test` in gateway | PASS | 2 tests, exit 0; idempotency, state progression, WAV download, auth, identity conflict |
| Gradle unit/lint/assemble | NOT_RUN | wrapper failed before task start: default Gradle ZIP lock `AccessDenied`, exit 1 |
| Connected Android tests | NOT_RUN | same wrapper failure before task start; `adb` not found, exit 127 |
| Actual Gleo invocation | NOT_RUN | Pleos Connect emulator/Devbox unavailable |
| Actual Suno generation | NOT_RUN | authenticated Platform contract, access, and cost approval unavailable |
| U1 Canvas boundary | NOT_FIXED HERE | STARLOG_NODE-owned dependency from PR #20 |

Do not reinterpret the independent 31-test result as a complete Gradle or Android PASS.

## MANAGE_NODE reproduction

Backend:

```powershell
Set-Location backend\suno-gateway
Copy-Item .env.example .env
$env:MUSIC_PROVIDER='mock'
npm test
npm start
```

The emulator reaches the host at `http://10.0.2.2:8787`. If a shared token is enabled, pass it only
through the runtime intent extra `starlog.musicBackendToken`; never commit it.

Required Android checks at the final branch tip:

```powershell
.\tools\Verify-G1.ps1
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Then verify the explicit synthetic REPLAY journey, score controls, PCM preview, confirmation,
mock generation states, file playback, process re-entry restoration, and no duplicate submission.
In the Pleos environment, invoke all three Gleo actions and inspect their callback JSON within the
seven-second action deadline. Separately verify v2-to-v4 SQLite migration and that base melody
upsert retains the AI job.

For live Suno verification, first obtain official authenticated REST documentation and explicit
credit approval. Record the provider job ID/model/result metadata without credentials, run exactly
one approved generation, download and replay it, then re-enter the app and replay the stored file.
Only after that may `LIVE_GENERATION_VERIFIED` be considered. `DEMO_READY`, `ACCEPTED`, and
`INTEGRATED` remain MANAGE_NODE decisions.
