# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

KiwiSDR Grabber & Station Recorder: a Kotlin Multiplatform app that connects to a KiwiSDR receiver, plays live audio, shows spectrum/waterfall, records WAV/MP3, transcribes offline with Vosk and sends Telegram alerts. The Compose Desktop app is Windows-only; an Android listening client (reduced feature set, `androidApp`) reuses the shared JVM logic. Project docs, UI strings, code comments and commit messages are in **Italian**; the user communicates in Italian.

## Commands (PowerShell, repo root)

```powershell
.\gradlew.bat :composeApp:run                                    # run the desktop app
.\gradlew.bat :shared:desktopTest                                # all shared desktop tests (kotlin.test)
.\gradlew.bat :shared:desktopTest --tests "com.grabber.kiwi.KiwiProtocolTests"   # single test class
.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:desktopTest :shared:desktopTest   # standard pre-commit check
.\gradlew.bat :composeApp:createDistributable                   # app image (bundled jlink runtime)
.\gradlew.bat :composeApp:packageMsiRelease :composeApp:packageExeRelease        # release installers
.\gradlew.bat :composeApp:suggestRuntimeModules                  # check JDK modules before dependency upgrades

# Android: runs Gradle inside Docker (no Android SDK on the dev PC); outputs in build\android-out\
.\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug   # Android check
.\scripts\android\build.ps1 :androidApp:assembleRelease :androidApp:bundleRelease
```

Use the Gradle wrapper with JDK 21 (Eclipse Temurin required for public releases — the bundled runtime must be GPL, not Oracle). `composeApp` only tests design-system tokens (`:composeApp:desktopTest`, palette contrast); there are no UI interaction tests. Lint exists only for Android (`:androidApp:lintDebug`, with `checkDependencies` so it also checks `shared/androidMain`). Do not install software on the dev PC: Android tooling lives in `docker/android-build/` (Docker Desktop has no KVM, so no emulator — Android is tested on a physical device).

## Architecture

Three modules:

- **`shared`** — almost all logic. `commonMain` holds pure Kotlin (`com.grabber.kiwi` protocol command builders, `com.grabber.model`); it must not use any `java.*` API (`Locale`, `String.format`, `System`…). `jvmCommonMain` holds JVM code shared by desktop and Android (`KiwiClient`, `WaterfallClient`, `WebSocketCommandQueue`, parser, squelch/VAD, `AppLogger`, `WaterfallCoordinator`, `WavWriter`, license logic): no `java.net.http`, `javax.*`, `java.awt`, JNA, and no covariant `java.nio.Buffer` methods (`flip()`, `position(int)`…, missing on Android 10). `desktopMain` holds desktop services, `androidMain` the Android ones (`com.grabber.mobile`). Tests: `desktopTest`, plus `jvmCommonTest` (runs on desktop and as Android host tests).
- **`composeApp`** — only the desktop Compose UI and entry point (`com/grabber/ui/Main.kt`). `App.kt` composes the persistent `RadioConsole`, `MainTabBar` and the active tab; each tab lives in `ui/feature/<name>/`, shared visual components/tokens in `ui/designsystem/` (spec: `docs/GUI_DESIGN_SYSTEM.md`). Colors only from `GrabberColors` (theme palette via `LocalGrabberPalette`, `@Composable` getters: read them before `Canvas`/`remember` blocks) or `DisplayColors` (instrument displays — LCD, spectrum, waterfall, ruler — dark in every theme); no `Color(0x…)`/`Color.White` in features.
- **`androidApp`** — Android app (`applicationId` `com.gig81.kiwisdrgrabber`, definitive: changing it changes device IDs). Included, together with the Android target of `shared`, only when an Android SDK is available (`ANDROID_HOME`/`local.properties`, or `-Pkiwi.android=true`), so desktop builds never need the SDK.

Key rules that span files:

- **`KiwiAppController` (desktop) and `MobileAppController` (Android) are the single facades for their UI.** UI code only uses their APIs and `StateFlow`s — never `KiwiClient`, `AppSettings`/`MobileSettings`, recorders, `LicenseManager` etc. directly, and never keeps a second source of truth for tuning/audio/recording/waterfall. Only exception: `waterfallBuffer` is read directly by the renderer for performance. Desktop orchestration is split into `WaterfallCoordinator`, `RecordingHistoryCoordinator`, `QsoAlertCoordinator`, `ScanCoordinator` (one scan at a time: favorite channels or a frequency range). Desktop user tuning goes through `tune()`, which stops a running scan.
- **Desktop audio path (`AudioPipeline`):** frame → DSP (only target "listen and record") → `SquelchController` → DSP (only target "listen") → player; the recorder (and squelch pre-buffer) gets the frame after the pre-squelch DSP. The pipeline, the DSP engine (`AudioDspChain`, `com.grabber.audio.dsp`), `AudioDspControl`, `ScanCoordinator` and `RangeScanSession` live in `jvmCommonMain` and are used by both controllers (Android has range scan and DSP, not channel scan); DSP and scans are license-gated in the controllers. Saved recordings are never processed.
- **Two WebSockets per session:** `/SND` (audio + control, `KiwiClient`) and `/W/F` (waterfall, `WaterfallClient`). W/F opens only after SND auth (`badp=0`) and must reuse the same token/timestamp, otherwise the server rejects it with `badp=5`. Never route W/F commands/frames over SND (exception: DX labels are requested with the common command `KiwiProtocol.dxMarkers()` on SND; the server only accepts the full 8-parameter `SET MARKER` and kicks unknown variants with `too_busy`). `badp` rejections are not auto-retried.
- Transport is abstracted by `KiwiWebSocketConnector` (`JdkWebSocketConnector` desktop, `OkHttpWebSocketConnector` Android). All WebSocket sends go through `WebSocketCommandQueue`. Audio frames are processed FIFO on a serialized dispatcher — don't parallelize per-frame work.
- SND frame: 10-byte header (seq u32 LE, RSSI u16 BE); flag `0x80` = little-endian PCM, otherwise convert from big-endian. After `audio_rate` reply `SET AR OK in=… out=…` before streaming. Listener names come from `user_cb`, not `/status`.
- Secrets: KiwiSDR password and Telegram token are stored encrypted via `SecretCipher` (`dpapi:` DPAPI on Windows, `aks:` Android Keystore); log commands through `KiwiProtocol.redactSecrets`. No empty `catch` blocks — log via `AppLogger`.
- Licenses (`KSR1`, Ed25519): shared logic in `LicenseFormat`/`LicenseChecker`; signature verifier injected (JDK on desktop, BouncyCastle on Android). Device IDs `GRAB-…` (Windows `MachineGuid`) and `GRAB-A-…` (`ANDROID_ID`). Android gates (`FeatureGate`/`GatedFeature`: background playback, recording, personal favorites, range scan, DSP) are enforced in the controller, not only the UI.
- Default receiver host/port only from `KiwiServerDefaults`; dependency versions only in `gradle/libs.versions.toml`.
- Runtime data desktop: `~/.kiwigrabber/` (`settings.json` written atomically, `kiwigrabber_history.db` SQLite kept open with WAL, `logs/`); recordings in `~/KiwiGrabberRecordings/`. Android: `filesDir/settings.json` and `filesDir/logs/` (excluded from backup); recordings in MediaStore `Recordings/KiwiGrabber` (`Music/KiwiGrabber` on Android 10–11).
- `Main.kt` forces Skiko `SOFTWARE_FAST` rendering unless overridden; don't disable VSync (it previously made input lag worse). `--installer-complete` argument shows the post-install Swing dialog instead of the app.

## Packaging and release

Never set `windows { console = true }` in `composeApp/build.gradle.kts`, and never use a relative exe path in `composeApp/packaging/windows/ui.wxf` custom actions (use `[INSTALLDIR]…`, otherwise MSI error 1721). Never commit release attachments (installers, zips, APK/AAB) or signing keys. Published installers and the signed APK come only from the `Release` GitHub Actions workflow (tag `v<version>` → draft release with attestations; the APK is signed by its `android` job with secrets of the `android-release` environment); never upload locally built ones. Details: the `windows-release` skill (`.claude/skills/windows-release/SKILL.md`) and the `android-release` skill (`.claude/skills/android-release/SKILL.md`: local signing with `build.ps1 -Keystore`, keys, certificate check). Android is distributed on GitHub only for now (Play Store deferred); GitHub and a future Play build must use the same app signing key.

Further reference: `docs/ARCHITECTURE.md` (official architecture and rules; §4.9–4.10 cover Android scope, activation and lifecycle decisions), `docs/DEVELOPMENT.md` (including the Android on-device test criteria), `docs/TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md`, `docs/DISTRIBUZIONE_E_RILASCI.md`.
