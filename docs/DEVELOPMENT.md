# Guida allo sviluppo

## 1. Requisiti

- JDK 17 o superiore
- Windows 10 / 11 per lo sviluppo desktop target
- Git
- Gradle Wrapper incluso nel repository

## 2. Setup locale

Da terminale PowerShell, nella root del progetto:

```powershell
./gradlew.bat --version
```

Se il toolchain JDK non è presente, installare Java 17+ e verificare che `JAVA_HOME` sia configurato correttamente.

## 3. Comandi principali

### Avvio dell'app desktop

```powershell
./gradlew.bat :composeApp:run
```

### Esecuzione dei test

```powershell
.\gradlew.bat :shared:desktopTest
```

Per verificare le modifiche della GUI desktop e i test di regressione del modulo condiviso:

```powershell
.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:desktopTest :shared:desktopTest
```

Le prove di compilazione/test non sostituiscono la verifica interattiva con un KiwiSDR raggiungibile, né il controllo del layout su monitor e scaling DPI differenti.

La suite `:shared:desktopTest` comprende 137 test automatici (inclusi i `jvmCommonTest`, eseguiti anche come test host Android) e `:composeApp:desktopTest` 4 test di contrasto delle palette. Al 17 novembre 2026 sono stati eseguiti senza errori, insieme al controllo Android (test host, lint, APK di debug).

I test di regressione coprono, tra l'altro, parser e trasformazioni waterfall, preferiti DX e loro persistenza, impostazioni e cifratura DPAPI delle credenziali, logging, Squelch/VAD, cooldown Telegram, codifica e oscuramento dei comandi KiwiSDR, parser dei messaggi del server (`user_cb`, canali DX `mkr`, frame `SND`), coda dei comandi WebSocket, Machine ID e licenze, migrazione del database, tema dell'interfaccia (persistenza e contrasto WCAG delle palette), scansione dei canali e degli intervalli (sorgente dei passi, validazione, esclusioni, segnali trovati, mutua esclusione), motore DSP (risposta dei filtri, continuità tra blocchi, limitatore, riduzione rumore, prestazioni) e percorso dell'audio tra DSP, squelch, ascolto e registrazione, anche senza licenza. Le verifiche manuali di protocollo/audio restano separate dai test automatici.

### Collaudi desktop riportati (10 novembre 2026)

- Trascrizione Vosk di registrazioni WAV e MP3 su Windows: verificata.
- Invio Telegram di allegati WAV/MP3 e cooldown tra messaggi, inclusi gli alert generati dalla coda STT: verificati dall'utente con esito positivo il 10 novembre 2026.
- Stress test e installazione/avvio dei pacchetti Windows: verificati.
- Contrasto dei pulsanti colorati nella scheda Impostazioni: verificato dall'utente con esito positivo il 10 novembre 2026.
- I test automatici condivisi coprono anche i limiti temporali del cooldown.
- Revisione del codice e aggiornamento della toolchain (Gradle 9.9.1, Kotlin 2.4.21, Compose 1.12.2, JNA 5.19.1): verificati dall'utente con esito positivo il 10 novembre 2026, compresi connessione con password cifrata, oscuramento della password nei log, migrazione di impostazioni e database, attivazione della licenza sul nuovo Machine ID, trascrizione Vosk con la nuova JNA, registrazione e riproduzione, aspetto di tutte le schede.

### Collaudo funzionale della 1.3.0

Verifiche manuali delle novità 1.3.0 (superate dall'utente su Windows e Android il 19 novembre 2026), con un KiwiSDR raggiungibile e una licenza attiva salvo dove indicato.

**Tema chiaro**

- Impostazioni → *Aspetto*: *Scuro*, *Chiaro*, *Sistema*. In ogni tema scorrere tutte le schede: nessun testo illeggibile, nessun pannello rimasto con i colori dell'altro tema; display del VFO, S-meter, spettro, waterfall e righello restano scuri.
- Cambio tema a caldo con connessione attiva: audio, waterfall e scansione non si interrompono; lo spettro resta fluido.
- Pulsanti colorati (Salva, Attiva Licenza, Telegram), segmented control, menu a tendina, tooltip, dialoghi di conferma, `FeatureLockedOverlay` e dialogo del primo avvio leggibili nel tema chiaro.
- *Sistema*: cambiare il tema delle app di Windows con l'app aperta e annotare se l'aggiornamento è immediato o richiede il riavvio.
- Il tema scelto resta dopo il riavvio. Scalatura 100% e 150%.

**Scansione dell'intervallo**

- Banda CB con soglia squelch adatta: la scansione si ferma sui canali attivi e riparte alla chiusura del gate; senza *Riprendi dopo il segnale* si ferma e mostra la notifica.
- Valori non validi (inizio ≥ fine, passo 0, oltre 30 MHz): messaggio d'errore, nessuna scansione.
- *Escludi* durante una sosta, riammissione dall'elenco, *Svuota*; *Segnali trovati*: sintonizza, aggiungi ai preferiti (gruppo `SCAN`), escludi.
- Avviare l'intervallo durante la scansione dei canali (e viceversa): la prima si ferma.
- Click sul waterfall, sintonia manuale, disconnessione: la scansione si ferma e non sintonizza più.
- Passo minimo (0,3 s) per qualche minuto: nessun `too_busy` né disconnessione dal server.
- Registrazione con squelch attiva durante la scansione: segmenti creati sulle soste.
- Senza licenza: pannello *SCANSIONE* bloccato.

**DSP**

- Scheda Audio: pannello squelch rinominato *SQUELCH: ANTI-POP E HANG TIME*, invariato nel comportamento; nuovo pannello *DSP*.
- Ogni preset su SSB, AM e CW; *A/B* per confrontare; nessun clic cambiando preset, destinazione o cursori; indicazione `DSP` sul display del VFO.
- *Solo ascolto*: le nuove registrazioni (WAV e MP3) restano originali. *Ascolto e registrazione*: le nuove registrazioni contengono l'audio filtrato; allegato Telegram e trascrizione Vosk coerenti.
- Riproduzione dall'archivio di una registrazione già salvata: nessun filtro applicato.
- Squelch e voice gate attivi con riduzione rumore: aperture del gate paragonabili a DSP spento.
- Cambio frequenza e riconnessione: nessun transitorio rumoroso.
- Licenza non attiva: pannello bloccato e audio non filtrato anche con una configurazione salvata; attivando la licenza la configurazione torna attiva senza riavvio.
- La configurazione resta dopo il riavvio (anche chiudendo l'app subito dopo aver mosso un cursore).

**Mappa dei ricevitori**

- Impostazioni → Server: *Mappa ricevitori SDR (rx-tx.info)* apre `https://rx-tx.info/map-sdr-points` nel browser e mostra la conferma; *Mappa pubblica KiwiSDR* funziona come prima.
- Pulsanti leggibili e non tagliati alla larghezza minima della finestra.

**Android (dispositivo fisico, APK di debug)**

- Impostazioni → *Aspetto*: Scuro, Chiaro, Sistema; tutte le schermate leggibili, icone della barra di stato e di navigazione coerenti con il tema; display della frequenza e waterfall restano scuri. Con *Sistema* cambiare il tema di Android con l'app aperta.
- Impostazioni → Ricevitore: *Mappa KiwiSDR* e *Mappa SDR (rx-tx.info)* aprono il browser.
- Preferiti → *Scansione*: stessi controlli del desktop (valori non validi, sosta e ripresa, *Escludi*, segnali trovati, aggiunta ai preferiti personali, stop con sintonia manuale, waterfall o disconnessione). Con la versione attivata la scansione continua con l'app in background e lo schermo spento; senza attivazione il comando mostra la richiesta di attivazione.
- Radio → *Equalizzatore e filtri (DSP)* → *Configura*: preset, filtri, equalizzatore e A/B udibili senza clic; con *Ascolto e registrazione* il WAV registrato è filtrato, con *Solo ascolto* no; indicazioni `SCAN`/`DSP` sul display. Senza attivazione: richiesta di attivazione e audio non filtrato.
- Consumo: con DSP attivo e scansione in corso per 15 minuti a schermo spento, nessun calo evidente di batteria rispetto al solo ascolto e nessun audio a scatti.

### Diagnostica UI Windows

Se su Windows compaiono ritardi di input insieme a errori di selezione del renderer DirectX e stack trace in OpenGL, verificare prima il renderer Skiko configurato in `Main.kt` e i relativi override. Non disabilitare VSync: il precedente tentativo ha aumentato il carico e peggiorato la consegna degli input. Per le regole sulle ricomposizioni e sul rendering per frame, consultare [GUI_DESIGN_SYSTEM.md](./GUI_DESIGN_SYSTEM.md).

### Build locale

```powershell
./gradlew.bat build
```

### Packaging Windows

Per una build locale di sviluppo:

```powershell
.\gradlew.bat :composeApp:packageExe
```

oppure

```powershell
.\gradlew.bat :composeApp:packageMsi
```

Per i pacchetti destinati a una release, usare invece `:composeApp:packageExeRelease` e/o `:composeApp:packageMsiRelease`: includono la scelta post-installazione per avviare l'app e/o aprire le note della versione. La procedura è descritta in [DISTRIBUZIONE_E_RILASCI.md](./DISTRIBUZIONE_E_RILASCI.md).

Gli installer vengono generati sotto `composeApp\build\compose\binaries\main\exe` e `composeApp\build\compose\binaries\main\msi`.

Gli installer locali servono per lo sviluppo e per provare modifiche al packaging: quelli pubblicati nelle release si producono solo con il workflow `Release` (vedi sotto).

### Android

Sul PC di sviluppo non c'è l'SDK Android: la build gira in un container Docker e il target Android si attiva solo lì (dettagli in [TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md](./TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md#3-sviluppo-e-rilascio--mobile-android)).

```powershell
# Controllo standard Android: test host (jvmCommonTest + androidHostTest), lint, APK di debug
.\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
```

Docker Desktop non espone KVM, quindi l'emulatore non è utilizzabile: il collaudo si fa su un dispositivo fisico con l'APK `build\android-out\apk\debug\androidApp-debug.apk` (oppure con la release firmata, che ha un ID dispositivo diverso). Criteri di collaudo:

- **Connessione:** con e senza password; rifiuti `badp` mostrati senza ritentativi; riconnessione dopo un cambio Wi-Fi ↔ rete mobile. Chiudendo l'app connessa (rimozione dai recenti) al riavvio si ricollega all'ultimo server, dopo *Disconnetti* no; registrazione e scansione dell'intervallo attive durante una caduta di rete riprendono alla riconnessione.
- **Avvio e icone:** splash con logo e versione; con una release più recente su GitHub resta aperto con *Scarica l'aggiornamento* (apre il browser) e *Continua*; icona del launcher, icona a tema (Android 13+) e icona della notifica dal logo ufficiale.
- **Audio:** nessun underrun percepibile in 30 minuti; squelch e volume reattivi; pausa su chiamata in arrivo, mute allo scollegamento delle cuffie.
- **Background:** con attivazione, audio continuo per 30 minuti a schermo spento con notifica visibile; senza attivazione, connessione chiusa e avviso al ritorno.
- **Waterfall:** sintonia al tocco entro la risoluzione del bin; socket W/F chiuso fuori dalla schermata e in background, riaperto senza `badp=5`.
- **Slot del server:** dopo *Disconnetti*, rimozione dai recenti o arresto forzato la connessione risulta chiusa sul KiwiSDR.
- **Registrazione:** WAV riproducibili con durata corretta; nessun file `IS_PENDING` residuo dopo un'interruzione.
- **Licenza:** il codice valido attiva le funzioni; codici desktop, di altri dispositivi o alterati vengono rifiutati; il gate tiene anche chiamando le API del controller senza passare dalla UI.
- **Segreti:** password mai in chiaro in file, log e backup.
- **Release:** con R8 attivo provare tutte le schede (una classe rimossa per errore da R8 si manifesta solo nella release).

I log dell'app sono in `filesDir/logs` (escluso dal backup).

### Integrazione continua (GitHub Actions)

- `.github/workflows/ci.yml` — a ogni push su `main` e pull request esegue su Windows `:composeApp:compileKotlinDesktop :composeApp:desktopTest :shared:desktopTest`, lo stesso controllo da lanciare in locale prima di un commit, e su Ubuntu il controllo Android (test host, lint, APK di debug).
- `.github/workflows/release.yml` — sul push di un tag `v<versione>` compila l'APK Android firmato con la chiave dell'app e gli installer con Eclipse Temurin 21, prepara gli allegati con `scripts/release/Prepare-ReleaseAssets.ps1`, crea le attestazioni di provenienza e una GitHub Release bozza. Avviato a mano (*Run workflow*) esegue solo la build e lascia gli allegati come artifact.
- `.github/workflows/codeql.yml` — analisi CodeQL settimanale e su push/pull request.

Lo script degli allegati si può eseguire anche in locale, dopo i task `package*Release`: `.\scripts\release\Prepare-ReleaseAssets.ps1 -Version <versione>` (output in `Rilascio-<versione>\`, ignorata da git).

## 4. Struttura dei moduli

- `shared`: logica cross-platform, modelli, protocollo, client SDR, audio e monitoraggio (`commonMain`, `jvmCommonMain` condiviso con Android, `desktopMain`, `androidMain`)
- `composeApp`: UI desktop e entry point dell'applicazione
- `androidApp`: app Android (UI Compose mobile, foreground service), presente nella build solo con l'SDK Android

La GUI desktop è in `composeApp/src/desktopMain/kotlin/com/grabber/ui/`: `App.kt` compone la console radio persistente (`RadioConsole`), la barra delle schede (`MainTabBar`) e la scheda attiva; ogni scheda ha il proprio package in `ui/feature/<nome>/`. I token e i controlli visuali riutilizzabili sono in `ui/designsystem/`. Consultare `GUI_DESIGN_SYSTEM.md` per layout, palette e regole di coerenza; mantenere lo stato funzionale nel controller/impostazioni, non duplicarlo nei composable.

## 5. Convenzioni di sviluppo

- Kotlin con style idiomatico e classi con responsabilità chiare
- package coerenti con il dominio e non con la UI
- `StateFlow`/`Flow` come meccanismo di reattività
- i servizi platform-specific vanno tenuti fuori dal modulo condiviso
- ogni nuova funzionalità va prima valutata in termini di riuso e modularità

## 6. Debugging e diagnostica

- usare i log applicativi in `~/.kiwigrabber/logs`
- controllare lo stato di connessione dei client KiwiSDR e Waterfall tramite i `StateFlow`
- verificare la persistenza dei dati nel database locale `~/.kiwigrabber/kiwigrabber_history.db` (spostato automaticamente da `~/kiwigrabber_history.db` al primo avvio)
- utilizzare i test di regressione per rompere i cambiamenti di protocollo e audio

## 7. Procedure consigliate

- aggiungere nuove utility in `shared` solo se possono essere usate tra desktop e Android (in `jvmCommonMain` se usano API JVM)
- lasciare la parte desktop-specifica in `desktopMain`, non in `commonMain`
- fare attenzione alle dipendenze circolari e ai callback tra UI e servizi di business
- documentare le nuove API pubbliche con KDoc chiaro, non solo per la sintassi ma per il contesto d'uso

## 8. Note di manutenzione

- Il build mostra un avviso di deprecazione interno al plugin Compose (`Configuration.setVisible`): non dipende dal progetto e sparirà aggiornando il plugin.
- `material-icons-extended` è ferma alla 1.7.3, ultima versione pubblicata; per nuove icone valutare la migrazione a Material Symbols come risorse vettoriali.
- `InitialSetupDialog` usa volutamente i controlli Material standard (campi con etichetta flottante): è coerente al suo interno e si vede solo al primo avvio.
- Le funzioni di disegno su `Canvas` (spettro, cascata, righello) sono lunghe per natura: dividerle non semplifica e rischia di alterare il rendering.
- Prima di un aggiornamento delle dipendenze verificare i moduli JDK del pacchetto con `.\gradlew.bat :composeApp:suggestRuntimeModules` e aggiornare `THIRD_PARTY_NOTICES.md` dai JAR presenti nell'immagine generata.

## 9. Documentazione di riferimento

- `../README.md`: overview del progetto
- `ARCHITECTURE.md`: architettura ufficiale e regole di sviluppo
- `GUI_DESIGN_SYSTEM.md`: riferimenti visivi e struttura della GUI desktop
- `TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md`: ambiente, build e packaging
- `DISTRIBUZIONE_E_RILASCI.md`: procedura di rilascio desktop e Android
