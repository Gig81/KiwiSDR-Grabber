# Distribuzione e rilasci

## Canale ufficiale

- Repository ufficiale: [Gig81/KiwiSDR-Grabber](https://github.com/Gig81/KiwiSDR-Grabber).
- I pacchetti si distribuiscono solo tramite **GitHub Releases** di questo repository. Ogni release contiene gli installer, gli hash SHA-256, la licenza, gli avvisi di terze parti e i sorgenti delle librerie LGPL.
- I pacchetti Android `.apk` / `.aab` e la relativa firma sono rinviati; vedere [piani/ANDROID.md](./piani/ANDROID.md).

## Stato della versione desktop

- `1.0.1` è la release Windows pubblicata attualmente; la `1.0.0` resta disponibile nelle GitHub Releases.
- I file di una release si preparano in una cartella **fuori dal repository** (o in una cartella `Rilascio*/`, ignorata da git) e non vanno committati: gli installer e gli allegati vivono solo nella GitHub Release.
- Gli installer Windows includono il runtime Java. I task ordinari `:composeApp:packageExe` e `:composeApp:packageMsi` sono utili per lo sviluppo; per le release usare `:composeApp:packageExeRelease` e `:composeApp:packageMsiRelease`, che aggiungono la scelta post-installazione. Gli output si trovano in `composeApp\build\compose\binaries\main`. Per le release pubbliche usare Eclipse Temurin (vedi [TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md](./TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md)).
- La 1.0.1 corregge la creazione della cartella dati SQLite al primo avvio, anticipa l'inizializzazione del logging e migliora la segnalazione delle eccezioni non gestite. Le note sono in [RELEASE_NOTES_1.0.1.md](./RELEASE_NOTES_1.0.1.md); la 1.0.0 è archiviata in [releases/](./releases/README.md).
- Indirizzo e password KiwiSDR sono configurabili dall'utente, anche nel setup iniziale. Password KiwiSDR e token del bot Telegram vengono salvati nelle impostazioni locali cifrati con Windows DPAPI, legati all'account Windows corrente; i valori in chiaro delle versioni precedenti vengono cifrati al primo avvio. Se il file viene copiato su un altro account o PC, le credenziali vanno reinserite.
- La libreria Vosk è inclusa; il modello linguistico no: l'utente lo scarica dal sito ufficiale e sceglie la cartella nell'app.
- Se il server rifiuta l'autenticazione (`badp=1`), l'app spiega che la causa può essere la password errata oppure l'esaurimento dei canali e indica di controllare le impostazioni.

## Attivazione offline

L'attivazione delle funzioni avanzate (registrazione, VAD, trascrizione, storico utenti, notifiche Telegram) è gratuita. L'utente copia il Machine ID dalla sezione Licenza dell'app e lo comunica all'autore tramite il canale ufficiale. Il Machine ID deriva dal `MachineGuid` di Windows: non cambia con rete, VPN o rinomina del PC, ma cambia reinstallando Windows, nel qual caso va richiesta una nuova attivazione.

L'autore genera offline un codice `KSR1`, firmato con una chiave privata Ed25519 conservata separatamente da questo repository. L'utente inserisce il codice nell'app, che lo verifica localmente con la chiave pubblica inclusa (`LicenseManager.kt`); non avviene alcuna comunicazione con server dell'autore. Una nuova coppia di chiavi richiede di aggiornare la chiave pubblica nell'app e di distribuire una nuova versione.

## Licenza

Il codice originale è distribuito secondo la [PolyForm Noncommercial License 1.0.0](../LICENSE): una licenza *source-available*, non open source, che consente uso, modifica e ridistribuzione per scopi non commerciali e non consente l'uso commerciale. Per le librerie LGPL distribuite con l'app vale il [permesso supplementare](../LICENSE-LGPL-EXCEPTION.md) richiesto dalla sezione 6 della LGPL 2.1. Le dipendenze mantengono le proprie licenze: vedi [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).

## Checklist di rilascio

Dalla versione successiva alla `1.0.1` gli installer pubblicati e i relativi allegati si producono **solo** con il workflow GitHub Actions `Release` (`.github/workflows/release.yml`). Non caricare mai su una release file compilati in locale: non avrebbero l'attestazione di provenienza e il loro hash non sarebbe in `SHA256SUMS.txt`. La build locale con `packageMsiRelease`/`packageExeRelease` resta per lo sviluppo e per provare modifiche al packaging.

1. Scegliere una nuova versione; non modificare mai il contenuto o gli asset di un tag già pubblicato. Aggiornare `appVersion` in `gradle.properties` e creare `docs/RELEASE_NOTES_<versione>.md` dalla [versione modello](./RELEASE_TEMPLATE.md): il workflow usa questo file come testo della release e si ferma se manca.
2. Se le dipendenze sono cambiate, aggiornare [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) e `docs/licenses/` seguendo la sezione "Manutenzione" di quel documento. Se cambiano le librerie LGPL incluse, aggiornare anche [LICENSE-LGPL-EXCEPTION.md](../LICENSE-LGPL-EXCEPTION.md) e l'elenco in `scripts/release/Prepare-ReleaseAssets.ps1`.
3. Eseguire `.\gradlew.bat :shared:desktopTest :composeApp:compileKotlinDesktop`, fare commit e push su `main` e attendere che il workflow `CI` sia verde.
4. Facoltativo, consigliato se è cambiato il packaging: avviare `Release` da *Actions → Release → Run workflow* su `main`, scaricare l'artifact `release-assets-<versione>` e fare una prima prova.
5. Creare il tag annotato sul commit verificato e inviarlo:
   ```powershell
   git tag -a v<versione> -m "Release <versione>"
   git push origin v<versione>
   ```
   Il workflow `Release` verifica che il tag coincida con `appVersion`, esegue i test, compila gli installer con Eclipse Temurin 21, controlla che il runtime incluso abbia licenza GPL, prepara gli allegati (installer, `SHA256SUMS.txt`, `LICENSE`, `LICENSE-LGPL-EXCEPTION.md`, avvisi di terze parti, sorgenti LGPL), crea le attestazioni di provenienza e infine una GitHub Release **bozza**.
6. Scaricare dalla bozza i due installer e collaudarli su una macchina o VM pulita (Windows Sandbox): installazione interattiva; dialogo finale con le opzioni indipendenti di avvio e apertura delle note; annullamento del dialogo; installazione silenziosa senza dialogo; aggiornamento di una versione precedente; primo avvio e creazione del database. Verificare inoltre logging, messaggio copiabile in caso di errore, licenza, credenziali, notifiche e trascrizione.
7. Sui file scaricati dalla bozza verificare hash e provenienza (vedi [Verifica degli allegati](#verifica-degli-allegati)).
8. Inviare i due installer a Microsoft (vedi [Invio degli installer a Microsoft](#invio-degli-installer-a-microsoft)).
9. Pubblicare la bozza dall'interfaccia web di GitHub, poi riscaricare un installer pubblicato e confrontarne l'hash con `SHA256SUMS.txt`. Non dichiarare pubblicata la release finché questa verifica non è completata.
10. Aggiornare [VERSION_HISTORY.md](./VERSION_HISTORY.md), README (sezioni IT ed EN), [USER_GUIDE.md](./USER_GUIDE.md), [docs/README.md](./README.md) e questo documento; spostare le note della versione precedente in [releases/](./releases/README.md). Fare un controllo dei collegamenti e di `git diff --check`.

Se il collaudo fallisce prima della pubblicazione: eliminare la bozza e il tag (`git push --delete origin v<versione>` e `git tag -d v<versione>`), correggere e ripetere dal punto 3. Una release già pubblicata non si modifica: le correzioni ricevono una nuova versione.

### Verifica degli allegati

```powershell
# Hash: confrontare con la riga corrispondente di SHA256SUMS.txt
Get-FileHash -Algorithm SHA256 .\KiwiSDRRecorder-<versione>.msi, .\KiwiSDRRecorder-<versione>.exe

# Provenienza (richiede GitHub CLI: winget install GitHub.cli)
gh attestation verify .\KiwiSDRRecorder-<versione>.msi --repo Gig81/KiwiSDR-Grabber
gh attestation verify .\KiwiSDRRecorder-<versione>.exe --repo Gig81/KiwiSDR-Grabber
```

L'attestazione lega l'hash di ogni allegato al repository, al workflow, al commit e al tag che lo hanno prodotto, firmata tramite Sigstore. Non è una firma Authenticode: Windows non la legge e SmartScreen può comunque mostrare l'avviso. Le attestazioni gratuite richiedono che il repository resti pubblico.

### Invio degli installer a Microsoft

Gli installer non sono firmati, quindi SmartScreen li valuta in base all'hash e ogni nuova versione riparte senza reputazione. A ogni release, prima di pubblicare la bozza:

1. Usare **esattamente** i file scaricati dalla bozza: un installer ricompilato ha un hash diverso e l'esito dell'analisi non vale per lui.
2. Aprire <https://www.microsoft.com/wdsi/filesubmission> e accedere come **Software developer** con un account Microsoft.
3. Inviare separatamente `.msi` ed `.exe`, indicando come prodotto Microsoft Defender SmartScreen (se non disponibile, Microsoft Defender Antivirus) e come motivo un rilevamento o avviso non corretto. Nella descrizione indicare nome e versione dell'app, link alla release, hash SHA-256 e che il file è compilato da GitHub Actions con attestazione di provenienza.
4. Annotare ID di invio ed esito fuori dal repository. Dopo qualche giorno verificare l'esito; non reinviare lo stesso file.

L'invio è gratuito ma non garantisce la rimozione dell'avviso: la documentazione per gli utenti continua a indicare che l'installer non è firmato. Se il portale cambia voci o percorso, aggiornare questa sezione.

### Preparazione manuale degli allegati

Lo script usato dal workflow si può eseguire anche in locale, dopo `:composeApp:packageMsiRelease :composeApp:packageExeRelease`:

```powershell
.\scripts\release\Prepare-ReleaseAssets.ps1 -Version <versione>
```

Crea `Rilascio-<versione>\` (ignorata da git). Le versioni delle librerie LGPL sono ricavate dai jar inclusi nell'app image. Gli allegati prodotti così servono solo per prove: non vanno pubblicati.

## Possibili evoluzioni

- La firma digitale Authenticode degli installer non è prevista: i certificati sono a pagamento e il programma gratuito SignPath Foundation richiede una licenza approvata OSI. Verifica di hash e attestazioni e l'invio a Microsoft ne fanno le veci.
- Per Android, coordinare firma e distribuzione con quanto definito in [piani/ANDROID.md](./piani/ANDROID.md).
