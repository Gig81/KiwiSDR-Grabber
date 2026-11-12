# KiwiSDR Grabber & Station Recorder 1.0.1

**Stato: pubblicata il 12 novembre 2026.**

**[Italiano](#italiano) | [English](#english)**

## Italiano

Release di manutenzione per Windows. Questa versione corregge l'avvio su nuove installazioni, rende più affidabile la segnalazione degli errori e aggiunge scelte facoltative al termine dell'installazione.

### Novità e correzioni

- Creazione automatica della cartella dati prima dell'apertura del database SQLite, per evitare l'errore `SQLITE_CANTOPEN` al primo avvio.
- Inizializzazione anticipata del log e gestione degli errori di avvio, comprese le eccezioni non gestite. Quando possibile, l'utente riceve un messaggio con i dettagli copiabili da inviare all'assistenza.
- Registrazione più completa delle eccezioni nei componenti applicativi, evitando che errori operativi vengano ignorati senza traccia.
- Al termine dell'installazione Windows, l'utente può scegliere separatamente se avviare KiwiSDR Grabber e/o aprire nel browser le note di rilascio della versione installata.
- L'applicazione non apre più una finestra di console insieme alla finestra principale.

### Installer

Allegati alla GitHub Release [`v1.0.1`](https://github.com/Gig81/KiwiSDR-Grabber/releases/tag/v1.0.1):

- `KiwiSDRRecorder-1.0.1.exe` — installer consigliato.
- `KiwiSDRRecorder-1.0.1.msi` — pacchetto Windows Installer, utile anche per installazioni gestite o silenziose (`msiexec /i KiwiSDRRecorder-1.0.1.msi /qn`).
- `SHA256SUMS.txt` — hash SHA-256 degli installer.
- `LICENSE.txt`, `LICENSE-LGPL-EXCEPTION.md`, `third-party-licenses-1.0.1.zip`, `lgpl-sources-1.0.1.zip` — licenza, avvisi di terze parti e sorgenti delle librerie LGPL.

L'installazione da `.exe` è stata collaudata su una Windows Sandbox pulita: installazione completata, dialogo finale, avvio dell'applicazione e apertura delle note di rilascio. Gli installer non sono firmati digitalmente: Windows SmartScreen può mostrare un avviso al primo avvio.

### Compatibilità

- Windows 10 e Windows 11 a 64 bit.
- Runtime Java incluso negli installer; non è richiesta un'installazione separata di Java.
- I dati locali restano nella cartella utente `.kiwigrabber`; la correzione crea automaticamente la cartella necessaria quando non esiste.
- L'installer sostituisce una 1.0.0 già installata; impostazioni e dati in `.kiwigrabber` non vengono modificati dall'installer.

## English

Windows maintenance release. This version fixes startup on new installations, improves error reporting, and adds optional actions at the end of installation.

### Changes and fixes

- Automatically creates the data directory before opening the SQLite database, avoiding `SQLITE_CANTOPEN` on first launch.
- Initializes logging earlier and handles startup errors, including uncaught exceptions. When possible, users see a message with copyable details to share with support.
- Improves exception logging across application components so operational errors are not silently lost.
- At the end of Windows installation, users can independently choose whether to launch KiwiSDR Grabber and/or open this version's release notes in a browser.
- The application no longer opens a console window alongside the main window.

### Installers

Attached to the GitHub Release [`v1.0.1`](https://github.com/Gig81/KiwiSDR-Grabber/releases/tag/v1.0.1):

- `KiwiSDRRecorder-1.0.1.exe` — recommended installer.
- `KiwiSDRRecorder-1.0.1.msi` — Windows Installer package, also suitable for managed or silent installs (`msiexec /i KiwiSDRRecorder-1.0.1.msi /qn`).
- `SHA256SUMS.txt` — SHA-256 checksums of the installers.
- `LICENSE.txt`, `LICENSE-LGPL-EXCEPTION.md`, `third-party-licenses-1.0.1.zip`, `lgpl-sources-1.0.1.zip` — license, third-party notices, and sources of the LGPL libraries.

The `.exe` installer was tested in a clean Windows Sandbox: installation, final dialog, application launch, and release-notes link. The installers are not digitally signed, so Windows SmartScreen may show a warning on first run.

### Compatibility

- Windows 10 and Windows 11, 64-bit.
- The installers include a Java runtime; a separate Java installation is not required.
- Local data remains under the user's `.kiwigrabber` directory; the fix creates the required directory when it does not exist.
- The installer replaces an existing 1.0.0 installation; settings and data in `.kiwigrabber` are not modified by the installer.
