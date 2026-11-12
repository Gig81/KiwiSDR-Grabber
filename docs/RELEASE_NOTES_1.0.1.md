# KiwiSDR Grabber & Station Recorder 1.0.1

**Stato: preparazione della release; non ancora pubblicata.**

**[Italiano](#italiano) | [English](#english)**

## Italiano

Release di manutenzione per Windows, in preparazione. Questa versione corregge l'avvio su nuove installazioni, rende più affidabile la segnalazione degli errori e aggiunge scelte facoltative al termine dell'installazione.

### Novità e correzioni

- Creazione automatica della cartella dati prima dell'apertura del database SQLite, per evitare l'errore `SQLITE_CANTOPEN` al primo avvio.
- Inizializzazione anticipata del log e gestione degli errori di avvio, comprese le eccezioni non gestite. Quando possibile, l'utente riceve un messaggio con i dettagli copiabili da inviare all'assistenza.
- Registrazione più completa delle eccezioni nei componenti applicativi, evitando che errori operativi vengano ignorati senza traccia.
- Al termine dell'installazione Windows, l'utente può scegliere separatamente se avviare KiwiSDR Grabber e/o aprire nel browser le note di rilascio della versione installata.

### Installer

La generazione locale di entrambi i pacchetti è riuscita; il MSI è stato ispezionato per verificare la presenza dell'azione post-installazione:

- `KiwiSDRRecorder-1.0.1.msi`
- `KiwiSDRRecorder-1.0.1.exe`

La pubblicazione, il tag `v1.0.1`, gli hash e gli allegati saranno completati solo al momento della release GitHub. Non usare questa nota come conferma che i pacchetti siano già pubblici.
Il collaudo manuale dell'installazione e del dialogo su una macchina Windows pulita resta un controllo pre-pubblicazione.

### Compatibilità

- Windows 10 e Windows 11 a 64 bit.
- Runtime Java incluso negli installer; non è richiesta un'installazione separata di Java.
- I dati locali restano nella cartella utente `.kiwigrabber`; la correzione crea automaticamente la cartella necessaria quando non esiste.

## English

Windows maintenance release in preparation. This version fixes startup on new installations, improves error reporting, and adds optional actions at the end of installation.

### Changes and fixes

- Automatically creates the data directory before opening the SQLite database, avoiding `SQLITE_CANTOPEN` on first launch.
- Initializes logging earlier and handles startup errors, including uncaught exceptions. When possible, users see a message with copyable details to share with support.
- Improves exception logging across application components so operational errors are not silently lost.
- At the end of Windows installation, users can independently choose whether to launch KiwiSDR Grabber and/or open this version's release notes in a browser.

### Installers

Local packaging of both installers succeeded; the MSI was inspected to confirm that the post-install action is present:

- `KiwiSDRRecorder-1.0.1.msi`
- `KiwiSDRRecorder-1.0.1.exe`

Publication, the `v1.0.1` tag, checksums, and release attachments will be completed only when the GitHub release is published. These notes do not mean that the packages are publicly available yet.
Manual installation and dialog testing on a clean Windows machine remains a pre-publication check.

### Compatibility

- Windows 10 and Windows 11, 64-bit.
- The installers include a Java runtime; a separate Java installation is not required.
- Local data remains under the user's `.kiwigrabber` directory; the fix creates the required directory when it does not exist.
