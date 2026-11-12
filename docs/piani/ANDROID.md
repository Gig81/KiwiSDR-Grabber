# Piano di sviluppo — versione Android

## 1. Stato e obiettivo

La versione Android non è ancora implementata: `shared` e `composeApp` espongono solo il target JVM `desktop`, non esistono sorgenti `androidMain` né pacchetti APK/AAB.

L'obiettivo è un **client di ascolto mobile, interattivo e attento ai consumi**, con un sottoinsieme ridotto delle funzioni desktop. Non è una versione "monitor continuo": niente automazioni, niente elaborazioni pesanti.

Questo documento sostituisce gli appunti iniziali e definisce ambito, architettura, strategia di attivazione, fasi di lavoro e criteri di verifica.

---

## 2. Ambito funzionale

### 2.1 Funzioni incluse

| # | Funzione | Dettaglio per Android | Riferimento desktop |
|---|----------|-----------------------|---------------------|
| F1 | **Ascolto in tempo reale** | Connessione/disconnessione, sintonia (frequenza, passo, modo), **volume** e mute, **squelch RF** (on/off + soglia). Ascolto anche a schermo spento tramite Foreground Service. | `KiwiClient`, `DesktopAudioPlayer`, `SquelchController` |
| F2 | **Spettro e waterfall** | Vista combinata; tocco per sintonizzare, pinch/pulsanti per lo zoom, scelta palette e range dB essenziali. Sospesa quando l'app non è visibile. | `WaterfallClient`, `WaterfallBuffer`, `WaterfallPalette` |
| F3 | **Utenti connessi** | Elenco in tempo reale (nominativo, frequenza, modo, località, tempo di connessione) da `user_cb`. Solo vista live, nessuno storico. | `KiwiMessageParser` (user_cb) |
| F4 | **Canali preferiti** | Due sorgenti: **canali del server** (etichette DX del KiwiSDR, sola lettura, aggiornate a ogni connessione) e **canali personali** (aggiungi canale corrente, modifica, elimina). Tocco = sintonia. | `requestDxLabels`, `syncFavoritesFromServer`, `FavoriteFrequency` |
| F5 | **Registrazione manuale** | Solo avvio/arresto manuale, file unico continuo. Formato WAV PCM 16 bit mono. Salvataggio in MediaStore. | `AudioRecorder` (solo scrittura WAV) |
| F6 | **Impostazioni** | Indirizzo server (host:porta), password del ricevitore, sezione Licenza (ID dispositivo, codice di attivazione), informazioni app. | `ServerSettingsSection`, `LicenseSettingsSection` |

### 2.2 Funzioni escluse

Esplicitamente fuori ambito per Android (restano solo desktop):

- trascrizione offline Vosk e ricerca full-text;
- VAD (in ascolto e in registrazione) e voice-gate;
- squelch applicato alla registrazione, segmentazione a intervalli, timer di registrazione;
- formato MP3 (valutabile in futuro, vedi §9);
- storico utenti e archivio registrazioni con database SQLite;
- notifiche Telegram e alert QSO/keyword;
- pannello Audio/DSP avanzato (soft-mute, pre-buffer, hang time restano ai valori predefiniti);
- player interno delle registrazioni (i file si aprono con le app di sistema tramite MediaStore).

---

## 3. Analisi del codice esistente

### 3.1 Riutilizzabilità dei componenti

Tutto il codice di servizio è oggi in `shared/src/desktopMain` e usa API JVM. Android esegue bytecode JVM ma **non** include `java.net.http`, `javax.sound`, `java.awt`/Swing né JNA per Windows.

| Componente | API bloccanti su Android | Azione |
|------------|--------------------------|--------|
| `KiwiProtocol`, `KiwiServerDefaults`, `model/*` | nessuna (già `commonMain`) | riuso diretto |
| `KiwiMessageParser` | nessuna (`ByteBuffer`, `URLDecoder` disponibili) | spostare in source set JVM condiviso |
| `KiwiTokenGenerator` | da verificare | spostare in source set JVM condiviso |
| `WebSocketCommandQueue` | `java.net.http.WebSocket` | astrarre sul trasporto (§4.3) |
| `KiwiClient`, `WaterfallClient` | `java.net.http.HttpClient/WebSocket` | astrarre sul trasporto (§4.3) |
| `WaterfallBuffer`, `WaterfallPalette`, `WaterfallFrame` | nessuna | spostare in source set JVM condiviso (o `commonMain`) |
| `SquelchController`, `SoftMuteFader`, `CircularAudioBuffer`, `SMeterUtils` | nessuna, ma `SquelchController` istanzia sempre `HfSpectralVad` | spostare; rendere il VAD opzionale (iniettato, `null` su Android) |
| `AudioRecorder` | `javax.sound.sampled.AudioFormat`, scrittura su `File` | estrarre un `WavWriter` puro su `OutputStream`; MP3/segmentazione restano desktop |
| `DesktopAudioPlayer`, `FileAudioPlayer` | `javax.sound` | nuova implementazione `AudioTrack` su Android |
| `AppSettings` | `java.nio.file` (ok), modello desktop molto ampio | impostazioni Android dedicate con modello ridotto |
| `SecretCipher` | DPAPI via JNA | nuova implementazione Android Keystore |
| `LicenseManager` | Ed25519 da `java.security` (supporto non garantito su tutte le API Android) | separare logica e verificatore (§5.4) |
| `MachineIdUtil` | registro Windows via JNA | nuova implementazione Android (§5.2) |
| `AppLogger` | `java.io`/`java.nio` (ok) | riuso con cartella log in `filesDir`; valutare un sink Logcat |
| `KiwiAppController` | dipende da tutti i servizi desktop | **non** riusato: nuova facciata mobile (§4.2) |
| UI `composeApp` | `java.awt.image.BufferedImage` nel waterfall, layout desktop | UI mobile dedicata; riuso solo di token/colori |

### 3.2 Gate di licenza attuale (desktop)

`KiwiAppController.hasAdvancedFeatures()` verifica la chiave salvata a ogni chiamata (con cache in `LicenseManager`) e blocca **nel controller**, non solo nella UI: registrazione e formato, squelch in registrazione, VAD, player delle registrazioni, archivio/storico, trascrizione, Telegram, registrazione utenti. Ascolto, sintonia, volume, squelch in ascolto, spettro/waterfall, utenti live e preferiti sono liberi.

---

## 4. Architettura proposta

### 4.1 Moduli e source set

```
shared/
  commonMain      ← protocollo, modelli (invariato: nessuna API java.*)
  jvmCommonMain   ← NUOVO: logica JVM condivisa desktop + Android
  desktopMain     ← servizi solo desktop (javax.sound, JNA, Vosk, SQLite, Telegram, …)
  androidMain     ← NUOVO: AudioTrack, OkHttp, Keystore, ANDROID_ID, MediaStore
composeApp/       ← UI desktop (invariata)
androidApp/       ← NUOVO: Activity, UI Compose mobile, Foreground Service, manifest
```

- `jvmCommonMain` è un source set intermedio (gruppo personalizzato della gerarchia KMP con `desktop` + `android`). Può usare `java.nio`, `java.util.concurrent`, `java.security` ma **non** `java.net.http`, `javax.*`, `java.awt`, JNA. La regola di `CLAUDE.md` su `commonMain` resta valida.
- **Modulo `androidApp` separato** invece di aggiungere il target Android a `composeApp`: con le versioni recenti di AGP il plugin applicazione Android non va combinato con il plugin KMP nello stesso modulo, e la UI mobile è comunque diversa da quella desktop. `shared` usa il plugin libreria Android per KMP.
- Le dipendenze Android (AGP, OkHttp, Compose Android/Activity, eventuale BouncyCastle) vanno in `gradle/libs.versions.toml`. Le dipendenze desktop (`sqlite-jdbc`, `vosk`, `mp3spi`, `jump3r`, `jna`) restano in `desktopMain` e non devono comparire nel grafo Android.

### 4.2 Facciata mobile e ciclo di vita

- **`MobileAppController`** (in `androidMain` o in `androidApp`) è l'unica facciata per la UI Android, con le stesse regole di `KiwiAppController`: la UI usa solo le sue API e i suoi `StateFlow`, nessuna seconda fonte di verità per sintonia/audio/registrazione/waterfall.
- La logica di sessione comune (client SND, coordinamento W/F, squelch, inoltro frame a player e recorder) viene estratta in un **`KiwiSessionCore`** in `jvmCommonMain`, usato da entrambi i controller. L'estrazione si fa a piccoli passi coperti dai test desktop esistenti (§7, Fase 1).
- Il controller vive nel **Foreground Service** (`KiwiPlaybackService`, tipo `mediaPlayback`), non nell'Activity: la sessione sopravvive a rotazioni, cambio app e schermo spento. L'Activity si collega al service e osserva i flow.
- Ciclo di vita:
  - connessione avviata dall'utente → avvio del service in foreground con notifica (stato, frequenza, azioni *Stop* e *Disconnetti*);
  - app in background → W/F chiusa (risparmio dati/batteria), SND attiva se l'ascolto in background è consentito (§6);
  - ritorno in foreground → riapertura W/F con lo **stesso token/timestamp** della sessione SND attiva (regola `badp=5` invariata);
  - rimozione del task / *Stop* dalla notifica → disconnessione pulita, così lo slot del KiwiSDR viene liberato subito.

### 4.3 Trasporto WebSocket

- Interfaccia `KiwiWebSocket` in `jvmCommonMain` (`sendText`, `sendBinary`, `close`, callback per testo/binario/chiusura/errore).
- Implementazioni: JDK `java.net.http.WebSocket` in `desktopMain` (comportamento attuale), **OkHttp** in `androidMain`.
- `WebSocketCommandQueue` viene riscritta sull'interfaccia, mantenendo la serializzazione degli invii (OkHttp accoda da solo, ma la coda resta l'unico punto di invio per coerenza e per i test).
- Frame audio elaborati FIFO su dispatcher serializzato, come su desktop.
- Su Android: listener `ConnectivityManager.NetworkCallback` per gestire cambio Wi-Fi ↔ rete mobile con la stessa logica di riconnessione del desktop (nessun ritentativo sui rifiuti `badp`).

### 4.4 Audio

- `AudioOutput` (interfaccia in `jvmCommonMain`): `start(sampleRate)`, `write(pcm)`, `setVolume`, `setMuted`, `stop`. Desktop: adattatore su `DesktopAudioPlayer`; Android: **`AudioTrack`** in streaming, PCM 16 bit mono al sample rate concordato con `SET AR OK`.
- Gestione **audio focus** (pausa/ducking su chiamate e altre app) e `ACTION_AUDIO_BECOMING_NOISY` (cuffie scollegate → mute).
- Buffer di jitter modesto per assorbire le variazioni di rete mobile; misurare latenza e underrun in collaudo.
- Squelch: `SquelchController` con VAD disattivato; soglia e on/off persistiti.
- Wi-Fi lock a bassa latenza durante l'ascolto a schermo spento; nessun wake lock CPU oltre a quello implicito del foreground service, salvo necessità emersa in collaudo.

### 4.5 Spettro e waterfall

- Riuso di `WaterfallBuffer`/`WaterfallPalette`; il renderer Android scrive i pixel in un `Bitmap` (al posto di `BufferedImage`) e disegna su `Canvas` Compose.
- Interazioni touch: tocco = sintonia, trascinamento orizzontale = spostamento, pinch o pulsanti = zoom, controlli compatti per palette e range dB.
- Velocità di aggiornamento ridotta di default su rete mobile; nessun rendering quando la vista non è visibile.

### 4.6 Persistenza e segreti

- `AndroidSettings` con modello ridotto: host, porta, password cifrata, volume, squelch (on/off, soglia), ultima frequenza/modo, preferiti personali, chiave di licenza. File JSON scritto in modo atomico in `filesDir` (stessa strategia di `AppSettings`) oppure DataStore; scelta in Fase 2.
- Password cifrata con **Android Keystore** (AES-GCM), prefisso `aks:` analogo a `dpapi:`. Log dei comandi sempre tramite `KiwiProtocol.redactSecrets`.
- `allowBackup`/regole di backup: escludere segreti e licenza dal backup cloud (la chiave Keystore non è trasferibile, quindi un ripristino porterebbe valori non decifrabili).

### 4.7 Registrazione

- `WavWriter` estratto da `AudioRecorder` in `jvmCommonMain`, scrive su `OutputStream` e aggiorna l'header alla chiusura (serve uno stream con posizionamento: si usa il `FileDescriptor` ottenuto da MediaStore o un file temporaneo copiato in MediaStore alla chiusura).
- Destinazione: `MediaStore.Audio` in `Music/KiwiGrabber` (o `Recordings/KiwiGrabber` dove disponibile), con `IS_PENDING` durante la scrittura. Nome file allineato al desktop (data, ora, frequenza, modo).
- Disconnessione o perdita di rete durante la registrazione: file chiuso correttamente; ripresa automatica come su desktop da valutare (default: la registrazione si interrompe e l'utente viene avvisato).
- Spazio libero controllato all'avvio e periodicamente; arresto pulito sotto soglia.

### 4.8 Permessi e manifest

- `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` (Wi-Fi lock), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (richiesto a runtime da Android 13).
- Nessun permesso di storage: con `minSdk` 29 MediaStore non richiede permessi per i file creati dall'app.
- Nessun permesso microfono: l'audio arriva dalla rete.

---

## 5. Attivazione su Android

### 5.1 Principi

L'attivazione desktop è gratuita, offline e serve a mantenere un contatto con chi usa le funzioni avanzate, non come protezione forte. Su Android si mantiene **lo stesso meccanismo e lo stesso strumento di firma**, cambiando solo la sorgente dell'identificativo del dispositivo.

### 5.2 Identificativo del dispositivo

- Fonte: `Settings.Secure.ANDROID_ID`. Da Android 8 è univoco per combinazione dispositivo + utente + **chiave di firma dell'app**, non richiede permessi e non è un identificativo pubblicitario.
- Formato: `GRAB-A-XXXX-XXXX-XXXX-XXXX`, ottenuto con SHA-256 di un namespace dedicato (`KiwiSDRRecorder:machine:android:v1:` + `ANDROID_ID`). Il prefisso `A` rende evidente la piattaforma a chi firma e impedisce collisioni con gli ID desktop.
- Se `ANDROID_ID` non è leggibile → `GRAB-NON-DISPONIBILE`, come su desktop: nessuna licenza valida.
- Cambia con: reset di fabbrica, nuovo profilo utente, **cambio della chiave di firma dell'APK**. Conseguenze:
  - la distribuzione è su **GitHub e Play Store**: entrambi i canali devono usare **la stessa chiave di firma dell'app**. Con Play App Signing va quindi caricata la propria chiave di firma (opzione "usa la tua chiave" alla creazione dell'app nella Play Console) invece di farla generare a Google; la chiave di upload può essere distinta. Altrimenti l'APK GitHub e quello Play produrrebbero `ANDROID_ID` diversi (licenza non valida passando da un canale all'altro) e non potrebbero aggiornarsi a vicenda;
  - la chiave di firma va custodita con backup sicuro fuori dal repository: perderla significa invalidare tutte le licenze Android emesse;
  - in caso di reset l'utente richiede un nuovo codice, come dopo una reinstallazione di Windows.

### 5.3 Formato della licenza

- Invariato: `KSR1:<base64url(machineId)>:<firma Ed25519>`, payload `KiwiSDRRecorder:license:v1:<machineId>`, stessa chiave pubblica.
- Una licenza desktop non attiva Android e viceversa: ogni dispositivo ha la sua.

**Modifica necessaria al License Manager** (verificata sul repository `KiwiSDR-License-Manager`, 12/11/2026): firma e payload sono già compatibili, ma `LicenseCrypto.normalizeMachineId` valida l'ID con il pattern `GRAB-[A-F0-9]{4}(?:-[A-F0-9]{4}){3}`, quindi oggi **rifiuta** gli ID `GRAB-A-…` sia nella GUI sia nella CLI. Interventi:

- pattern esteso a `GRAB-(?:A-)?[A-F0-9]{4}(?:-[A-F0-9]{4}){3}` in `LicenseCrypto.java`;
- testo del tooltip e del messaggio d'errore in `LicenseManagerApp.java` aggiornati con entrambi i formati;
- facoltativo: colonna o etichetta "piattaforma" nel registro `activations.csv`, ricavabile dal prefisso dell'ID senza cambiare il formato del file;
- verifica: emissione di una licenza per un ID `GRAB-A-…` e controllo con `verify` e con l'app Android (Fase 7).

### 5.4 Verifica della firma

- Spostare in `jvmCommonMain` la logica di parsing/confronto (`verifyLicense` con `MachineIdProvider` e `SignatureVerifier` iniettati) e i test esistenti.
- Il verificatore Ed25519 di `java.security` non è garantito su tutte le versioni Android supportate: su Android si usa l'API leggera di **BouncyCastle** (`Ed25519Signer`), su desktop resta il JDK. Test con gli stessi vettori (chiave, ID, firma) su entrambe le piattaforme.
- Cache dell'esito come su desktop; il gate è applicato **nel controller** (non solo nella UI).
- R8 attivo in release (offusca e riduce l'APK); nessun controllo anti-manomissione aggiuntivo né Play Integrity: costo sproporzionato per un'attivazione gratuita.

### 5.5 Flusso utente

Impostazioni → **Licenza**: ID dispositivo con pulsante *Copia* e *Condividi* (intent di condivisione verso Telegram o altra app, avviato dall'utente), campo codice, pulsante *Attiva*. Nessuna chiamata di rete automatica. Le funzioni bloccate mostrano un overlay/sheet equivalente a `FeatureLockedOverlay` con collegamento diretto alla sezione Licenza.

---

## 6. Limitazioni per le versioni non attive

### 6.1 Proposta (raccomandata)

Coerente con il desktop: **tutto ciò che serve ad ascoltare e sintonizzare è libero**; si limita ciò che produce valore persistente o consuma risorse in modo prolungato.

| Funzione | Non attivata | Attivata |
|----------|--------------|----------|
| Connessione, sintonia, modo, volume, mute | ✅ | ✅ |
| Squelch in ascolto | ✅ | ✅ |
| Spettro e waterfall | ✅ | ✅ |
| Utenti connessi (live) | ✅ | ✅ |
| Preferiti del server (lettura + sintonia) | ✅ | ✅ |
| Preferiti personali (aggiunta, modifica, eliminazione) | ❌ | ✅ |
| Ascolto con app in background / schermo spento | ❌ l'audio si ferma quando l'app non è visibile | ✅ Foreground Service |
| Registrazione manuale | ❌ | ✅ |
| Impostazioni server e password | ✅ | ✅ |

Motivazioni:

- **Registrazione**: è la funzione bloccata anche su desktop; mantenere lo stesso confine rende il messaggio coerente tra le piattaforme.
- **Ascolto in background**: è la principale differenza d'uso su mobile e ha un costo reale (batteria, slot del server occupato a lungo). Senza attivazione l'app resta pienamente utilizzabile in primo piano. Va comunque gestita bene la prima esperienza: un avviso chiaro quando l'audio si interrompe passando in background, non un blocco silenzioso.
- **Preferiti personali**: riservati alla versione attivata (decisione del 12/11/2026); senza attivazione si usano i preferiti del server, completi e sintonizzabili. Il pulsante "aggiungi ai preferiti" resta visibile e porta alla sezione Licenza.
- Nessun periodo di prova, limite di tempo sulla sessione né "nag", come sul desktop.

### 6.2 Alternative considerate

- **Alcuni preferiti personali gratuiti** (es. 5): scartato, i preferiti personali sono interamente riservati alla versione attivata.
- **Solo registrazione bloccata** (identico al desktop): più semplice, ma su Android la registrazione richiede comunque il service, quindi lo sblocco del background diventa implicito e il gate perde significato.
- **Waterfall o utenti bloccati**: sconsigliato, sono funzioni di base per orientarsi sulla banda e su desktop sono libere.
- **Durata massima di sessione** (es. 15–30 minuti) senza attivazione: possibile, ma più invasivo; da considerare solo se la proposta §6.1 risultasse insufficiente.

### 6.3 Implementazione del gate

- `enum class GatedFeature { BACKGROUND_PLAYBACK, MANUAL_RECORDING, PERSONAL_FAVORITES }` e un `FeatureGate` in `jvmCommonMain` basato sull'esito della verifica licenza.
- Il controller verifica il gate prima di ogni azione (`startRecording`, `addFavorite`/`editFavorite`/`removeFavorite`, passaggio in background) ed emette una notifica UI in caso di rifiuto, come `_connectionNotifications` su desktop.
- Se l'attivazione viene persa (es. reset del dispositivo con ripristino dei dati), i preferiti personali già salvati non vengono cancellati: restano in sola lettura e sintonizzabili, e tornano modificabili dopo una nuova attivazione.
- In futuro il desktop può adottare lo stesso `FeatureGate` al posto di `hasAdvancedFeatures()`.

---

## 7. Fasi di sviluppo

Ogni fase si chiude con build desktop e test verdi (`.\gradlew.bat :composeApp:compileKotlinDesktop :shared:desktopTest`) e, dalla Fase 2, con la build Android.

### Fase 0 — Decisioni e ambiente

Decisioni già prese (12/11/2026):

- `minSdk` **29** (Android 10): nessun permesso di storage legacy;
- distribuzione su **GitHub Release (APK) e Play Store (AAB)**, con la stessa chiave di firma dell'app (§5.2);
- **nessun** preferito personale nella versione non attivata (§6);
- License Manager: serve la modifica del pattern dell'ID descritta in §5.3.

Attività rimanenti:

- Installare Android Studio, SDK e `local.properties` (non versionato).
- Scegliere `targetSdk` (ultimo richiesto dal Play Store al momento dello sviluppo) e la versione AGP compatibile con Kotlin 2.4.21 e Compose Multiplatform 1.12.2.
- Generare la chiave di firma dell'app e quella di upload, con backup fuori dal repository; creare l'app nella Play Console caricando la propria chiave di firma.
- Scegliere `applicationId` e confermare il formato di registrazione (WAV).
- Applicare e verificare la modifica al License Manager (§5.3).

**Uscita:** ambiente funzionante, chiavi di firma create e custodite, License Manager aggiornato.

### Fase 1 — Refactoring di `shared` (nessuna modifica funzionale al desktop)

1. Creare il source set `jvmCommonMain` e spostarvi le classi portabili: parser, generatore token, waterfall buffer/palette/frame, squelch (VAD opzionale), utilità S-meter, `WavWriter`, logica di verifica licenza.
2. Introdurre le interfacce `KiwiWebSocket`, `AudioOutput`, `SecretStore`, `MachineIdProvider`, `SignatureVerifier`; adattare `KiwiClient`, `WaterfallClient` e `WebSocketCommandQueue` all'interfaccia del trasporto con implementazione JDK desktop.
3. Estrarre `KiwiSessionCore` da `KiwiAppController` mantenendo invariate API e `StateFlow` pubblici del controller desktop.
4. Spostare i test corrispondenti in `jvmCommonTest`, aggiungere test sulla coda con trasporto finto.

**Uscita:** desktop identico (test verdi, collaudo manuale di connessione, waterfall, registrazione, licenza), base pronta per Android.

### Fase 2 — Target Android e scheletro app

- Plugin libreria Android KMP su `shared`, nuovo modulo `androidApp` con Compose, tema derivato da `GrabberColors`, navigazione a schede o barra inferiore (Radio, Waterfall, Preferiti, Utenti, Impostazioni).
- `AndroidSettings`, `KeystoreSecretStore`, logger con file in `filesDir`.
- Task di build e verifica: `:androidApp:assembleDebug`, test unitari Android per le parti `androidMain` testabili.
- Avvisi di terze parti per le nuove dipendenze (OkHttp, BouncyCastle, AndroidX) in `THIRD_PARTY_NOTICES.md`.

**Uscita:** APK debug installabile che mostra la UI vuota e salva/legge le impostazioni.

### Fase 3 — Ascolto (F1)

- Trasporto OkHttp, `AudioTrack` output, `MobileAppController` sopra `KiwiSessionCore`.
- `KiwiPlaybackService` foreground `mediaPlayback` con notifica e azioni, audio focus, `BECOMING_NOISY`, Wi-Fi lock, `NetworkCallback`.
- UI Radio: stato connessione, display frequenza, tastierino/step, selettore modo, volume, mute, squelch on/off e soglia, S-meter.
- Gate `BACKGROUND_PLAYBACK` (§6).

**Uscita:** ascolto stabile per almeno 30 minuti a schermo spento (versione attivata), arresto audio corretto in background (non attivata), riconnessione dopo cambio rete.

### Fase 4 — Spettro e waterfall (F2)

- Apertura W/F dopo `badp=0` con token SND; chiusura in background e riapertura in foreground.
- Renderer `Bitmap`, interazioni touch, zoom, palette, range dB; layout verticale e orizzontale.

**Uscita:** waterfall fluido su un dispositivo di fascia media, sintonia al tocco corretta, nessun traffico W/F con app in background.

### Fase 5 — Utenti e preferiti (F3, F4)

- Elenco utenti live da `user_cb`.
- Preferiti: sezione "Server" (etichette DX, sola lettura, ricaricate a ogni connessione) e sezione "Personali" (CRUD, persistiti); ricerca/filtro per banda; aggiunta rapida del canale corrente dalla schermata Radio.
- Gate `PERSONAL_FAVORITES`: senza attivazione la sezione "Personali" mostra l'invito all'attivazione.

**Uscita:** preferiti del server coerenti con il client web del KiwiSDR; preferiti personali conservati tra gli avvii.

### Fase 6 — Registrazione manuale (F5)

- `WavWriter` + MediaStore con `IS_PENDING`, indicatore di durata e dimensione, avviso su spazio insufficiente.
- Comportamento definito su disconnessione, perdita di rete, chiusura dell'app.
- Gate `MANUAL_RECORDING`.

**Uscita:** file WAV validi, visibili nelle app musicali/file di sistema, header corretto anche dopo interruzioni.

### Fase 7 — Impostazioni e licenza (F6, §5)

- Server (host, porta, password con visibilità commutabile), verifica formale dell'indirizzo, valori predefiniti da `KiwiServerDefaults`.
- `AndroidMachineIdProvider`, verificatore BouncyCastle, sezione Licenza con copia/condivisione dell'ID.
- Test con vettori di licenza comuni a desktop e Android.

**Uscita:** attivazione end-to-end con un codice generato da License Manager per un ID `GRAB-A-…`.

### Fase 8 — Rifinitura UX e accessibilità

- Layout per smartphone e tablet (window size class), tema scuro, dimensioni dei target touch ≥ 48 dp, `contentDescription`, testi scalabili.
- Gestione errori con messaggi italiani coerenti con il desktop.
- Verifica dei consumi (batteria, dati) e ottimizzazione degli intervalli di aggiornamento.

### Fase 9 — Collaudo

Vedi §8. Matrice minima: un emulatore con API = `minSdk`, un emulatore con l'ultima API, almeno un dispositivo fisico (preferibilmente con un produttore noto per politiche di risparmio energetico aggressive).

### Fase 10 — Packaging e rilascio

- Firma di release con keystore custodito fuori dal repository (backup documentato), R8/shrinking, `versionCode`/`versionName` allineati a `appVersion`.
- `assembleRelease` (APK per GitHub) e `bundleRelease` (AAB per Play Store, con Play App Signing sulla propria chiave e scheda "Sicurezza dei dati": nessun dato raccolto). Stesso `versionCode` sui due canali per la stessa versione.
- Verifica che APK GitHub e app installata da Play abbiano la stessa firma (`apksigner verify --print-certs`) e producano lo stesso ID dispositivo.
- Nessun APK/AAB committato nel repository.
- Aggiornare `TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md`, `ARCHITECTURE.md`, `DISTRIBUZIONE_E_RILASCI.md`, `USER_GUIDE.md`, `VERSION_HISTORY.md`, `THIRD_PARTY_NOTICES.md`; estendere la skill `windows-release` o crearne una Android.

---

## 8. Criteri di verifica

- **Non regressione desktop:** test `:shared:desktopTest` verdi a ogni fase; collaudo manuale desktop dopo la Fase 1.
- **Connessione:** autenticazione con e senza password, rifiuti `badp` mostrati senza ritentativi, riconnessione dopo cambio Wi-Fi ↔ mobile.
- **Audio:** nessun underrun percepibile in 30 minuti su Wi-Fi; squelch e volume reattivi; pausa corretta su chiamata in arrivo e scollegamento cuffie.
- **Background:** con attivazione, audio continuo per 30 minuti a schermo spento con notifica visibile; senza attivazione, audio fermato e avviso mostrato.
- **Waterfall:** sintonia al tocco entro la risoluzione del bin; W/F chiusa in background, riaperta senza `badp=5`.
- **Slot del server:** dopo *Stop*, rimozione del task o arresto forzato, la connessione risulta chiusa sul KiwiSDR.
- **Registrazione:** file WAV riproducibili, durata corretta, nessun file `IS_PENDING` residuo dopo interruzioni.
- **Licenza:** codice valido attiva le funzioni; codice desktop, codice di un altro dispositivo e codice alterato vengono rifiutati; il gate tiene anche chiamando le API del controller senza passare dalla UI.
- **Segreti:** password non presente in chiaro nei file, nei log e nei backup.
- **Pacchetto:** APK di release firmato installato e avviato su dispositivo fisico.

---

## 9. Rischi e punti aperti

| Rischio | Impatto | Mitigazione |
|---------|---------|-------------|
| Compatibilità AGP / Kotlin 2.4.21 / Compose MP 1.12.2 | blocco build | verifica in Fase 0 prima di ogni modifica; eventuale aggiornamento coordinato con il desktop |
| Refactoring di `shared` introduce regressioni desktop | alto | Fase 1 a piccoli passi, test esistenti spostati e non riscritti, collaudo manuale |
| Politiche di risparmio energetico dei produttori uccidono il service | ascolto interrotto | foreground service corretto, guida utente per escludere l'app dall'ottimizzazione batteria, test su dispositivo fisico |
| `ANDROID_ID` cambia (reset, chiave di firma diversa) | licenza persa | chiave di firma unica, procedura di riemissione documentata |
| Ed25519 non disponibile nel provider di sistema | verifica licenza impossibile | BouncyCastle leggero su Android |
| Consumo dati del waterfall su rete mobile | costi per l'utente | W/F sospesa in background, velocità ridotta di default su rete mobile, interruttore "waterfall solo su Wi-Fi" (opzionale) |
| Policy Play Store sui foreground service | rifiuto pubblicazione | tipo `mediaPlayback` coerente con l'uso; dichiarazione nella console Play |
| Password KiwiSDR in chiaro sulla rete (limite del protocollo) | esposizione credenziali | stesso avviso della guida desktop |

Punti aperti da decidere in Fase 0: `targetSdk` e versione AGP, eventuale MP3 in una versione successiva (`jump3r` è Java puro ma va verificato su Android per prestazioni e licenza), nome del pacchetto applicativo (`applicationId`).

---

## 10. Riferimenti

- [ARCHITECTURE.md](../ARCHITECTURE.md) — regole architetturali e linee guida per l'evoluzione Android.
- [TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md](../TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md) — proposta preliminare di toolchain e comandi APK/AAB (da aggiornare in Fase 0).
- [GUI_DESIGN_SYSTEM.md](../GUI_DESIGN_SYSTEM.md) — token visivi da riprendere nel tema mobile.
