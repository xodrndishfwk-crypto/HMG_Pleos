# StarLog Gleo journey control handoff

## Source basis

The requested starting commit `608b9727c894619c34825d6efe354be25fa02e70` and predecessor
`620622c` were not present in the configured GitHub remote on 2026-11-15. This work continues from
local E2E commit `e736304` based on remote `0bfb42c`.

## Public Gleo schema

The public `assets/schema.json` contains the official maximum of three actions:

1. `CONTROL_STARLOG_JOURNEY` — `START`, `END`, or read-only `STATUS`.
2. `PREPARE_CONSTELLATION_MUSIC` — completed journeys only; opens review and requires on-screen
   confirmation before any provider request.
3. `PLAY_CONSTELLATION_MUSIC` — plays only a persisted successful local file and returns `silent`.

`OPEN_MELODY_STUDIO` remains an Android intent-filter and internal compatibility alias, but is no
longer advertised in the public schema. Official Pleos documentation confirms the three-action
limit, uppercase function names, `ACTION_PARAM`, action filters, callback statuses
`success/asking/fail`, and the seven-second callback deadline:

- <https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability>
- <https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples>
- <https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback>
- <https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema>

Callback messages are complete English sentences as required by the current Pleos guide. Data
preserves `tripId`, `journeyStatus`, `sourceMode`, `startedAt`, `endedAt`, `recordedPointCount`, and
`constellationId`. It contains no token, backend URL, secret, or local path.

## Shared lifecycle

Screen buttons and the Gleo activity action call the application-scoped `JourneyController`.
`START` atomically creates one REPLAY journey with zero points, or resumes the existing ACTIVE
journey. The replay driver then feeds all stops through `VisitDetector`. `END` first disables further
input; incomplete data returns `asking` without mutating the journey, and complete data calls the
same deterministic `SubmissionDemoJourney.complete()` used by the screen. Repeated END returns the
same completed record and constellation. `STATUS` only reads SQLite state.

Process recreation restores ACTIVE or COMPLETED state from SQLite. An ACTIVE journey does not resume
input silently after recreation; a screen or Gleo START explicitly resumes it.

## Music and Suno boundary

The current gateway provider is persisted as `demo-mock`, exposed as generation source
`DEMO_MOCK`, and shown in the UI. `SUNO` is a distinct reserved source, but no official adapter or
request is implemented. Live Suno remains blocked until authenticated official REST documentation,
endpoint and authorization details, API-key server injection, cost approval, rate limits, and media
ownership/retention policy are supplied.

`10.0.2.2:8787` is Android-emulator-only. A vehicle deployment requires an approved HTTPS backend
using TLS 1.2 or later, certificate validation, and server-side secret injection.

## Manage-node validation

Intent-level tests are included, but actual Gleo schema scan/approval and Korean voice/NLU are not
available on this Navigation machine and must remain `NOT_RUN` until MANAGE_NODE executes them.
`StarLogFlowInstrumentedTest` already removes the production telemetry watchdog before virtual-time
jumps. The remaining idle risk is the real MapLibre/WebView lifecycle around `waitForIdleSync`; no
product polling or map behavior was disabled to hide this risk.
