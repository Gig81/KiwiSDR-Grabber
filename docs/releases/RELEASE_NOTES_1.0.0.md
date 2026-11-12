# KiwiSDR Grabber & Station Recorder 1.0.0

> Documento storico della release 1.0.0. Per la versione più recente, consulta la [cronologia delle versioni](../VERSION_HISTORY.md).

Prima release pubblica desktop di KiwiSDR Grabber per Windows.

**[Italiano](#italiano) | [English](#english)**

## Italiano

KiwiSDR Grabber ti permette di collegarti a un ricevitore KiwiSDR, sintonizzare e ascoltare segnali radio, visualizzare spettro e waterfall e, dopo l'attivazione, registrare e archiviare l'audio.

### Funzionalità

- Connessione a un ricevitore KiwiSDR configurabile, con sintonia e modalità AM, USB, LSB, CW e NFM.
- Visualizzazione dello spettro in tempo reale e waterfall, con sintonia tramite clic.
- Preferiti e preset di frequenza per la selezione rapida dei canali.
- Registrazione audio MP3 o WAV, continua o suddivisa in segmenti temporali.
- Squelch RF e rilevamento dell'attività vocale (VAD) per filtrare ascolto e registrazioni.
- Trascrizione offline delle registrazioni con Vosk e modello vocale installato dall'utente.
- Monitoraggio della stazione e degli utenti collegati.
- Notifiche Telegram configurabili per attività rilevata e parole chiave trascritte.

Registrazione, VAD, trascrizione, storico utenti e notifiche Telegram richiedono l'attivazione gratuita descritta sotto. Le funzioni di base non hanno un limite temporale.

### Requisiti

- Windows 10 o Windows 11 a 64 bit.
- Non è necessario installare Java: il pacchetto include un runtime Java.
- Per ascoltare e visualizzare i dati live occorre un ricevitore KiwiSDR raggiungibile in rete.

### Installazione e primo avvio

1. Apri la [pagina della release 1.0.0](https://github.com/Gig81/KiwiSDR-Grabber/releases/tag/v1.0.0).
2. Scarica **uno solo** dei due installer:
   - `KiwiSDRRecorder-1.0.0.exe` per l'installer EXE;
   - `KiwiSDRRecorder-1.0.0.msi` per l'installer MSI.
3. Scarica anche `SHA256SUMS.txt`. Prima di eseguire l'installer, verifica che il suo hash SHA-256 corrisponda al valore riportato nella tabella seguente. In PowerShell, dalla cartella in cui hai scaricato il file, esegui il comando relativo:

   ```powershell
   Get-FileHash .\KiwiSDRRecorder-1.0.0.exe -Algorithm SHA256
   ```

   oppure:

   ```powershell
   Get-FileHash .\KiwiSDRRecorder-1.0.0.msi -Algorithm SHA256
   ```

   Confronta il valore `Hash` con il valore previsto per lo stesso file:

   | Installer | SHA-256 |
   |---|---|
   | `KiwiSDRRecorder-1.0.0.exe` | `b5d9f64f4b316cb50b5a638737eb9942d8a994421349c78ef64bf91df734dfd5` |
   | `KiwiSDRRecorder-1.0.0.msi` | `03e11114404e48629734ed79a238bcaae6c9ea29c233d08676d9f9be02012d73` |

4. Se l'hash non corrisponde, non eseguire il file: scaricalo di nuovo dalla pagina ufficiale e verifica ancora.
5. Seleziona l'installer scelto e segui le istruzioni visualizzate da Windows. Non è necessario installare Java separatamente.
6. Al primo avvio inserisci il nominativo e l'indirizzo del KiwiSDR nella configurazione guidata. Inserisci la password solo se il ricevitore la richiede. Telegram è facoltativo e può essere configurato in seguito.
7. Completa la configurazione e seleziona l'icona di accensione nella console radio per collegarti. L'app non si collega automaticamente al termine della configurazione iniziale.

L'installer non è firmato digitalmente; Windows potrebbe mostrare un avviso relativo all'editore o a SmartScreen. Verifica di aver scaricato il file dalla release ufficiale e che l'hash corrisponda prima di decidere se proseguire.

Per ulteriori istruzioni, consulta la [Guida rapida e manuale utente](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md).

### Attivazione delle funzioni avanzate

L'attivazione è gratuita e offline. Nella sezione **Gestione licenza** dell'app, copia il Machine ID e richiedi all'autore un codice di attivazione tramite il canale indicato nell'applicazione. Inserisci il codice ricevuto nella stessa sezione. La licenza è verificata localmente ed è legata a quel computer; non viene effettuata una richiesta di attivazione a un server.

### Trascrizione Vosk

La libreria applicativa Vosk è inclusa, ma il modello vocale no. Scarica e decomprimi un modello dalla [pagina ufficiale dei modelli Vosk](https://alphacephei.com/vosk/models), quindi seleziona nell'app la cartella estratta che contiene la sottocartella `am`. Le impostazioni attuali dell'applicazione indicano un modello italiano. La trascrizione viene eseguita sul computer.

### Sicurezza e note

- La password del ricevitore e il token Telegram, se configurati, sono salvati nelle impostazioni locali cifrati con Windows DPAPI e legati all'account Windows corrente. Se trasferisci il file delle impostazioni a un altro account o computer, potrebbe essere necessario reinserire le credenziali.
- Il protocollo KiwiSDR utilizzato dall'applicazione non cifra la password durante il trasferimento. Usa una password dedicata che non riutilizzi altrove.
- Un rifiuto di accesso `badp=1` può indicare una password non corretta oppure l'esaurimento dei canali del ricevitore.
- L'applicazione è destinata alla ricezione e al monitoraggio; non trasmette segnali radio.

### File inclusi nella release

| File | Contenuto |
|---|---|
| `KiwiSDRRecorder-1.0.0.exe` | Installer Windows EXE |
| `KiwiSDRRecorder-1.0.0.msi` | Installer Windows MSI |
| `SHA256SUMS.txt` | Hash SHA-256 degli installer |
| `LICENSE.txt` | Licenza dell'applicazione |
| `LICENSE-LGPL-EXCEPTION.md` | Permesso aggiuntivo per le librerie LGPL |
| `third-party-licenses-1.0.0.zip` | Avvisi e testi delle licenze di terze parti |
| `lgpl-sources-1.0.0.zip` | Sorgenti delle librerie LGPL distribuite con l'app |

Il codice originale dell'applicazione è distribuito secondo la [PolyForm Noncommercial License 1.0.0](https://github.com/Gig81/KiwiSDR-Grabber/blob/v1.0.0/LICENSE). Il [permesso aggiuntivo LGPL](https://github.com/Gig81/KiwiSDR-Grabber/blob/v1.0.0/LICENSE-LGPL-EXCEPTION.md) riguarda solo le librerie elencate e non concede diritti di uso commerciale. I componenti di terze parti restano soggetti alle rispettive licenze; vedi gli [Avvisi di terze parti](https://github.com/Gig81/KiwiSDR-Grabber/blob/v1.0.0/docs/THIRD_PARTY_NOTICES.md).

**[Cronologia completa delle modifiche](https://github.com/Gig81/KiwiSDR-Grabber/commits/v1.0.0)**

## English

This is the first public desktop release of KiwiSDR Grabber for Windows.

KiwiSDR Grabber connects to a KiwiSDR receiver so you can tune and listen to radio signals, view the spectrum and waterfall, and—after activation—record and archive audio.

### Features

- Connect to a configurable KiwiSDR receiver; tune frequencies and select AM, USB, LSB, CW, or NFM.
- View the live spectrum and waterfall, with click-to-tune.
- Use favorites and frequency presets to select channels quickly.
- Record audio as MP3 or WAV, continuously or in timed segments.
- Use RF squelch and Voice Activity Detection (VAD) to gate listening and recordings.
- Transcribe recordings offline with Vosk and a speech model installed by the user.
- Monitor the receiver and its connected users.
- Configure Telegram notifications for detected activity and keywords found in transcripts.

Recording, VAD, transcription, user history, and Telegram alerts require the free activation described below. The base features have no time limit.

### Requirements

- Windows 10 or Windows 11, 64-bit.
- Java does not need to be installed separately; a Java runtime is included.
- A reachable KiwiSDR receiver is required for live listening and receiver data.

### Installation and first launch

1. Open the [1.0.0 release page](https://github.com/Gig81/KiwiSDR-Grabber/releases/tag/v1.0.0).
2. Download **one** of the two installers:
   - `KiwiSDRRecorder-1.0.0.exe` for the EXE installer;
   - `KiwiSDRRecorder-1.0.0.msi` for the MSI installer.
3. Download `SHA256SUMS.txt` as well. Before running the installer, verify its SHA-256 hash against the value below. In PowerShell, from the folder containing the downloaded file, run the matching command:

   ```powershell
   Get-FileHash .\KiwiSDRRecorder-1.0.0.exe -Algorithm SHA256
   ```

   or:

   ```powershell
   Get-FileHash .\KiwiSDRRecorder-1.0.0.msi -Algorithm SHA256
   ```

   Compare the `Hash` value with the expected hash for that file:

   | Installer | SHA-256 |
   |---|---|
   | `KiwiSDRRecorder-1.0.0.exe` | `b5d9f64f4b316cb50b5a638737eb9942d8a994421349c78ef64bf91df734dfd5` |
   | `KiwiSDRRecorder-1.0.0.msi` | `03e11114404e48629734ed79a238bcaae6c9ea29c233d08676d9f9be02012d73` |

4. If the hash does not match, do not run the file. Download it again from the official release page and verify it once more.
5. Run the installer you selected and follow the Windows prompts. You do not need to install Java separately.
6. On first launch, enter your callsign and KiwiSDR address in the setup dialog. Enter a password only if the receiver requires one. Telegram is optional and can be configured later.
7. Complete setup, then select the power icon in the radio console to connect. The application does not connect automatically after initial setup.

The installer is not digitally signed, so Windows may display a publisher or SmartScreen warning. Verify that you downloaded it from the official release and that its hash matches before deciding whether to continue.

For further instructions, see the [Quick Start and User Guide](https://github.com/Gig81/KiwiSDR-Grabber/blob/main/docs/USER_GUIDE.md).

### Activating advanced features

Activation is free and performed offline. In the application's **License Management** section, copy the Machine ID and request an activation code from the project owner using the channel indicated in the application. Enter the code in the same section. The license is checked locally and is tied to that computer; no activation request is sent to a server.

### Vosk transcription

The Vosk application library is included, but the speech model is not. Download and extract a model from the [official Vosk models page](https://alphacephei.com/vosk/models), then select the extracted folder containing the `am` subfolder in the application. The current application settings specify an Italian model. Transcription runs on your computer.

### Security and notes

- The receiver password and Telegram token, if configured, are stored in the local settings protected with Windows DPAPI for the current Windows account. If you move the settings file to another account or computer, you may need to enter the credentials again.
- The KiwiSDR protocol used by the application does not encrypt the password in transit. Use a dedicated password that you do not reuse elsewhere.
- A `badp=1` access rejection can indicate either an incorrect password or that the receiver has no available user channels.
- The application is for reception and monitoring; it does not transmit radio signals.

### Files included with this release

| File | Contents |
|---|---|
| `KiwiSDRRecorder-1.0.0.exe` | Windows EXE installer |
| `KiwiSDRRecorder-1.0.0.msi` | Windows MSI installer |
| `SHA256SUMS.txt` | Installer SHA-256 hashes |
| `LICENSE.txt` | Application license |
| `LICENSE-LGPL-EXCEPTION.md` | Additional permission for LGPL libraries |
| `third-party-licenses-1.0.0.zip` | Third-party notices and license texts |
| `lgpl-sources-1.0.0.zip` | Source code for the bundled LGPL libraries |

The original application code is offered under the [PolyForm Noncommercial License 1.0.0](https://github.com/Gig81/KiwiSDR-Grabber/blob/v1.0.0/LICENSE). The [additional LGPL permission](https://github.com/Gig81/KiwiSDR-Grabber/blob/v1.0.0/LICENSE-LGPL-EXCEPTION.md) applies only to the listed libraries and does not grant commercial-use rights. Third-party components remain subject to their own licenses; see [Third-Party Notices](https://github.com/Gig81/KiwiSDR-Grabber/blob/v1.0.0/docs/THIRD_PARTY_NOTICES.md).

**[Full changelog](https://github.com/Gig81/KiwiSDR-Grabber/commits/v1.0.0)**
