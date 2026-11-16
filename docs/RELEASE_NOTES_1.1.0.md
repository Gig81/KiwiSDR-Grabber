# KiwiSDR Grabber & Station Recorder 1.1.0

**Stato: in preparazione.**

**[Italiano](#italiano) | [English](#english)**

## Italiano

Release minore per Windows. Introduce la scansione dei canali preferiti, richiesta dagli utenti, ed è la prima versione compilata e pubblicata da GitHub Actions con attestazione di provenienza degli allegati.

### Novità

- **Scansione dei canali** (funzione avanzata, richiede l'attivazione): nella scheda **Preferiti** si spuntano i canali da includere e il pannello **SCANSIONE CANALI** li ascolta a turno.
  - Tempo di ascolto per canale regolabile da 1 a 30 secondi, salvato nelle impostazioni.
  - Quando il segnale supera la soglia dello squelch la scansione resta sul canale; con **Riprendi dopo il segnale** riparte da sola al termine della trasmissione e dell'hang time, altrimenti si ferma su quel canale.
  - **Spunta elenco** seleziona i canali visibili con filtro e ricerca attivi; **Togli spunte** azzera la selezione.
  - Durante la scansione il display VFO mostra `SCAN`. Sintonizzare a mano, scegliere un preferito o disconnettersi interrompe la scansione.
  - La selezione resta salvata con i preferiti e viene conservata anche dopo **Sincronizza** per i canali con stessa frequenza e modo.
- Modificare un preferito non ne cambia più la selezione per la scansione.

### Compatibilità e aggiornamento

- Sistemi supportati: Windows 10 e Windows 11 a 64 bit; runtime Java incluso.
- L'installer sostituisce una versione 1.0.x già installata; impostazioni, preferiti e dati in `.kiwigrabber` vengono mantenuti. I preferiti esistenti partono senza canali selezionati per la scansione.
- Nessuna migrazione richiesta.

### File della release

| File | Descrizione |
|---|---|
| `KiwiSDRRecorder-1.1.0.exe` | Installer Windows EXE |
| `KiwiSDRRecorder-1.1.0.msi` | Installer Windows MSI (anche per installazioni silenziose: `msiexec /i KiwiSDRRecorder-1.1.0.msi /qn`) |
| `SHA256SUMS.txt` | Hash SHA-256 degli asset pubblicati |
| `third-party-licenses-1.1.0.zip` | Avvisi e licenze dei componenti di terze parti |
| `lgpl-sources-1.1.0.zip` | Sorgenti delle librerie LGPL incluse |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Licenza del progetto e permesso supplementare LGPL |

Gli installer non sono firmati digitalmente: Windows potrebbe mostrare un avviso SmartScreen. Sono compilati da GitHub Actions e ogni allegato ha un'attestazione di provenienza; per verificare hash e provenienza vedi la [guida utente](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifica-dellinstaller).

## English

Windows minor release. It adds favorite-channel scanning, requested by users, and is the first version built and published by GitHub Actions with build provenance attestations for its attachments.

### New features

- **Channel scanning** (advanced feature, requires activation): tick the channels to include in the **Favorites** tab (`Preferiti`) and the **Channel scan** panel (`SCANSIONE CANALI`) listens to them in turn.
  - Listening time per channel adjustable from 1 to 30 seconds, saved in the settings.
  - When a signal exceeds the squelch threshold, scanning stays on the channel; with **Resume after signal** (`Riprendi dopo il segnale`) it resumes automatically after the transmission and hang time end, otherwise it stops on that channel.
  - **Select list** (`Spunta elenco`) selects the channels visible with the current filter and search; **Clear selection** (`Togli spunte`) unticks all channels.
  - While scanning, the VFO display shows `SCAN`. Tuning manually, choosing a favorite, or disconnecting stops scanning.
  - The selection is saved with the favorites and is kept after **Sync** (`Sincronizza`) for channels with the same frequency and mode.
- Editing a favorite no longer changes its scan selection.

### Compatibility and upgrade

- Supported systems: Windows 10 and Windows 11, 64-bit; Java runtime included.
- The installer replaces an installed 1.0.x version; settings, favorites, and data in `.kiwigrabber` are kept. Existing favorites start with no channels selected for scanning.
- No migration required.

### Release files

| File | Description |
|---|---|
| `KiwiSDRRecorder-1.1.0.exe` | Windows EXE installer |
| `KiwiSDRRecorder-1.1.0.msi` | Windows MSI installer (also for silent installs: `msiexec /i KiwiSDRRecorder-1.1.0.msi /qn`) |
| `SHA256SUMS.txt` | SHA-256 hashes for published assets |
| `third-party-licenses-1.1.0.zip` | Third-party notices and licenses |
| `lgpl-sources-1.1.0.zip` | Source code of the bundled LGPL libraries |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Project license and additional LGPL permission |

The installers are not digitally signed, so Windows may show a SmartScreen warning. They are built by GitHub Actions and every attachment has a build provenance attestation; see the [user guide](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifying-the-installer) to verify checksums and provenance.
