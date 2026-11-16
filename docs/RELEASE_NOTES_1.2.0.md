# KiwiSDR Grabber & Station Recorder 1.2.0

**Stato: in preparazione.**

**[Italiano](#italiano) | [English](#english)**

## Italiano

Release minore. Introduce la **prima versione per Android**, un'app di ascolto per i ricevitori KiwiSDR, e corregge il caricamento dei canali memorizzati sul ricevitore anche nella versione Windows.

### Novità: app Android

- **Ascolto** con frequenza, passo, modo, volume, squelch RF con soglia regolabile e S-meter.
- **Spettro e waterfall** con sintonia al tocco, trascinamento, zoom con due dita, palette e range dB regolabili. Il waterfall si collega solo mentre la sua schermata è visibile e su rete a consumo rallenta per risparmiare dati.
- **Preferiti:** i canali memorizzati sul ricevitore (sola lettura) e i preferiti personali, con ricerca e filtro per banda.
- **Utenti** collegati al ricevitore, con frequenza, modo e località.
- **Registrazione WAV** nella cartella di sistema Registrazioni/KiwiGrabber, con riparazione automatica dei file interrotti.
- **Attivazione** gratuita e offline con ID dispositivo `GRAB-A-…`, come sul desktop: sblocca ascolto in background, registrazione e preferiti personali. Senza attivazione, quando l'app va in background la connessione si chiude e libera il canale del ricevitore.
- Password del ricevitore salvata cifrata nel Keystore di Android.

Istruzioni complete nella [guida utente](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#12-versione-android).

### Correzioni (Windows)

- **Sincronizza** nella scheda Preferiti ora importa davvero i canali memorizzati sul ricevitore. In precedenza la richiesta usava un comando che restituisce solo la configurazione DX del ricevitore e la sincronizzazione non aveva effetto. Attenzione: la sincronizzazione sostituisce i preferiti con tutti i canali del ricevitore, che possono essere centinaia; le spunte di scansione restano sui canali con stessa frequenza e modo.

### Compatibilità e aggiornamento

- Windows: Windows 10 e Windows 11 a 64 bit, runtime Java incluso. L'installer sostituisce la versione installata mantenendo impostazioni, preferiti e dati in `.kiwigrabber`. Nessuna migrazione richiesta.
- Android: Android 10 o successivo. L'app non è sul Play Store: si installa dall'APK, consentendo l'installazione di app sconosciute. Le versioni future si installano sopra quella esistente, mantenendo impostazioni e attivazione.
- Le licenze desktop e Android sono distinte: ogni dispositivo Android richiede il proprio codice.

### File della release

| File | Descrizione |
|---|---|
| `KiwiSDRRecorder-1.2.0.exe` | Installer Windows EXE |
| `KiwiSDRRecorder-1.2.0.msi` | Installer Windows MSI (anche per installazioni silenziose: `msiexec /i KiwiSDRRecorder-1.2.0.msi /qn`) |
| `KiwiSDRGrabber-1.2.0.apk` | App Android (Android 10 o successivo) |
| `SHA256SUMS.txt` | Hash SHA-256 degli asset pubblicati |
| `third-party-licenses-1.2.0.zip` | Avvisi e licenze dei componenti di terze parti |
| `lgpl-sources-1.2.0.zip` | Sorgenti delle librerie LGPL incluse nell'installer Windows |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Licenza del progetto e permesso supplementare LGPL |

Gli installer Windows non sono firmati digitalmente: Windows potrebbe mostrare un avviso SmartScreen. L'APK è firmato con il certificato dell'app (SHA-256 `EF:6D:6C:A3:…:EC:20:21:29`, impronta completa nella guida utente). Tutti gli allegati sono compilati da GitHub Actions con attestazione di provenienza; per verificare hash e provenienza vedi la [guida utente](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifica-dellinstaller).

## English

Minor release. It introduces the **first Android version**, a listening app for KiwiSDR receivers, and fixes loading of the channels stored on the receiver in the Windows version too.

### New: Android app

- **Listening** with frequency, step, mode, volume, RF squelch with adjustable threshold, and S-meter.
- **Spectrum and waterfall** with tap to tune, drag, pinch zoom, adjustable palette and dB range. The waterfall connects only while its screen is visible and slows down on metered networks to save data.
- **Favorites:** the channels stored on the receiver (read-only) and personal favorites, with search and band filter.
- **Users** connected to the receiver, with frequency, mode, and location.
- **WAV recording** to the system Recordings/KiwiGrabber folder, with automatic repair of interrupted files.
- Free offline **activation** with a `GRAB-A-…` device ID, as on the desktop: it unlocks background listening, recording, and personal favorites. Without activation, the connection is closed when the app goes to the background, freeing the receiver channel.
- Receiver password stored encrypted in the Android Keystore.

Full instructions in the [user guide](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#12-android-version).

### Fixes (Windows)

- **Sync** (`Sincronizza`) in the Favorites tab now actually imports the channels stored on the receiver. Previously the request used a command that returns only the receiver DX configuration, so syncing had no effect. Note: syncing replaces the favorites with all the receiver channels, which can be hundreds; scan selections are kept for channels with the same frequency and mode.

### Compatibility and upgrade

- Windows: Windows 10 and Windows 11, 64-bit, Java runtime included. The installer replaces the installed version and keeps settings, favorites, and data in `.kiwigrabber`. No migration required.
- Android: Android 10 or later. The app is not on the Play Store: install it from the APK, allowing installation of unknown apps. Future versions install over the existing one, keeping settings and activation.
- Desktop and Android licenses are separate: each Android device needs its own code.

### Release files

| File | Description |
|---|---|
| `KiwiSDRRecorder-1.2.0.exe` | Windows EXE installer |
| `KiwiSDRRecorder-1.2.0.msi` | Windows MSI installer (also for silent installs: `msiexec /i KiwiSDRRecorder-1.2.0.msi /qn`) |
| `KiwiSDRGrabber-1.2.0.apk` | Android app (Android 10 or later) |
| `SHA256SUMS.txt` | SHA-256 hashes for published assets |
| `third-party-licenses-1.2.0.zip` | Third-party notices and licenses |
| `lgpl-sources-1.2.0.zip` | Source code of the LGPL libraries bundled with the Windows installer |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Project license and additional LGPL permission |

The Windows installers are not digitally signed, so Windows may show a SmartScreen warning. The APK is signed with the app certificate (SHA-256 `EF:6D:6C:A3:…:EC:20:21:29`, full fingerprint in the user guide). All attachments are built by GitHub Actions with build provenance attestations; see the [user guide](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifying-the-installer) to verify checksums and provenance.
