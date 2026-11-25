# Architettura del progetto

## 1. Panoramica

KiwiSDR Grabber & Station Recorder è un'applicazione desktop multipiattaforma sviluppata in Kotlin, pensata per connettersi a un ricevitore KiwiSDR, ricevere lo stream audio in tempo reale, monitorare il canale RF e registrare i contenuti audio su disco.

La soluzione è costruita con un'architettura modulare orientata al riutilizzo: la logica applicativa core è contenuta nel modulo `shared`, mentre il rendering e i componenti desktop specifici sono separati nella UI application `composeApp`.

Il progetto ha una versione Desktop Windows completa e un client Android di ascolto con un sottoinsieme ridotto delle funzioni (vedi [§4.10](#410-android-ambito-attivazione-e-decisioni-di-progetto)), che riusa la logica JVM condivisa. L'architettura non impone un framework applicativo complesso: mantiene un design semplice, reattivo e guidato da flussi di eventi (`Flow`/`Coroutine`) e da servizi dedicati per audio, monitoraggio, registrazione e connessione KiwiSDR.

### Obiettivi principali

- Connettersi a un qualsiasi server KiwiSDR configurabile.
- Sintonizzare frequenza e modalità di demodulazione in tempo reale.
- Ascoltare l'audio live con controllo di volume, AGC e S-meter.
- Registrare audio in WAV o MP3 con segmentazione temporale.
- Filtrare audio e registrazioni con Squelch RF e VAD, inclusi pre-buffer e hang time.
- Monitorare utenti e stato della stazione.
- Sincronizzare i segnalibri DX del ricevitore con i preferiti locali.
- Rappresentare lo spettro e il waterfall con una WebSocket W/F dedicata, aperta dopo l'autenticazione del client SND, seguendo il modello del client web ufficiale KiwiSDR.
- Trascrivere registrazioni localmente con Vosk e inviare alert configurabili via Telegram.
- Mantenere la logica di business separata dalla UI.

### Struttura generale

Il repository è attualmente organizzato così:

- `shared/`: logica condivisa tra piattaforme, protocollo KiwiSDR, modelli dati, servizi audio e monitoraggio desktop
- `composeApp/`: applicazione desktop Compose + UI per le schede e i componenti grafici
- `androidApp/`: applicazione Android (Activity, UI Compose mobile, foreground service di ascolto, manifest); il modulo esiste nella build solo se l.SDK Android è disponibile
- `docker/android-build/`, `scripts/android/`: ambiente di build Android in container (nessun SDK sul PC)
- `docs/`: documentazione architetturale, guide operative e piani per sviluppi futuri
- `build/`, `gradle/`: build system e toolchain

### Flussi operativi principali

1. L'applicazione avvia `KiwiAppController` con tutte le dipendenze concrete.
2. `KiwiClient` si connette via WebSocket al server KiwiSDR e inizializza lo stream audio e i messaggi di controllo.
3. `AudioPipeline` fa passare ogni frame audio PCM dal DSP (equalizzatore e filtri, se attivo) e dallo squelch, poi lo consegna a `DesktopAudioPlayer`, che lo rende in output audio locale, e ad `AudioRecorder` (§4.3.1).
4. `AudioRecorder` registra i segmenti audio in formato MP3 o WAV.
5. `StationMonitor` interroga periodicamente `/status` del ricevitore ed espone lo stato della stazione; lo storico degli utenti connessi viene scritto in `HistoryDatabase` solo quando cambia l'elenco dei nominativi.
6. Dopo l'autenticazione SND, `WaterfallCoordinator` (per conto di `KiwiAppController`) apre automaticamente la WebSocket dedicata `/W/F` usando lo stesso token/timestamp di sessione della socket SND, come fa il client web ufficiale. La W/F resta attiva indipendentemente dalla schermata; in caso di riconnessione SND viene chiusa e riaperta con il nuovo token.
7. La UI in `composeApp` osserva i `StateFlow` esposti dal controller e aggiorna lo schermo in tempo reale.

---

## 2. Stack tecnologico

### 2.1 Kotlin Multiplatform

- Nome e versione: Kotlin 2.4.21
- Finalità: base del progetto e condivisione della logica tra moduli e piattaforme
- Motivazione: rende possibile mantenere la business logic comune, riducendo duplicazioni tra desktop e future piattaforme
- Ambito: condiviso
- Note: il modulo `shared` usa `commonMain` (Kotlin puro), `jvmCommonMain` (codice JVM condiviso tra desktop e Android), `desktopMain` e `androidMain`; il target Android (plugin `com.android.kotlin.multiplatform.library`) si attiva solo con l.SDK disponibile

### 2.2 Compose Multiplatform

- Nome e versione: Compose Multiplatform 1.12.2 (material3 1.9.0, icone Material 1.7.3) + Compose Compiler plugin
- Finalità: interfaccia grafica declarativa desktop
- Motivazione: renderizzazione moderna, meno boilerplate rispetto alla UI Swing/awt tradizionale e compatibilità con il modello declarativo Kotlin
- Ambito: Desktop
- Alternative valutate: UI basata su Swing o classica JavaFX; la scelta è coerente con il modello Kotlin moderno e con la necessità di alta reattività

### 2.3 Networking JDK (`java.net.http`)

- Nome e versione: `java.net.http.HttpClient` / `WebSocket` del JDK 17
- Finalità: WebSocket KiwiSDR (SND e W/F), polling HTTP dello stato stazione e API Telegram
- Nota: i client usano l.interfaccia `KiwiWebSocketConnector` / `KiwiWebSocket` (`jvmCommonMain`); su desktop l.implementazione è `JdkWebSocketConnector` (`java.net.http`), su Android `OkHttpWebSocketConnector`. I comandi passano sempre da `WebSocketCommandQueue`, che li serializza senza bloccare il chiamante.
- Ambito: desktop

### 2.4 Kotlin Coroutines + Flow

- Nome e versione: coroutines 1.11.0
- Finalità: gestione della concorrenza del client KiwiSDR, audio, monitoraggio e callback UI
- Motivazione: modello nativo di Kotlin per orchestrare task asincroni e reattività dell'interfaccia
- Ambito: condiviso / desktop

### 2.5 Kotlinx Serialization

- Nome e versione: kotlinx-serialization-json 1.12.0
- Finalità: impostazioni utente (`settings.json`), segnalibri DX e messaggi JSON del protocollo KiwiSDR
- Ambito: condiviso
- Nota: le date usano oggi `java.time` lato desktop; `kotlinx-datetime`, mai usato, è stato rimosso.

### 2.6 SQLite JDBC

- Nome e versione: org.xerial:sqlite-jdbc 3.54.1.0
- Finalità: database locale per storico utenti e registrazioni
- Motivazione: storage local semplice, affidabile e senza dependency extra di runtime di backend
- Ambito: desktop

### 2.7 LAME / Jump3r e JLayer

- Nome e versione: Jump3r 1.0.6, JLayer 1.0.1
- Finalità: codifica MP3 e riproduzione file audio registrati
- Motivazione: supporto Java puro senza dipendenze native esterne per le feature implementate
- Ambito: desktop

### 2.8 Java Sound API

- Nome e versione: Java SE AudioSystem / SourceDataLine
- Finalità: output audio live e riproduzione file WAV/MP3 in locale
- Motivazione: accesso diretto al device audio del sistema operativo ed elevata compatibilità su Windows
- Ambito: desktop

### 2.9 WiX / packaging desktop

- Nome e versione: WiX 3.11 (nella cartella `tmp/wix311` e toolchain del repository)
- Finalità: packaging e installazione del software Windows
- Motivazione: creare installer MSI/EXE della desktop app senza dipendenze esterne di sistema
- Ambito: desktop

### 2.10 Android

- Nome e versione: Android Gradle Plugin 9.5.1, `compileSdk` 37, `targetSdk` 36, `minSdk` 29; Compose AndroidX (BOM 2026.09.00), Activity 1.13, Lifecycle 2.11, Core 1.19
- Librerie: OkHttp 5.5.1 (WebSocket), BouncyCastle `bcprov-jdk18on` 1.87 (solo l'API leggera Ed25519 per la licenza), kotlinx-coroutines-android
- Finalità: client di ascolto mobile (ascolto, spettro/waterfall, utenti, preferiti, registrazione WAV, impostazioni e licenza)
- Ambito: Android (`shared/androidMain`, `androidApp`)
- Note: build nel container Docker (`scripts/android/build.ps1`); dettagli in `TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md`

---

## 3. Architettura software

### 3.1 Livelli architetturali

L'architettura attuale è organizzata in quattro aree principali:

1. Shared domain / protocol logic
   - modelli condivisi, protocollo KiwiSDR, utility di token / comando, tipo di frequenza e modelli di connessione
   - package principali: `com.grabber.model`, `com.grabber.kiwi`

2. Desktop application services
   - audio player, monitor station, recorder, database history, alerting, app settings, coordinatori, licenza
   - package principali: `com.grabber.app`, `com.grabber.audio`, `com.grabber.monitor`, `com.grabber.recording`, `com.grabber.license`, `com.grabber.alerting`

3. UI desktop
   - componenti Compose per VFO, spettro, waterfall, registrazioni, monitor utenti e impostazioni
   - package: `com.grabber.ui` (finestra e navigazione), `com.grabber.ui.feature.*` (schede), `com.grabber.ui.designsystem` (componenti condivisi)

4. Platform runtime
   - Java runtime, JDK 17+ e Java Sound, WebSocket JDK, SQLite JDBC e packaging WiX

### 3.2 Relazioni tra i componenti

```mermaid
flowchart LR
    UI[composeApp UI] --> CTRL[KiwiAppController]
    CTRL --> WFC[WaterfallCoordinator]
    CTRL --> HIS[RecordingHistoryCoordinator]
    CTRL --> ALR[QsoAlertCoordinator]
    CTRL --> SCN[ScanCoordinator]
    CTRL --> PIPE[AudioPipeline]
    PIPE --> DSP[AudioDspChain]
    PIPE --> SQL[SquelchController]
    CTRL --> KCL[KiwiClient]
    CTRL --> AUD[DesktopAudioPlayer]
    CTRL --> REC[AudioRecorder]
    CTRL --> MON[StationMonitor]
    CTRL --> SET[AppSettings]
    WFC --> WCL[WaterfallClient (dedicated W/F WebSocket)]
    HIS --> DB[HistoryDatabase]
    HIS --> STT[OfflineTranscriptionQueue]
    ALR --> TG[TelegramAlertService]
    KCL --> Q[WebSocketCommandQueue]
    WCL --> Q
    KCL --> PROTO[KiwiProtocol]
    KCL --> PARSE[KiwiMessageParser]
    WCL --> WF[WaterfallFrame / Buffer]
    SET --> SEC[SecretCipher (DPAPI)]
```

### Shell e schede della GUI desktop

La UI Compose vive in `composeApp/src/desktopMain/kotlin/com/grabber/ui/`. `Main.kt` crea la finestra desktop massimizzata all'avvio (dimensione iniziale 1100×740 dp quando ripristinata, minima 960×640 px), mostra nel titolo la versione Gradle `appVersion` e crea l'istanza di `KiwiAppController`. `App.kt` compone la console radio persistente (`feature/console/RadioConsole`), la barra delle schede (`MainTabBar`, con l'enum `MainTab`) e la scheda attiva. Ogni scheda ha il proprio package `ui/feature/<nome>/` (`console`, `favorites`, `audio`, `recordings`, `spectrum`, `users`, `digital`, `settings`), diviso in un file per pannello, sezione o dialogo:

- Preferiti (`FavoritesTab`)
- Audio (`AudioTab`)
- Registratore (`RecordingsTab`)
- Spettro & Cascata (`SpectrumTab`)
- Utenti & Monitor (`UsersTab`)
- Modi digitali (`DigitalModesTab`): elenco dei modi, dettagli, parametri e uscita del decoder; riga AFC nella console (`ConsoleAudioPanel`)
- Impostazioni (`SettingsTab`)

La console rimane montata mentre cambia la scheda. Le schermate accedono all'applicazione solo tramite le API e i `StateFlow` di `KiwiAppController` (unica eccezione: `waterfallBuffer`, letto direttamente dal renderer per prestazioni) e non devono introdurre una seconda sorgente di verità per sintonia, audio, registrazione o waterfall. La vista waterfall compatta e quella estesa riutilizzano lo stesso componente e lo stato gestito dal controller. I token e i componenti Compose condivisi sono in `ui/designsystem/`; la specifica visuale è in `docs/GUI_DESIGN_SYSTEM.md`. Il tema (scuro, chiaro o di sistema) è letto da `settings.uiTheme` in `App.kt` e applicato da `GrabberTheme`, che fornisce la palette del tema (`LocalGrabberPalette`); i display strumentali (LCD del VFO, spettro, waterfall, righello) usano `DisplayColors` e restano scuri in tutti i temi. Il tema è una funzione libera (non riservata) e il predefinito è quello scuro, così chi aggiorna non vede cambiamenti. Le mappe dei ricevitori (pubblica KiwiSDR e rx-tx.info) sono solo collegamenti che aprono il browser di sistema: l'app non scarica né elabora i loro dati e si collega solo a ricevitori KiwiSDR; gli indirizzi sono costanti uniche in `ReceiverMaps` (`commonMain`), usate da desktop e Android.

### 3.3 Gestione dello stato

L'applicazione usa `StateFlow` e `SharedFlow` come meccanismo principale per propagare lo stato. I servizi eseguono aggiornamenti reattivi e la UI osserva gli stream. Questo produce una separazione netta tra:

- stato del protocollo
- stato del canale audio
- stato della registrazione
- stato del monitor
- stato della GUI

Il pattern centrale è il seguente:

- `KiwiClient` emette `AudioFrame`, `ConnectionState`, `currentFrequencyKhz`, `currentMode`, `activeUsers`, `serverDxLabels`
- `WaterfallClient` emette `WaterfallFrame`
- `KiwiAppController` aggrega i flussi e li rende disponibili alla UI
- `App.kt` osserva i `StateFlow` e aggiorna i componenti composable
- `WaterfallCoordinator` gestisce lo stream waterfall per tutta la sessione connessa, senza dipendere dalla composizione della UI; la card condivisa tra Ricevitore & VFO e Spettro & Cascata si limita a visualizzare i frame.

### 3.4 Gestione degli errori

La gestione degli errori è locale per servizio:

- `KiwiClient` tenta la riconnessione automatica quando il socket si chiude, va in errore o un keepalive non viene inviato entro 10 secondi
- `ConnectionResumeCoordinator` (`jvmCommonMain`, usato da entrambi i controller): alla perdita di rete (stato `Reconnecting`) ricorda registrazione e scansione attive e le ferma (il file viene chiuso); dopo la riconnessione e il `badp=0` riavvia la registrazione con gli stessi parametri in un nuovo file e la scansione dal passo su cui si trovava. Disconnessione voluta, rifiuto `badp`, sintonia o stop manuali annullano la ripresa
- Riconnessione all'avvio: `connect()` imposta `resumeConnectionOnStartup` nelle impostazioni, la disconnessione voluta e il rifiuto del server lo azzerano, la chiusura dell'app lo lascia invariato. All'avvio successivo il controller (desktop) o `MainActivity` (Android, solo all'avvio e non dopo una rotazione) si ricollegano all'ultimo server usato
- `UpdateChecker` (`jvmCommonMain`, logica di confronto in `commonMain` `com.grabber.update`): all'avvio legge `GET /repos/Gig81/KiwiSDR-Grabber/releases/latest` (richiesta anonima, timeout 6 s, bozze e pre-release ignorate) ed espone `updateCheck`; lo splash screen propone la pagina della release se la versione pubblicata è più recente. Gli errori di rete producono solo lo stato `Unavailable`
- i comandi WebSocket di `KiwiClient` e `WaterfallClient` passano da `WebSocketCommandQueue`: invio in ordine, senza bloccare il chiamante, con gli invii falliti registrati nel log
- le coroutine del controller e dei coordinatori hanno un `CoroutineExceptionHandler` che registra nel log le eccezioni non gestite, con il nome dell'operazione (`CoroutineName`)
- `AudioRecorder` usa `try/catch` locali intorno alla scrittura di chunk audio
- `HistoryDatabase` registra nel log le migrazioni, i file audio non eliminabili e la chiusura non riuscita; non ci sono `catch` vuoti
- `AppSettings` non interrompe il caricamento se una credenziale non è decifrabile: il campo viene svuotato e va reinserito
- `AppLogger` viene inizializzato dal punto d'ingresso prima di creare il controller, salva anche gli stack trace e accoda gli errori che precedono l'apertura del file. `AppErrorReporter` intercetta le eccezioni JVM/coroutine non gestite, le registra e mostra un rapporto copiabile all'utente

L'infrastruttura non usa una `Result` monad globale né un framework di dependency injection. I flussi di errore sono quindi reattivi e pubblici attraverso `ConnectionState` e log.

### 3.5 Gestione dati e risorse

- File di configurazione: `AppSettings` persiste i parametri in `~/.kiwigrabber/settings.json`. La scrittura è atomica (file temporaneo e rinomina) e gli aggiornamenti dello stato usano `MutableStateFlow.update`; il salvataggio resta sincrono per non perdere modifiche alla chiusura.
- Credenziali: password del ricevitore e token del bot Telegram sono salvati cifrati con Windows DPAPI (`SecretCipher`, prefisso `dpapi:`), legati all'account Windows; i valori in chiaro delle versioni precedenti vengono cifrati al primo avvio. Nei log il comando di autenticazione compare come `p=***`.
- Preferiti: il flag `favoritesCustomized` distingue una lista modificata dall'utente da una lista predefinita di versioni precedenti, che viene aggiornata automaticamente; una lista modificata non viene mai sostituita.
- Primo avvio: wizard obbligatorio per confermare callsign e indirizzo server; configurazione Telegram facoltativa. Le installazioni esistenti senza il nuovo flag sono considerate già configurate per non interrompere l'uso.
- Versione applicazione: proprietà Gradle `appVersion` in `gradle.properties`, condivisa tra versione del progetto e pacchetti desktop.
- La versione viene passata al runtime desktop e mostrata nel titolo della finestra.
- La sezione Licenza mostra il Machine ID e verifica localmente i codici firmati Ed25519; non invia richieste a un bot interno. Le funzioni avanzate sono disponibili solo dopo l'attivazione. Le notifiche Telegram usano il bot personale configurato dall'utente e sono soggette allo stesso gate.
- Gli alert Telegram QSO/keyword includono testo e allegati audio configurabili; ogni messaggio, compresi gli alert delle trascrizioni, rispetta l'intervallo minimo impostato. Il timestamp del cooldown usa un clock monotono e gli alert nel periodo di cooldown vengono soppressi, non accodati. La logica è coperta da test automatici e la verifica operativa è stata confermata il 10/11/2026. Email/SMTP è esclusa dal progetto; Toast Windows non rientra nella Fase 8.
- Preferenze predefinite: livello log `WARN`, registrazione degli utenti connessi disattivata per nuove installazioni; preferenze già salvate sono mantenute.
- Database: `HistoryDatabase` crea la cartella dati se manca e usa SQLite in `~/.kiwigrabber/kiwigrabber_history.db`, con le tabelle `user_logs` e `recording_history`. Un database delle versioni precedenti in `~/kiwigrabber_history.db` viene spostato con un'unica rinomina; se restano file WAL/SHM o lo spostamento fallisce si continua a usare il vecchio percorso.
- Storico utenti: una riga viene scritta solo quando cambia l'elenco dei nominativi connessi (non a intervalli fissi); se il server non comunica il proprio nome si registra l'host del ricevitore.
- Trascrizioni: lo stato è l'enum `TranscriptionStatus`, salvato nel database con il proprio nome.
- Database: la connessione SQLite è mantenuta aperta con WAL e `busy_timeout`; non aprire/chiudere una connessione a ogni polling o aggiornamento UI.
- Registrazioni: salvate in `~/KiwiGrabberRecordings/YYYY-MM-DD/HH-mm-ss.mp3` o `.wav`
- Log applicativi: `~/.kiwigrabber/logs`; file giornalieri creati all'avvio, con rotazione a 5 MiB, tre backup per giornata e rimozione dei file oltre 14 giorni
- Waterfall: le frequenze preferite salvate nelle impostazioni sono mostrate come marker ed etichette quando rientrano nella banda visibile
- Waterfall: il livello di zoom viene salvato nelle impostazioni e ripristinato alla riapertura
- Tema dell'interfaccia: `uiThemeName` (`UiThemeMode`: `DARK` predefinito, `LIGHT`, `SYSTEM`); un valore sconosciuto torna al tema scuro
- Scansione dell'intervallo: `rangeScanStartKhz`, `rangeScanEndKhz`, `rangeScanStepKhz`, `rangeScanModeCode`, `rangeScanDwellMs` (300–10.000 ms), `rangeScanAutoResume`, `rangeScanExcludedKhz`; predefinito banda CB 26.965–27.405 kHz a passi di 10 kHz in AM. I parametri vengono salvati solo se validi (`validateRangeScan`)
- DSP: `audioDsp` (`AudioDspConfig`), riportato nei limiti (`normalized()`) dal controller al caricamento e da `AppSettings` al salvataggio; assente nei file delle versioni precedenti, vale la configurazione neutra con destinazione spenta
- Squelch e VAD: ascolto e registrazione hanno ciascuno una modalità esplicita (`GateMode`: `OFF`, `RF`, `VOICE`, `RF_AND_VOICE`), salvata in `listenGateModeName`/`recordGateModeName`. Nei file delle versioni precedenti si ricava dagli interruttori `squelch*Enabled`/`vad*Enabled` conservando il comportamento di allora (squelch e VAD attivi → `RF`; solo VAD in registrazione → `RF_AND_VOICE`). Il gate RF di `SquelchController` segue sempre la soglia (serve anche alla scansione); il segmento di registrazione ha una macchina a stati separata: `RF` apre col gate RF e chiude a fine hang time, `VOICE` apre alla prima voce e chiude dopo l'hang time senza voce, `RF_AND_VOICE` apre con voce e gate RF aperto e chiude col gate RF o dopo `voiceSilenceCloseSeconds` senza voce (`voiceSilenceCloseEnabled`, 5 s predefiniti). Con `OFF` i segmenti seguono il gate RF solo per gli avvisi QSO. Senza licenza le modalità perdono la componente voce; la modalità di registrazione non si cambia durante una registrazione
- Filtro anti-rumore: `recordingNoiseFilter` (`RecordingNoiseFilterConfig`). Solo per i segmenti chiusi dal gate (`AudioRecorder.onSegmentCompleted` riceve la durata attiva, senza la coda di hang time o silenzio; `null` per continua e intervalli): sotto la durata minima il file è cancellato prima dell'archiviazione; dopo la trascrizione automatica `RecordingHistoryCoordinator` cancella file e riga se il testo è vuoto o fatto solo di parole rumore, o se la confidenza media Vosk (`setWords(true)`) è sotto la soglia, e `QsoAlertCoordinator.forgetRecording` evita l'avviso Telegram. Le trascrizioni richieste a mano non sono mai filtrate
- Machine ID: SHA-256 del `MachineGuid` di Windows (registro, vista 64 bit), nel formato `GRAB-XXXX-XXXX-XXXX-XXXX`; non cambia con rete, VPN o rinomina del PC, cambia reinstallando Windows. Se il valore non è leggibile si usa `GRAB-NON-DISPONIBILE`, che nessuna licenza può validare. L'esito della verifica della firma viene memorizzato finché la chiave non cambia.
- Licenza desktop: verifica locale legata al Machine ID e richiesta di attivazione via canale Telegram dedicato. Non esiste un periodo di prova: senza attivazione restano disponibili le funzioni di base (ascolto, sintonia, spettro/waterfall, utenti connessi, preferiti) e le funzioni avanzate sono bloccate. Le chiavi sono firmate offline con Ed25519 dall'app License Manager del repository privato di gestione licenze.

### 3.6 Vincoli verificati del protocollo KiwiSDR

- I client audio/controllo (`/SND`) e waterfall (`/W/F`) usano WebSocket distinti. La socket waterfall si apre solo dopo l'autenticazione SND e riutilizza lo stesso token di sessione; mantenere separati i comandi e i frame dei due flussi.
- Dopo il messaggio `audio_rate`, rispondere con `SET AR OK in=<rate> out=<rate>` prima di avviare lo stream audio.
- Un frame SND ha un header di 10 byte: sequenza a 32 bit little-endian e RSSI a 16 bit big-endian. Il payload PCM inizia all'offset 10; il flag `0x80` indica PCM little-endian, altrimenti i campioni big-endian vanno convertiti prima di inviarli a Java Sound e al registratore. Il valore RSSI in dBm si calcola come `0.1 * smeter - 127.0`.
- Processare i frame audio in ordine FIFO su un dispatcher serializzato; il lavoro concorrente per frame può causare riordino o duplicazione percepibile dell'audio.
- `/status` fornisce conteggi e stato della stazione, non i nominativi degli ascoltatori. I nomi arrivano dal messaggio WebSocket `user_cb`; combinare le due fonti prima di salvare lo storico.
- `/EXT` (decoder del server, verificato su v1.902): terzo WebSocket con lo stesso token della SND, aperto solo dopo `badp=0`. Sequenza `SET auth`, `SET ident_user`, `SET ext_switch_to_client=<NOME> first_time=1 rx_chan=-1`; il server risponde `MSG ext_client_init=<0|1>` (1 = DRM in uso, estensioni bloccate) e l'estensione `EXT ready` (nome sconosciuto: nessuna risposta, serve un timeout). Chiusura: comando di stop dell'estensione, poi `SET ext_blur` (che da solo non ferma il decoder). Valori di testo percent-encoded; frame `DAT` con un byte di comando. La sintonia resta su SND.
- I client non browser (`/<token>/SND` senza `/kiwi/`) contano tutti nel limite `ext_api_nchans`: una sessione con audio, waterfall e `/EXT` usa 3 connessioni; il rifiuto è una chiusura senza messaggi. Il limite e le abilitazioni `<chiave>.enable` delle estensioni arrivano in `load_cfg` (SND), l'elenco delle estensioni installate in `extint_list_json` (W/F).
- Al quinto keepalive il server chiude il canale se la SND non ha ricevuto frequenza, modo, banda, AGC e `AR OK`, o il W/F zoom, avvio, dB e velocità.
- In CW la frequenza mostrata è quella del segnale (portante + centro della banda passante, come nel client web): la conversione è solo in `KiwiProtocol.tune`/`carrierFrequencyKhz` e vale per tutte le sintonie, desktop e Android.

---

## 4. Componenti riutilizzabili

### 4.1 `KiwiClient`

- Package: `com.grabber.kiwi.KiwiClient`
- Responsabilità: connessione WebSocket tramite `KiwiWebSocketConnector` (JDK su desktop, OkHttp su Android), autenticazione, keepalive, riconnessione automatica, invio dei comandi e streaming audio
- API pubblica: `connect`, `disconnect`, `retryReconnectNow` (anticipa la riconnessione quando la rete torna disponibile), `updateServer`, `updatePassword`, `tune`, `setMode`, `setAgc`, `setIdentUser`, `requestDxLabels`, `audioFlow`, `connectionState`, `activeUsers`, `serverDxLabels`
- Dipendenze: `KiwiWebSocketConnector`, `KiwiProtocol`, `KiwiMessageParser`, `WebSocketCommandQueue`, `KiwiTokenGenerator`, `AppLogger`, `ModulationMode`
- Source set: `jvmCommonMain`, come parser, coda, waterfall, squelch/VAD, `AppLogger`, `WaterfallCoordinator` e `WavWriter`
- Riutilizzabilità: core del sistema; riveste il protocollo KiwiSDR e deve rimanere condiviso tra tutte le piattaforme future

### 4.1.1 `KiwiProtocol`, `KiwiMessageParser`, `WebSocketCommandQueue`

- `KiwiProtocol` (`commonMain`, Kotlin puro): costruisce i comandi testuali. Password e nominativo sono codificati come `encodeURIComponent` (il server li decodifica con `kiwi_str_decode_inplace`); `redactSecrets` oscura la password per i log; la frequenza è formattata con tre decimali senza dipendere dalla lingua di sistema.
- `KiwiMessageParser` (senza stato e senza I/O): decodifica `user_cb` (JSON), l'elenco dei canali DX `mkr` e l'header dei frame `SND`. I canali DX si chiedono con `KiwiProtocol.dxMarkers()` (`SET MARKER` completo a 8 parametri, accettato dal server anche su SND): una variante non riconosciuta fa chiudere la connessione con `too_busy`. `load_dxcfg` contiene solo la configurazione dei tipi DX e viene ignorato.
- `KiwiMessageParser` legge anche la località (`g`) degli utenti di `user_cb`.
- `KiwiWebSocket` / `KiwiWebSocketListener` / `KiwiWebSocketConnector`: astrazione del trasporto; i messaggi arrivano al listener già ricomposti. Implementazioni: `JdkWebSocketConnector` (desktop), `OkHttpWebSocketConnector` (Android, con ping ogni 20 s per rilevare le connessioni morte dopo un cambio di rete).
- `WebSocketCommandQueue`: serializza invii e chiusura su un `KiwiWebSocket` (il WebSocket del JDK rifiuta un nuovo invio prima del completamento del precedente); un invio fallito non blocca i successivi. La coda e l'intera sessione SND sono testate con un trasporto finto (`WebSocketCommandQueueTests`, `KiwiClientTransportTests`).
- `KiwiServerDefaults`: host e porta del ricevitore predefinito, unica fonte per tutti i client e per `DEFAULT_SERVER_ADDRESS`.

### 4.2 `WaterfallClient`

- Package: `com.grabber.waterfall.WaterfallClient`
- Responsabilità: apertura della WebSocket dedicata `/W/F` (stesso `KiwiWebSocketConnector` della sessione SND) dopo l'autenticazione SND, autenticazione e comandi di waterfall, decodifica frame FFT, gestione zoom e centro frequenza
- Dipendenze: `KiwiProtocol`, `WebSocketCommandQueue`, `AppLogger`, `ConnectionState`
- Lifecycle: lo stream viene avviato da `WaterfallCoordinator` solo dopo il `badp=0` di autenticazione SND, resta attivo cambiando schermata e viene chiuso durante disconnessione/riconnessione. La configurazione W/F parte dopo il proprio `badp=0`; i rifiuti permanenti (`badp`) generano una notifica e non vengono ritentati automaticamente.
- Protocollo: audio/controllo (`/SND`) e waterfall (`/W/F`) sono socket distinti, ma devono condividere lo stesso token/timestamp nell'URL per essere associate alla medesima sessione Kiwi. Il client web ufficiale apre la seconda socket dopo l'autenticazione principale e ripete il flusso di autenticazione per W/F. Token distinti fanno rifiutare W/F con `badp=5` come connessione multipla dallo stesso IP. Evitare di inoltrare i frame o i comandi W/F sulla socket SND.
- Riutilizzabilità: condivisibile in una futura UI mobile o in una sua interfaccia testuale; la parte rendering grafico è separata dal protocollo e va mantenuta indipendente dalla piattaforma

### 4.3 `DesktopAudioPlayer`

- Package: `com.grabber.audio.DesktopAudioPlayer`
- Responsabilità: output locale del flusso audio con Java Sound, volume e livello RMS
- Dipendenze: `AudioFrame`
- Riutilizzabilità: parziale; la logica di base del player audio è portabile, ma l'implementazione Java Sound è fortemente desktop-only

### 4.3.1 Pipeline audio e DSP: `AudioPipeline`, `AudioDspChain`

- `AudioPipeline` (`jvmCommonMain`, package `com.grabber.audio`, usata da desktop e Android): percorso di ogni frame SND, chiamato in ordine sul flusso seriale dei frame. Su Android l'uscita è `AudioTrackOutput` e il registratore `MediaStoreWavRecorder`. Ordine degli stadi:

  ```
  frame ─┬▶ [DSP pre-squelch] ─▶ SquelchController ─▶ [DSP post-squelch] ─▶ DesktopAudioPlayer
         │          │
         │          └──────────────▶ AudioRecorder (e pre-buffer dello squelch)
         └▶ AudioAnalysisTap (copia originale: decoder locali, AFC, riconoscimento)
  ```

  Con destinazione *Ascolto e registrazione* (`DspTarget.LISTEN_AND_RECORD`) il DSP agisce **prima** dello squelch, così pre-buffer, registrazione, ascolto, allegati Telegram e trascrizioni contengono lo stesso audio; il VAD analizza l'audio filtrato. Con *Solo ascolto* (`LISTEN`) agisce sui byte in uscita dallo squelch e il registratore riceve l'originale. "Solo registrazione" non esiste per scelta. RSSI e gate RF non cambiano (dipendono dall'header del frame). Le due catene hanno stato separato; la catena che entra in uso viene azzerata, e `KiwiAppController.applyTuning` azzera i filtri a ogni cambio frequenza.
- `AudioDspChain` (`jvmCommonMain`, package `com.grabber.audio.dsp`, Kotlin puro riusabile su Android): passa-alto → passa-basso → notch → riduzione rumore (`LmsNoiseReducer`, predittore NLMS) → equalizzatore a 7 bande (shelf a 100 Hz e 4 kHz, peaking in mezzo) → limitatore morbido. Filtri biquad RBJ (`Biquad`, `BiquadDesign`, formule di pubblico dominio). Coefficienti calcolati da `AudioFrame.sampleRate` e ricalcolati se cambia; bande oltre 0,45 × sample rate ignorate. La configurazione (`AudioDspConfig`, immutabile e serializzata in `settings.json`) si cambia da qualsiasi thread e si applica all'inizio del blocco successivo senza azzerare lo stato dei filtri; con configurazione neutra la catena restituisce lo stesso array senza elaborarlo. Nessuna dipendenza esterna. Requisito di prestazioni: 60 s di audio a 12 kHz con la catena completa elaborati in meno di 1 s. Se in futuro la riduzione del rumore peggiorasse il rilevamento del VAD con *Ascolto e registrazione*, la soluzione prevista è passare al VAD il frame originale.
- Licenza: senza attivazione la pipeline tratta la destinazione come spenta anche se le impostazioni contengono una configurazione attiva; il controller riapplica la configurazione quando cambia la chiave di licenza. Le registrazioni già salvate non vengono mai elaborate.
- Controller: la logica è in `AudioDspControl` (`jvmCommonMain`), usato da `KiwiAppController` e `MobileAppController`; `dspConfig` (`StateFlow`) applica subito le modifiche all'audio e le salva su disco con un debounce di 300 ms (salvataggio immediato allo spegnimento); `setDspBypass` (confronto A/B) non viene salvato; `isDspActive` alimenta l'indicatore `DSP` della console.
- Prestazioni: 60 s di audio con la catena completa sono elaborati in poche centinaia di ms (test `fullChainIsMuchFasterThanRealTime`).

### 4.4 `AudioRecorder`

- Package: `com.grabber.recording.AudioRecorder`
- Responsabilità: registrazione audio continua o segmentata, file MP3/WAV, callback di completamento
- Dipendenze: `AudioFrame`, `LameEncoder`, `WavWriter`, filesystem, Java IO
- Riutilizzabilità: la scrittura WAV è in `WavWriter` (`jvmCommonMain`, su un `FileChannel`, con `repairHeader` per i file interrotti), usata anche da `MediaStoreWavRecorder` su Android; MP3, segmentazione e filesystem restano specifici del desktop

### 4.5 `HistoryDatabase`

- Package: `com.grabber.monitor.HistoryDatabase`
- Responsabilità: persistenza di dati storici su SQLite, migrazione delle colonne (`ensureColumn`) e della posizione del file (`relocateLegacyDatabase`)
- Dipendenze: SQLite JDBC
- Riutilizzabilità: condivisibile tramite abstraction se in futuro verranno introdotti altri backend o una layer di persistence platform-agnostic

### 4.6 `KiwiAppController` e coordinatori

- Package: `com.grabber.app`
- `KiwiAppController`: facciata unica della UI. Collega client, pipeline audio, registrazione, squelch/VAD, preferiti, impostazioni e licenza; i servizi sono privati e la UI usa solo le sue API e i suoi `StateFlow`.
- `WaterfallCoordinator` (`jvmCommonMain`, condiviso con Android): socket W/F aperto dopo l'autenticazione SND, ritentativi sulle interruzioni temporanee (non sui rifiuti `badp`), palette, zoom, range dBm, inseguimento del VFO. Persiste le impostazioni tramite l'interfaccia `WaterfallPreferences` (`AppSettingsWaterfallPreferences` su desktop, `MobileSettings` su Android).
- `RecordingHistoryCoordinator`: storico di registrazioni e utenti, ricerca full-text, coda di trascrizione offline; senza licenza lo storico risulta vuoto.
- `QsoAlertCoordinator`: notifiche Telegram di inizio/fine QSO e, a trascrizione conclusa, parole chiave con audio e testo allegati; rispetta il gate di licenza e l'intervallo minimo tra messaggi.
- `ScanCoordinator` (`jvmCommonMain`, file `ScanCoordinator.kt`, `FrequencyRangeScan.kt`, `RangeScanSession.kt`; usato anche da `MobileAppController` per la scansione dell'intervallo): motore unico delle scansioni. Un `ScanPlan` indica il tipo (`ScanKind.CHANNELS` o `RANGE`), la sorgente dei passi (`ScanSource`, letta per indice), il tempo di permanenza e la ripresa automatica; sorgente e parametri sono riletti a ogni passo. Sorgenti: `ListScanSource` (preferiti con `scanEnabled`) e `FrequencyRangeScanSource` (da inizio a fine con passo e modo, frequenze calcolate come `inizio + i × passo` arrotondate a 1 Hz, frequenze escluse saltate con tolleranza di mezzo passo; il controller la ricrea solo quando cambiano i parametri). Dopo ogni cambio frequenza attende un tempo di assestamento (i primi frame possono riferirsi ancora alla frequenza precedente) e azzera lo squelch, poi ascolta per il tempo di permanenza; se il gate RF dello squelch si apre resta sul passo e, con la ripresa automatica, riparte alla chiusura del gate (dopo l'hang time). Una sola scansione alla volta: il controller ferma quella in corso prima di avviarne un'altra. I segnali trovati dalla scansione dell'intervallo sono raccolti in memoria da `ScanHitLog` (nessuna persistenza). La sintonia manuale (`KiwiAppController.tune`, anche da waterfall, preferiti o segnali trovati) e la perdita della connessione fermano la scansione; il controller sintonizza per conto dello scanner con un percorso interno che non la interrompe. Funzione riservata alla licenza (controllata nel controller, anche per i setter). Il segnale si rileva solo dal gate RF dello squelch (soglia RSSI): serve una soglia adatta alla banda, e l'app non avvisa se lo squelch d'ascolto è spento. Durante la scansione il waterfall segue il VFO (`followTuning`) con lo zoom scelto dall'utente; la permanenza dell'intervallo (`rangeScanDwellMs`) è separata da quella dei canali (`scanDwellSeconds`). Non realizzati, possibili evoluzioni: rilevazione dai dati FFT del waterfall, evidenziazione dell'intervallo sul righello, salvataggio su database dei segnali trovati.
- `DigitalDecoderCoordinator` (`jvmCommonMain`, package `com.grabber.decoder`): un decoder alla volta. I decoder del server passano da `ExtensionClient` (`com.grabber.kiwi.ext`, socket `/EXT` con handshake, timeout su `EXT ready`, keepalive, chiusura ordinata, rifiuti permanenti senza nuovi tentativi) e dagli adattatori `ServerDecoderSession` (`decoder/server`: FT8/FT4, WSPR, CW, CW Skimmer, FAX, SSTV, HFDL, ALE); quelli locali (`decoder/fsk`: `FskDemodulator`, `AsyncBitDecoder`, `SyncBitClock`, ITA2, CCIR476/SITOR-B, NAVTEX, DSC) ricevono l'audio dal tap di analisi. Le uscite si accumulano in `DecoderOutputBuffer` (limiti di righe e immagini) su un dispatcher seriale; `/EXT` viene riaperto alla riautenticazione della SND. Disponibilità dei modi in `DigitalModeAvailabilityRules`, frequenze e sintonie per modo in `DigitalModeCatalog` (`commonMain`). Avviare un decoder ferma la scansione e viceversa.
- `AudioAnalysisTap` (`jvmCommonMain`): copia del frame originale (prima di DSP e squelch) in una coda limitata con dispatcher proprio; costo nullo se nessun consumatore è attivo; in saturazione scarta i frame più vecchi (i consumatori vedono il buco dal numero di sequenza).
- `AutoCenterCoordinator` (`jvmCommonMain`): AFC one-shot dopo le sintonie `TuneSource.USER_TYPED` e `WATERFALL_CLICK`, con misura sullo spettro W/F e/o sull'audio (`AudioSpectrumAnalyzer`, `SignalCenterEstimator`); la correzione usa `TuneSource.AFC` e non riattiva l'AFC.
- `ModeClassifier` (`jvmCommonMain`, `audio/analysis`): riconoscimento euristico del modo con isteresi; il controller avvia da solo solo i decoder locali sopra il 90 %.
- `DecoderAlertCoordinator` e `DecoderAlertRules` (desktop): notifiche Telegram dei decoder con parole chiave dedicate, limite per parola e scarto dei duplicati. `DecodedOutputStore` (desktop) salva testi e PNG in `<registrazioni>/Decodifiche/`.
- Riutilizzabilità: livello di orchestrazione desktop; in un'architettura futura i coordinatori possono diventare `UseCase` o `ViewModel` separati.

### 4.7 `AppSettings` e `SecretCipher`

- Package: `com.grabber.app`
- Responsabilità: persistenza atomica della configurazione, migrazione dei preferiti predefiniti, cifratura delle credenziali tramite `SecretCipher` (interfaccia in `jvmCommonMain`; `DpapiSecretCipher` su Windows, `PlainTextSecretCipher` altrove, `KeystoreSecretCipher` con prefisso `aks:` su Android)
- Dipendenze: JSON serialization, filesystem, JNA Platform (DPAPI)
- `FavoriteFrequency`, `parseServerAddress` e `bandGroupFor` sono in `jvmCommonMain` (stesso package) e servono anche ad Android
- Su Android le impostazioni ridotte sono in `MobileSettings` (`filesDir/settings.json`, scritto in modo atomico e asincrono, escluso dal backup)

### 4.8 Licenza: `LicenseFormat`, `LicenseChecker`, `LicenseManager`, `MachineIdUtil`

- Package: `com.grabber.license`
- Logica comune (`jvmCommonMain`): `LicenseFormat` (formato `KSR1`, payload, chiave pubblica), `LicenseChecker` (esito con cache per il dispositivo corrente), `DeviceIds` (ID Android `GRAB-A-…`), `FeatureGate` / `GatedFeature` (funzioni riservate su Android)
- Verifica della firma iniettata (`SignatureVerifier`): `JdkEd25519SignatureVerifier` su desktop, `BouncyCastleEd25519Verifier` su Android; i vettori comuni `LicenseTestVectors` sono verificati con entrambi
- ID dispositivo: `MachineIdUtil` (desktop, `MachineGuid` di Windows, `GRAB-XXXX-…`), `AndroidDeviceId` (`ANDROID_ID`, `GRAB-A-XXXX-…`)
- `LicenseManager` (desktop) mantiene l'API usata da `KiwiAppController`
- Dipendenze: JDK (`java.security`), JNA Platform (registro di Windows); BouncyCastle su Android

### 4.9 Android: `MobileAppController` e servizi `androidMain`

- `MobileAppController` (`shared/androidMain`, package `com.grabber.mobile`): unica facciata della UI Android, con le stesse regole di `KiwiAppController` (solo API e `StateFlow`; eccezione `waterfallBuffer` per il renderer). Vive per tutto il processo, creato da `KiwiGrabberApplication`; applica il `FeatureGate` prima di ogni azione riservata (registrazione, preferiti personali, ascolto in background, scansione, DSP) ed emette `lockedFeatureRequests` per la UI. Usa le stesse classi condivise del desktop (`jvmCommonMain`): `AudioPipeline` + `AudioDspControl` per DSP e squelch, `ScanCoordinator` + `RangeScanSession` per la scansione dell'intervallo.
- Servizi: `OkHttpWebSocketConnector`, `AudioTrackOutput` (thread dedicato, coda limitata, pre-buffer 250 ms), `AudioSessionPolicy` (audio focus, cuffie scollegate), `NetworkMonitor` (riconnessione anticipata, rete a consumo → waterfall a velocità minima), `MediaStoreWavRecorder` (WAV in MediaStore con `IS_PENDING` e riparazione all'avvio), `KeystoreSecretCipher`, `MobileSettings`.
- Squelch con VAD disattivato (`SquelchController(vad = null)`); i frame passano da `AudioPipeline` come sul desktop (DSP prima dello squelch con destinazione *Ascolto e registrazione*, dopo con *Solo ascolto*); `MediaStoreWavRecorder` riceve il frame dopo il DSP pre-squelch.
- Waterfall: socket W/F aperto solo con la schermata Waterfall visibile e l'app in primo piano; riaperto con lo stesso token SND.
- `androidApp`: `KiwiGrabberApplication` (ciclo di vita del processo, avvio/arresto del servizio), `KiwiPlaybackService` (foreground service `mediaPlayback` con notifica, azione *Disconnetti*, Wi-Fi lock, chiusura della sessione alla rimozione dai recenti), `MainActivity` e UI Compose in `com.gig81.kiwisdrgrabber.ui.*` (Radio, Waterfall, Preferiti, Utenti, Impostazioni).

### 4.10 Android: ambito, attivazione e decisioni di progetto

L'app Android (dalla 1.2.0) è un **client di ascolto interattivo e attento ai consumi**, non un monitor continuo: niente elaborazioni pesanti (Vosk, VAD). Dalla 1.3.0 le nuove funzioni sono disponibili su entrambe le piattaforme: la scansione dell'intervallo è avviata dall'utente e si ferma con la sintonia manuale o la perdita della connessione; il DSP è leggero (60 s di audio in poche centinaia di ms su desktop).

**Ambito.** Incluso: ascolto (connessione, sintonia, passo, modo, volume, mute, squelch RF con soglia), spettro e waterfall (tocco per sintonizzare, trascinamento, pinch, palette, range dB), utenti connessi live da `user_cb` (con località), preferiti del server (canali DX `mkr`, sola lettura, ricaricati a ogni connessione) e personali (CRUD), registrazione manuale WAV PCM 16 bit mono, impostazioni (server, password, nome, tema scuro/chiaro/sistema, mappe dei ricevitori nel browser, licenza); dalla 1.3.0 anche scansione di un intervallo di frequenze (scheda Preferiti → *Scansione*) ed equalizzatore e filtri DSP (schermata Radio → *Configura*), con le stesse regole del desktop. Escluso, solo desktop: Vosk e ricerca, VAD e voice gate, squelch in registrazione, segmentazione e timer, MP3, storico utenti e archivio SQLite, Telegram e alert QSO, scansione dei canali preferiti, player interno (i file si aprono con le app di sistema).

**Attivazione.** Stesso meccanismo e stesso strumento di firma del desktop (§4.8): formato `KSR1:<base64url(machineId)>:<firma Ed25519>`, payload `KiwiSDRRecorder:license:v1:<machineId>`, stessa chiave pubblica; il License Manager accetta gli ID `GRAB-(?:A-)?…`. Una licenza desktop non attiva Android e viceversa.

- ID dispositivo `GRAB-A-XXXX-XXXX-XXXX-XXXX`: SHA-256 di `KiwiSDRRecorder:machine:android:v1:` + `Settings.Secure.ANDROID_ID` (nessun permesso, non pubblicitario). Se `ANDROID_ID` non è leggibile l'ID è `GRAB-NON-DISPONIBILE` e nessuna licenza è valida.
- `ANDROID_ID` è univoco per dispositivo + utente + **chiave di firma dell'APK**: cambia con ripristino di fabbrica, nuovo profilo utente o cambio di chiave. Per questo tutti i canali di distribuzione devono usare la stessa chiave dell'app (vedi [DISTRIBUZIONE_E_RILASCI.md](./DISTRIBUZIONE_E_RILASCI.md#versione-android)); un APK di debug e uno di release hanno ID diversi.
- Verifica Ed25519 con BouncyCastle (`java.security` non lo garantisce su tutte le API supportate); nessun controllo anti-manomissione né Play Integrity, sproporzionati per un'attivazione gratuita. R8 attivo in release.
- Flusso: Impostazioni → Licenza con ID, *Copia*, *Condividi* (intent avviato dall'utente), codice e *Attiva*; nessuna chiamata di rete automatica.

**Limitazioni senza attivazione.** Tutto ciò che serve ad ascoltare e sintonizzare è libero; si limita ciò che produce valore persistente o occupa risorse a lungo. Nessun periodo di prova, limite di sessione o "nag".

| Funzione | Non attivata | Attivata |
|---|---|---|
| Connessione, sintonia, modo, volume, squelch, spettro e waterfall, utenti, preferiti del server, impostazioni | ✅ | ✅ |
| Preferiti personali: aggiunta, modifica, eliminazione | ❌ (quelli salvati restano sintonizzabili) | ✅ |
| Ascolto con app in background o schermo spento | ❌ la connessione si chiude e libera lo slot del ricevitore; avviso al ritorno in primo piano | ✅ foreground service |
| Registrazione manuale | ❌ | ✅ |
| Scansione di un intervallo di frequenze | ❌ | ✅ (continua in background con il foreground service) |
| Equalizzatore e filtri DSP | ❌ (una configurazione salvata resta inattiva) | ✅ |
| Tema chiaro, mappe dei ricevitori | ✅ | ✅ |

Motivazioni: la registrazione è riservata anche su desktop (confine coerente); l'ascolto in background ha costi reali (batteria, slot del server occupato) ed è la principale differenza d'uso su mobile; senza preferiti personali restano quelli del server, completi. Scansione e DSP sono riservati come sul desktop. Il gate (`GatedFeature`: `BACKGROUND_PLAYBACK`, `MANUAL_RECORDING`, `PERSONAL_FAVORITES`, `FREQUENCY_SCAN`, `AUDIO_DSP`) è applicato nel controller prima di ogni azione; se l'attivazione si perde, i preferiti personali non vengono cancellati.

**Ciclo di vita e risorse.**

- Il controller vive nell'`Application`, non nel service: `KiwiPlaybackService` serve solo a tenere vivo il processo in background quando la sessione è attiva e la licenza lo consente. La sessione sopravvive a rotazioni e cambi di app.
- Socket W/F aperto solo con la schermata Waterfall visibile e l'app in primo piano, sempre con lo stesso token della sessione SND (regola `badp=5`); su rete a consumo velocità del waterfall al minimo. I canali DX si chiedono su SND (`KiwiProtocol.dxMarkers()`), quindi non richiedono il W/F.
- Cambio rete: `NetworkMonitor` anticipa la riconnessione; nessun ritentativo sui rifiuti `badp`. Rimozione dai recenti o *Disconnetti* dalla notifica chiudono la sessione, liberando lo slot del KiwiSDR; solo *Disconnetti* (notifica o app) evita la riconnessione al prossimo avvio. Caduta di rete: registrazione e scansione dell'intervallo riprendono dopo la riconnessione (`ConnectionResumeCoordinator`).
- Registrazione: MediaStore `Recordings/KiwiGrabber` (`Music/KiwiGrabber` su Android 10–11) con `IS_PENDING`; i file interrotti vengono riparati all'avvio; spazio libero controllato all'avvio (50 MB) e durante la scrittura (20 MB); disconnessione o perdita di rete chiudono il file e avvisano l'utente; dopo una caduta di rete la registrazione riparte in un nuovo file alla riconnessione.
- Persistenza: `MobileSettings` in `filesDir/settings.json`, password cifrata con Android Keystore (`aks:`); impostazioni e log sono esclusi dal backup, perché la chiave Keystore non è trasferibile.
- Permessi: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` (Wi-Fi lock), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (chiesto solo con l'app attivata); nessun permesso di storage (`minSdk` 29) né microfono. `usesCleartextTraffic` è necessario perché il protocollo KiwiSDR usa `ws://`.

**Scelte rispetto all'impostazione iniziale.** Non è stato estratto un `KiwiSessionCore` comune: la parte di sessione condivisibile (coordinamento W/F con token SND, ritentativi, `badp`) è già in `WaterfallCoordinator`, mentre registrazione segmentata, VAD, storico e alert restano desktop. Modulo `androidApp` separato da `composeApp`, perché il plugin applicazione Android non va combinato con il plugin KMP nello stesso modulo e la UI mobile è diversa.

---

## 5. Regole implementative

### 5.1 Naming e package

- I package devono riflettere il dominio e non la piattaforma: `app`, `audio`, `kiwi`, `monitor`, `recording`, `waterfall`, `model`.
- I nomi delle classi devono descrivere un ruolo concreto: `KiwiClient`, `AudioRecorder`, `StationMonitor`, `WaterfallClient`.
- I nomi delle funzioni devono descrivere il comportamento e non il dettaglio implementativo.
- I file Kotlin devono seguire un nome coerente con il contenuto del tipo principale.

### 5.2 Separazione delle responsabilità

- i client di protocollo non devono contenere UI
- la UI non deve eseguire logica di business e protocollo
- il database non deve essere acceduto direttamente dal rendering
- la UI non accede ai servizi (`KiwiClient`, `AppSettings`, `AudioRecorder`, …) né a `LicenseManager`: passa dalle API di `KiwiAppController`
- l'audio e la registrazione devono essere separate dall'orchestrazione della sessione

### 5.3 SOLID / Clean Code

- una classe deve avere un unico motivo principale di cambiamento
- le funzioni devono essere brevi e testabili
- i metodi di parsing, networking e conversione devono essere separati dai componenti di orchestrazione
- i parametri di configurazione devono essere espliciti e non nascosti in variabili globali

### 5.4 Dipendenze

- il codice condiviso non deve dipendere da `compose` o da API desktop specifiche
- `shared` può dipendere da modelli e utility cross-platform, non da `java.awt` o `javax.sound` direttamente
- `commonMain` non usa API Java (`Locale`, `String.format`, `System`, `java.*`): il codice JVM condiviso va in `jvmCommonMain`, quello specifico in `desktopMain` / `androidMain`
- `jvmCommonMain` (desktop + Android) può usare `java.nio`, `java.util.concurrent`, `java.security`, `java.time`, ma non `java.net.http`, `javax.*`, `java.awt` né JNA
- in `jvmCommonMain` evitare i metodi covarianti di `java.nio.Buffer` (`flip()`, `position(int)`, …), assenti su Android 10: usare letture/scritture assolute o `ByteBuffer.wrap`
- le dipendenze desktop (`sqlite-jdbc`, `vosk`, `mp3spi`, `jump3r`, `jna`) restano in `desktopMain` e non devono comparire nel grafo Android
- il target Android si verifica con `scripts/android/build.ps1` (test host, lint con `checkDependencies`, APK), perché sul PC desktop senza SDK `jvmCommonMain` viene compilato solo per il desktop
- versioni e coordinate delle dipendenze si dichiarano solo nel version catalog `gradle/libs.versions.toml`

### 5.5 Error handling e logging

- usare `Result`/eccezioni localmente e non diffuse attraverso la UI
- catturare eccezioni in contesti operativi specifici
- i messaggi di errore devono essere leggibili e non generici
- usare `AppLogger` per i casi di diagnosi e persistenza
- non usare `catch` vuoti: un errore ignorato va almeno registrato nel log
- non scrivere segreti nei log (usare `KiwiProtocol.redactSecrets` per i comandi)
- le operazioni di rete non devono bloccare il thread UI

### 5.6 Configurazione

- tutte le preferenze dell'utente vanno persistite in una struttura impostabile e serializzabile
- i valori di default devono essere dichiarati explicitamente
- i server e le impostazioni di rete devono essere validati tramite parser dedicati (`parseServerAddress`)
- le credenziali si salvano solo cifrate tramite `SecretCipher`, mai in chiaro
- i valori predefiniti del ricevitore vengono da `KiwiServerDefaults`, non da stringhe ripetute nel codice

### 5.7 Coroutine e flussi

- i flussi asincroni devono essere osservabili tramite `Flow`
- il codice con callback deve essere limitato a casi già esistenti e mantenuti in modo gestibile
- i job e le coroutine devono essere supervisionati con scope chiari
- il dispatcher dedicato per la manipolazione dei frame audio va mantenuto isolato in modo da evitare race condition

### 5.8 Risorse e filesystem

- le cartelle di output devono essere create in modo deterministico e verificabile
- i file temporanei devono essere puliti o mantenuti in directory esplicite
- le registrazioni devono avere nomi stabili e include un timestamp nella loro convenzione di naming

### 5.9 Documentazione

- ogni interfaccia pubblica e ogni classe architetturale di rilievo deve avere KDoc e descrizione delle responsabilità
- il commento deve spiegare il perché, non solo il cosa
- le regole di architettura devono essere documentate in questo file e in `docs/DEVELOPMENT.md`

### 5.10 Test

- i test devono coprire i moduli critici: protocollo, VAD/Squelch, logging, serializzazione e collegamento dei frame audio
- i test di regressione devono essere aggiunti quando si cambia il protocollo o la gestione del buffer
- la compatibilità tra componenti condivisi e desktop-specifici va verificata in build e test

---

## 6. Linee guida per gli sviluppi futuri

### 6.1 Nuove funzionalità

- tutte le nuove funzionalità applicative devono essere implementate rispettando il principio: `shared` per la logica, `composeApp` per la UI, `desktopMain` per il runtime specifico
- i nuovi servizi di dominio non devono dipendere direttamente dall'UI
- le feature di protocollo e di rete vanno inserite in `kiwi` o in package dedicati di dominio

### 6.2 Nuovi componenti condivisi

- ogni nuovo componente che intende essere riutilizzato fra desktop e mobile deve vivere in `shared/src/commonMain`
- se la fase di sviluppo richiede una piattaforma specifica diversa dal desktop, si aggiunge una target concrete come `androidMain`, non una duplicazione di code paths nel modulo desktop

### 6.3 Funzionalità desktop-specifiche

- i componenti che usano `Java Sound`, filesystem avanzato, gestione Win32, `javax.sound`, `java.awt` o WiX devono rimanere in `desktopMain`
- dovrebbero essere incapsulati in classi ad alto livello che espongano interfacce usabili dal resto dell'app

### 6.4 Funzionalità mobile-specifiche

- la logica mobile va progettata in modo da poter riusare i servizi già condivisi
- un eventuale MP3 su Android va valutato per prestazioni e licenza (`jump3r` è Java puro, ma oggi è solo desktop)
- i service Android e le notifiche di foreground vanno separati dalla business logic core
- il binding UI e di render deve essere testato in modo da mantenere il modulo condiviso indipendente da `compose.desktop`

### 6.5 Nuove dipendenze

- ogni nuova libreria va valutata rispetto a: costo di manutenzione, compatibilità KMP, presenza di alternative native o specifiche per piattaforma
- la dipendenza non va introdotta se il problema è già risolto da un modulo esistente
- ogni dipendenza esterna deve essere documentata in `docs/ARCHITECTURE.md` e nel README

---

## 7. Stato attuale del repository

Il progetto è desktop-first, con un client Android di ascolto e con i principali componenti core implementati e in uso:

- `shared` contiene la logica di protocollo e modelli chiave (`commonMain`), i servizi JVM condivisi (`jvmCommonMain`) e quelli specifici di desktop e Android
- `composeApp` fornisce l'interfaccia grafica desktop
- `KiwiAppController` è la facciata della UI; `WaterfallCoordinator`, `RecordingHistoryCoordinator`, `QsoAlertCoordinator` e `ScanCoordinator` orchestrano rispettivamente waterfall, storico/trascrizioni, notifiche e scansioni (canali e intervallo); `AudioPipeline` instrada i frame audio attraverso DSP e squelch, sopra `KiwiClient`, `WaterfallClient`, `AudioRecorder` e `StationMonitor`
- `androidApp` + `MobileAppController` forniscono il client Android di ascolto, pubblicato dalla 1.2.0 come APK nelle GitHub Releases (§4.9, §4.10)
- la documentazione storica sotto `docs/` è utile come riferimento, ma la struttura architetturale ufficiale del progetto deve essere mantenuta in questo documento

La direzione consigliata è di mantenere la base condivisa come asset riutilizzabile e di evitare duplicazioni tra la UI e i servizi di dominio, senza introdurre accoppiamenti impropri tra codice condiviso e runtime specifici.
