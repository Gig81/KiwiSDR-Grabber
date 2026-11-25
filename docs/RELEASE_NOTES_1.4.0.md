# KiwiSDR Grabber & Station Recorder 1.4.0

**Stato: bozza / in preparazione.**

**[Italiano](#italiano) | [English](#english)**

## Italiano

La versione 1.4.0 introduce la decodifica dei **modi digitali** sul desktop Windows: i decoder integrati nel ricevitore KiwiSDR (FT8, FT4, WSPR, CW, CW Skimmer, fax meteo, SSTV, HFDL, ALE 2G) e tre decoder eseguiti nell'app (RTTY/FSK, NAVTEX, DSC), con riconoscimento automatico del modo, autocentraggio del segnale e notifiche Telegram dedicate. Cambia anche la frequenza mostrata in CW, ora allineata al client web di KiwiSDR.

### Novità e correzioni

- **Scheda "Modi digitali"** (tra "Utenti & Monitor" e "Impostazioni avanzate"): elenco dei modi raggruppati per sorgente ("Sul server", "Nell'app") con disponibilità sul ricevitore corrente; per ogni modo frequenze note con "Sintonizza", parametri, comandi Avvia/Ferma/Pulisci/Salva e uscita dedicata (tabella FT8/FT4 e WSPR, testo continuo, righe per segnale dello skimmer, messaggi NAVTEX/DSC espandibili, immagini fax e SSTV).
- **Decoder del server** tramite il canale `/EXT` di KiwiSDR, con le estensioni abilitate dall'amministratore: FT8/FT4 (messaggi con nominativo e locatore, filtro "solo CQ", copia con un clic), WSPR (nessun caricamento su wsprnet), CW con velocità e soglia automatiche, CW Skimmer (clic su un segnale per sintonizzarlo), fax meteo con fasatura e arresto automatico, SSTV con nome del modo, HFDL e ALE 2G.
- **Decoder nell'app:** RTTY/FSK (ITA2, preset radioamatori 170 Hz/45,45 Bd e meteo DWD 450 Hz/50 Bd, parametri personalizzati), NAVTEX SITOR-B con correzione FEC, filtri per stazione e tipo e scarto dei duplicati, DSC (solo informativo, non per la sicurezza della vita in mare). I decoder seguono da soli piccoli errori di sintonia.
- **Salvataggio** dei testi decodificati e delle immagini (PNG) in `KiwiGrabberRecordings/Decodifiche/`, anche automatico per fax e SSTV.
- **Riconoscimento automatico del modo:** suggerisce il modo più probabile (RTTY con shift stimato, NAVTEX, DSC, CW, fax, FT8/FT4/WSPR dalla frequenza, PSK31 solo segnalato) con la percentuale di confidenza; facoltativo l'avvio automatico dei soli decoder dell'app oltre il 90 %.
- **Autocentraggio (AFC)** con l'interruttore nella console audio, spento di default: dopo una frequenza digitata o un clic sul waterfall/spettro centra il tono CW, la portante AM, i segnali NFM e i segnali dei decoder RTTY/NAVTEX/fax. Non agisce in USB/LSB voce, sui preferiti, sulle scansioni e sui passi di sintonia.
- **Notifiche Telegram dei decoder** con parole chiave dedicate (Impostazioni avanzate → Telegram), al massimo una ogni 30 secondi per parola e senza duplicati dello stesso messaggio NAVTEX.
- Indicatori `DEC` (decoder attivo) e `AFC` (correzione appena applicata) sul display del VFO; lo spettro mostra la banda passante reale del canale.
- Avvio di un decoder ferma una scansione in corso e viceversa; dopo una caduta di rete il decoder del server riparte con la sessione.
- Tutte le nuove funzioni richiedono l'attivazione della licenza.

### Compatibilità e aggiornamento

- Sistemi supportati: Windows 10/11 a 64 bit (desktop); Android 10 o successivo per l'app di ascolto, che in questa versione non ha i modi digitali.
- **Frequenza in CW (Windows e Android):** la frequenza mostrata in CW è ora quella del segnale (portante + centro della banda passante), come nel client web KiwiSDR: a parità di frequenza il tono si sente a 500 Hz. Le etichette DX del ricevitore e la sintonia dagli utenti connessi diventano corrette; i **preferiti CW salvati in precedenza** restano numericamente invariati e risultano spostati di 500 Hz: se servono, correggili aggiungendo 0,5 kHz.
- I decoder del server richiedono che il ricevitore permetta le connessioni delle app (`ext_api_nchans`): con audio e waterfall aperti l'app usa tre connessioni. Se il ricevitore rifiuta il decoder, la scheda lo spiega e propone "Libera una connessione" (chiude il waterfall e riprova). FT8, FT4 e SSTV richiedono un ricevitore in modalità 12 kHz.
- Le impostazioni esistenti restano valide: le nuove preferenze hanno valori predefiniti.

### File della release

| File | Descrizione |
|---|---|
| `KiwiSDRRecorder-1.4.0.exe` | Installer Windows EXE |
| `KiwiSDRRecorder-1.4.0.msi` | Installer Windows MSI |
| `KiwiSDRGrabber-1.4.0.apk` | App Android (Android 10 o successivo) |
| `SHA256SUMS.txt` | Hash SHA-256 degli asset pubblicati |
| `third-party-licenses-1.4.0.zip` | Avvisi e licenze dei componenti di terze parti |
| `lgpl-sources-1.4.0.zip` | Sorgenti delle librerie LGPL incluse |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Licenza del progetto e permesso supplementare LGPL |

Gli installer Windows non sono firmati digitalmente: Windows potrebbe mostrare un avviso SmartScreen. L'APK è firmato con il certificato dell'app (impronta nella guida utente). Tutti gli allegati sono compilati da GitHub Actions con un'attestazione di provenienza; per verificare hash e provenienza vedi la [guida utente](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifica-dellinstaller).

## English

Version 1.4.0 adds **digital mode decoding** to the Windows desktop app: the decoders built into the KiwiSDR receiver (FT8, FT4, WSPR, CW, CW Skimmer, weather fax, SSTV, HFDL, ALE 2G) and three decoders running in the app (RTTY/FSK, NAVTEX, DSC), with automatic mode recognition, automatic signal centering and dedicated Telegram notifications. The frequency shown in CW now matches the KiwiSDR web client.

### Changes and fixes

- **"Digital modes" tab** (between "Users & Monitor" and "Advanced settings"): modes grouped by source ("On the server", "In the app") with availability on the current receiver; for each mode, known frequencies with "Tune", parameters, Start/Stop/Clear/Save and a dedicated output (FT8/FT4 and WSPR tables, running text, one line per skimmer signal, expandable NAVTEX/DSC messages, fax and SSTV images).
- **Server decoders** through the KiwiSDR `/EXT` channel, honouring the extensions enabled by the receiver administrator: FT8/FT4 (messages with callsign and locator, "CQ only" filter, click to copy), WSPR (no wsprnet upload), CW with automatic speed and threshold, CW Skimmer (click a signal to tune it), weather fax with phasing and auto stop, SSTV with mode name, HFDL and ALE 2G.
- **In-app decoders:** RTTY/FSK (ITA2, ham 170 Hz/45.45 Bd and DWD weather 450 Hz/50 Bd presets, custom parameters), NAVTEX SITOR-B with FEC correction, station/type filters and duplicate suppression, DSC (informational only, not for safety of life at sea). The decoders follow small tuning errors on their own.
- **Saving** of decoded text and images (PNG) to `KiwiGrabberRecordings/Decodifiche/`, optionally automatic for fax and SSTV.
- **Automatic mode recognition:** suggests the most likely mode (RTTY with estimated shift, NAVTEX, DSC, CW, fax, FT8/FT4/WSPR from the frequency, PSK31 reported only) with a confidence percentage; optional automatic start of the in-app decoders only, above 90 %.
- **Automatic centering (AFC)** with a switch in the audio console, off by default: after a typed frequency or a click on the waterfall/spectrum it centers the CW tone, the AM carrier, NFM signals and RTTY/NAVTEX/fax decoder signals. It does not act on USB/LSB voice, favorites, scans or tuning steps.
- **Decoder Telegram notifications** with dedicated keywords (Advanced settings → Telegram), at most one every 30 seconds per keyword and no duplicates of the same NAVTEX message.
- `DEC` (decoder running) and `AFC` (correction just applied) indicators on the VFO display; the spectrum shows the actual channel passband.
- Starting a decoder stops a running scan and vice versa; after a network drop the server decoder restarts with the session.
- All new features require license activation.

### Compatibility and upgrade

- Supported systems: 64-bit Windows 10/11 (desktop); Android 10 or later for the listening app, which does not include digital modes in this version.
- **CW frequency (Windows and Android):** the frequency shown in CW is now the signal frequency (carrier + passband center), as in the KiwiSDR web client: for the same frequency the tone is heard at 500 Hz. Receiver DX labels and tuning from connected users become correct; **previously saved CW favorites** keep their numbers and are therefore 500 Hz off: if needed, fix them by adding 0.5 kHz.
- Server decoders require the receiver to allow app connections (`ext_api_nchans`): with audio and waterfall open the app uses three connections. If the receiver refuses the decoder, the tab explains it and offers "Free a connection" (closes the waterfall and retries). FT8, FT4 and SSTV need a receiver in 12 kHz mode.
- Existing settings remain valid: new preferences have default values.

### Release files

| File | Description |
|---|---|
| `KiwiSDRRecorder-1.4.0.exe` | Windows EXE installer |
| `KiwiSDRRecorder-1.4.0.msi` | Windows MSI installer |
| `KiwiSDRGrabber-1.4.0.apk` | Android app (Android 10 or later) |
| `SHA256SUMS.txt` | SHA-256 hashes for published assets |
| `third-party-licenses-1.4.0.zip` | Third-party notices and licenses |
| `lgpl-sources-1.4.0.zip` | Source code of the bundled LGPL libraries |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Project license and additional LGPL permission |

The Windows installers are not digitally signed: Windows may show a SmartScreen warning. The APK is signed with the app certificate (fingerprint in the user guide). All attachments are built by GitHub Actions with a provenance attestation; to verify hashes and provenance see the [user guide](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifying-the-installer).
