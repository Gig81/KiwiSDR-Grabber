# KiwiSDR Grabber — GUI Design System

This document describes the visual tokens and reusable Compose components currently implemented in the Desktop UI. The source of truth is `composeApp/src/desktopMain/kotlin/com/grabber/ui/designsystem/`.

## Theme and color palette

`GrabberTheme(mode)` applies the Material 3 color scheme of the selected theme: **Dark** (`UiThemeMode.DARK`, default), **Light** (`LIGHT`) or **System** (`SYSTEM`, follows the Windows light/dark app setting through `isSystemInDarkTheme()`). The user picks it in Settings → *Aspetto & tema dell'interfaccia*; `App.kt` reads `settings.uiTheme` and the change applies immediately.

Each theme is a `GrabberPalette` (`DarkPalette`, `LightPalette`) provided through `LocalGrabberPalette`. UI code keeps using `GrabberColors.X`: every token is a `@Composable` getter that reads the active palette, so it must be read in a composable context. Inside drawing blocks (`Canvas`, `drawBehind`) or `remember { … }`, read the color first into a `val`. `GrabberTypography` styles work the same way (`LocalGrabberTextStyles`). Feature packages never define `Color(0x…)`, `Color.White`/`Black`/`Gray` constants: add a token instead.

| Token | Dark | Light | Intended use |
|---|---|---|---|
| `Background` | `#0D1117` | `#F3F5F8` | App window background |
| `Surface` | `#161B22` | `#FFFFFF` | Panels and cards |
| `SurfaceVariant` | `#21262D` | `#EAEEF2` | Nested sections and controls |
| `SurfaceElevated` | `#2B313A` | `#DDE3EA` | Elevated surfaces |
| `Border` | `#30363D` | `#C4CCD5` | Outlines and dividers |
| `Primary` | `#58A6FF` | `#0959C2` | Primary actions and selected controls |
| `PrimaryVariant` | `#3B82F6` | `#1D4ED8` | Alternate primary accent |
| `Secondary` | `#7DD3FC` | `#0E6A85` | Secondary accent |
| `Success` | `#3FB950` | `#1A7535` | Connected, enabled, or successful state |
| `Warning` | `#D29922` | `#8A5A00` | Waiting or caution state |
| `Error` | `#F85149` | `#C0202B` | Error, stop, or destructive state |
| `TextPrimary` | `#E6EDF3` | `#1F2328` | Main text |
| `TextSecondary` | `#B3BECA` | `#4F5A66` | Supporting text |
| `TextDisabled` | `#7A8796` | `#7D8793` | Disabled or inactive text |
| `SignalGreen` | `#34D399` | `#13773F` | Signal indication |
| `SignalAmber` | `#FBBF24` | `#9A5B00` | Signal warning |
| `OnAccent` | `#000000` | `#FFFFFF` | Label and icon on bright fills (`Primary`, `Secondary`, `Success`, `Warning`) |
| `OnStrongAccent` | `#FFFFFF` | `#FFFFFF` | Label and icon on saturated fills (`Error`, selected `Primary` items, checkmarks) |
| `SelectedTab` | `#2E3D52` | `#D6E4F7` | Selected main tab |
| `LockedOverlay` | `#E6101824` | `#E6F3F5F8` | Veil over license-locked panels |
| `ModeAm` / `ModeUsb` / `ModeLsb` / `ModeCw` / `ModeNfm` | amber / blue / violet / red / green | darker variants | Demodulation mode badges |

Light-theme accents are darker than the dark-theme ones so that, both as text on light surfaces and as fills under `OnAccent`, contrast stays at least 4.5:1 (WCAG AA). `composeApp/src/desktopTest/.../PaletteContrastTests.kt` checks these ratios for both palettes: run `.\gradlew.bat :composeApp:desktopTest` after changing a color.

**Instrument displays stay dark in every theme.** The VFO LCD, S-meter bar, spectrum, waterfall, frequency ruler and LCD counters use the constant `DisplayColors` object (`LcdBackground`, `LcdPrimary`, `SpectrumBackground`, `SpectrumGrid`, `SpectrumScaleText`, `WaterfallBackground`, `RulerBackground`, `RulerText`, `RulerTick`, `LabelBackdrop`, `MeterTickHigh`/`Low` and dark-theme accents `Primary`, `Success`, `Warning`, `Error`, `Text`, `TextMuted`, `Border`). Because they are plain constants they can be used directly inside drawing code. `GrabberColors.LcdBackground`/`LcdPrimary` point to the same values. Waterfall palettes (`WaterfallPaletteType`) are independent of the UI theme.

Use text or an icon/shape together with semantic color when a state must be unambiguous; do not communicate state through color alone.

Not themed: the native Windows title bar (follows Windows) and the Swing dialogs (`--installer-complete` choices, `AppErrorReporter`).
**Android.** `androidApp/.../ui/theme/Theme.kt` mirrors the same values: `KiwiPalette` (`DarkKiwiPalette`, `LightKiwiPalette`) provided through `LocalKiwiPalette`, read via the `KiwiColors.X` composable getters, and the constant `DisplayKiwiColors` for the frequency display, S-meter, spectrum and waterfall. `KiwiGrabberTheme(mode)` also switches the status and navigation bar icons (light icons on the dark theme). When a token changes, update both files.

## Typography

`GrabberTypography` provides the base text styles:

| Style | Typeface | Size | Typical use |
|---|---|---:|---|
| `titleLarge` | Sans Serif, bold | 24 sp | Main screen title |
| `titleMedium` | Sans Serif, semibold | 18 sp | Section title |
| `panelTitle` | Sans Serif, bold | 15 sp | Panel heading |
| `label` | Sans Serif, medium | 12 sp | Labels and controls |
| `body` | Sans Serif, normal | 13 sp | Standard content |
| `numeric` | Monospace, bold | 14 sp | Numeric values |
| `frequency` | Monospace, semibold | 32 sp | Prominent frequency |
| `status` | Sans Serif, bold | 10 sp | Compact status badges |

The active radio display uses a larger monospaced LCD treatment. Compact controls may use smaller styles where the available space requires it, while retaining legibility and contrast.

## Spacing and shapes

Use the shared spacing and shape constants:

- `GrabberSpacing`: `xs` 4 dp, `sm` 8 dp, `md` 12 dp, `lg` 16 dp, `xl` 20 dp.
- `GrabberShapes`: `control` 6 dp, `panel` 10 dp, `chip` 999 dp.

## Reusable layout and controls

The design system lives in `ui/designsystem/` and every screen uses it instead of hand-built controls:

- **Sizes:** `ControlSize` sets height, padding, icon, text size and corner radius — `Regular` (38 dp, dialogs), `Compact` (32 dp, panel actions), `Dense` (26 dp, toolbars and list rows), `Mini` (24 dp, high-density spectrum and console toolbars).
- **Containers (`AppComponents.kt`):** `AppPanel` (title, icon, `titleAccessory`, `actions`, `overlay` for locked features), `AppDivider`, `AppSubtleDivider`, `AppSection`, `AppCard`.
- **Actions:** `PrimaryButton` (filled accent), `SecondaryButton` (tonal with border), `OutlinedButton`, `DangerButton`, `AppToggleButton` (two-state), `AppMenuButton` (drop-down choice), `CompactButton`, `IconButton`. Buttons take `icon`, `iconTint` and `size`.
- **Status:** `StatusBadge` (optional icon, monospace, compact size), `StatusIndicator`, `LedIndicator`.
- **Inputs:** `AppCheckbox`, `AppSwitch`, `AppSlider` (compact two-line variant with value and range labels), `AppSegmentedControl` (chips, optional full width), `SelectableListItem`, `CompactTextField`, `AppSearchField`, `AppTextField`.
- **Feedback (`AppFeedback.kt`, `AppDialogs.kt`):** `AppEmptyState`, `AppNotice`, `AppVerticalScrollbar`, `AppScrollableColumn`, `AppConfirmDialog`, `FeatureLockedOverlay`.

Colors always come from `GrabberColors`; feature packages do not define their own color constants. Radio-specific visuals (VFO LCD input, step and band preset grids, S-meter, spectrum, waterfall, frequency ruler) remain feature components drawn on top of these tokens.

## Current radio console

The desktop radio console currently uses the shared theme and panel colors, with compact controls specialized for immediate operation:

- A Power icon reflects disconnected, connecting/reconnecting, and connected states; the configured server address is displayed alongside it.
- The editable frequency is displayed in MHz on a dark LCD surface (dark in every theme). A confirmation/check action or Enter applies a valid frequency; minus and plus tune by the currently selected channel step. The LCD shows `SCAN` while a channel or range scan runs and `DSP` while the DSP chain is processing audio.
- The compact waterfall reuses the full waterfall/spectrum component and renderer. Its scale summary is reserved for the dedicated spectrum/waterfall tab.
- Audio controls reuse the existing S-Meter representation and controller state for volume, mute, Squelch, and VAD. Gate/confidence indicators use live VAD state.

All local color constants have been migrated to theme tokens or `DisplayColors`; keep it that way (a `grep "Color(0x" composeApp/src` outside `AppTheme.kt` must stay empty). Do not change functional behavior as part of a visual-token migration.

Buttons using bright semantic fills (for example `Secondary`, `Success`, `Warning`, and `Primary`) use `GrabberColors.OnAccent` for both label and icon (dark in the dark theme, white in the light theme); saturated fills such as `Error` use `OnStrongAccent`. Outlined and neutral buttons use `TextPrimary`.

## Desktop shell and tabs

The window opens maximized by default, uses 1100×740 dp when restored to a floating window, and enforces a minimum size of 960×640 px. Its title includes the Gradle `appVersion`. The radio console stays visible above the workspace while the selected tab changes. The six workspace tabs are:

1. Favorites
2. Audio
3. Recorder
4. Spectrum & Waterfall
5. Users & Monitor
6. Settings

Each screen lives in its own feature package under `composeApp/src/desktopMain/kotlin/com/grabber/ui/feature/<name>/` (`console`, `favorites`, `audio`, `recordings`, `spectrum`, `users`, `settings`), split into one file per panel, section or dialog; `App.kt`, `Main.kt` and `InitialSetupDialog.kt` stay in `ui/`. The Favorites tab hosts the `SCANSIONE` panel with a *Canali / Intervallo* switch (channel scan and frequency-range scan). The Audio tab shows three panels: squelch timing (`SQUELCH: ANTI-POP E HANG TIME`), `DSP` (target, presets, filters, 7-band equalizer, A/B) and VAD. The Settings tab includes an *Aspetto* section for the theme, map links that open the KiwiSDR public map and the rx-tx.info SDR map in the browser (`ExternalLinks`/`openInBrowser`), and an Information accordion with developer details and a contact form that sends to the dedicated developer Telegram bot; it does not attach the Machine ID or user credentials. Keep shared visual tokens/components in `ui/designsystem/`, use the existing controller and settings as the state source, and avoid duplicating controls or state across tabs. The compact console waterfall and full Spectrum & Waterfall view share the existing renderer and controller state.

The desktop UI and shared regression tests are verified with `:composeApp:compileKotlinDesktop`, `:composeApp:desktopTest` (palette contrast) and `:shared:desktopTest`. Interactive behavior against a reachable KiwiSDR, both themes and layout checks across physical display scaling/resolutions require a separate manual workstation check.

## Scope and preview

The current implementation is in the Desktop source set. Keep shared tokens and components free of Desktop-only services so they can be moved to a shared Compose source set when Android UI work begins.

The temporary in-app Design System preview has been removed. This document and the source components are the maintained reference.

## Performance and regression constraints

- Collect rapidly changing flows such as waterfall frames and VAD confidence in the smallest leaf composable that needs them; avoid collecting them in a screen or card root.
- Keep expensive text measurement and image/pixel allocation out of per-frame `Canvas` drawing. Cache stable measurements and reuse buffers where practical.
- Keep pointer-input handlers stable across frames; use stable keys and updated state rather than restarting gesture handlers for each incoming frame.
- On the supported Windows configuration, `Main.kt` selects Skiko `SOFTWARE_FAST` only when no renderer override is supplied. Do not disable VSync to address input latency; renderer selection and input responsiveness must be verified together.
- For compact inputs, use the existing compact-field pattern instead of constraining a Material `OutlinedTextField` below its supported height, which can clip its text.
