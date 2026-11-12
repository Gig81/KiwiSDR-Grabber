# Architettura del progetto

## 1. Panoramica

KiwiSDR Grabber & Station Recorder è un'applicazione desktop multipiattaforma sviluppata in Kotlin, pensata per connettersi a un ricevitore KiwiSDR, ricevere lo stream audio in tempo reale, monitorare il canale RF e registrare i contenuti audio su disco.

La soluzione è costruita con un'architettura modulare orientata al riutilizzo: la logica applicativa core è contenuta nel modulo `shared`, mentre il rendering e i componenti desktop specifici sono separati nella UI application `composeApp`.

Il progetto è oggi fortemente orientato alla versione Desktop Windows, con l'obiettivo dichiarato di evolvere la base condivisa verso una futura variante mobile. L'architettura non impone un framework applicativo complesso: mantiene un design semplice, reattivo e guidato da flussi di eventi (`Flow`/`Coroutine`) e da servizi dedicati per audio, monitoraggio, registrazione e connessione KiwiSDR.

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
- `docs/`: documentazione architetturale, guide operative e piani per sviluppi futuri
- `build/`, `gradle/`: build system e toolchain

### Flussi operativi principali

1. L'applicazione avvia `KiwiAppController` con tutte le dipendenze concrete.
2. `KiwiClient` si connette via WebSocket al server KiwiSDR e inizializza lo stream audio e i messaggi di controllo.
3. `DesktopAudioPlayer` riceve i frame audio PCM e li rende in output audio locale.
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
- Note: il modulo `shared` usa `commonMain` e `desktopMain`, ma non è ancora stato esteso a una target mobile reale

### 2.2 Compose Multiplatform

- Nome e versione: Compose Multiplatform 1.12.2 (material3 1.9.0, icone Material 1.7.3) + Compose Compiler plugin
- Finalità: interfaccia grafica declarativa desktop
- Motivazione: renderizzazione moderna, meno boilerplate rispetto alla UI Swing/awt tradizionale e compatibilità con il modello declarativo Kotlin
- Ambito: Desktop
- Alternative valutate: UI basata su Swing o classica JavaFX; la scelta è coerente con il modello Kotlin moderno e con la necessità di alta reattività

### 2.3 Networking JDK (`java.net.http`)

- Nome e versione: `java.net.http.HttpClient` / `WebSocket` del JDK 17
- Finalità: WebSocket KiwiSDR (SND e W/F), polling HTTP dello stato stazione e API Telegram
- Nota: i comandi WebSocket passano da `WebSocketCommandQueue`, che li serializza senza bloccare il chiamante. Le dipendenze Ktor, mai usate, sono state rimosse; per un futuro target non JVM andrà scelto un client multipiattaforma.
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

La UI Compose vive in `composeApp/src/desktopMain/kotlin/com/grabber/ui/`. `Main.kt` crea la finestra desktop massimizzata all'avvio (dimensione iniziale 1100×740 dp quando ripristinata, minima 960×640 px), mostra nel titolo la versione Gradle `appVersion` e crea l'istanza di `KiwiAppController`. `App.kt` compone la console radio persistente (`feature/console/RadioConsole`), la barra delle schede (`MainTabBar`, con l'enum `MainTab`) e la scheda attiva. Ogni scheda ha il proprio package `ui/feature/<nome>/` (`console`, `favorites`, `audio`, `recordings`, `spectrum`, `users`, `settings`), diviso in un file per pannello, sezione o dialogo:

- Preferiti (`FavoritesTab`)
- Audio (`AudioTab`)
- Registratore (`RecordingsTab`)
- Spettro & Cascata (`SpectrumTab`)
- Utenti & Monitor (`UsersTab`)
- Impostazioni (`SettingsTab`)

La console rimane montata mentre cambia la scheda. Le schermate accedono all'applicazione solo tramite le API e i `StateFlow` di `KiwiAppController` (unica eccezione: `waterfallBuffer`, letto direttamente dal renderer per prestazioni) e non devono introdurre una seconda sorgente di verità per sintonia, audio, registrazione o waterfall. La vista waterfall compatta e quella estesa riutilizzano lo stesso componente e lo stato gestito dal controller. I token e i componenti Compose condivisi sono in `ui/designsystem/`; la specifica visuale è in `docs/GUI_DESIGN_SYSTEM.md`.

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
- Squelch: quando lo squelch RF e il voice gate sono entrambi attivi, lo stato RF prevale; il VAD non può chiudere un gate RF aperto
- Machine ID: SHA-256 del `MachineGuid` di Windows (registro, vista 64 bit), nel formato `GRAB-XXXX-XXXX-XXXX-XXXX`; non cambia con rete, VPN o rinomina del PC, cambia reinstallando Windows. Se il valore non è leggibile si usa `GRAB-NON-DISPONIBILE`, che nessuna licenza può validare. L'esito della verifica della firma viene memorizzato finché la chiave non cambia.
- Licenza desktop: verifica locale legata al Machine ID e richiesta di attivazione via canale Telegram dedicato. Non esiste un periodo di prova: senza attivazione restano disponibili le funzioni di base (ascolto, sintonia, spettro/waterfall, utenti connessi, preferiti) e le funzioni avanzate sono bloccate. Le chiavi sono firmate offline con Ed25519 dall'app License Manager del repository privato di gestione licenze.

### 3.6 Vincoli verificati del protocollo KiwiSDR

- I client audio/controllo (`/SND`) e waterfall (`/W/F`) usano WebSocket distinti. La socket waterfall si apre solo dopo l'autenticazione SND e riutilizza lo stesso token di sessione; mantenere separati i comandi e i frame dei due flussi.
- Dopo il messaggio `audio_rate`, rispondere con `SET AR OK in=<rate> out=<rate>` prima di avviare lo stream audio.
- Un frame SND ha un header di 10 byte: sequenza a 32 bit little-endian e RSSI a 16 bit big-endian. Il payload PCM inizia all'offset 10; il flag `0x80` indica PCM little-endian, altrimenti i campioni big-endian vanno convertiti prima di inviarli a Java Sound e al registratore. Il valore RSSI in dBm si calcola come `0.1 * smeter - 127.0`.
- Processare i frame audio in ordine FIFO su un dispatcher serializzato; il lavoro concorrente per frame può causare riordino o duplicazione percepibile dell'audio.
- `/status` fornisce conteggi e stato della stazione, non i nominativi degli ascoltatori. I nomi arrivano dal messaggio WebSocket `user_cb`; combinare le due fonti prima di salvare lo storico.

---

## 4. Componenti riutilizzabili

### 4.1 `KiwiClient`

- Package: `com.grabber.kiwi.KiwiClient`
- Responsabilità: connessione WebSocket JDK (un `HttpClient` per istanza), autenticazione, keepalive, riconnessione automatica, invio dei comandi e streaming audio
- API pubblica: `connect`, `disconnect`, `updateServer`, `updatePassword`, `tune`, `setMode`, `setAgc`, `setIdentUser`, `requestDxLabels`, `audioFlow`, `connectionState`, `activeUsers`, `serverDxLabels`
- Dipendenze: `KiwiProtocol`, `KiwiMessageParser`, `WebSocketCommandQueue`, `KiwiTokenGenerator`, `AppLogger`, `ModulationMode`
- Riutilizzabilità: core del sistema; riveste il protocollo KiwiSDR e deve rimanere condiviso tra tutte le piattaforme future

### 4.1.1 `KiwiProtocol`, `KiwiMessageParser`, `WebSocketCommandQueue`

- `KiwiProtocol` (`commonMain`, Kotlin puro): costruisce i comandi testuali. Password e nominativo sono codificati come `encodeURIComponent` (il server li decodifica con `kiwi_str_decode_inplace`); `redactSecrets` oscura la password per i log; la frequenza è formattata con tre decimali senza dipendere dalla lingua di sistema.
- `KiwiMessageParser` (senza stato e senza I/O): decodifica `user_cb` (JSON), `load_dxcfg` (anche URL-encoded) e l'header dei frame `SND`.
- `WebSocketCommandQueue`: serializza invii e chiusura sul WebSocket del JDK, che rifiuta un nuovo invio prima del completamento del precedente; un invio fallito non blocca i successivi.
- `KiwiServerDefaults`: host e porta del ricevitore predefinito, unica fonte per tutti i client e per `DEFAULT_SERVER_ADDRESS`.

### 4.2 `WaterfallClient`

- Package: `com.grabber.waterfall.WaterfallClient`
- Responsabilità: apertura della WebSocket JDK dedicata `/W/F` dopo l'autenticazione SND, autenticazione e comandi di waterfall, decodifica frame FFT, gestione zoom e centro frequenza
- Dipendenze: `KiwiProtocol`, `WebSocketCommandQueue`, `AppLogger`, `ConnectionState`
- Lifecycle: lo stream viene avviato da `WaterfallCoordinator` solo dopo il `badp=0` di autenticazione SND, resta attivo cambiando schermata e viene chiuso durante disconnessione/riconnessione. La configurazione W/F parte dopo il proprio `badp=0`; i rifiuti permanenti (`badp`) generano una notifica e non vengono ritentati automaticamente.
- Protocollo: audio/controllo (`/SND`) e waterfall (`/W/F`) sono socket distinti, ma devono condividere lo stesso token/timestamp nell'URL per essere associate alla medesima sessione Kiwi. Il client web ufficiale apre la seconda socket dopo l'autenticazione principale e ripete il flusso di autenticazione per W/F. Token distinti fanno rifiutare W/F con `badp=5` come connessione multipla dallo stesso IP. Evitare di inoltrare i frame o i comandi W/F sulla socket SND.
- Riutilizzabilità: condivisibile in una futura UI mobile o in una sua interfaccia testuale; la parte rendering grafico è separata dal protocollo e va mantenuta indipendente dalla piattaforma

### 4.3 `DesktopAudioPlayer`

- Package: `com.grabber.audio.DesktopAudioPlayer`
- Responsabilità: output locale del flusso audio con Java Sound, volume e livello RMS
- Dipendenze: `AudioFrame`
- Riutilizzabilità: parziale; la logica di base del player audio è portabile, ma l'implementazione Java Sound è fortemente desktop-only

### 4.4 `AudioRecorder`

- Package: `com.grabber.recording.AudioRecorder`
- Responsabilità: registrazione audio continua o segmentata, file MP3/WAV, callback di completamento
- Dipendenze: `AudioFrame`, `LameEncoder`, filesystem, Java IO
- Riutilizzabilità: il modello di registrazione è condivisibile; la serializzazione MP3 e la gestione del filesystem sono specifiche del desktop

### 4.5 `HistoryDatabase`

- Package: `com.grabber.monitor.HistoryDatabase`
- Responsabilità: persistenza di dati storici su SQLite, migrazione delle colonne (`ensureColumn`) e della posizione del file (`relocateLegacyDatabase`)
- Dipendenze: SQLite JDBC
- Riutilizzabilità: condivisibile tramite abstraction se in futuro verranno introdotti altri backend o una layer di persistence platform-agnostic

### 4.6 `KiwiAppController` e coordinatori

- Package: `com.grabber.app`
- `KiwiAppController`: facciata unica della UI. Collega client, pipeline audio, registrazione, squelch/VAD, preferiti, impostazioni e licenza; i servizi sono privati e la UI usa solo le sue API e i suoi `StateFlow`.
- `WaterfallCoordinator`: socket W/F aperto dopo l'autenticazione SND, ritentativi sulle interruzioni temporanee (non sui rifiuti `badp`), palette, zoom, range dBm, inseguimento del VFO.
- `RecordingHistoryCoordinator`: storico di registrazioni e utenti, ricerca full-text, coda di trascrizione offline; senza licenza lo storico risulta vuoto.
- `QsoAlertCoordinator`: notifiche Telegram di inizio/fine QSO e, a trascrizione conclusa, parole chiave con audio e testo allegati; rispetta il gate di licenza e l'intervallo minimo tra messaggi.
- Riutilizzabilità: livello di orchestrazione desktop; in un'architettura futura i coordinatori possono diventare `UseCase` o `ViewModel` separati.

### 4.7 `AppSettings` e `SecretCipher`

- Package: `com.grabber.app`
- Responsabilità: persistenza atomica della configurazione, migrazione dei preferiti predefiniti, cifratura delle credenziali tramite `SecretCipher` (`DpapiSecretCipher` su Windows, `PlainTextSecretCipher` altrove)
- Dipendenze: JSON serialization, filesystem, JNA Platform (DPAPI)
- Riutilizzabilità: condivisibile in un ambiente desktop o mobile, a patto che backing storage e cifratura siano adattati alla piattaforma

### 4.8 `LicenseManager` e `MachineIdUtil`

- Package: `com.grabber.license`
- Responsabilità: verifica locale delle licenze `KSR1` firmate Ed25519 e calcolo del Machine ID dal `MachineGuid` di Windows
- Dipendenze: JDK (`java.security`), JNA Platform (registro di Windows)

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
- `commonMain` non usa API Java (`Locale`, `String.format`, `System`, `java.*`): il codice JVM va in `desktopMain`
- versioni e coordinate delle dipendenze si dichiarano solo nel version catalog `gradle/libs.versions.toml`
- nella futura evoluzione verso Android, i servizi portabili andranno in `commonMain`, mentre le implementazioni platform-specific andranno in `desktopMain` / `androidMain`

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

- la logica mobile va progettata in modo da poter reusarare i servizi già condivisi
- i service Android e le notifiche di foreground vanno separati dalla business logic core
- il binding UI e di render deve essere testato in modo da mantenere il modulo condiviso indipendente da `compose.desktop`

### 6.5 Nuove dipendenze

- ogni nuova libreria va valutata rispetto a: costo di manutenzione, compatibilità KMP, presenza di alternative native o specifiche per piattaforma
- la dipendenza non va introdotta se il problema è già risolto da un modulo esistente
- ogni dipendenza esterna deve essere documentata in `docs/ARCHITECTURE.md` e nel README

---

## 7. Stato attuale del repository

Il progetto attuale è in uno stato di sviluppo desktop-first e già funzionale, con i principali componenti core implementati e in uso:

- `shared` contiene la logica di protocollo e modelli chiave
- `composeApp` fornisce l'interfaccia grafica desktop
- `KiwiAppController` è la facciata della UI; `WaterfallCoordinator`, `RecordingHistoryCoordinator` e `QsoAlertCoordinator` orchestrano rispettivamente waterfall, storico/trascrizioni e notifiche, sopra `KiwiClient`, `WaterfallClient`, `AudioRecorder` e `StationMonitor`
- la documentazione storica sotto `docs/` è utile come riferimento, ma la struttura architetturale ufficiale del progetto deve essere mantenuta in questo documento

La direzione consigliata è di mantenere la base condivisa come asset riutilizzabile e di evitare duplicazioni tra la UI e i servizi di dominio. Qualsiasi futura espansione verso Android deve partire da questa separazione, senza introdurre accoppiamenti impropri tra codice condiviso e runtime specifici.
