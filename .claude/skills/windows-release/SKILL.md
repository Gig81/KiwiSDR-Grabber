---
name: windows-release
description: Build, inspect and release the Windows MSI/EXE installers of KiwiSDR Grabber (jpackage + WiX, ui.wxf post-install action, appVersion bump, release docs to update). Use when touching packaging, installers or preparing/publishing a version.
---

# Windows packaging and release

- App version is the single property `appVersion` in `gradle.properties` (passed to the app as `-Dapp.version`, shown in the window title, used for installers and the release-notes URL `releases/tag/v<version>`).
- Release installers are built by the custom `package{Msi,Exe}Release` tasks in `composeApp/build.gradle.kts`, which call `jpackage` on the `createDistributable` app image with `--resource-dir composeApp/packaging/windows` (WiX 3.11 auto-downloaded to `build/wix311`, or `WIX_PATH`). Plain `packageMsi`/`packageExe` are dev builds without the post-install action.
- `packaging/windows/ui.wxf` overrides jpackage's generated (empty) `JpUI` fragment to add the post-install custom action (`KiwiSDRRecorder.exe --installer-complete`). EXE custom actions must use the absolute path `[INSTALLDIR]KiwiSDRRecorder.exe` (a relative name causes MSI error 1721) and `Return="asyncNoWait"`. Inspect a built MSI with `build\wix311\dark.exe -nologo <msi> out.wxs`.
- On the corporate dev PC, Microsoft Defender for Endpoint may block launching/reading the built launcher exe (`light.exe` then fails with `LGHT0001` access denied); don't launch built exes there — installers are tested by the user in Windows Sandbox.
- Release procedure and checklist: `docs/DISTRIBUZIONE_E_RILASCI.md`. Public installers and attachments are built **only** by `.github/workflows/release.yml` (triggered by pushing an annotated `v<version>` tag that must match `appVersion`; requires `docs/RELEASE_NOTES_<version>.md`). It runs the tests, builds with Temurin 21, runs `scripts/release/Prepare-ReleaseAssets.ps1` (GPL runtime check, zips, LGPL sources resolved from the app-image jars, `SHA256SUMS.txt`), creates build provenance attestations and a **draft** GitHub Release. The user tests the draft's installers in Windows Sandbox, submits them to Microsoft (WDSI) and publishes from the web UI. Never upload locally built installers to a release. `.github/workflows/ci.yml` runs the standard compile+test check on Windows.
- Local runs of the script write to the ignored `Rilascio-<version>/` folder; never commit attachments. `gh` is not installed locally (it is on the GitHub runners).
- On release update `docs/RELEASE_NOTES_<v>.md`, `docs/VERSION_HISTORY.md`, README download links and status (IT + EN sections), `docs/USER_GUIDE.md` download links, `docs/README.md` and `docs/DISTRIBUZIONE_E_RILASCI.md`. Changed dependencies require updating `docs/THIRD_PARTY_NOTICES.md` and `docs/licenses/`.
