# Piano di sviluppo: richieste degli utenti

Piano di implementazione delle quattro funzionalità raccolte in [RICHIESTE_FUNZIONALITA.md](./RICHIESTE_FUNZIONALITA.md):

1. [Tema chiaro](#step-1--tema-chiaro)
2. [Scansione di un intervallo di frequenze](#step-2--scansione-di-un-intervallo-di-frequenze)
3. [Processamento audio: equalizzatore e filtri DSP](#step-3--processamento-audio-equalizzatore-e-filtri-dsp)
4. [Collegamento alla mappa dei ricevitori](#step-4--collegamento-alla-mappa-dei-ricevitori)

Ogni step è **autonomo**: si può sviluppare, verificare e documentare da solo, in qualsiasi ordine. Le dipendenze tra step sono solo facoltative e sono indicate esplicitamente. Ogni step si chiude con la propria checklist "Definizione di fatto".

**Rilascio:** le quattro funzionalità escono insieme in un **rilascio unico** (versione attuale `1.2.0`, prossima proposta `1.3.0`). Ogni step aggiunge la propria voce alle note di rilascio in preparazione; il rilascio si fa solo a step completati e collaudati tutti (vedi [§5](#5-ordine-di-sviluppo-e-rilascio)).

### Stato dell'implementazione (19 novembre 2026)

Sviluppo dei quattro step completato su **Windows e Android** (decisione del 19/11/2026: tutte le nuove funzioni su entrambe le piattaforme, superando le esclusioni Android indicate in questo piano); manca il **collaudo funzionale** (criteri in `docs/DEVELOPMENT.md`, sezione "Collaudo funzionale della 1.3.0"). Verifiche automatiche eseguite con esito positivo: `:composeApp:compileKotlinDesktop :composeApp:desktopTest :shared:desktopTest` e controllo Android (`:shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug`).

| Step | Stato | Scostamenti dal piano |
|---|---|---|
| 1 Tema chiaro | Sviluppato (Windows, Android) | Oltre al display LCD restano scuri anche spettro, waterfall, righello, barra dell'S-meter e contatori LCD del registratore (`DisplayColors`, su Android `DisplayKiwiColors`). Sotto-step 1.C realizzato: Impostazioni → *Aspetto*, barre di sistema coerenti con il tema. Test di contrasto in un nuovo source set `composeApp/desktopTest`, aggiunto al controllo standard e alla CI. |
| 2 Scansione intervallo | Sviluppato (Windows, Android) | `ChannelScanCoordinator` → `ScanCoordinator` con `ScanPlan`; stato `ScanState`. Motore e sessione (`RangeScanSession`) in `jvmCommonMain`, usati da entrambi i controller. Su Android: Preferiti → *Scansione*, riservata (`GatedFeature.FREQUENCY_SCAN`); la scansione dei canali preferiti resta solo desktop. Nessun avviso se lo squelch d'ascolto è spento (il gate dipende solo dalla soglia RSSI). Evidenziazione dell'intervallo sul righello (facoltativa) non realizzata. |
| 3 DSP | Sviluppato (Windows, Android) | `AudioPipeline` e `AudioDspControl` in `jvmCommonMain`, usati da entrambi i controller. Su Android: Radio → *Equalizzatore e filtri (DSP)* → *Configura* (pannello a scomparsa), riservato (`GatedFeature.AUDIO_DSP`). Il confronto A/B è un pulsante a due stati. |
| 4 Mappa | Sviluppato (Windows, Android) | URL condivisi in `ReceiverMaps` (`commonMain`); su Android i pulsanti sono in Impostazioni → Ricevitore e aprono il browser con un intent. |

La versione (`appVersion` 1.3.0), il tag e la pubblicazione restano da fare dopo il collaudo; le note di rilascio sono in bozza in `docs/RELEASE_NOTES_1.3.0.md`.

---

## 0. Regole comuni a tutti gli step

Valgono per ogni step e non vengono ripetute nelle singole checklist.

**Architettura** (da `CLAUDE.md` e `docs/ARCHITECTURE.md` §5):

- La UI desktop usa solo API e `StateFlow` di `KiwiAppController`; la UI Android solo `MobileAppController`. Nessun accesso diretto a `AppSettings`, `KiwiClient`, recorder, `LicenseManager`.
- Logica pura in `shared` (`commonMain` senza `java.*`; `jvmCommonMain` senza `java.net.http`, `javax.*`, `java.awt`, JNA e senza metodi covarianti di `java.nio.Buffer`); servizi desktop in `desktopMain`.
- Le funzioni riservate alla licenza sono bloccate nel controller, non solo nella UI.
- Nuove preferenze in `SettingsData` con default espliciti, compatibili con i `settings.json` esistenti (campo mancante → default; valore sconosciuto → default).
- Nessun `catch` vuoto: errori nel log con `AppLogger`. Nessun segreto nei log.
- Frame audio elaborati in FIFO sul dispatcher serializzato: nessuna parallelizzazione per frame.
- Versioni delle dipendenze solo in `gradle/libs.versions.toml`; ogni nuova dipendenza documentata in `ARCHITECTURE.md`, `README.md` e `THIRD_PARTY_NOTICES.md`, e verificata con `:composeApp:suggestRuntimeModules`.
- Testi UI, commenti, KDoc e documentazione in italiano (la guida utente e il README hanno anche la sezione inglese).

**Verifiche automatiche standard** (da eseguire a fine di ogni step):

```powershell
.\gradlew.bat :composeApp:compileKotlinDesktop :shared:desktopTest
```

Se lo step tocca `jvmCommonMain`, `androidMain` o `androidApp`, anche il controllo Android:

```powershell
.\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
```

**Verifica di packaging** (solo se lo step aggiunge dipendenze o risorse): `.\gradlew.bat :composeApp:suggestRuntimeModules` e `:composeApp:createDistributable`, poi avvio dell'app dall'immagine creata.

**Documentazione comune a ogni step:**

- `docs/piani/RICHIESTE_FUNZIONALITA.md`: segnare la richiesta come realizzata (con la versione) o spostarla in una sezione "Realizzate".
- `docs/RELEASE_NOTES_<versione>.md`: voce nella sezione novità (italiano e inglese, secondo `docs/RELEASE_TEMPLATE.md`).
- `docs/VERSION_HISTORY.md`: aggiornato al momento del rilascio.
- `README.md`: elenco "Funzionalità principali" / "Key features".
- `docs/USER_GUIDE.md`: sezione italiana **e** inglese.
- `docs/DEVELOPMENT.md`: criteri di collaudo manuale dello step.

**Commit:** li esegue l'utente; il piano indica solo i punti di consegna.

---

## Step 1 — Tema chiaro

### 1.1 Obiettivo

Permettere di scegliere il tema dell'interfaccia: **Scuro** (attuale, predefinito), **Chiaro** o **Segui sistema**. La scelta è persistente e si applica subito, senza riavvio.

### 1.2 Ambito

- Desktop: tutte le schede, la console radio, i dialoghi Compose (incluso `InitialSetupDialog`), spettro e righello frequenze.
- Android: incluso come sotto-step facoltativo 1.C (solo UI, costo ridotto).
- Fuori ambito: la barra del titolo nativa di Windows (segue il tema di Windows) e i dialoghi Swing (`JOptionPane` in `Main.kt` e `AppErrorReporter`), che restano con l'aspetto di sistema.
- Il waterfall mantiene le proprie palette (`WaterfallPaletteType`), indipendenti dal tema.

### 1.3 Decisioni di progetto

| # | Decisione | Scelta proposta |
|---|---|---|
| D1.1 | Valore predefinito | `DARK`: nessun cambiamento visivo per chi aggiorna. |
| D1.2 | API dei colori | Si mantiene il punto d'accesso `GrabberColors.X` e `GrabberTypography.x` per non toccare a mano i ~540 usi: le proprietà diventano getter `@Composable @ReadOnlyComposable` che leggono una `CompositionLocal`. Così gli altri step possono usare `GrabberColors` senza dipendere da questo. |
| D1.3 | Display LCD (VFO) | **Confermato:** resta scuro anche nel tema chiaro (è un "display" radio); i token `LcdBackground`/`LcdPrimary` hanno lo stesso valore in entrambi i temi. |
| D1.4 | Licenza | Funzione libera, non riservata. |
| D1.5 | "Segui sistema" | Usa `isSystemInDarkTheme()` di Compose Desktop; se su Windows non riflette l'impostazione di sistema (da verificare in 1.A.1), lettura del valore `AppsUseLightTheme` del registro in `desktopMain` tramite JNA, già presente. |

### 1.4 Sotto-step

#### 1.A — Modello e persistenza (`shared`)

1. Enum `UiThemeMode { DARK, LIGHT, SYSTEM }` in `shared/commonMain` (`com.grabber.model`), riutilizzabile da Android.
2. `SettingsData.uiThemeName: String = "DARK"` con proprietà derivata `uiTheme` (valore sconosciuto → `DARK`), sullo stesso schema di `waterfallPaletteName`.
3. `AppSettings.setUiTheme(mode)` con salvataggio atomico esistente.
4. `KiwiAppController.setUiTheme(mode)`; la UI legge il valore da `controller.settings`.
5. Verifica tecnica (spike breve): `isSystemInDarkTheme()` su Windows 11 con tema app chiaro/scuro. Annotare l'esito in D1.5.

#### 1.B — Design system desktop (`composeApp/.../designsystem`)

1. `AppTheme.kt`: introdurre `data class GrabberPalette` con gli stessi token di oggi più quelli mancanti (vedi punto 4), e due istanze `DarkPalette` (valori attuali, invariati) e `LightPalette`.
2. `LocalGrabberPalette = staticCompositionLocalOf { DarkPalette }`; `GrabberColors` diventa un oggetto di getter `@Composable @ReadOnlyComposable` sulla palette corrente. Stessa conversione per gli stili di `GrabberTypography` che portano colore.
3. `GrabberTheme(mode: UiThemeMode)`: sceglie palette e `lightColorScheme`/`darkColorScheme`, fornisce `LocalGrabberPalette` e `LocalContentColor`. `Main.kt`/`App.kt` passano il tema letto dalle impostazioni.
4. Nuovi token per eliminare i colori locali (oggi 21 `Color(0x…)` fuori da `AppTheme.kt`): `OnAccent` (primo piano su riempimenti accesi, già regola del design system), `SpectrumBackground`, `SpectrumGrid`, `SpectrumTrace`, `RulerText`, `RulerTick`, `Overlay` (per `FeatureLockedOverlay`) e quelli che emergono dalla migrazione.
5. Correggere gli errori di compilazione dovuti ai getter `@Composable`: gli usi dentro lambda non composable (in particolare `Canvas { … }`/`DrawScope`, `remember { … }`, funzioni normali) leggono il colore in una `val` del composable prima del blocco. Il compilatore li elenca tutti: è la checklist della migrazione.
6. Migrare i `Color(0x…)` locali: `FavoriteChannelCard.kt`, `FrequencyRuler.kt`, `SpectrumAnalyzerView.kt`, `WaterfallCanvasView.kt`, `SMeterView.kt`, `UserRows.kt`, `MainTabBar.kt`, `FeatureLockedOverlay.kt`. Nessun cambiamento funzionale (regola già presente in `GUI_DESIGN_SYSTEM.md`).
7. Prestazioni: i colori dello spettro vanno letti fuori dal disegno per frame (nessuna lettura di `CompositionLocal` per frame, nessuna allocazione nuova nel `Canvas`).

#### 1.C — Android (facoltativo)

1. `KiwiColors` → palette chiara/scura come in 1.B (stessa tavolozza del desktop).
2. `MobileSettings.uiTheme` e `MobileAppController.setUiTheme`; scelta in Impostazioni.
3. `KiwiGrabberTheme(mode)` con `isSystemInDarkTheme()` per `SYSTEM`; icone di status bar e navigation bar coerenti (`enableEdgeToEdge` con `SystemBarStyle.auto`/`light`/`dark`).

#### 1.D — UI di selezione

- Desktop: in Impostazioni (sezione generale o nuova sezione "Aspetto"), `AppSegmentedControl` con *Scuro / Chiaro / Sistema*.
- Android: voce analoga in `SettingsScreen`.

### 1.5 Palette chiara: criteri

- Contrasto testo/sfondo ≥ 4.5:1 (WCAG AA) per `TextPrimary` e `TextSecondary` su `Background`, `Surface`, `SurfaceVariant`; ≥ 3:1 per bordi di controlli e icone di stato.
- Colori semantici (`Success`, `Warning`, `Error`, `Primary`) scuriti nel tema chiaro per mantenere il contrasto su fondo chiaro.
- Lo stato non è mai comunicato solo dal colore (regola esistente).

### 1.6 Test e verifiche

**Automatici (`:shared:desktopTest`, `AppSettingsTests`):**

- `uiTheme` predefinito `DARK` con `settings.json` privo del campo (compatibilità).
- Valore sconosciuto (`"PINK"`) → `DARK`, senza eccezioni.
- `setUiTheme(LIGHT)` persiste e sopravvive al ricaricamento.

**Automatici (facoltativi, `composeApp`):** aggiungere un source set di test a `composeApp` (oggi assente) con un test di contrasto che calcola il rapporto WCAG per le coppie testo/sfondo delle due palette. Se si preferisce non aggiungere test a `composeApp`, spostare i valori delle palette (ARGB `Long`) in un oggetto puro testabile in `shared` e lasciare in `composeApp` solo la conversione a `Color`.

**Android (se 1.C):** `MobileSettings` — default e ricaricamento; controllo Android standard.

**Manuali (desktop):**

- Ogni scheda nei tre modi: nessun testo illeggibile, nessun pannello rimasto scuro per un colore locale dimenticato.
- Cambio tema a caldo con connessione attiva: audio, waterfall e scansione non si interrompono; nessun calo evidente di fluidità dello spettro.
- Pulsanti con riempimento acceso: etichetta e icona leggibili in entrambi i temi.
- `FeatureLockedOverlay`, dialoghi di conferma, menu a tendina e tooltip in tema chiaro.
- Modo `SYSTEM`: cambiare il tema di Windows con l'app aperta e verificare l'esito atteso (aggiornamento immediato o al riavvio, secondo lo spike 1.A.5).
- Scalatura display 100%/150% (verifica esistente di `GUI_DESIGN_SYSTEM.md`).

### 1.7 Documentazione da aggiornare

- `docs/GUI_DESIGN_SYSTEM.md`: sezione "Theme and color palette" con le due palette (tabella a due colonne), `LocalGrabberPalette`, regola "nei `Canvas` leggere i colori prima del blocco di disegno", nuovi token; aggiornare "Scope and preview".
- `docs/ARCHITECTURE.md` §3.5: preferenza `uiThemeName`; §4.10 se Android incluso.
- `docs/USER_GUIDE.md` §9 Impostazioni (IT/EN) e §12 Android se 1.C.
- `README.md`, note di rilascio, `RICHIESTE_FUNZIONALITA.md`.
- `CLAUDE.md`: una riga nella sezione architettura: "Colori solo da `GrabberColors` (palette per tema via `LocalGrabberPalette`); nessun `Color(0x…)` nelle feature".
- Screenshot in `docs/images/` se la guida ne mostra di quelli coinvolti (facoltativo: uno per tema).

### 1.8 Definizione di fatto

- [ ] Nessun `Color(0x…)` fuori da `AppTheme.kt` (`grep` vuoto).
- [ ] Test automatici verdi; controllo Android verde se toccato.
- [ ] Collaudo manuale 1.6 eseguito e riportato in `DEVELOPMENT.md`.
- [ ] Documentazione 1.7 aggiornata.

---

## Step 2 — Scansione di un intervallo di frequenze

### 2.1 Obiettivo

Scansionare le frequenze tra una **frequenza iniziale** e una **finale** con **passo** e **modo** configurabili. Come la scansione dei canali esistente, la scansione si ferma (o sosta) quando lo squelch RF rileva un segnale e riprende alla sua chiusura.

### 2.2 Ambito

- Desktop, funzione avanzata (licenza), come la scansione dei canali.
- Android: escluso (l'app mobile non fa automazioni, `ARCHITECTURE.md` §4.10).
- Le due scansioni (canali e intervallo) sono mutuamente esclusive.

### 2.3 Decisioni di progetto

| # | Decisione | Scelta proposta |
|---|---|---|
| D2.1 | Motore | Generalizzare `ChannelScanCoordinator` in un motore unico (`ScanCoordinator`) che scorre una sorgente di passi, invece di duplicare la logica (assestamento, gate squelch, ripresa, stop thread-safe). |
| D2.2 | Rilevazione segnale | Gate RF dello squelch, come oggi: richiede lo squelch con soglia sensata. Rilevazione dai dati FFT del waterfall: **fuori ambito**, possibile evoluzione futura. |
| D2.3 | Permanenza | In millisecondi per l'intervallo (passi rapidi, default 1500 ms, minimo 300 ms), separata da `scanDwellSeconds` dei canali. |
| D2.4 | Waterfall | Continua a seguire il VFO (`followTuning`) come oggi; con intervalli larghi lo zoom resta quello impostato dall'utente. |
| D2.5 | Frequenze escluse | Elenco di frequenze da saltare (portanti fisse, disturbi), con tolleranza di mezzo passo; azione rapida "Escludi questa frequenza" durante la sosta. |
| D2.6 | Risultati | Elenco in memoria dei segnali trovati nella sessione (frequenza, ora, picco S-meter), con "Sintonizza" e "Aggiungi ai preferiti". Nessuna persistenza su database in questo step. |

### 2.4 Sotto-step

#### 2.A — Refactoring del motore (`shared/desktopMain/app`)

1. Introdurre `ScanTarget(frequencyKhz: Double, mode: ModulationMode, label: String)` e l'interfaccia `ScanSource { val size: Int; fun targetAt(index: Int): ScanTarget }`.
2. Rinominare/generalizzare `ChannelScanCoordinator` → `ScanCoordinator(source: () -> ScanSource, dwellMillis, autoResume, signalGateOpen, tuneTo, resetSignalDetection, …)`. La sorgente è riletta a ogni passo (comportamento attuale per i canali).
3. `ChannelScanState` → `ScanState` con il `ScanTarget` corrente e il tipo di scansione (`CHANNELS`, `RANGE`). Adeguare `ChannelScanPanel` e la console.
4. Nessun cambiamento di comportamento per la scansione dei canali: i test esistenti di `ChannelScanTests` devono passare con le sole modifiche di firma.

#### 2.B — Sorgente intervallo e impostazioni

1. `FrequencyRangeScanSource(startKhz, endKhz, stepKhz, mode, excluded)`: genera i passi per indice (nessuna lista materializzata: 0–30 MHz a 1 kHz sono 30 000 passi), esclude le frequenze nell'elenco.
2. Validazione in una funzione pura testabile (`validateRangeScan`): `start < end`, `0 ≤ start`, `end ≤ MAX_FREQUENCY_KHZ`, `step` tra 0.1 e 1000 kHz, almeno un passo non escluso; messaggi d'errore leggibili.
3. `SettingsData`: `rangeScanStartKhz`, `rangeScanEndKhz`, `rangeScanStepKhz`, `rangeScanModeCode`, `rangeScanDwellMs`, `rangeScanExcludedKhz: List<Double>` con default (per esempio banda CB 26965–27405 kHz, passo 10 kHz, AM, 1500 ms).
4. Facoltativo: preset di intervallo nominati (CB, 40 m, 11 m…) con elenco in `SettingsData`.

#### 2.C — Controller

1. `KiwiAppController`: `startRangeScan()`, `stopScan()`, setter delle impostazioni, `rangeScanHits: StateFlow<List<ScanHit>>`, `excludeCurrentFromRangeScan()`, `clearRangeScanHits()`.
2. Controlli all'avvio come `startChannelScan`: licenza, connessione, parametri validi, squelch attivo (avviso se lo squelch d'ascolto è spento: senza gate la scansione non si ferma mai). Messaggi su `connectionNotifications`.
3. `tune()` ferma qualsiasi scansione; perdita della connessione la ferma (regole esistenti).
4. I setter rispettano il gate di licenza nel controller.

#### 2.D — UI desktop

1. Nella scheda Preferiti, accanto a `ChannelScanPanel`, un pannello "Scansione intervallo" (oppure un `AppSegmentedControl` *Canali / Intervallo* nello stesso pannello): campi frequenza iniziale/finale (`CompactTextField` con validazione), passo, modo, permanenza, ripresa automatica, Avvia/Ferma, stato (frequenza corrente, avanzamento `n/N`, segnale trovato).
2. Elenco dei segnali trovati con "Sintonizza", "Aggiungi ai preferiti", "Escludi".
3. Indicatore compatto nella console radio durante la scansione (riusa quello dei canali).
4. `FeatureLockedOverlay` senza licenza.
5. Facoltativo: evidenziazione dell'intervallo sul righello/waterfall (rettangolo semitrasparente tra inizio e fine).

### 2.5 Test e verifiche

**Automatici (`:shared:desktopTest`):**

- `ChannelScanTests` invariati nei comportamenti (regressione del refactoring).
- `FrequencyRangeScanSourceTests`:
  - numero di passi e ultimo passo esatto (fine inclusa se multipla del passo, altrimenti ultimo ≤ fine);
  - assenza di deriva in virgola mobile su molti passi (frequenze calcolate come `start + i * step`, arrotondate a 1 Hz);
  - esclusioni con tolleranza di mezzo passo;
  - intervallo di un solo passo.
- `validateRangeScan`: inizio ≥ fine, fuori limiti, passo nullo/negativo/troppo piccolo, tutti i passi esclusi.
- `RangeScanTests` (con `runTest` e tempo virtuale, come `ChannelScanTests`):
  - scorre l'intervallo e ricomincia dall'inizio;
  - sosta su segnale e riprende alla chiusura del gate; con ripresa disattivata si ferma e notifica;
  - segnale durante l'assestamento ignorato;
  - `stop()` impedisce ulteriori sintonie;
  - modifica dei parametri durante la scansione applicata dal passo successivo;
  - un `ScanHit` registrato per ogni sosta, senza duplicati per lo stesso segnale.
- `AppSettingsTests`: default e compatibilità dei nuovi campi; persistenza delle esclusioni.
- Mutua esclusione: avviare la scansione intervallo ferma quella dei canali e viceversa.

**Manuali (con un KiwiSDR raggiungibile):**

- Banda CB con squelch a soglia adatta: sosta sui canali attivi e ripresa.
- Click sul waterfall o modifica della frequenza durante la scansione: la scansione si ferma.
- Disconnessione/riconnessione durante la scansione: si ferma, nessuna sintonia dopo lo stop.
- Squelch spento all'avvio: avviso mostrato.
- Licenza non attiva: pannello bloccato e controller che rifiuta l'avvio.
- Registrazione con squelch attiva durante la scansione: i segmenti vengono creati sulle soste (comportamento coerente con la scansione canali).
- Nessun `too_busy`/disconnessione del server con passi rapidi (300 ms) per qualche minuto; annotare nel log la cadenza effettiva dei comandi di sintonia.

### 2.6 Documentazione da aggiornare

- `docs/ARCHITECTURE.md` §4.6: `ScanCoordinator` con le due sorgenti (canali, intervallo), regole di stop e mutua esclusione; aggiornare §3.2 (diagramma: `SCN[ScanCoordinator]`) e §7.
- `CLAUDE.md`: sostituire `ChannelScanCoordinator` con il nuovo nome nell'elenco dei coordinatori; "Desktop user tuning goes through `tune()`, which stops a running scan".
- `docs/USER_GUIDE.md` §4 "Scansione dei canali" → "Scansione" con la parte intervallo (IT/EN), incluso il consiglio sullo squelch.
- `README.md`, note di rilascio, `RICHIESTE_FUNZIONALITA.md`, criteri in `DEVELOPMENT.md`.

### 2.7 Definizione di fatto

- [ ] Refactoring con `ChannelScanTests` verdi senza cambiare le asserzioni.
- [ ] Nuovi test verdi; collaudo manuale eseguito.
- [ ] Gate di licenza verificato nel controller.
- [ ] Documentazione 2.6 aggiornata.

---

## Step 3 — Processamento audio: equalizzatore e filtri DSP

### 3.1 Obiettivo

Catena DSP locale configurabile — **equalizzatore** a bande e **filtri** (passa-alto, passa-basso, notch, riduzione del rumore) — applicabile all'**ascolto in tempo reale** e alle **nuove registrazioni**. Le registrazioni già salvate in archivio non vengono elaborate.

### 3.2 Ambito

- Desktop. Il motore DSP è in `jvmCommonMain` (Kotlin puro) per poterlo riusare su Android in futuro; l'interfaccia Android resta esclusa ("pannello DSP avanzato" escluso in §4.10).
- Fuori ambito: elaborazione delle registrazioni già presenti in archivio (né nel player interno `FileAudioPlayer`, né come esportazione di una copia filtrata).
- Il DSP lato server KiwiSDR (noise blanker / noise filter via comandi `SET`) è **fuori ambito**; valutabile in uno step successivo perché agisce su tutti i client della sessione e va verificato sul protocollo.
- Funzione **riservata alla licenza**, bloccata nel controller.

### 3.3 Decisioni di progetto

| # | Decisione | Scelta proposta |
|---|---|---|
| D3.1 | Destinazione | Tre valori: *Spento*, *Solo ascolto*, *Ascolto e registrazione*. "Solo registrazione" non è previsto (si registrerebbe audio diverso da quello ascoltato) e complicherebbe la pipeline. |
| D3.2 | Posizione nella pipeline | *Solo ascolto*: DSP sui byte in uscita dallo squelch, prima di `player.submitPcm`. *Ascolto e registrazione*: DSP sul frame **prima** dello squelch, così pre-buffer, registrazione, ascolto e invii Telegram ricevono lo stesso audio. RSSI e gate RF non cambiano (dipendono dall'header del frame). |
| D3.3 | VAD | Con *Ascolto e registrazione* il VAD analizza l'audio filtrato. Verificare in collaudo che la riduzione del rumore non peggiori il rilevamento; se sì, passare al VAD il frame originale (lo `SquelchController` riceverebbe entrambi). |
| D3.4 | Registrazioni già esistenti | **Confermato:** i filtri si applicano solo all'ascolto in tempo reale e alle nuove registrazioni; l'archivio non viene elaborato. |
| D3.5 | Licenza | **Confermato:** riservato alla licenza, coerente con VAD e voice gate. Senza licenza la catena resta in bypass anche se `settings.json` contiene una configurazione attiva. |
| D3.8 | Nomi dei pannelli | **Confermato:** il pannello esistente "DSP, ANTI-POP E HANG TIME" (che regola lo squelch) viene rinominato "SQUELCH: ANTI-POP E HANG TIME"; il nome **"DSP"** va al nuovo pannello di equalizzatore e filtri. |
| D3.6 | Sample rate | Mai fissato a 12 kHz: coefficienti calcolati da `AudioFrame.sampleRate` e ricalcolati se cambia (ricevitori con `audio_rate` diverso, per esempio 20.25 kHz). |
| D3.7 | Algoritmi | Biquad RBJ ("Audio EQ Cookbook") per EQ peaking/shelving, passa-alto, passa-basso e notch; riduzione rumore con filtro LMS adattivo (leggero, adatto a HF in fonia). Limitatore morbido finale per evitare il clipping con guadagni positivi. Nessuna nuova dipendenza. |

### 3.4 Sotto-step

#### 3.A — Motore DSP (`shared/jvmCommonMain`, package `com.grabber.audio.dsp`)

1. `Biquad` (Direct Form I o Transposed Form II, `Double` interno, stato persistente tra frame) e `BiquadDesign` (funzioni pure per i coefficienti: peaking, low/high shelf, high-pass, low-pass, notch).
2. `Equalizer`: bande fisse adatte all'audio KiwiSDR (fino a ~6 kHz con 12 kHz): per esempio 100, 250, 500, 1000, 1800, 2700, 4000 Hz; guadagno −12…+12 dB; bande sopra Nyquist disattivate automaticamente.
3. `LmsNoiseReducer`: livello 0–100%, lunghezza filtro e passo di adattamento fissi e testati.
4. `SoftLimiter`.
5. `AudioDspChain`: ordine fisso HP → LP → notch → NR → EQ → limitatore; `process(pcm: ByteArray, sampleRate: Int): ByteArray` (PCM 16 bit LE mono); `configure(config)` thread-safe (configurazione immutabile sostituita atomicamente e applicata all'inizio del frame successivo); con configurazione neutra restituisce l'input invariato (bypass, nessun costo). Buffer di lavoro riutilizzati, nessuna allocazione per campione.
6. `AudioDspConfig` (data class serializzabile, `commonMain` o `jvmCommonMain`): `target`, `highPassHz?`, `lowPassHz?`, `notchHz?`, `notchQ`, `noiseReduction`, `eqGainsDb: List<Double>`, `presetName`. Preset predefiniti: *Neutro*, *Voce SSB*, *AM broadcast*, *CW stretto*, *Riduzione QRM*.

#### 3.B — Pipeline (`desktopMain`)

1. Estrarre da `KiwiAppController.initializeAudioPipeline` una classe `AudioPipeline` testabile (frame → [DSP pre-squelch] → squelch → [DSP post-squelch] → player; frame elaborato → recorder), con sink iniettabili.
2. Due istanze di `AudioDspChain` (stato dei filtri separato): una pre-squelch, attiva solo con *Ascolto e registrazione*; una post-squelch, attiva solo con *Solo ascolto*. Al cambio di destinazione si azzera lo stato della catena che entra in uso (niente transitori da stato vecchio).
3. Reset dello stato dei filtri al cambio di frequenza (`applyTuning`), come già per lo squelch.
4. Il recorder riceve il frame con `pcmBytes` elaborati; `AudioFrame` resta immutabile (copia con `copy(pcmBytes = …)`).

#### 3.C — Impostazioni e controller

1. `SettingsData.audioDsp: AudioDspConfig = AudioDspConfig()` (neutro, destinazione *Spento*).
2. API del controller: `setDspTarget`, `setEqGain(band, db)`, `setHighPass`, `setLowPass`, `setNotch`, `setNoiseReduction`, `applyDspPreset`, `resetDsp`; `dspConfig: StateFlow`. Gate di licenza (D3.5): i setter non agiscono senza licenza e la pipeline applica la catena solo se `hasAdvancedFeatures()` è vero (stesso schema di `applySquelchSettings` per il VAD); alla perdita o all'attivazione della licenza la catena si spegne o si riaccende senza riavvio.
3. Salvataggio delle modifiche dai cursori con debounce (per esempio 300 ms) per non riscrivere `settings.json` a ogni movimento; l'effetto audio invece è immediato.

#### 3.D — UI desktop (scheda Audio)

1. Rinominare il pannello esistente "DSP, ANTI-POP E HANG TIME" in "SQUELCH: ANTI-POP E HANG TIME" (solo il titolo in `AudioDspPanel.kt`; valutare di rinominare anche il file e la funzione in `AudioSquelchTimingPanel` per liberare il nome). Nessun cambiamento funzionale.
2. Nuovo pannello **"DSP"** (equalizzatore e filtri): destinazione (`AppSegmentedControl`: *Spento / Solo ascolto / Ascolto e registrazione*), preset (`AppMenuButton`), cursori delle bande EQ con valore in dB, sezione filtri (HP/LP/notch con frequenza, riduzione rumore), pulsanti "Reimposta" e "Bypass" momentaneo (A/B per confronto). `FeatureLockedOverlay` senza licenza.
3. Indicatore compatto "DSP" nella console radio quando la catena è attiva.
4. Facoltativo: curva di risposta della catena disegnata da `BiquadDesign` (funzione pura, calcolata solo al cambio di configurazione).

#### 3.E — Prestazioni

- Microbenchmark manuale (test escluso dalla suite o `main` di prova): 60 s di audio a 12 kHz con catena completa elaborati in meno di 1 s su PC di sviluppo (margine > 60×).
- Verifica in esecuzione che la coda di `DesktopAudioPlayer` non scarti frame più di oggi (nessun nuovo crepitio).

### 3.5 Test e verifiche

**Automatici (`jvmCommonTest`, quindi eseguiti anche come test host Android):**

- `BiquadDesignTests`: risposta in frequenza calcolata dai coefficienti — peaking +6 dB alla frequenza centrale (±0.1 dB), 0 dB lontano; HP/LP −3 dB alla frequenza di taglio; notch ≥ 30 dB di attenuazione al centro.
- `AudioDspChainTests`:
  - configurazione neutra → output identico byte per byte (bypass);
  - sinusoide a 1 kHz attraverso EQ +6 dB a 1 kHz → ampiezza RMS ×2 (±5%);
  - continuità tra frame: lo stesso segnale elaborato in un blocco unico o in frame da 512/1024 byte dà lo stesso risultato (stato dei filtri conservato);
  - notch a 1 kHz su un tono a 1 kHz → attenuazione ≥ 30 dB a regime;
  - limitatore: nessun campione oltre ±32767 con +12 dB su un segnale a fondo scala, nessun wrap-around;
  - silenzio in ingresso → silenzio in uscita (nessun rumore introdotto, nessun `NaN`);
  - cambio di sample rate → coefficienti ricalcolati (risposta corretta a 20 250 Hz);
  - bande sopra Nyquist ignorate;
  - `configure` concorrente a `process` (thread separati) senza eccezioni.
- `LmsNoiseReducerTests`: tono + rumore bianco → rapporto segnale/rumore in uscita migliore dell'ingresso; livello 0% → bypass.

**Automatici (`desktopTest`):**

- `AudioPipelineTests` con sink finti: *Spento* → player e recorder ricevono il PCM originale; *Solo ascolto* → player filtrato, recorder originale; *Ascolto e registrazione* → entrambi filtrati e pre-buffer filtrato; senza licenza → PCM originale ovunque anche con configurazione attiva; ordine FIFO dei frame preservato; RSSI e stato del gate invariati con il DSP attivo.
- `AppSettingsTests`: `audioDsp` assente nel file → configurazione neutra; preset sconosciuto → neutro; valori fuori range ricondotti nei limiti.
- Regressione: `SquelchTests` e `VadTests` invariati.

**Manuali:**

- A/B con *Bypass* su SSB e AM: l'effetto è udibile, nessun clic al cambio di preset o di destinazione.
- Registrazione WAV e MP3 con *Ascolto e registrazione*: il file contiene l'audio filtrato; con *Solo ascolto* il file è originale.
- Squelch e voice gate attivi con riduzione rumore: aperture del gate comparabili a DSP spento (D3.3).
- Trascrizione Vosk di una registrazione filtrata: nessun peggioramento evidente.
- Allegato audio Telegram coerente con la registrazione.
- Cambio frequenza e riconnessione: nessun transitorio rumoroso.
- Licenza non attiva: pannello "DSP" bloccato, audio non elaborato; attivando la licenza la configurazione salvata torna attiva senza riavvio.
- Riproduzione di una registrazione dall'archivio: nessuna elaborazione applicata (D3.4).
- Pannello rinominato "SQUELCH: ANTI-POP E HANG TIME" con gli stessi controlli e lo stesso comportamento di prima.
- Controllo Android standard (il motore è in `jvmCommonMain`).

### 3.6 Documentazione da aggiornare

- `docs/ARCHITECTURE.md`: nuovo §4.x "Pipeline audio e DSP" (ordine degli stadi, le due destinazioni, perché DSP pre-squelch per la registrazione, reset al cambio frequenza); §3.6/§5.7 se cambia la regola sul dispatcher; §4.10 per ribadire che su Android il motore c'è ma non l'interfaccia.
- `CLAUDE.md`: una riga sulla pipeline: "frame → DSP (ascolto+registrazione) → squelch → DSP (solo ascolto) → player; il recorder riceve il frame dopo il DSP pre-squelch".
- `docs/USER_GUIDE.md` §6 "Elaborazione audio" (IT/EN): nuovo pannello "DSP" (EQ, filtri, preset, destinazione, consigli d'uso per SSB/AM/CW), nota che le registrazioni già salvate non vengono elaborate, requisito di licenza; aggiornare i riferimenti al pannello squelch rinominato.
- `README.md` e guida: aggiungere il DSP all'elenco delle funzioni che richiedono l'attivazione, dove è presente.
- `README.md`, note di rilascio, `RICHIESTE_FUNZIONALITA.md`, criteri in `DEVELOPMENT.md`.
- `THIRD_PARTY_NOTICES.md`: solo se si riprende codice di terzi (le formule RBJ sono di pubblico dominio; citarle nel KDoc).

### 3.7 Definizione di fatto

- [ ] Motore in `jvmCommonMain`, nessuna API vietata (controllo Android verde).
- [ ] Test DSP e pipeline verdi; `SquelchTests`/`VadTests` invariati.
- [ ] Gate di licenza verificato nel controller e nella pipeline; pannello esistente rinominato.
- [ ] Benchmark 3.E entro il margine; collaudo d'ascolto eseguito.
- [ ] Documentazione 3.6 aggiornata.

---

## Step 4 — Collegamento alla mappa dei ricevitori

### 4.1 Obiettivo

Aggiungere un pulsante che apre nel browser di sistema la pagina [rx-tx.info/map-sdr-points](https://rx-tx.info/map-sdr-points), allo stesso modo del pulsante "Mappa pubblica KiwiSDR" che c'è già in Impostazioni → Server.

### 4.2 Ambito

- Desktop, sezione `ServerSettingsSection` della scheda Impostazioni.
- **Fuori ambito:** mappa integrata nell'app, download o lettura dei dati di rx-tx.info, selezione del ricevitore dalla mappa, Android.
- Funzione libera, non riservata alla licenza.

### 4.3 Decisioni di progetto

| # | Decisione | Scelta |
|---|---|---|
| D4.1 | Comportamento | **Confermato:** solo un link che apre il browser di sistema sulla pagina indicata. Nessun dato viene scaricato o elaborato dall'app. |
| D4.2 | Posizione | Accanto al pulsante "Mappa pubblica KiwiSDR", stesso stile (`OutlinedButton`, `ControlSize.Compact`, icona `OpenInNew`). |
| D4.3 | URL | Costante unica (per esempio in un oggetto `ExternalLinks` in `ui/feature/settings/` o in `KiwiServerDefaults` se si preferisce tenere insieme gli indirizzi esterni), non stringa ripetuta. |

### 4.4 Sotto-step

1. Estrarre la logica del pulsante esistente in una funzione riutilizzabile, per esempio `openInBrowser(url: String, description: String): String` in `ui/feature/settings/`, che restituisce il messaggio di esito (`✓ …` / `✗ …`) mostrato oggi in `serverAddressFeedback`.
2. Nel `catch` della funzione aggiungere il log con `AppLogger.warn` (oggi l'errore è mostrato solo nella UI: regola "nessun errore senza log").
3. Usare la funzione per il pulsante esistente (comportamento invariato) e per il nuovo pulsante **"Mappa ricevitori SDR (rx-tx.info)"**, con messaggio "✓ Mappa dei ricevitori SDR di rx-tx.info aperta nel browser.".
4. Testo di aiuto breve sotto i pulsanti: la mappa rx-tx.info elenca anche WebSDR e OpenWebRX; l'app si collega solo ai ricevitori **KiwiSDR**, di cui va copiato l'indirizzo `host:porta` nel campo Server.
5. Layout: i due pulsanti affiancati (o uno sotto l'altro se lo spazio non basta) senza tagli del testo alle larghezze minime della finestra (960 px).

### 4.5 Test e verifiche

**Automatici:** compilazione (`:composeApp:compileKotlinDesktop`) e `:shared:desktopTest` invariato. La logica è un'azione di sistema non testabile in modo utile senza un browser; nessun nuovo test.

**Manuali:**

- Il nuovo pulsante apre `https://rx-tx.info/map-sdr-points` nel browser predefinito e mostra il messaggio di conferma.
- Il pulsante "Mappa pubblica KiwiSDR" funziona come prima.
- Con un errore simulato (per esempio URL non valido in una prova locale) compaiono il messaggio `✗` e la riga nel log.
- Finestra alla larghezza minima e scalatura 150%: pulsanti leggibili e non tagliati.
- Tema chiaro (se lo Step 1 è già fatto): pulsanti e testo leggibili.

### 4.6 Documentazione da aggiornare

- `docs/USER_GUIDE.md` §3 "Collegamento a un KiwiSDR" (IT/EN): i due pulsanti per trovare un ricevitore e la nota che solo i KiwiSDR sono collegabili.
- `README.md` (se cita la ricerca dei ricevitori), note di rilascio, `RICHIESTE_FUNZIONALITA.md`.
- Nessun aggiornamento di `ARCHITECTURE.md` né di `THIRD_PARTY_NOTICES.md`: l'app non usa dati di rx-tx.info, apre solo un link.

### 4.7 Definizione di fatto

- [ ] Pulsante presente e funzionante; pulsante esistente invariato.
- [ ] Errore di apertura registrato nel log.
- [ ] Guida utente IT/EN aggiornata.

---

## 5. Ordine di sviluppo e rilascio

Gli step sono indipendenti; l'ordine suggerito riduce i conflitti e anticipa il valore. Il rilascio è **unico** e avviene dopo il completamento di tutti gli step.

| Ordine | Step | Motivo | Impegno indicativo |
|---|---|---|---|
| 1 | Step 4 Collegamento alla mappa | Piccolo, rischio nullo | molto basso |
| 2 | Step 1 Tema chiaro | Tocca molti file UI: farlo presto evita conflitti con le nuove schermate degli altri step (che usano comunque `GrabberColors`, invariato come API) | medio |
| 3 | Step 2 Scansione intervallo | Riusa il motore della scansione canali; rischio basso | medio |
| 4 | Step 3 DSP | Tocca la pipeline audio: richiede più collaudo d'ascolto | alto |

**Chiusura del rilascio unico (dopo i quattro step):**

1. `appVersion` in `gradle.properties` → `1.3.0` (e `versionCode`/`versionName` Android solo se lo Step 1.C o lo Step 3 toccano l'app Android, secondo lo skill `android-release`).
2. Regressione completa: `:composeApp:compileKotlinDesktop :shared:desktopTest`, controllo Android standard, `:composeApp:createDistributable` e avvio dall'immagine.
3. Collaudo incrociato tra gli step: scansione intervallo con DSP attivo e registrazione con squelch; tema chiaro su tutti i pannelli nuovi (DSP, scansione intervallo, pulsanti mappa).
4. `docs/RELEASE_NOTES_1.3.0.md` completo (IT/EN) con le quattro novità; `docs/VERSION_HISTORY.md`; `RICHIESTE_FUNZIONALITA.md` con le quattro richieste segnate come realizzate in 1.3.0.
5. Pubblicazione solo tramite il workflow `Release` (tag `v1.3.0`), secondo gli skill `windows-release` e `android-release`.

## 6. Decisioni confermate

| Tema | Decisione |
|---|---|
| Display della frequenza (D1.3) | Resta scuro anche nel tema chiaro. |
| Registrazioni già salvate (D3.4) | I filtri si applicano solo all'ascolto in tempo reale e alle nuove registrazioni. |
| Licenza DSP (D3.5) | Riservato alla licenza. |
| Nomi dei pannelli (D3.8) | Il pannello esistente diventa "SQUELCH: ANTI-POP E HANG TIME"; il nuovo pannello si chiama "DSP". |
| Mappa (D4.1) | Solo un pulsante che apre rx-tx.info nel browser di sistema, come quello della mappa KiwiSDR. |
| Versione | Rilascio unico (`1.3.0`) con tutte e quattro le funzionalità. |
