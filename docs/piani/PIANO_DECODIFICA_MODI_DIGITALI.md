# Piano di sviluppo: decodifica dei modi digitali

Piano di implementazione della richiesta n. 7 di [RICHIESTE_FUNZIONALITA.md](./RICHIESTE_FUNZIONALITA.md) ("Decodifica dei modi digitali"), secondo la **soluzione b** (decoder integrati nel server KiwiSDR, estensioni `/EXT`), con in più:

- decodifica **locale** nell'app di **FSK (RTTY)** e **NAVTEX** (sul server KiwiSDR questi due modi sono decodificati dal JavaScript del browser, non dal server);
- una nuova scheda **"Modi digitali"** tra "Utenti & Monitor" e "Impostazioni avanzate", con l'elenco dei modi a sinistra e i dettagli del modo scelto a destra;
- uno studio di fattibilità, con proposta di implementazione, del **riconoscimento automatico del modo** dal segnale audio;
- uno studio di fattibilità, con proposta di implementazione, dell'**autocentraggio del segnale** (AFC), attivabile con un selettore on/off nella console in alto a destra, sotto i controlli esistenti.

Stato: **piano approvato, implementazione non ancora avviata** (24 novembre 2026). Le decisioni prese con l'utente sono in [§10](#10-decisioni-prese); lo sviluppo procede in autonomia fase dopo fase, con un resoconto a fine fase (sezione "Stato dell'implementazione", da aggiungere qui sotto all'avvio). Commit e rilascio li esegue l'utente: a fine fase il resoconto indica i punti di commit e, alla fine, i comandi di rilascio. Tutto è predisposto per un **rilascio unico 1.4.0**.

Indice:

1. [Fonti e verifiche già fatte](#1-fonti-e-verifiche-già-fatte)
2. [Regole comuni](#2-regole-comuni)
3. [Ambito e modi supportati](#3-ambito-e-modi-supportati)
4. [Architettura](#4-architettura)
5. [Interfaccia: scheda "Modi digitali" e selettore AFC](#5-interfaccia-scheda-modi-digitali-e-selettore-afc)
6. [Fattibilità: riconoscimento automatico del modo](#6-fattibilità-riconoscimento-automatico-del-modo)
7. [Fattibilità: autocentraggio del segnale (AFC)](#7-fattibilità-autocentraggio-del-segnale-afc)
8. [Fasi di sviluppo](#8-fasi-di-sviluppo)
9. [Rischi](#9-rischi)
10. [Decisioni prese](#10-decisioni-prese)

---

## 1. Fonti e verifiche già fatte

Protocollo e comportamento sono stati letti nei sorgenti di [jks-prv/KiwiSDR](https://github.com/jks-prv/KiwiSDR) (versione v1.902, novembre 2026) e di [jks-prv/kiwiclient](https://github.com/jks-prv/kiwiclient). Come per SND e W/F, il protocollo si **reimplementa senza copiare codice**.

### 1.1 WebSocket `/EXT`

- URL `ws://<host>:<port>/<token>/EXT`, con lo **stesso token** della connessione SND. Il server accetta `/EXT` solo se esiste già una connessione SND con quel token e con un canale assegnato (`rx_server.cpp`); un secondo `/EXT` con lo stesso token viene rifiutato.
- Tutti i messaggi del server sono frame **binari** con un prefisso di 3 byte:
  - `MSG k=v k=v …` e `EXT k=v …`: testo separato da spazi. I valori inviati con `ext_send_msg_encoded` sono percent-encoded (decodifica percentuale semplice, `+` resta `+`).
  - `DAT ` (4 byte) + 1 byte di comando + payload a lunghezza fissa per estensione, senza header di lunghezza né sequenza.
- Sequenza:
  1. Il server invia `MSG version_maj=… version_min=…`, poi `MSG ext_client_init=<is_locked>`. Con `is_locked=1` un altro canale usa DRM e **le estensioni non vanno avviate**.
  2. Il client invia `SET auth t=kiwi p=…` (`KiwiProtocol.auth`), poi `SET ident_user=…`.
  3. Il client invia `SET ext_switch_to_client=<NOME> first_time=1 rx_chan=<n>` (il server ignora `rx_chan` e usa il canale della SND abbinata). Il server risponde `MSG EXT-STOP-FLUSH-INPUT` e l'estensione `EXT ready…`.
  4. Comandi dell'estensione (`SET ft8_start=0`, `SET cw_start=…`, ecc.).
  5. Keepalive: `SET keepalive` periodico (timeout lato server 60 s), oppure `SET ext_no_keepalive` affidandosi ai keepalive della SND (scelta di kiwiclient). Proposta: `SET keepalive` ogni 10 s, come difesa in più.
  6. Chiusura: prima il comando di stop dell'estensione (es. `SET ft8_close`), poi `SET ext_blur=<ch>` e chiusura del socket. `ext_blur` **non** chiama la chiusura dell'estensione: senza lo stop esplicito il decoder resta attivo fino allo scadere del keepalive.
- Nome sconosciuto: il server lo scrive solo nel suo log e non risponde. Serve quindi un timeout lato client sull'`EXT ready`.
- Elenco delle estensioni installate: arriva su **W/F** come `MSG extint_list_json=<JSON codificato>` (`rx_waterfall.cpp`), oggi ignorato da `WaterfallClient`. Le abilitazioni dell'amministratore (`<nome>.enable`) sono applicate solo dal menu del browser: l'app deve rispettarle comunque, leggendole dalla configurazione (`load_cfg`, oggi scartato da `KiwiClient`).
- `/EXT` cade insieme alla SND. Va riaperto dopo la riconnessione, come il waterfall.

### 1.2 Sintonia e audio delle estensioni

- La sintonia **resta sulla SND**: il JavaScript delle estensioni chiama `ext_tune()`/`ext_set_mode()`, che inviano su SND il normale `SET mod=… low_cut=… high_cut=… freq=…`. Eccezioni: ALE_2G e HFDL accettano anche `SET tune=` su EXT.
- I decoder del server leggono i campioni del **DSP del canale**, quindi modo e banda passante impostati dall'app **contano**. FT8, CW, CW_skimmer, FAX e SSTV usano audio reale dopo il rivelatore e l'AGC; WSPR usa IQ dopo la banda passante; HFDL IQ.
- In modo `cw` il browser mostra come frequenza di "dial" la portante + il centro della banda passante: portante = dial − (low+high)/2. Oggi l'app invia in CW `freq` = frequenza mostrata con banda 300–700 Hz, quindi il tono si sente a 500 Hz ma la frequenza mostrata è quella della portante, non quella del segnale. **Decisione D5:** l'app si allinea al browser (frequenza mostrata = frequenza del segnale CW); la conversione dial ↔ portante sta in `KiwiProtocol.tune` (commonMain) e vale per sintonia manuale, preferiti, scansione, DX label e decoder, quindi anche su Android (codice condiviso: comportamento coerente sulle due piattaforme). Effetto: a parità di numero salvato, la ricezione CW si sposta di 500 Hz. Le etichette DX del server e le frequenze pubblicate sono già frequenze di segnale e diventano corrette; i preferiti CW salvati dall'utente in passato (frequenze di portante) restano numericamente invariati, senza migrazione automatica, e il cambiamento va spiegato nelle note di rilascio 1.4.0.
- FT8 e SSTV richiedono il canale a **12 kHz** (la frequenza negoziata in `audio_rate`). Sui ricevitori configurati a 20,25 kHz vanno mostrati come non disponibili.

### 1.3 Limiti per i client non-browser

- L'app si collega con `ws://host:port/<token>/SND` (senza `/kiwi/`), che il server tratta come **kiwirecorder** (`isKrec`). Da IP non locali queste connessioni sono limitate dall'impostazione di amministrazione `ext_api_nchans`:
  - `0`: rifiutate tutte;
  - altrimenti rifiutate quando `n_krec >= ext_api_nchans`, dove `n_krec` conta **tutte** le connessioni krec (SND + W/F + EXT). Una sessione completa ne usa quindi **3**: `/EXT` può essere rifiutato anche se SND e W/F sono già attive;
  - il rifiuto è una chiusura del socket senza messaggio;
  - possibile anomalia da verificare: con il valore predefinito `-1` ("mai impostato") il controllo `n_krec >= -1` rifiuterebbe tutto.
- Dopo 10 s una connessione da un IP che ha scaricato meno di 3 file web viene marcata come "app non Kiwi" e può essere espulsa se le app superano `ext_api_nchans`. Il limite vale già oggi.
- L'app deve trasformare questi rifiuti in messaggi chiari ("il ricevitore non consente decoder a client esterni") **senza** influire su SND e W/F e senza riprovare a vuoto.

### 1.4 Estensioni: comandi e risposte verificati

| Estensione (nome EXT) | Sintonia richiesta | Avvio / arresto | Risultati |
|---|---|---|---|
| `FT8` (FT8 e FT4) | `usb`, 100–3100 Hz, dial; canale 12 kHz | `SET ft8_start=<0 FT8 / 1 FT4> debug=0`; `SET ft8_protocol=`, `SET ft8_clear`; stop `SET ft8_close` | `EXT chars=<enc>`: intestazione + righe `HH:MM:SS ±SNR ±dT Hz [km] [età] msg`, con **codici ANSI** e tag `<a>` da ripulire |
| `wspr` (minuscolo) | `usb`, dial = cf − BFO (750), banda BFO±150 | `SET BFO=750`, `SET dialfreq=… centerfreq=… cf_offset=…`, `SET capture=1`; stop `SET capture=0` | `EXT WSPR_DECODED=<enc>` (HTML), `WSPR_STATUS`, `WSPR_PEAKS`; `WSPR_UPLOAD` da **non** inoltrare a wsprnet; `DAT` cmd 0 = riga waterfall (1 byte marker + 411 bin) |
| `CW_decoder` | `cw` | `SET cw_start=<training>`, `SET cw_pboff=<Hz>`, `SET cw_wpm=<wpm>,<train>`, `SET cw_auto_thresh=`, `SET cw_threshold=`, `SET cw_wsc=`; stop `SET cw_stop` | `EXT cw_chars=<enc>`, `cw_wpm=`, `cw_train=`, `cw_plot=dB,sig,thresh` |
| `CW_skimmer` | banda corrente (tutti i segnali CW) | `SET cws_start`, `SET cws_params=<pwr>,<neighbors>`; stop `SET cws_stop` | `EXT cw_chars=<enc "<car><Hz>,<wpm>">`, un carattere per volta per segnale |
| `FAX` | `usb` a frequenza pubblicata − 1,9 kHz (`lsb` +1,9), 1300–2500 Hz | `SET fax_start lpm=<60…240> phasing=0/1 autostop=0/1 debug=0`, `SET fax_shift=`; stop `SET fax_stop` | `DAT` cmd 254 = riga di 1024 byte in scala di grigi, cmd 255 = pulisci; `EXT fax_phased`, `fax_autostopped` |
| `SSTV` | `usb` (`lsb` sotto 10 MHz), 1000–2500 Hz; canale 12 kHz | `SET start`, `SET stop`, `SET reset`, `SET auto`/`SET noadj=` | `EXT img_size=w,h`, `new_img`, `mode_name`, `status`, `fsk_id`; `DAT` cmd 0/1 + SNR + w,h u16 BE + riga RGB |
| `HFDL` | `iq`, 300–2600 Hz | `SET start`, `SET stop` | `EXT chars=<enc>` (testo dumphfdl), `lowres_latlon` |
| `ALE_2G` | `usb`, 600–2650 Hz | `SET start`, `SET stop`, `SET tune=<kHz>,<scan>` | `EXT chars=<enc>`, `active=`, `event` |
| `NAVTEX`, `FSK` | `cw`, banda cf ± (shift/2 + margine) | — (decodifica nel browser) | — |
| `DRM`, `FreeDV` | — | — | esclusi ([§3.2](#32-esclusi)) |

### 1.5 Decoder FSK e NAVTEX del browser (riferimento algoritmico)

Algoritmo usato dal client web (file `FSK/JNX.js`, `CCIR476.js`, `FSK_async.js`), utile come riferimento funzionale:

- due filtri passa-banda biquad sui toni mark e space (mark = tono più alto), differenza dei moduli normalizzata sulla media del segnale, passa-basso a 140 Hz; il segno dà il bit; sotto una soglia di livello → "nessun segnale";
- sincronizzazione di bit integrate-and-dump su `sr/baud` campioni, con recupero del clock da un istogramma degli attraversamenti dello zero;
- macchina a stati di sincronismo dei caratteri (4 caratteri validi consecutivi per agganciare, risincronizzazione dopo più di 2 errori);
- ITA2 variante US-TTY; CCIR476 a 7 bit con esattamente 4 "mark"; SITOR-B in FEC: ogni carattere trasmesso due volte (fase RX e, tre caratteri dopo, fase DX), con scelta della copia valida.

Parametri: NAVTEX 518/490/4209,5 kHz, modo `cw`, cf 500 Hz, shift 170 Hz, 100 baud, CCIR476 non invertito; RTTY radioamatori 170 Hz / 45,45 baud / 5N1.5 ITA2; meteo DWD 450 Hz / 50 baud / ITA2 invertito.

**Uso dei file del client web (decisione del 24/11/2026):** i file JavaScript del client web (`FSK/*.js`, `NAVTEX.js`, `CCIR476.js`, `FSK_async.js`, `JNX.js`, `DSC.js`, i `*.js` delle estensioni server) sono il **riferimento di verifica**. Si analizzano per dedurne logiche, parametri e casi limite (soglie, sincronismo, FEC, gestione degli errori, sintonia per modo) e si usano per correggere e migliorare la nostra implementazione, confrontando il comportamento sugli stessi segnali.

**Licenze:** `JNX.js`, `CCIR476.js`, `FSK_async.js`, `BiQuadraticFilter.js` sono GPL-2.0-or-later (Paul Lutus); `FSK.js`, `NAVTEX.js`, `DSC.js` sono di John Seamons senza licenza esplicita nel file (repository "misto GPL/MIT"). Per questo il codice **non si copia né si traduce riga per riga**: si scrive in Kotlin una implementazione propria, con struttura nostra, a partire dalle logiche dedotte, dalle specifiche pubbliche (ITU-R M.476/M.625 per SITOR, ITU-R M.540 per NAVTEX, ITA2) e da tecniche DSP standard. Le idee e i parametri non sono protetti; lo è l'espressione del codice.

### 1.6 Riconoscimento del modo e AFC nel server

- Il server **non** offre AFC né centraggio automatico (FSK e NAVTEX inclusi).
- Il riconoscimento RSID (di fldigi) esiste nell'estensione `digi_modes`, ma è nascosto in modalità sviluppatore e attivo solo con un'opzione di compilazione: **non è utilizzabile**.
- Funzioni automatiche esistenti nei singoli decoder: velocità e soglia automatiche del CW, rilevamento del modo VIS e correzione dell'inclinazione in SSTV, fasatura e arresto automatico del FAX, scansione con arresto su attività in ALE. FT8 e WSPR cercano già tutti i segnali nella banda passante, quindi non hanno bisogno di centraggio.

---

## 2. Regole comuni

Valgono per ogni fase (da `CLAUDE.md` e `docs/ARCHITECTURE.md` §5):

- La UI desktop usa solo API e `StateFlow` di `KiwiAppController`. Nessun accesso diretto a `KiwiClient`, al nuovo `ExtensionClient`, ai decoder o ad `AppSettings`.
- `commonMain` senza API `java.*`; `jvmCommonMain` senza `java.net.http`, `javax.*`, `java.awt`, JNA e metodi covarianti di `java.nio.Buffer` (così la logica resta riutilizzabile su Android).
- Tutti gli invii WebSocket passano da `WebSocketCommandQueue`; nessun comando `/EXT` passa su SND o W/F, tranne la **sintonia**, che per protocollo va su SND (§1.2).
- I frame audio restano FIFO sul dispatcher serializzato: i decoder locali e l'analisi spettrale ricevono una **copia** del frame e lavorano su un proprio dispatcher seriale, senza rallentare la riproduzione.
- Le funzioni riservate alla licenza si bloccano nel controller, non solo nella UI.
- Nuove preferenze in `SettingsData` con default espliciti, compatibili con i `settings.json` esistenti.
- Nessun `catch` vuoto; log con `AppLogger` e `KiwiProtocol.redactSecrets` per i comandi.
- Nessuna nuova dipendenza prevista (FFT, filtri e decoder scritti in Kotlin; PNG con `javax.imageio` in `desktopMain`). Se servisse, va in `gradle/libs.versions.toml` e nei documenti di terze parti.
- Testi UI, KDoc e documentazione in italiano (guida utente e README anche in inglese).
- Commit: li esegue l'utente; il piano indica solo i punti di consegna.

**Verifica standard a fine fase:**

```powershell
.\gradlew.bat :composeApp:compileKotlinDesktop :composeApp:desktopTest :shared:desktopTest
```

Le fasi che toccano `jvmCommonMain` richiedono anche il controllo Android (compilazione di `shared` per Android, anche se l'app Android non usa ancora la funzione):

```powershell
.\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
```

---

## 3. Ambito e modi supportati

### 3.1 Inclusi

| Modo (elenco a sinistra) | Dove si decodifica | Uscita | Fase |
|---|---|---|---|
| FT8 | Server (`FT8`, protocollo 0) | Tabella di messaggi | 3 |
| FT4 | Server (`FT8`, protocollo 1) | Tabella di messaggi | 3 |
| WSPR | Server (`wspr`) | Tabella di spot | 3 |
| CW | Server (`CW_decoder`) | Testo continuo + WPM | 3 |
| CW Skimmer | Server (`CW_skimmer`) | Testo per segnale (frequenza audio) | 3 |
| Fax meteo (HF-FAX) | Server (`FAX`) | Immagine in scala di grigi | 4 |
| SSTV | Server (`SSTV`) | Immagine a colori + nome del modo | 4 |
| RTTY / FSK | **Locale** | Testo continuo | 5 |
| NAVTEX | **Locale** | Messaggi strutturati (ZCZC … NNNN) | 5 |
| HFDL | Server (`HFDL`) | Testo (messaggi aeronautici) | 8 |
| ALE 2G | Server (`ALE_2G`) | Testo eventi | 8 |
| DSC | Locale | Testo (solo informativo) | 8 |

In più: **notifiche Telegram** su messaggi decodificati che contengono parole chiave dedicate (fase 8).

### 3.2 Esclusi

- **DRM**: blocca le estensioni su tutto il ricevitore, restituisce audio decodificato sulla SND e richiede decodifica audio HE-AAC/xHE-AAC. Fuori ambito.
- **FreeDV**: nascosto in modalità sviluppatore, JavaScript "All rights reserved" e dipendente da un servizio esterno. Non realizzabile.
- **PSK31 e altri modi di fldigi**: non esiste un'estensione server; resterebbero solo da decodificare localmente (soluzione c). Fuori ambito; il riconoscimento automatico li può però **segnalare** (§6).
- Upload verso PSKReporter / wsprnet: l'app **non** carica spot. L'upload FT8 del server, se configurato dall'amministratore, avviene comunque lato server con il nominativo del ricevitore.

### 3.3 Piattaforme

Solo **desktop Windows** in questa richiesta (decisione D2). Protocollo, parser, decoder locali, analisi spettrale e coordinatori stanno in `jvmCommonMain`, così una futura scheda Android li può riusare senza riscrittura. Unico effetto su Android in questo sviluppo: l'allineamento della sintonia CW (D5) se la conversione passa da codice condiviso, da verificare con il controllo Android.

---

## 4. Architettura

### 4.1 Nuovi componenti

```text
shared/commonMain
  com.grabber.kiwi.ExtProtocol                comandi /EXT (switch, blur, keepalive) e comandi per estensione
  com.grabber.model.digital.DigitalMode       enum dei modi: id, nome, sorgente (SERVER/LOCALE), tipo di uscita
  com.grabber.model.digital.DigitalModeCatalog  frequenze note (FT8, WSPR, NAVTEX, fax DWD…), sintonia consigliata
  com.grabber.model.digital.DigitalTuning     modo, banda (low/high), conversione dial ↔ portante
  com.grabber.model.digital.DecodedItem       messaggi decodificati (testo, riga FT8/WSPR, messaggio NAVTEX, riga immagine)

shared/jvmCommonMain
  com.grabber.kiwi.ext.ExtensionClient        WebSocket /EXT (stile WaterfallClient): connessione, handshake, keepalive, frame MSG/EXT/DAT
  com.grabber.kiwi.ext.ExtMessageParser       decodifica percentuale, rimozione ANSI/HTML, parsing DAT
  com.grabber.decoder.server.*                adattatori per estensione: Ft8Session, WsprSession, CwSession, CwSkimmerSession,
                                              FaxSession, SstvSession (comandi di avvio/arresto, parsing dei risultati)
  com.grabber.decoder.fsk.*                   FskDemodulator, BitSynchronizer, Ita2Decoder, Ccir476Decoder (SITOR-B FEC),
                                              NavtexMessageAssembler, FskSignalGenerator (solo test)
  com.grabber.audio.analysis.*                AudioSpectrumAnalyzer (FFT a finestra), SignalCenterEstimator (AFC),
                                              ModeClassifier (riconoscimento)
  com.grabber.app.DigitalDecoderCoordinator   un decoder alla volta: sintonia, avvio/arresto, riapertura dopo riconnessione
  com.grabber.app.AutoCenterCoordinator       AFC one-shot dopo la sintonia dell'utente

shared/desktopMain
  KiwiAppController                            nuove API e StateFlow (§4.4)
  com.grabber.decoder.DecodedOutputStore       salvataggio log testuali e immagini PNG

composeApp (ui/feature/digital/)
  DigitalModesTab, DigitalModeList, DigitalModeDetail, DecoderTextOutput, DecoderTableOutput (FT8/WSPR),
  DecoderImageOutput (FAX/SSTV), NavtexMessageList, FskParametersPanel, DecoderStatusChip
composeApp (ui/feature/console/)
  riga AFC in ConsoleAudioPanel
```

### 4.2 Flussi

```text
                    ┌─────────── SND (KiwiClient) ── audio ─┬─▶ AudioPipeline (DSP, squelch, player, recorder)
 KiwiAppController ─┤                                        └─▶ tap analisi (copia del frame grezzo)
                    │                                                ├─▶ decoder locale FSK/NAVTEX
                    │                                                ├─▶ AutoCenterCoordinator (AFC)
                    │                                                └─▶ ModeClassifier (riconoscimento)
                    ├─────────── W/F (WaterfallClient) ── spettro ─▶ AFC grossolano, elenco estensioni
                    └─────────── EXT (ExtensionClient) ── risultati ─▶ decoder server
```

- **Tap di analisi:** `AudioPipeline` riceve un terzo sink opzionale `analysisSink`, chiamato con il frame **originale** (prima del DSP e dello squelch: i decoder non devono vedere filtri utente né silenziamenti). Il sink copia il frame in un `Channel` con capacità limitata, consumato da un dispatcher seriale dedicato. Se nessun consumatore è attivo il sink non fa nulla (costo nullo). In caso di saturazione si scarta il frame più vecchio e si segnala un buco al decoder (che risincronizza), con un `AppLogger.warn` limitato nel tempo.
- **Un decoder alla volta:** `DigitalDecoderCoordinator` tiene lo stato `DecoderState` (`Idle`, `Starting`, `Running`, `Error(motivo)`, `Unavailable(motivo)`) e il modo attivo. Avviare un altro modo ferma quello corrente. Un solo `/EXT` per sessione.
- **Sintonia:** avviando un modo l'utente può scegliere "Sintonizza per il modo": il coordinatore applica modo e banda richiesti con `applyTuning` (senza fermare il decoder). Una sintonia manuale successiva lascia attivo il decoder; se modo o banda non sono più compatibili il pannello mostra un avviso ("Banda passante non adatta a FAX: ripristina") invece di fermare il decoder.
- **Scansione:** avviare un decoder ferma la scansione; avviare una scansione ferma il decoder (stesso principio di `tune()`).
- **Riconnessione:** dopo la caduta della SND, il coordinatore riapre `/EXT` all'autenticazione (`badp=0`) e rinvia il comando di avvio, come `WaterfallCoordinator`. Un rifiuto (`ext_client_init=1`, socket rifiutato, `EXT ready` mancante entro 5 s) porta a `Unavailable` senza nuovi tentativi automatici.

### 4.3 Modifiche ai componenti esistenti

- `KiwiProtocol.tune` supporta già `lowCut`/`highCut`; `KiwiClient.tune`/`setMode` vanno estesi con la banda passante e un nuovo `StateFlow` `currentPassband`, oltre a `audioSampleRate` (dalla risposta `audio_rate`) e `assignedChannel`.
- `KiwiClient`: leggere da `load_cfg` le abilitazioni delle estensioni (oggi scartato) solo se necessario per rispettare `<nome>.enable` (verifica in fase 0).
- `WaterfallClient`: interpretare `MSG extint_list_json=` ed esporre l'elenco delle estensioni installate.
- `AudioPipeline`: sink di analisi (§4.2), coperto da test.
- `MainTab`: nuova voce `DIGITAL("Modi digitali", icona)` tra `USERS` e `SETTINGS`; `App.kt` la mostra.
- `ConsoleAudioPanel`: riga AFC (§5.3).
- `VfoDisplayCard`: nuovi indicatori `DEC` (decoder attivo) e `AFC` (correzione appena applicata).
- `SettingsData`: `autoCenterEnabled = false`, `lastDigitalMode`, parametri per modo (`fskParams`, `navtexFilter`, `faxLpm`, `faxAutostop`, `cwWpm`, `ft8Protocol`…), tutti con default.

### 4.4 API del controller

```kotlin
// Stato
val digitalModes: StateFlow<List<DigitalModeAvailability>>   // modo + disponibile/motivo (estensione assente, 12 kHz, DRM…)
val digitalDecoderState: StateFlow<DecoderState>
val decodedItems: StateFlow<List<DecodedItem>>              // buffer limitato (es. 2000 righe) del modo attivo
val decodedImage: StateFlow<DecodedImage?>                  // FAX/SSTV: righe accumulate
val modeSuggestion: StateFlow<ModeSuggestion?>              // riconoscimento automatico (§6)
val autoCenterEnabled: StateFlow<Boolean>
val lastAutoCenter: StateFlow<AutoCenterResult?>            // es. +120 Hz alle 12:03:10

// Azioni
fun startDigitalDecoder(mode: DigitalMode, tuneForMode: Boolean, presetKhz: Double? = null)
fun stopDigitalDecoder()
fun updateDigitalModeParams(params: DigitalModeParams)
fun clearDecodedOutput()
fun saveDecodedOutput(): String?                           // percorso del file salvato
fun setAutoCenterEnabled(enabled: Boolean)
fun setModeRecognitionEnabled(enabled: Boolean)
```

### 4.5 Dati e file

- Testi e messaggi decodificati restano in memoria (buffer circolare per modo attivo) e si salvano su richiesta in `~/KiwiGrabberRecordings/Decodifiche/<modo>_<AAAAMMGG_hhmmss>.txt`; immagini FAX/SSTV in PNG nella stessa cartella. Proposta: salvataggio automatico facoltativo delle immagini completate (FAX con `fax_autostopped`, SSTV a fine immagine).
- Nessuna nuova tabella SQLite in questa richiesta (decisione D4).

---

## 5. Interfaccia: scheda "Modi digitali" e selettore AFC

### 5.1 Struttura della scheda

```text
┌───────────────────────── Modi digitali ───────────────────────────────────────────────────┐
│ ┌─ Modi ──────────────┐ ┌─ NAVTEX ─────────────────────────────── [Locale] ● In ascolto ─┐ │
│ │ Suggerito: NAVTEX ⓘ │ │ Frequenze: [518 kHz ▾] [Sintonizza]   Filtro: stazioni, tipi    │ │
│ │ ─ Sul server ─      │ │ Parametri: shift 170 Hz · 100 baud · SITOR-B (fisso)            │ │
│ │  FT8           ●    │ │ [▶ Avvia] [■ Ferma] [Pulisci] [Salva]   Livello ▮▮▮▯ Errori 2%  │ │
│ │  FT4                │ ├────────────────────────────────────────────────────────────────┤ │
│ │  WSPR               │ │ 12:01 ZCZC OA12  CULLERCOATS  NAV WARNING …         [espandi]  │ │
│ │  CW                 │ │ 11:47 ZCZC IE45  …                                              │ │
│ │  CW Skimmer         │ │                                                                │ │
│ │  Fax meteo          │ │                                                                │ │
│ │  SSTV   (non disp.) │ │                                                                │ │
│ │ ─ Nell'app ─        │ │                                                                │ │
│ │  RTTY / FSK         │ │                                                                │ │
│ │  NAVTEX        ◉    │ │                                                                │ │
│ └─────────────────────┘ └────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Colonna sinistra** (larghezza fissa ~220 dp): modi raggruppati per sorgente ("Sul server", "Nell'app"); per ognuno stato (attivo, non disponibile con tooltip del motivo, bloccato dalla licenza). In cima la riga **"Suggerito"** del riconoscimento automatico (§6), con un interruttore per attivarlo.
- **Colonna destra**, in tre blocchi:
  1. **Intestazione:** nome, sorgente (chip "Server"/"Locale"), stato (`DecoderStatusChip`), requisiti (12 kHz, estensione abilitata, modo/banda richiesti, avviso di incompatibilità con la sintonia corrente).
  2. **Impostazioni del modo:** frequenze note dal catalogo con "Sintonizza", parametri specifici (tabella sotto), comandi Avvia/Ferma/Pulisci/Salva, indicatori (livello, WPM, errori, tempo al prossimo slot FT8/WSPR).
  3. **Uscita:** testo monospazio con scorrimento automatico (pausa se l'utente scorre), tabella ordinabile (FT8/WSPR), elenco messaggi espandibili (NAVTEX) o immagine con zoom e salvataggio (FAX/SSTV).
- Messaggi di stato e limiti spiegati in italiano: "Il ricevitore non consente decoder a client esterni (limite connessioni app)", "Estensione non installata o disabilitata dall'amministratore", "Un altro utente usa DRM: decoder del server sospesi", "Richiede un ricevitore in modalità 12 kHz".
- Colori solo da `GrabberColors`; l'immagine e il grafico di livello usano `DisplayColors` (display strumentali, scuri in ogni tema). Layout coerente con `UsersTab` (due pannelli) e `docs/GUI_DESIGN_SYSTEM.md`.

### 5.2 Parametri per modo

| Modo | Parametri esposti |
|---|---|
| FT8 / FT4 | Frequenze di banda (dial standard), ordinamento per frequenza, filtro "solo CQ" lato client |
| WSPR | Frequenze di banda; BFO fisso 750 Hz; nessun upload |
| CW | WPM automatico o fisso, soglia automatica o manuale, correzione spazi tra parole; pitch = centro banda |
| CW Skimmer | Sensibilità, filtro segnali vicini |
| Fax meteo | Frequenze (DWD, NOAA…), LPM (120 predefinito), fasatura, arresto automatico, correzione inclinazione |
| SSTV | Correzione automatica inclinazione, reset, nome modo/ID FSK in sola lettura |
| RTTY / FSK | Preset (Radioamatori 170/45,45; Meteo DWD 450/50; Personalizzato), shift, baud, inversione, frame (5N1, 5N1.5, 5N2), cf audio |
| NAVTEX | Frequenza (518, 490, 4209,5 kHz), filtro per stazione (B1) e tipo di messaggio (B2), scarto dei duplicati per ID messaggio |

### 5.3 Selettore AFC nella console

Nel `ConsoleAudioPanel` (pannello in alto a destra con S-meter, VOL, SQL, VAD), **sotto la riga di telemetria VAD**, una nuova riga compatta:

```text
 AFC  [●──]  Centraggio automatico            +120 Hz ✓
```

- Interruttore on/off (`autoCenterEnabled`, persistente, predefinito **off**), etichetta "AFC" con tooltip che spiega quando agisce (§7.3).
- A destra l'esito dell'ultima correzione (offset applicato, "nessun segnale", "fuori portata"), che si attenua dopo qualche secondo.
- Il pannello ha altezza fissa: va verificato che la riga entri senza tagliare gli slider alla dimensione minima della finestra (960×640); se non entra, si riducono le spaziature verticali (5 dp → 4 dp) o la telemetria VAD e la riga AFC si affiancano.

---

## 6. Fattibilità: riconoscimento automatico del modo

### 6.1 Conclusione

**Fattibile in forma assistita**: l'app propone il modo più probabile con un livello di confidenza e l'utente lo avvia con un clic. Un avvio completamente automatico è sconsigliato come predefinito (falsi positivi, decoder del server che occupano risorse del ricevitore), ma si offre come opzione (decisione D3): con l'opzione attiva l'app avvia da sola **solo i decoder locali** (RTTY/FSK, NAVTEX, DSC) quando la confidenza è ≥ 90 %; i decoder del server si avviano sempre a mano. Il server non offre nulla di utilizzabile (RSID non disponibile, §1.6), quindi l'analisi si fa nell'app sull'audio SND, eventualmente aiutata dallo spettro W/F.

### 6.2 Limiti di base

- Si analizza solo ciò che passa nella **banda passante** corrente (al massimo circa ±5 kHz a 12 kHz di campionamento). In CW (300–700 Hz) si vede pochissimo: per riconoscere serve una banda larga (USB 100–3100 Hz). Il riconoscimento propone quindi anche la sintonia di analisi ("Allarga la banda per analizzare").
- L'audio è già demodulato e passato dall'AGC del server: i livelli assoluti non sono affidabili, le caratteristiche vanno normalizzate.
- Servono **alcuni secondi** di segnale (FSK 2–4 s; FT8 un ciclo di 15 s; WSPR fino a 2 minuti).

### 6.3 Metodo proposto (euristico, senza apprendimento automatico)

1. **Spettro medio e picchi:** FFT a 4096 punti (≈3 Hz di risoluzione a 12 kHz) con media esponenziale; rilevamento dei picchi stretti sopra il rumore mediano.
2. **Caratteristiche estratte:**
   - numero di toni discreti e loro spaziatura (2 toni a 170/450/850 Hz → FSK; 8 toni a 6,25 Hz su ~50 Hz → FT8; 4 toni a 1,46 Hz su ~6 Hz → WSPR; 4 toni a 20,8 Hz → FT4);
   - occupazione di banda (CW < 100 Hz; PSK31 ~60 Hz con portante unica modulata in fase; FAX 1500–2300 Hz continuo; voce SSB 300–2700 Hz con struttura formantica);
   - inviluppo on/off di un tono singolo e durate dei segmenti (CW, con stima dei WPM);
   - velocità di simbolo dall'autocorrelazione della differenza mark−space (FSK: 45,45, 50, 75, 100 baud);
   - sincronismo temporale: raffiche allineate ai 15 s UTC (FT8), 7,5 s (FT4), 2 min (WSPR);
   - firme specifiche: intestazione VIS SSTV (1900 Hz, pausa 1200 Hz, bit 1100/1300 Hz); toni di avvio/arresto FAX (300/450 Hz) e righe a 2 Hz (120 LPM);
   - voce: riuso del VAD esistente (`HfSpectralVad`) per escludere la fonia.
3. **Contesto di frequenza:** `DigitalModeCatalog` contiene le allocazioni note (dial FT8/FT4/WSPR per banda, NAVTEX 490/518/4209,5 kHz, fax DWD 3855/7880/13882,5 kHz, RTTY DWD, ecc.). La frequenza aumenta o riduce la confidenza ma non basta da sola.
4. **Decisione:** punteggio per modo (regole pesate), soglia minima, isteresi (il suggerimento cambia solo se un altro modo resta in testa per N finestre). Uscita `ModeSuggestion(mode, confidence, paramsStimati, sintoniaConsigliata)`, per esempio "RTTY 450 Hz / 50 baud, invertito — 82%".
5. **Conferma con il decoder (facoltativa):** per FSK e NAVTEX il decoder locale può girare "in prova" per qualche secondo; se produce caratteri validi la confidenza sale, altrimenti il suggerimento decade.

### 6.4 Affidabilità attesa

| Modo | Riconoscibilità | Note |
|---|---|---|
| RTTY / FSK, NAVTEX | Alta | Shift e baud misurabili; NAVTEX in più dalla frequenza |
| CW | Alta | Tono singolo on/off; sovrapposizioni di più segnali riducono l'affidabilità |
| FAX | Alta | Banda 1500–2300 Hz continua, righe periodiche |
| SSTV | Media-alta | Certa con l'intestazione VIS; a trasmissione iniziata solo dallo spettro |
| FT8 / FT4 / WSPR | Media (alta con la frequenza) | Segnali deboli, spesso sotto il rumore: frequenza + sincronismo temporale |
| PSK31 | Media | Solo segnalato (nessun decoder) |
| Voce SSB/AM | Alta come "non digitale" | VAD esistente |

Va misurata in fase 7 su un corpus di registrazioni reali (WAV registrati con l'app, metadati frequenza/modo) e su segnali sintetici generati nei test.

---

## 7. Fattibilità: autocentraggio del segnale (AFC)

### 7.1 Conclusione

**Fattibile** come correzione **one-shot** dopo una sintonia dell'utente (clic sul waterfall/spettro o frequenza digitata), con limiti chiari per modo. Un inseguimento continuo è sconsigliato: con più segnali vicini o fading l'app "salterebbe" tra segnali e contraddirebbe l'utente. Si propone un inseguimento continuo **solo** dentro i decoder locali FSK/NAVTEX, che spostano il proprio centro audio senza risintonizzare il ricevitore.

### 7.2 Problema chiave e soluzione: due sorgenti di misura

L'audio SND contiene solo ciò che passa nella banda passante: in CW (300–700 Hz) un segnale spostato di 300 Hz **non si sente** e non è misurabile dall'audio. Si usano quindi due sorgenti:

- **Spettro W/F (stima grossolana, ampia):** il frame W/F più recente copre la finestra intorno alla frequenza cliccata. È utile solo se la risoluzione è adeguata: a zoom 10 un bin vale ~29 Hz, a zoom 12 ~7 Hz, a zoom ≤ 8 oltre 100 Hz (troppo per CW). Si cerca il picco più vicino al clic entro una finestra dipendente dal modo.
- **Audio SND (stima fine):** FFT sull'audio dopo l'assestamento; precisione di pochi hertz, ma solo entro la banda passante.

Strategia: se il W/F è abbastanza risolto e trova un picco → correzione grossolana, poi rifinitura audio; altrimenti solo audio, se il segnale è già in banda.

### 7.3 Comportamento per modo

| Modo corrente | Riferimento | Finestra di ricerca | Fonte |
|---|---|---|---|
| CW | Tono al centro banda (500 Hz) | ±1 kHz | W/F + audio |
| AM | Portante | ±5 kHz (raster 1/5/9/10 kHz opzionale) | W/F (la portante non c'è nell'audio demodulato AM) |
| NFM | Baricentro del segnale | ±6 kHz | W/F |
| USB/LSB voce | — | — | **Non applicato**: senza portante non esiste un riferimento affidabile (spiegato nel tooltip) |
| Decoder FSK/NAVTEX attivo | Punto medio mark/space = cf del decoder | ±shift | Audio (il ricevitore viene risintonizzato) |
| Decoder FAX attivo | 1900 Hz | ±300 Hz | Audio |
| FT8/FT4/WSPR/CW Skimmer attivo | — | — | Non applicato: decodificano tutta la banda |

### 7.4 Algoritmo one-shot

1. Evento (decisione del 24/11/2026): **solo** frequenza digitata dall'utente e clic/trascinamento sul waterfall o sullo spettro (`tuneFromWaterfall()`). **Non** su preferiti, bande, catalogo dei modi, `stepFrequency()` (passo fine voluto dall'utente), scansione, ripresa dopo riconnessione o correzioni AFC stesse (niente cicli). L'interruttore AFC è **spento** alla prima installazione e si attiva solo manualmente.
2. Annulla un'eventuale misura in corso; attende l'**assestamento**: i frame audio con la frequenza vecchia continuano ad arrivare per la latenza del server (da misurare in fase 0, stimata 300–800 ms); si scartano per tempo e, se possibile, per variazione del contatore di sequenza.
3. Accumula 0,5–1 s di spettro (audio e/o W/F).
4. Cerca il picco nella finestra del modo; richiede SNR ≥ 10 dB sul rumore mediano e un picco "stretto" coerente con il modo (CW < 150 Hz). Con più candidati sceglie il più vicino alla frequenza richiesta, non il più forte.
5. Se l'offset è oltre una soglia minima (es. 15 Hz in CW, 100 Hz in AM) applica la correzione con `applyTuning` interno, arrotondata a 1 Hz (10 Hz in AM/NFM).
6. Pubblica `AutoCenterResult` (offset, fonte, esito) per l'indicatore in console e nel VFO; registra nel log a livello `DEBUG`.
7. Al massimo un secondo passaggio di rifinitura audio; poi si ferma fino alla prossima sintonia dell'utente.

### 7.5 Interazioni

- Scansione: AFC non agisce sulle frequenze della scansione (prima versione).
- Squelch/registrazione: la correzione chiama gli stessi reset di `applyTuning` (squelch e filtri DSP); un segmento in registrazione non viene diviso.
- Waterfall con "segui sintonia" attivo: segue anche la correzione.
- Licenza (D1): l'AFC è riservato. Senza licenza l'interruttore in console è disattivato con tooltip "richiede l'attivazione"; il controller ignora comunque l'AFC se la licenza non è valida, anche con la preferenza salvata accesa.

---

## 8. Fasi di sviluppo

Ogni fase si chiude con la verifica standard (§2) e la propria "definizione di fatto". Le fasi 3, 4 e 5 sono indipendenti tra loro dopo la 1 e la 2.

### Fase 0 — Verifiche su ricevitori reali (spike)

Obiettivo: eliminare le incertezze prima di scrivere codice di produzione. Ricevitore: **quello predefinito dell'app** (`KiwiServerDefaults` / impostazioni), senza accesso di amministrazione; per i casi non riproducibili (es. `ext_api_nchans` basso) si usano ricevitori pubblici e i sorgenti. Nessuna installazione sul PC: le prove si fanno con un piccolo programma di prova Kotlin eseguito da Gradle (fuori dai test automatici, non distribuito) che apre SND + `/EXT`, invia i comandi e salva le tracce, oppure con l'app in log `TRACE`.

- [ ] `/EXT` aperto dall'app su un ricevitore pubblico e su uno locale: handshake, `ext_client_init`, `EXT ready`, effetto di `ext_api_nchans` (SND + W/F + EXT = 3 connessioni krec; comportamento con il valore predefinito `-1`).
- [ ] Tracce reali di `EXT chars` (FT8), `WSPR_DECODED`, `cw_chars`, `DAT` FAX e SSTV, salvate come **fixture di test** (solo dati di protocollo, nessun codice KiwiSDR).
- [ ] Come leggere le abilitazioni `<nome>.enable` (`load_cfg` su SND) e `extint_list_json` (W/F).
- [ ] Latenza tra `SET freq` e audio alla nuova frequenza (per l'assestamento AFC).
- [ ] Frequenza di campionamento effettiva (`audio_rate`) sui ricevitori in modalità 12 kHz e 20,25 kHz.
- [ ] Comportamento dell'attuale sintonia CW rispetto al browser (dial vs portante, D5).

Consegna: sezione "Esiti della fase 0" aggiunta a questo piano; eventuali modifiche di ambito.

### Fase 1 — Infrastruttura `/EXT`

- [ ] `ExtProtocol` (commonMain) con test in `KiwiProtocolTests`/nuovo `ExtProtocolTests`.
- [ ] `ExtensionClient` (jvmCommonMain) sul modello di `WaterfallClient`: token SND, handshake, timeout su `EXT ready`, keepalive, chiusura ordinata (stop + `ext_blur`), frame `MSG`/`EXT`/`DAT` su dispatcher seriale, errori su `connectionErrors`.
- [ ] `ExtMessageParser`: decodifica percentuale, rimozione ANSI e HTML, parsing `DAT`.
- [ ] `KiwiClient`: banda passante nella sintonia, `currentPassband`, `audioSampleRate`, `assignedChannel`.
- [ ] `WaterfallClient`: `extint_list_json`.
- [ ] `DigitalDecoderCoordinator` con stato, un decoder alla volta, riapertura dopo riconnessione, interazione con la scansione.
- [ ] API del controller (§4.4) e gate di licenza.
- [ ] Test: parser (fixture della fase 0), `ExtensionClient` con connettore finto (sequenza dei comandi, `ext_client_init=1`, rifiuto del socket, timeout, chiusura), coordinatore (cambio modo, riconnessione, scansione).

### Fase 2 — Scheda "Modi digitali"

- [ ] `MainTab.DIGITAL` tra Utenti & Monitor e Impostazioni avanzate; package `ui/feature/digital/`.
- [ ] Colonna sinistra con disponibilità per modo e motivi; colonna destra con intestazione, impostazioni e uscita (componenti generici testo/tabella/immagine).
- [ ] Indicatore `DEC` nel VFO.
- [ ] Overlay di licenza (`FeatureLockedOverlay`) se la funzione è riservata.
- [ ] `docs/GUI_DESIGN_SYSTEM.md`: nuova scheda.

### Fase 3 — Decoder del server a testo: FT8, FT4, WSPR, CW, CW Skimmer

- [ ] Sessioni per estensione con comandi di avvio/arresto e parametri (§1.4, §5.2).
- [ ] Parsing in righe strutturate: FT8/FT4 (UTC, SNR, dT, Hz, messaggio, nominativo e locatore estratti), WSPR (ora, SNR, dt, frequenza, drift, nominativo, locatore, potenza), CW (testo + WPM), Skimmer (testo per frequenza audio → frequenza assoluta).
- [ ] Sintonia per modo dal catalogo (dial FT8/FT4/WSPR per banda; WSPR con BFO 750 Hz e `dialfreq`/`centerfreq`).
- [ ] Clic su una riga FT8/WSPR: copia del messaggio; clic su un segnale dello Skimmer: sintonia CW su quel segnale.
- [ ] Mai inoltrare `WSPR_UPLOAD`.
- [ ] Test con fixture reali.

### Fase 4 — Decoder del server a immagini: FAX e SSTV

- [ ] Accumulo delle righe `DAT` in un'immagine (FAX 1024 px in scala di grigi, SSTV RGB con dimensioni da `img_size`), con limite di altezza e nuova immagine su `new_img`/`CLEAR`.
- [ ] Visualizzazione con zoom, salvataggio PNG (`DecodedOutputStore`, `javax.imageio` in desktopMain), salvataggio automatico facoltativo.
- [ ] Parametri FAX (LPM, fasatura, arresto automatico, inclinazione) e SSTV (auto/manuale).
- [ ] Test sul parsing `DAT` e sull'accumulo delle righe.

### Fase 5 — Decoder locali: RTTY/FSK e NAVTEX

Scritti da zero dalle specifiche (§1.5), in `com.grabber.decoder.fsk` (jvmCommonMain).

- [ ] Tap di analisi in `AudioPipeline` (§4.2) con test che verificano che riproduzione e registrazione non cambino.
- [ ] `FskDemodulator`: filtri mark/space (riuso di `Biquad` del DSP), discriminatore normalizzato, passa-basso, rilevamento assenza di segnale; AFC interno del centro audio entro ±shift/2.
- [ ] `BitSynchronizer`: integrazione per bit e recupero del clock; frame asincroni 5N1/5N1.5/5N2.
- [ ] `Ita2Decoder` (LTRS/FIGS, US-TTY e variante ITA2 internazionale).
- [ ] `Ccir476Decoder` sincrono (7 bit, 4 mark), fasatura, FEC SITOR-B con le due copie e indicazione degli errori.
- [ ] `NavtexMessageAssembler`: `ZCZC B1B2B3B4 … NNNN`, filtri per stazione e tipo, scarto dei duplicati per ID, timestamp di ricezione.
- [ ] Sintonia: modo `cw` con banda cf ± (shift/2 + margine), come il client web (es. NAVTEX 518 kHz: `low_cut=365 high_cut=635`, portante 517,500 kHz).
- [ ] Indicatori: livello, sincronismo, percentuale di errori, scope mark/space semplificato.
- [ ] Test con `FskSignalGenerator` (solo test): testo noto ITA2 a varie velocità, shift e inversioni; rumore bianco a SNR decrescente; offset di frequenza; SITOR-B con errori iniettati sulla copia DX o RX; messaggi NAVTEX completi e duplicati. Test anche come Android host test (jvmCommonTest).
- [ ] Prova con registrazioni WAV reali del NAVTEX 518 kHz e della RTTY meteo DWD.

### Fase 6 — Analisi spettrale e autocentraggio (AFC)

- [ ] `AudioSpectrumAnalyzer` (FFT radix-2 in Kotlin, finestra di Hann, media esponenziale), condiviso con la fase 7; attivo solo quando serve.
- [ ] `SignalCenterEstimator`: picco nella finestra, SNR, larghezza, interpolazione parabolica; fonti audio e W/F (§7.2).
- [ ] `AutoCenterCoordinator` (§7.4) con eventi di sintonia distinti (`USER_TYPED`, `WATERFALL_CLICK`, `PRESET`, `STEP`, `SCAN`, `AFC`) per decidere quando agire.
- [ ] Riga AFC nel `ConsoleAudioPanel` (§5.3), indicatore `AFC` nel VFO, preferenza `autoCenterEnabled`.
- [ ] Test: toni sintetici con offset noti e rumore, due segnali vicini (sceglie il più vicino), assenza di segnale (nessuna correzione), modi esclusi (USB voce), nessun ciclo AFC→AFC, annullamento su nuova sintonia.

### Fase 7 — Riconoscimento automatico del modo

- [ ] `ModeClassifier` con le caratteristiche di §6.3 e il contesto da `DigitalModeCatalog`.
- [ ] Riga "Suggerito" nella colonna sinistra, interruttore di attivazione, pulsanti "Sintonizza per analizzare" e "Avvia decoder"; avvio automatico opzionale solo sopra una soglia alta ([§10](#10-decisioni-prese), D3).
- [ ] Prova in background del decoder FSK/NAVTEX per confermare.
- [ ] Test con segnali sintetici (FSK, CW, FT8-like 8-FSK, WSPR-like 4-FSK, FAX, intestazione VIS, rumore, voce sintetica) e misura su un corpus reale annotato; obiettivo indicativo ≥ 90 % di riconoscimenti corretti su FSK/NAVTEX/CW/FAX e < 5 % di falsi positivi su rumore e voce.

### Fase 8 — HFDL, ALE 2G, DSC e notifiche Telegram

Incluse nello sviluppo (decisione D6/D7).

- [ ] **HFDL** (server): sintonia `iq` 300–2600 Hz, `SET display_freq=`, messaggi dumphfdl multiriga raggruppati per messaggio (Uplink/Downlink), posizione `lowres_latlon` mostrata come testo; catalogo delle frequenze HFDL principali.
- [ ] **ALE 2G** (server): sintonia `usb` 600–2650 Hz, eventi `chars` con data/ora e frequenza, indicatore `active`; la scansione lato server (`SET tune=…,1`) **non** si usa nella prima versione, perché risintonizza la SND senza passare dal controller.
- [ ] **DSC** (locale): riuso del demodulatore FSK (cf 500 Hz in `cw`, 170 Hz, 100 baud, invertito), simboli a 10 bit (7 informativi + 3 di controllo), sincronismo sui pattern di fasatura DX/RX, decodifica di formato, indirizzo, categoria e MMSI; avviso fisso "solo informativo, non per la sicurezza della vita in mare". Frequenze 2187,5 / 4207,5 / 6312 / 8414,5 / 12577 / 16804,5 kHz.
- [ ] **Notifiche Telegram:** nuovo elenco di **parole chiave dedicate ai decoder**, separato da quello delle trascrizioni, in Impostazioni avanzate → Telegram; vale per tutti i modi testuali (NAVTEX, RTTY, CW, CW Skimmer, FT8/FT4, WSPR, HFDL, ALE, DSC). Riuso di `KeywordMatcher` e del servizio Telegram esistente tramite `QsoAlertCoordinator` o un nuovo `DecoderAlertCoordinator`; messaggio con modo, frequenza, ora UTC e testo; limite di invio (es. al massimo 1 notifica ogni 30 s per parola chiave e nessun duplicato dello stesso messaggio NAVTEX). Riservato alla licenza come gli altri avvisi.
- [ ] Test: parser HFDL/ALE con fixture, DSC con segnali sintetici, regole di notifica (corrispondenza, limite, duplicati).

### Fase 9 — Documentazione, collaudo e rilascio

- [ ] `docs/USER_GUIDE.md` (italiano e inglese): scheda Modi digitali, limiti dei ricevitori (`ext_api_nchans`, 12 kHz, DRM), AFC e riconoscimento.
- [ ] `docs/ARCHITECTURE.md`: §3.6 vincoli `/EXT` verificati, §4 nuovi componenti, shell e schede.
- [ ] `docs/DEVELOPMENT.md`: criteri di collaudo manuale (sotto).
- [ ] `README.md`: funzionalità principali (italiano e inglese).
- [ ] `docs/piani/RICHIESTE_FUNZIONALITA.md`: richiesta 7 segnata come implementata (soluzione b).
- [ ] `docs/RELEASE_NOTES_1.4.0.md` in bozza durante lo sviluppo (ogni fase aggiunge la propria voce, secondo `docs/RELEASE_TEMPLATE.md`), incluso il cambiamento della sintonia CW (D5); preparazione di `appVersion` 1.4.0 e di `docs/VERSION_HISTORY.md`. Commit, tag e pubblicazione li fa l'utente: a fine sviluppo indico i comandi (skill `windows-release`).
- [ ] `docs/THIRD_PARTY_NOTICES.md`: nota sul protocollo KiwiSDR reimplementato (nessun codice incluso).

**Collaudo manuale proposto:**

1. FT8 su 14074 kHz di giorno: decodifiche entro 30 s, coerenti con quelle del client web sullo stesso ricevitore.
2. WSPR su 10138,7 kHz: almeno uno spot in 10 minuti.
3. CW su un segnale di beacon o contest: testo leggibile, WPM coerente.
4. Fax DWD 7880 kHz: immagine completa con arresto automatico, PNG salvato.
5. SSTV su 14230 kHz (quando attivo): immagine e nome del modo.
6. NAVTEX 518 kHz (ricevitore europeo, orari delle stazioni): messaggi completi, duplicati scartati, confronto con il decoder del client web.
7. RTTY DWD (es. 10100,8 kHz, 450 Hz/50 baud): testo meteo leggibile.
8. AFC: clic a 200 Hz da un segnale CW → centrato a ±10 Hz; frequenza digitata 1 kHz sopra una portante AM → centrata; USB voce → nessuna correzione; interruttore off → nessuna correzione.
9. Riconoscimento: suggerimenti corretti sui casi 4, 6, 7 e su un segnale CW; nessun suggerimento su voce e rumore.
10. Rifiuti: ricevitore con `ext_api_nchans` basso → messaggio chiaro, audio e waterfall non interrotti; caduta di rete → decoder ripreso dopo la riconnessione.
11. Prestazioni: nessun peggioramento dell'audio (assenza di buchi) con decoder locale + AFC + riconoscimento attivi; CPU sotto controllo.
12. HFDL (es. 8942 / 11384 kHz) e ALE 2G: messaggi ed eventi leggibili e confrontati con il client web.
13. DSC su 2187,5 o 8414,5 kHz: chiamate decodificate con MMSI e categoria, avviso "solo informativo" visibile.
14. Telegram: parola chiave presente in un messaggio NAVTEX o RTTY → una sola notifica, nessun duplicato, rispetto del limite di invio.
15. Sintonia CW (D5): stessa frequenza digitata nell'app e nel client web → stesso tono a 500 Hz; etichette DX CW centrate.
16. Licenza: senza attivazione la scheda è bloccata, l'interruttore AFC disattivato e il controller rifiuta l'avvio dei decoder e AFC.

---

## 9. Rischi

| Rischio | Impatto | Mitigazione |
|---|---|---|
| `ext_api_nchans` basso o zero sui ricevitori pubblici: `/EXT` rifiutato (la sessione usa già 2 connessioni krec) | Decoder del server non disponibili su molti ricevitori | Verifica in fase 0; messaggio chiaro; i decoder locali (FSK/NAVTEX/DSC) funzionano comunque; pulsante "Libera una connessione" che, solo su richiesta dell'utente, chiude il waterfall e riprova l'`/EXT` (mai automatico); il waterfall si riapre con il pulsante esistente |
| Formati dei messaggi diversi tra versioni del server | Parsing errato | Fixture reali, parser tolleranti, versione del server da `MSG version_maj/min` nel log |
| Espulsione come "app non Kiwi" dopo 10 s | Caduta della sessione | Già presente oggi; nessun comportamento nuovo che aumenti il rischio (niente `SET MARKER` diversi, niente impronte note) |
| Decoder locali poco robusti con fading e QRM | Testo con errori | AFC interno, FEC SITOR-B, test a SNR decrescente, confronto con il client web |
| Carico CPU di FFT e decoder | Audio a scatti | Dispatcher dedicato, analisi solo se richiesta, FFT 4096 a cadenza limitata, misura in collaudo |
| Falsi positivi del riconoscimento e dell'AFC | Risintonie indesiderate | Suggerimento invece di azione, soglie e isteresi, AFC one-shot e disattivato per USB/LSB voce, predefinito off |
| Licenze del codice KiwiSDR | Obblighi GPL | Solo reimplementazione da specifiche; nessun porting dei file JavaScript (§1.5) |
| Ingombro della console | Riga AFC tagliata | Verifica alla dimensione minima, layout alternativo (§5.3) |

---

## 10. Decisioni prese

Decisioni prese con l'utente il 24 novembre 2026.

| # | Tema | Decisione |
|---|---|---|
| D1 | Licenza | **Tutto riservato**: decoder (server e locali), riconoscimento automatico, autocentraggio (AFC) e notifiche Telegram dei decoder richiedono l'attivazione. Blocco nel controller; in UI `FeatureLockedOverlay` sulla scheda e interruttore AFC disattivato con tooltip |
| D2 | Android | Solo desktop in questo sviluppo; logica in `jvmCommonMain` pronta per un rilascio Android successivo. Su Android cambia solo la sintonia CW (D5), perché il codice è condiviso |
| D3 | Riconoscimento | Suggerimento predefinito; opzione di avvio automatico solo per i decoder locali con confidenza ≥ 90 % |
| D4 | Messaggi decodificati | In memoria + salvataggio su richiesta (TXT/PNG) in `~/KiwiGrabberRecordings/Decodifiche/`, con salvataggio automatico opzionale delle immagini FAX/SSTV; nessuno storico SQLite |
| D5 | Sintonia CW | Allineata al client web: frequenza mostrata = frequenza del segnale (portante + centro banda), annunciata nelle note di rilascio |
| D6 | Modi facoltativi | Inclusi HFDL, ALE 2G e DSC (fase 8) |
| D7 | Notifiche Telegram | Incluse, con parole chiave dedicate ai decoder valide per tutti i modi testuali (fase 8) |
| D8 | Limite connessioni `/EXT` | Messaggio + pulsante "Libera una connessione" (chiude il waterfall e riprova), mai automatico |
| D9 | Attivazione AFC | Interruttore spento al primo avvio, attivato solo manualmente; agisce solo dopo una frequenza digitata o un clic/trascinamento sul waterfall o sullo spettro |
| D10 | File del client web | Riferimento di verifica: se ne deducono logiche e parametri per correggere e migliorare l'implementazione, senza copiare o tradurre il codice (§1.5) |
| D11 | Ricevitore di prova | Quello predefinito dell'app per la fase 0; ricevitori pubblici per i casi che il predefinito non permette |
| D12 | Avanzamento | Tutte le fasi di seguito in autonomia, con resoconto a fine fase in questo piano (verifiche eseguite, punti di commit) |
| D13 | Rilascio | Rilascio unico **1.4.0** predisposto (note di rilascio, versione, documentazione); commit, tag e pubblicazione li esegue l'utente, a cui vanno indicati i comandi |
