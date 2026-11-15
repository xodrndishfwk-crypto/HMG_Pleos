# StarLog Suno/Gleo independent verification report R1

## Verdict

**[REJECTED]**

- [확실] The exact code and handoff tip supplied for PR #21 passed G1, the full local Gradle test/lint/assemble set, connected Android tests, the mock gateway tests, and a manual synthetic REPLAY-to-WAV-to-playback path.
- [확실] Approval is blocked by a credential-boundary defect in `HttpAiMusicGateway`: a gateway result may contain an absolute cross-origin audio URL, while `open()` attaches `X-StarLog-Demo-Token` to every URL. A provider/CDN URL can therefore receive the gateway token.
- [확실] A second boundary defect lets any caller of the exported `MelodyStudioActivity` replace the backend URL through an intent extra. A saved non-terminal job is resumed automatically, so constellation melody request data can be posted to a caller-selected HTTPS origin without a new in-app confirmation.
- [확실] PR #21 remains Draft and was not merged or pushed to `integration/starlog-v1`.

## Scope and immutable references

- [확실] Repository: `xodrndishfwk-crypto/HMG_Pleos`
- [확실] PR: <https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/21>
- [확실] Feature branch: `feat/starlog-suno-demo`
- [확실] Target branch: `feat/starlog-melody-v1`
- [확실] `baseSHA`: `eda7eabb3cc1ddf858ec00fc02d4f269268dcc0b`
- [확실] `testedCodeSHA`: `4178787c797b4a873f861543e307f9eda8b8fd34`
- [확실] `testedFeatureTipSHA`: `92eda7668547dbae9b96220477c88b02b1ba8e94`
- [확실] `latestRemoteTipSHA` before this report commit: `92eda7668547dbae9b96220477c88b02b1ba8e94`
- [확실] `latestIntegrationSHA`: `ede0b01f2cd5efcd5eae09631c5ed29d00b7d3a9`
- [확실] `mergeBaseSHA`: `eda7eabb3cc1ddf858ec00fc02d4f269268dcc0b`
- [확실] PR #21 was open, mergeable, unmerged, and Draft at the final pre-report query.
- [확실] Validation ran in an independent detached worktree at the exact tested feature tip. The user's original worktree and its untracked files were not staged, reset, cleaned, overwritten, or deleted.

## Rules and source review

- [확실] No applicable `AGENTS.md` was present in the independent worktree.
- [확실] The required workflow, collaboration, handoff, baseline, melody plan, melody R1 handoff, Suno/Gleo handoff, PR #20, and PR #21 materials were read before the verdict.
- [확실] PR #20 remains Draft and unmerged. Its latest manager result records unresolved U1 score-canvas clipping; PR #21 is stacked on that branch.
- [확실] Official Pleos documentation was checked for App Schema, actions, `ACTION_PARAM`, callback broadcast, response status/mode, the seven-second callback requirement, and `silent` playback behavior:
  - <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts>
  - <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/app-capability>
  - <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/action_callback>
  - <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/examples>
  - <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema>
- [확실] Suno's public platform page and MCP page were checked. They do not provide the authenticated REST endpoint/schema needed by this adapter without account authorization:
  - <https://platform.suno.com/>
  - <https://suno.com/mcp>

## Findings

### F1 — P1 — gateway token is attached to cross-origin result URLs

- [확실] File: `app/src/main/java/com/example/starlog2/starlog/music/HttpAiMusicGateway.kt`, lines 31-43, 129-137, and 148-152 at the tested tip.
- [확실] `resolveUrl()` accepts an absolute `http://` or `https://` result URL unchanged. `download()` passes it to `open()`, and `open()` adds `X-StarLog-Demo-Token` whenever a token exists. There is no same-origin test before the header is attached.
- [확실] Reproduction by code path: configure a session token, return an absolute HTTPS audio URL from the gateway job result, call `download()`, and observe that the request to that origin contains the gateway token.
- [확실] Impact: an external provider/CDN or a compromised gateway can receive a credential intended for the StarLog gateway.
- [확실] Required correction: attach the token only when the destination origin equals the validated gateway origin, and add a client test using two local origins proving that the second origin never receives the header.

### F2 — P1 — exported activity accepts a caller-selected backend and can auto-resume

- [확실] Files: `app/src/main/AndroidManifest.xml`, lines 30-39; `MelodyStudioActivity.kt`, lines 103-115, 293-303, and 554-559 at the tested tip.
- [확실] `MelodyStudioActivity` is exported for Gleo actions. It also accepts `starlog.musicBackendUrl` and `starlog.musicBackendToken` from the launching intent without caller validation or a debug-only gate.
- [확실] When a persisted job is non-terminal, activity re-entry calls `resumeAiMusicJobIfNeeded()` and performs submit/poll operations against that intent-selected backend without another confirmation.
- [확실] Reproduction: leave a synthetic job in `SUBMITTING`, `QUEUED`, or `GENERATING`; launch the exported activity with an arbitrary HTTPS backend extra; observe the saved request being submitted or polled at that origin.
- [확실] Impact: another installed app can redirect constellation IDs, melody notes, preferences, and prompt data to its own HTTPS endpoint in the auto-resume window.
- [확실] Required correction: do not consume backend configuration from untrusted exported intents. Use app-owned/debug configuration, or validate the caller and a strict origin allowlist; add an exported-intent regression test.

### F3 — P2 — missing response MIME is accepted and MP3 content is not identified

- [확실] File: `app/src/main/java/com/example/starlog2/starlog/music/HttpAiMusicGateway.kt`, lines 52-63 and 79-82 at the tested tip.
- [확실] A non-null unsupported `Content-Type` is rejected, but a missing `Content-Type` passes. RIFF/WAVE bytes are checked only when the result metadata says `audio/wav`; the MP3 path has no file signature validation.
- [추정] This is primarily an integrity/robustness problem rather than direct code execution, because Android `MediaPlayer` still parses the downloaded file before playback.
- [확실] Required correction: require a supported response MIME, reconcile it with result metadata, validate supported file signatures, and test missing MIME, wrong MIME, empty body, corrupt WAV, and existing-target preservation.

### F4 — P2 dependency — PR #20 U1 remains visible

- [확실] On the Pleos Connect AVD, the generated score's high-note stems/circles render above the score panel boundary. This matches the unresolved PR #20 U1 finding.
- [확실] The defect is in the stacked base and was not introduced or fixed by PR #21. This report does not modify that unrelated owner code.
- [확실] It prevents an overall `DEMO_READY` claim even after the PR #21 network issues are corrected.

### F5 — P2 test gap — Android HTTP failure and lifecycle matrix is incomplete

- [확실] The repository has unit coverage for coordinator idempotency, playback request replacement, Gleo parsing, SQLite migration, and gateway server behavior.
- [확실] It has no dedicated `HttpAiMusicGateway` test for cross-origin headers, invalid JSON, missing/wrong MIME, empty body, corrupt WAV, size limits, redirects, or preserving an existing target.
- [확실] It also has no connected UI test that rapidly repeats generation input while pausing/stopping/destroying and re-entering the activity.

## Static review of the requested invariants

1. [확실] File/package conventions are coherent, `git diff --check` passed, and the three commits separate Z1, feature code, and handoff documentation.
2. [확실] No committed Suno token or fixed credential was found. `.env.example` contains blank values and `.env` remained ignored.
3. [확실] Android cleartext is disabled by default and enabled only for `10.0.2.2`, `127.0.0.1`, and `localhost`, without subdomains.
4. [확실] `MUSIC_PROVIDER=suno-platform` fails closed with HTTP 503 `PROVIDER_UNAVAILABLE` and no live request.
5. [확실] The gateway and UI identify the deterministic provider/model as mock; this report does not treat it as a Suno result.
6. [확실] Request ID, constellation, source mode, base melody ID, and identity-conflict checks are present and automated tests pass.
7. [확실] Empty-body, maximum-size, WAV header, `.part` cleanup, and replace-after-validation logic exist; [확실] F1/F3 and the missing client failure tests block full acceptance.
8. [확실] Playback replacement/deactivation has unit coverage and process re-entry succeeded manually; [불확실] the complete rapid-input/activity lifecycle matrix was not exercised.
9. [확실] DB v4 stores `ai_music_jobs` separately from `base_melodies`.
10. [확실] The connected v2-to-v4 migration suite proved that re-saving the same base melody preserves the linked AI job.
11. [확실] Signed-zero equivalence has a focused unit test and did not alter the JSON/schema/stable-ID wire policy.
12. [확실] `schema.json` contains exactly three uppercase actions, parses `ACTION_PARAM`, and uses the documented callback action.
13. [확실] `PREPARE_CONSTELLATION_MUSIC` opened the settings dialog and did not submit before the on-screen confirmation.
14. [확실] `PLAY_CONSTELLATION_MUSIC` checks for a persisted successful job and an existing local file; absent data takes a failure path rather than fixed success.

## Automated evidence

### G1

```powershell
.\tools\Verify-G1.ps1
```

- [확실] Exit code: `0` — PASS.
- [확실] Files: `26`.
- [확실] Manifest SHA-256: `a99ecd2e21b0c84717f11b99a7202b5cd3ef844a4a606bf45ce30a9f637cfe48`.

### Unit, lint, and assemble

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

- [확실] Environment: Android Studio bundled JBR and installed Android SDK; no private SDK path is recorded.
- [확실] Exit code: `0` — PASS; `BUILD SUCCESSFUL` in 55 seconds.
- [확실] Gradle tasks: 82 executed.
- [확실] JVM tests: 124, failures 0, errors 0, skipped 0.
- [확실] Android lint XML issues: 0.
- [확실] Debug app and androidTest APKs assembled.
- [확실] Non-failing warnings: an existing deprecated map call and future generic type-inference warnings in androidTest.

### Connected Android tests

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

- [확실] Device: Pleos Connect x86_64 AVD, Android 14, synthetic validation profile.
- [확실] Exit code: `0` — PASS; `BUILD SUCCESSFUL` in 1 minute 25 seconds.
- [확실] Tests: 21, failures 0, errors 0, skipped 0.
- [확실] The delegated SQLite suite executed a real v2-to-v4 migration, preserved legacy visit/star/constellation rows, created separate melody/music tables, preserved the base melody and AI job across re-open, and proved same-base-melody re-save does not cascade-delete the job.
- [확실] Existing navigation, map recovery, StarLog acceptance/flow, and contract golden connected tests also passed.

### Mock gateway

```powershell
Set-Location backend\suno-gateway
Copy-Item .env.example .env
$env:MUSIC_PROVIDER='mock'
npm test
npm start
```

- [확실] Environment: Node `24.18.0`, npm `11.16.0` (both satisfy Node 20+).
- [확실] The first sandboxed `npm test` attempt could not bind loopback and ended with `EACCES`; this was an environment restriction, not counted as a product failure.
- [확실] The unrestricted local retry exited `0`: 2 tests, 2 passed. It covered deterministic idempotent WAV generation/download, state progression, authorization, and identity conflict.
- [확실] `npm start` served the mock gateway at local port 8787 and the Android emulator reached it through `10.0.2.2`.
- [확실] `.env` was not staged and the worktree remained clean before the report was added.

### Official-provider fail-closed check

```powershell
$env:MUSIC_PROVIDER='suno-platform'
$env:GATEWAY_PORT='8788'
npm start
curl.exe -i -X POST -H "Content-Type: application/json" --data "{}" http://127.0.0.1:8788/v1/music-jobs
```

- [확실] Health identified `suno-platform-unconfigured`.
- [확실] Submit returned HTTP `503`, code `PROVIDER_UNAVAILABLE`, `retryable=false`.
- [확실] No official Suno generation was attempted and no credits were used.

## Android/UI/audio evidence

- [확실] A dedicated synthetic REPLAY seed was created from the explicit empty-state dialog; LIVE data was not created by that seed.
- [확실] Manual end-to-end path passed: constellation selection → score preview → base melody confirmation → arrangement dialog → explicit `AI 음악 생성` confirmation → mock submit → polling → WAV download → persisted success → `AI 곡 재생`.
- [확실] The observed UI states included `AI 음악 저장 완료 · 재생 가능` and `AI 음악 재생 중`.
- [확실] After force-stop/process recreation, the confirmed melody, completed job, local-file eligibility, and playback action were restored.
- [확실] PCM preview and generated-song code explicitly deactivate one player before starting the other; generated playback was observed. [불확실] an audio capture proving absence of every possible overlap was not made.
- [확실] The activity fit the Pleos app surface without control clipping. [확실] The score canvas still exhibited base-branch U1 high-note overflow.
- [불확실] Network failure, malformed JSON, missing/wrong MIME, empty body, corrupt WAV, old-target preservation, and rapid lifecycle races were not fully exercised against the Android HTTP client; they remain `NOT_RUN` rather than inferred PASS.

## Gleo Interface evidence

- [확실] Actual action intents ran on the Pleos Connect AVD and the ambient-AI receiver registered for `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` received the response broadcasts.
- [확실] `OPEN_MELODY_STUDIO`: callback dispatch about 500 ms after enqueue; selected synthetic constellation opened.
- [확실] `PREPARE_CONSTELLATION_MUSIC`: callback dispatch about 502 ms; requested JAZZ appeared in the arrangement dialog; no generation occurred before the user confirmation button.
- [확실] `PLAY_CONSTELLATION_MUSIC`: callback dispatch about 501 ms; the saved file began playback and the UI showed `AI 음악 재생 중`.
- [확실] The code uses `mode=silent` for successful PLAY, matching the official non-overlap guidance.
- [확실] Malformed JSON and a nonexistent constellation were invoked; the app remained stable, and the nonexistent selection rendered `별자리 없음`.
- [불확실] The system broadcast dump masks the callback extra payload as `STRIPPED`; therefore the runtime values of every `status`, `data`, and `mode` field were not independently captured.
- [불확실] A Gleo schema scan/approval UI result and natural-language/voice invocation were not available. These items remain `NOT_RUN`.

## Live Suno boundary

- [확실] Live Suno: `NOT_RUN / NOT_VERIFIED`.
- [확실] Missing prerequisites: authenticated official REST endpoint/schema/authorization documentation, account authorization, and explicit cost ceiling.
- [확실] No unofficial reseller, guessed endpoint, credential, or paid request was used.

## PASS / FAIL / NOT_RUN summary

### PASS

- [확실] Exact SHA/ref/Draft checks, diff review, `git diff --check`.
- [확실] G1: 26 files and expected manifest hash.
- [확실] Full JVM/lint/assemble command: 124 tests, lint 0, APKs built.
- [확실] Connected Android suite: 21 tests.
- [확실] Real synthetic DB v2-to-v4 migration and melody/job preservation.
- [확실] Gateway mock: 2 tests; server start and emulator access.
- [확실] Explicit synthetic REPLAY happy path through saved WAV playback and process re-entry.
- [확실] Gleo action dispatches for OPEN/PREPARE/PLAY within seven seconds.
- [확실] Official-provider fail-closed response.

### FAIL

- [확실] F1 cross-origin gateway-token forwarding.
- [확실] F2 untrusted exported-intent backend override with auto-resume.
- [확실] F3 incomplete response-MIME/file identification contract.
- [확실] Stacked PR #20 U1 visual overflow remains.

### NOT_RUN

- [불확실] Authenticated Gleo schema scan/approval and voice/NLU invocation.
- [불확실] Unmasked runtime inspection of every callback `status/data/mode` value.
- [불확실] Android-client malformed JSON/MIME/empty/corrupt/redirect/old-file matrix.
- [불확실] Full rapid-tap plus pause/stop/destroy/re-entry stress matrix.
- [불확실] Physical in-vehicle hardware demo.
- [불확실] Paid/live Suno generation.

## Required receiver actions and exact retest

### NAVIGATION_NODE

1. [확실] Fix F1 by binding credentials to the gateway origin and add a two-origin regression test.
2. [확실] Fix F2 by removing or strictly gating backend extras on the exported activity and add an external-launch/auto-resume regression test.
3. [확실] Fix F3 and add Android HTTP-client failure/preservation tests.
4. [확실] Keep PR #21 Draft; do not merge/push integration.
5. [확실] Re-run at the corrected feature tip:

```powershell
.\tools\Verify-G1.ps1
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
Set-Location backend\suno-gateway
Copy-Item .env.example .env
$env:MUSIC_PROVIDER='mock'
npm test
npm start
```

### HIGH_PC / Pleos validation

1. [확실] Use the corrected SHA on a Pleos Connect emulator or Devbox with the Gleo app/schema approval surface.
2. [확실] Scan and approve `app/src/main/assets/schema.json`.
3. [확실] Invoke all three actions through Gleo voice/NLU with valid, omitted, malformed, nonexistent, unconfirmed, and missing-file cases.
4. [확실] Capture the unmasked callback payload and timestamps, verifying `status`, stringified `data`, `mode`, and response under seven seconds.
5. [확실] Verify PLAY uses a silent callback and PREPARE emits no network request before user confirmation.
6. [확실] Do not run live Suno until the authenticated REST contract, account authorization, secret-injection route, and cost ceiling are approved.

## Final handoff record

```text
[REJECTED]
handoff: STARLOG_SUNO_GLEO_R1
from: MANAGE_NODE
to: NAVIGATION_NODE / HIGH_PC
PR: https://github.com/xodrndishfwk-crypto/HMG_Pleos/pull/21
branch: feat/starlog-suno-demo
baseSHA: eda7eabb3cc1ddf858ec00fc02d4f269268dcc0b
testedCodeSHA: 4178787c797b4a873f861543e307f9eda8b8fd34
testedFeatureTipSHA: 92eda7668547dbae9b96220477c88b02b1ba8e94
latestRemoteTipSHA: 92eda7668547dbae9b96220477c88b02b1ba8e94 (before report-only commit)
latestIntegrationSHA: ede0b01f2cd5efcd5eae09631c5ed29d00b7d3a9
mergeBaseSHA: eda7eabb3cc1ddf858ec00fc02d4f269268dcc0b
reviewFindings: F1 P1, F2 P1, F3 P2, U1 dependency, missing client/lifecycle tests
fileAndCodeConvention: PASS
secretAndNetworkBoundary: FAIL
databaseMigrationAndPreservation: PASS
idempotencyAndLifecycle: PARTIAL
G1: PASS
unitLintAssemble: PASS
gatewayMock: PASS
androidConnected: PASS
uiAndAudio: PARTIAL_PASS
GleoInterface: PARTIAL_PASS / schema approval and unmasked payload NOT_RUN
liveSuno: NOT_RUN / NOT_VERIFIED
U1Dependency: OPEN / base PR #20
PASS: G1; 124 JVM; lint 0; assemble; 21 connected; gateway 2; mock UI/audio; fail-closed
FAIL: cross-origin token; untrusted backend override; MIME integrity; inherited U1
NOT_RUN: Gleo approval/voice/unmasked payload; Android failure/stress matrix; physical car; live Suno
knownIssues: F1-F5 in this report
reportCommitSHA: TO_BE_FILLED_AFTER_COMMIT
userChangesPreserved: YES
receiverNextAction: NAVIGATION_NODE fixes F1-F3; HIGH_PC completes Gleo/U1/device gates
integrationStatus: NOT_INTEGRATED
```
