# KiwiSDR Grabber & Station Recorder 1.3.0

**Stato: bozza, in collaudo.**

**[Italiano](#italiano) | [English](#english)**

## Italiano

Release minore per Windows. Introduce le funzioni richieste dagli utenti: **tema chiaro**, **scansione di un intervallo di frequenze**, **equalizzatore e filtri DSP** e un collegamento alla **mappa dei ricevitori SDR di rx-tx.info**.

### Novità (Windows)

- **Tema chiaro.** In Impostazioni → *Aspetto & tema dell'interfaccia* scegli *Scuro* (predefinito), *Chiaro* o *Sistema*, che segue il tema delle app di Windows. Il cambio è immediato. Il display della frequenza, lo spettro e il waterfall restano scuri, come il pannello di una radio.
- **Scansione di un intervallo di frequenze.** Il pannello *Scansione* della scheda Preferiti ha ora due modalità: *Canali* (come prima) e *Intervallo*, con frequenza iniziale e finale, passo, modo e tempo di ascolto per passo. La scansione si ferma sui segnali sopra la soglia dello squelch e riparte quando terminano. Puoi escludere le frequenze disturbate e ritrovare i segnali della sessione in un elenco, da cui sintonizzarli o aggiungerli ai preferiti. Richiede l'attivazione.
- **Equalizzatore e filtri DSP.** Nuovo pannello *DSP* nella scheda Audio: passa-alto, passa-basso, notch, riduzione del rumore ed equalizzatore a 7 bande, con preset (*Voce SSB*, *AM broadcast*, *CW stretto*, *Riduzione QRM*) e confronto A/B. Si applica al solo ascolto oppure all'ascolto e alle nuove registrazioni; le registrazioni già salvate non vengono modificate. Richiede l'attivazione.
- **Mappa dei ricevitori SDR.** In Impostazioni → Server, il pulsante *Mappa ricevitori SDR (rx-tx.info)* apre nel browser la mappa di rx-tx.info, accanto alla mappa pubblica KiwiSDR. L'app si collega solo ai ricevitori KiwiSDR.

### Modifiche

- Il pannello della scheda Audio con soft mute, pre-buffer e hang time si chiama ora *Squelch: anti-pop e hang time* (prima *DSP, anti-pop e hang time*); il comportamento non cambia.
- Il pannello *Scansione canali* è diventato *Scansione*, con la scelta *Canali* / *Intervallo*.

### Compatibilità e aggiornamento

- Windows: Windows 10 e Windows 11 a 64 bit, runtime Java incluso. L'installer sostituisce la versione installata mantenendo impostazioni, preferiti e dati in `.kiwigrabber`. Nessuna migrazione richiesta: le nuove impostazioni partono con il tema scuro, il DSP spento e l'intervallo di scansione sulla banda CB.
- Android: nessuna novità funzionale in questa versione; le nuove funzioni sono solo per Windows.

### File della release

| File | Descrizione |
|---|---|
| `KiwiSDRRecorder-1.3.0.exe` | Installer Windows EXE |
| `KiwiSDRRecorder-1.3.0.msi` | Installer Windows MSI (anche per installazioni silenziose: `msiexec /i KiwiSDRRecorder-1.3.0.msi /qn`) |
| `KiwiSDRGrabber-1.3.0.apk` | App Android (Android 10 o successivo) |
| `SHA256SUMS.txt` | Hash SHA-256 degli asset pubblicati |
| `third-party-licenses-1.3.0.zip` | Avvisi e licenze dei componenti di terze parti |
| `lgpl-sources-1.3.0.zip` | Sorgenti delle librerie LGPL incluse nell'installer Windows |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Licenza del progetto e permesso supplementare LGPL |

Gli installer Windows non sono firmati digitalmente: Windows potrebbe mostrare un avviso SmartScreen. L'APK è firmato con il certificato dell'app (impronta nella guida utente). Tutti gli allegati sono compilati da GitHub Actions con attestazione di provenienza; per verificare hash e provenienza vedi la [guida utente](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifica-dellinstaller).

## English

Minor release for Windows. It adds features requested by users: a **light theme**, **frequency range scanning**, an **equalizer and DSP filters**, and a link to the **rx-tx.info SDR receiver map**.

### New (Windows)

- **Light theme.** In Settings → *Appearance & interface theme* choose *Dark* (default), *Light* or *System*, which follows the Windows app theme. The change applies immediately. The frequency display, spectrum and waterfall stay dark, like a radio front panel.
- **Frequency range scanning.** The *Scan* panel in the Favorites tab now has two modes: *Channels* (as before) and *Range*, with start and end frequency, step, mode and listening time per step. Scanning stops on signals above the squelch threshold and resumes when they end. You can exclude noisy frequencies and find the session's signals in a list, from which you can tune to them or add them to favorites. Requires activation.
- **Equalizer and DSP filters.** New *DSP* panel in the Audio tab: high-pass, low-pass, notch, noise reduction and a 7-band equalizer, with presets (*SSB voice*, *AM broadcast*, *Narrow CW*, *QRM reduction*) and A/B comparison. It applies to listening only, or to listening and new recordings; recordings already saved are not changed. Requires activation.
- **SDR receiver map.** In Settings → Server, the *SDR receiver map (rx-tx.info)* button opens the rx-tx.info map in your browser, next to the KiwiSDR public map. The app connects only to KiwiSDR receivers.

### Changes

- The Audio tab panel with soft mute, pre-buffer and hang time is now called *Squelch: anti-pop and hang time* (previously *DSP, anti-pop and hang time*); its behavior is unchanged.
- The *Channel scan* panel is now *Scan*, with a *Channels* / *Range* switch.

### Compatibility and upgrade

- Windows: Windows 10 and Windows 11, 64-bit, Java runtime included. The installer replaces the installed version and keeps settings, favorites, and data in `.kiwigrabber`. No migration required: new settings start with the dark theme, DSP off and the scan range on the CB band.
- Android: no functional changes in this version; the new features are Windows-only.

### Release files

| File | Description |
|---|---|
| `KiwiSDRRecorder-1.3.0.exe` | Windows EXE installer |
| `KiwiSDRRecorder-1.3.0.msi` | Windows MSI installer (also for silent installs: `msiexec /i KiwiSDRRecorder-1.3.0.msi /qn`) |
| `KiwiSDRGrabber-1.3.0.apk` | Android app (Android 10 or later) |
| `SHA256SUMS.txt` | SHA-256 hashes for published assets |
| `third-party-licenses-1.3.0.zip` | Third-party notices and licenses |
| `lgpl-sources-1.3.0.zip` | Source code of the LGPL libraries bundled with the Windows installer |
| `LICENSE`, `LICENSE-LGPL-EXCEPTION.md` | Project license and additional LGPL permission |

The Windows installers are not digitally signed, so Windows may show a SmartScreen warning. The APK is signed with the app certificate (fingerprint in the user guide). All attachments are built by GitHub Actions with build provenance attestations; see the [user guide](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md#verifying-the-installer) to verify checksums and provenance.
