# KiwiSDR Grabber & Station Recorder 1.0.0

> Prima release pubblica. Prima di pubblicarla completa il collaudo dell'installer (sezione "Verifica della build") e aggiungi gli hash SHA-256 definitivi.

## Funzionalità

- Ricezione audio, sintonia, waterfall e informazioni live del ricevitore KiwiSDR.
- Registrazione audio, rilevamento VAD e storico disponibili con attivazione.
- Trascrizione offline tramite Vosk con modello linguistico scaricato separatamente dall'utente.
- Notifiche Telegram tramite bot personale configurato dall'utente; la funzione richiede attivazione.
- Licenza attivabile offline, vincolata al Machine ID e verificata con firma Ed25519.
- La versione base non ha un limite temporale.

## Requisiti e note

- Windows 10/11 x64. Il runtime Java (Eclipse Temurin) è incluso nel pacchetto: non serve installare Java.
- L'installer non è firmato digitalmente: Windows SmartScreen può mostrare "Editore sconosciuto". Verifica l'hash SHA-256 del file scaricato, poi scegli *Ulteriori informazioni → Esegui comunque*.
- L'attivazione delle funzioni avanzate è gratuita e offline: copia il Machine ID dalla sezione Licenza e richiedi il codice tramite il canale indicato nell'app.
- La password del ricevitore, se richiesta, e le credenziali Telegram vengono configurate dall'utente nell'app e persistono nel file settings locale cifrate con Windows DPAPI. Il protocollo KiwiSDR qui usato non cifra il trasporto; non riutilizzare password importanti.
- Nel setup iniziale è possibile inserire la password KiwiSDR. Su installazioni esistenti, verificare la password in Impostazioni; un rifiuto `badp=1` può indicare password errata oppure canali occupati.
- L'utente deve scaricare il modello Vosk dal sito ufficiale e selezionarne la cartella nell'app. La presenza dei JAR Vosk/JNA è stata verificata nell'immagine Windows ricostruita da zero; la trascrizione nel pacchetto installato va ancora collaudata.
- Codice sorgente originale sotto PolyForm Noncommercial 1.0.0 (uso non commerciale), con il permesso supplementare per le librerie LGPL (`LICENSE-LGPL-EXCEPTION.md`). I componenti di terze parti e le relative licenze sono elencati in `THIRD_PARTY_NOTICES.md`; i testi integrali e i sorgenti delle librerie LGPL sono allegati alla release.

## Verifica della build

- `:shared:desktopTest`: superato nel working tree.
- `:composeApp:compileKotlinDesktop`: superato nel working tree.
- Generazione EXE/MSI: superata nel working tree.
- Installazione, avvio, verifica della configurazione password su installazioni nuove/esistenti e test delle funzioni avanzate sull'installer che sarà allegato: da completare.

## File di distribuzione

| File | Contenuto |
|---|---|
| `KiwiSDRRecorder-1.0.0.exe` | Installer Windows (EXE) |
| `KiwiSDRRecorder-1.0.0.msi` | Installer Windows (MSI) |
| `SHA256SUMS.txt` | Hash SHA-256 degli installer |
| `LICENSE.txt`, `LICENSE-LGPL-EXCEPTION.md` | Licenza dell'applicazione e permesso supplementare LGPL |
| `third-party-licenses-1.0.0.zip` | `THIRD_PARTY_NOTICES.md` e la cartella `licenses/` con i testi integrali |
| `lgpl-sources-1.0.0.zip` | Sorgenti di JLayer, MP3SPI, Tritonus Share e Jump3r |

Gli hash vanno calcolati sugli installer definitivi, dopo il collaudo; gli hash delle build intermedie non sono hash di release.
