# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

KiwiSDR Grabber & Station Recorder: a Kotlin Multiplatform + Compose Desktop app (Windows-only today) that connects to a KiwiSDR receiver, plays live audio, shows spectrum/waterfall, records WAV/MP3, transcribes offline with Vosk and sends Telegram alerts. Project docs, UI strings, code comments and commit messages are in **Italian**; the user communicates in Italian.

## Commands (PowerShell, repo root)

```powershell
.\gradlew.bat :composeApp:run                                    # run the desktop app
.\gradlew.bat :shared:desktopTest                                # all tests (kotlin.test, only in :shared)
.\gradlew.bat :shared:desktopTest --tests "com.grabber.kiwi.KiwiProtocolTests"   # single test class
.\gradlew.bat :composeApp:compileKotlinDesktop :shared:desktopTest               # standard pre-commit check
.\gradlew.bat :composeApp:createDistributable                   # app image (bundled jlink runtime)
.\gradlew.bat :composeApp:packageMsiRelease :composeApp:packageExeRelease        # release installers
.\gradlew.bat :composeApp:suggestRuntimeModules                  # check JDK modules before dependency upgrades
```

There is no linter configured. `composeApp` has no tests. Use the Gradle wrapper with JDK 21 (Eclipse Temurin required for public releases — the bundled runtime must be GPL, not Oracle).

## Architecture

Two modules:

- **`shared`** — almost all logic. `commonMain` holds pure Kotlin (`com.grabber.kiwi` protocol command builders, `com.grabber.model`); it must not use any `java.*` API (`Locale`, `String.format`, `System`…). `desktopMain` holds the JVM services.
- **`composeApp`** — only the Compose UI and entry point (`com/grabber/ui/Main.kt`). `App.kt` composes the persistent `RadioConsole`, `MainTabBar` and the active tab; each tab lives in `ui/feature/<name>/`, shared visual components/tokens in `ui/designsystem/` (spec: `docs/GUI_DESIGN_SYSTEM.md`).

Key rules that span files:

- **`KiwiAppController` is the single facade for the UI.** UI code only uses its APIs and `StateFlow`s — never `KiwiClient`, `AppSettings`, `AudioRecorder`, `LicenseManager` etc. directly, and never keeps a second source of truth for tuning/audio/recording/waterfall. Only exception: `waterfallBuffer` is read directly by the renderer for performance. Orchestration is split into `WaterfallCoordinator`, `RecordingHistoryCoordinator`, `QsoAlertCoordinator`.
- **Two WebSockets per session:** `/SND` (audio + control, `KiwiClient`) and `/W/F` (waterfall, `WaterfallClient`). W/F opens only after SND auth (`badp=0`) and must reuse the same token/timestamp, otherwise the server rejects it with `badp=5`. Never route W/F commands/frames over SND. `badp` rejections are not auto-retried.
- All WebSocket sends go through `WebSocketCommandQueue` (the JDK WebSocket rejects overlapping sends). Audio frames are processed FIFO on a serialized dispatcher — don't parallelize per-frame work.
- SND frame: 10-byte header (seq u32 LE, RSSI u16 BE); flag `0x80` = little-endian PCM, otherwise convert from big-endian. After `audio_rate` reply `SET AR OK in=… out=…` before streaming. Listener names come from `user_cb`, not `/status`.
- Secrets: KiwiSDR password and Telegram token are stored DPAPI-encrypted via `SecretCipher` (`dpapi:` prefix); log commands through `KiwiProtocol.redactSecrets`. No empty `catch` blocks — log via `AppLogger`.
- Default receiver host/port only from `KiwiServerDefaults`; dependency versions only in `gradle/libs.versions.toml`.
- Runtime data: `~/.kiwigrabber/` (`settings.json` written atomically, `kiwigrabber_history.db` SQLite kept open with WAL, `logs/`); recordings in `~/KiwiGrabberRecordings/`.
- `Main.kt` forces Skiko `SOFTWARE_FAST` rendering unless overridden; don't disable VSync (it previously made input lag worse). `--installer-complete` argument shows the post-install Swing dialog instead of the app.

## Packaging and release

Never set `windows { console = true }` in `composeApp/build.gradle.kts`, and never use a relative exe path in `composeApp/packaging/windows/ui.wxf` custom actions (use `[INSTALLDIR]…`, otherwise MSI error 1721). Never commit release attachments (installers, zips). Details, build tasks and the release doc checklist: the `windows-release` skill (`.claude/skills/windows-release/SKILL.md`).

Further reference: `docs/ARCHITECTURE.md` (official architecture and rules), `docs/DEVELOPMENT.md`, `docs/TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md`.
