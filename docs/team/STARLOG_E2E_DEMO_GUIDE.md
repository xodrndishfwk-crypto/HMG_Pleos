# StarLog E2E submission demo guide

## Manage-node run order

1. Build and install the debug APK, then scan and approve `assets/schema.json` in Gleo.
2. Invoke `CONTROL_STARLOG_JOURNEY` with START, STATUS, and END through both direct intents and Korean
   voice/NLU. Capture callback payloads and verify they arrive within seven seconds.
3. Launch `StarLogDemoActivity` and verify its buttons operate the same persisted journey.
4. Confirm the screen says `DEMO REPLAY`, select 4x/8x/16x, and press **여정 시작**.
5. Confirm nine points appear in order. The final point must not complete the journey automatically.
6. Press **여정 명시적 종료**. Confirm five outbound stars and one Gimhae branch star.
7. Open the melody studio. Rotate the constellation, inspect the staff, run auto optimization, and
   confirm the base melody.
8. Start the local gateway with `MUSIC_PROVIDER=mock`. The UI must retain the `DEMO MOCK` badge,
   progress submit → poll → download, and play the saved WAV.
9. Stop the gateway and repeat a request. Confirm a clear connection failure and that the app stays
   usable.
10. Open **별 선택 · 사진 추억**, select a star, and use either Android `OpenDocument` or
   **시연 사진 등록 (DEMO)** on a Pleos image where DocumentsUI cancels.
11. Force-stop and relaunch. Confirm the completed journey and photo count restore.

## Commands

```powershell
.\tools\Verify-G1.ps1
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain

Set-Location backend\suno-gateway
$env:MUSIC_PROVIDER='mock'
npm test
npm start
```

The emulator accesses the host gateway at `http://10.0.2.2:8787`; this address is not valid in a
vehicle. Vehicle deployment requires an approved HTTPS backend. Do not add a Suno key to the app,
source tree, or Git. `MUSIC_PROVIDER=suno-platform` must remain fail-closed until the official
authenticated contract, secret injection, cost approval, and media policy are supplied.
