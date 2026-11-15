# StarLog music gateway

This is the secret-free backend boundary used by the Android demo. It stores no Suno token in the
APK and exposes an idempotent request/job/result protocol.

## Local deterministic demo

```powershell
Set-Location backend\suno-gateway
Copy-Item .env.example .env
$env:MUSIC_PROVIDER='mock'
npm test
npm start
```

The mock provider is explicit: it creates a deterministic WAV from the confirmed melody and does
not call Suno or spend credits. Android emulators reach the host service at `http://10.0.2.2:8787`.

## Official Suno status (checked 2026-11-15)

Suno publicly advertises an account-managed REST Platform at `https://platform.suno.com/` and an
OAuth-approved MCP connector at `https://suno.com/mcp`. The public pages do not expose the
authenticated Platform endpoint, authorization, request/response schema, rate limits, or media
retention contract needed by this backend. MCP account authorization is not treated as an Android
REST credential.

`MUSIC_PROVIDER=suno-platform` therefore fails closed until an authorized account supplies the
official REST contract and a reviewed provider adapter is implemented. Do not replace it with an
unofficial API reseller or commit credentials. Live generation, credits, and demo usage remain
`NOT_RUN` until that account-gated step is completed.
