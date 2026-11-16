# Distribuzione e rilasci

## Canale ufficiale

- Repository ufficiale: [Gig81/KiwiSDR-Grabber](https://github.com/Gig81/KiwiSDR-Grabber).
- I pacchetti si distribuiscono solo tramite **GitHub Releases** di questo repository. Ogni release contiene gli installer, gli hash SHA-256, la licenza, gli avvisi di terze parti e i sorgenti delle librerie LGPL.
- Dalla versione `1.2.0` ogni release contiene anche l'APK Android (`KiwiSDRGrabber-<versione>.apk`), firmato con la chiave dell'app (vedi [Versione Android](#versione-android)). Il Play Store è rinviato: l'AAB non viene pubblicato.

## Stato delle versioni

- `1.2.0` è la release pubblicata attualmente: installer Windows e prima versione Android (APK). `1.1.0` è stata la prima prodotta dal workflow `Release` con attestazioni di provenienza; `1.0.1` e `1.0.0` restano disponibili nelle GitHub Releases.
- I file di una release si preparano in una cartella **fuori dal repository** (o in una cartella `Rilascio*/`, ignorata da git) e non vanno committati: gli installer e gli allegati vivono solo nella GitHub Release.
- Gli installer Windows includono il runtime Java. I task ordinari `:composeApp:packageExe` e `:composeApp:packageMsi` sono utili per lo sviluppo; per le release usare `:composeApp:packageExeRelease` e `:composeApp:packageMsiRelease`, che aggiungono la scelta post-installazione. Gli output si trovano in `composeApp\build\compose\binaries\main`. Per le release pubbliche usare Eclipse Temurin (vedi [TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md](./TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md)).
- La 1.2.0 introduce l'app Android e corregge la sincronizzazione dei preferiti del server sul desktop. Le note sono in [RELEASE_NOTES_1.2.0.md](./RELEASE_NOTES_1.2.0.md); le versioni precedenti sono archiviate in [releases/](./releases/README.md).
- Indirizzo e password KiwiSDR sono configurabili dall'utente, anche nel setup iniziale. Password KiwiSDR e token del bot Telegram vengono salvati nelle impostazioni locali cifrati con Windows DPAPI, legati all'account Windows corrente; i valori in chiaro delle versioni precedenti vengono cifrati al primo avvio. Se il file viene copiato su un altro account o PC, le credenziali vanno reinserite.
- La libreria Vosk è inclusa; il modello linguistico no: l'utente lo scarica dal sito ufficiale e sceglie la cartella nell'app.
- Se il server rifiuta l'autenticazione (`badp=1`), l'app spiega che la causa può essere la password errata oppure l'esaurimento dei canali e indica di controllare le impostazioni.

## Attivazione offline

L'attivazione delle funzioni avanzate (registrazione, VAD, trascrizione, storico utenti, scansione dei canali, notifiche Telegram) è gratuita. L'utente copia il Machine ID dalla sezione Licenza dell'app e lo comunica all'autore tramite il canale ufficiale. Il Machine ID deriva dal `MachineGuid` di Windows: non cambia con rete, VPN o rinomina del PC, ma cambia reinstallando Windows, nel qual caso va richiesta una nuova attivazione.

L'autore genera offline un codice `KSR1`, firmato con una chiave privata Ed25519 conservata separatamente da questo repository. L'utente inserisce il codice nell'app, che lo verifica localmente con la chiave pubblica inclusa (`LicenseFormat` in `shared`); non avviene alcuna comunicazione con server dell'autore. Una nuova coppia di chiavi richiede di aggiornare la chiave pubblica nell'app e di distribuire una nuova versione.

Su Android l'ID ha il formato `GRAB-A-…` e deriva da `ANDROID_ID`, che a sua volta dipende dalla **chiave di firma dell'APK**: ogni dispositivo ha la sua licenza, e una licenza desktop non vale su Android. L'ID cambia con un ripristino di fabbrica o un nuovo profilo utente.

## Versione Android

- **Chiavi.** La chiave di firma dell'app (`kiwigrabber-release.jks`, alias `kiwigrabber`) e la chiave di upload per un futuro Play Store (`kiwigrabber-upload.jks`, alias `upload`) sono custodite dall'autore fuori dal repository, con backup offline; generazione e uso locale in [TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md](./TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md#firma-e-rilascio-android). La chiave dell'app non è sostituibile: perderla impedisce gli aggiornamenti e invalida tutte le licenze Android.
- **Certificato dell'app**, SHA-256 `EF:6D:6C:A3:CE:92:C7:A0:47:15:43:A1:FE:48:68:42:47:C6:B7:D4:B7:DD:8C:7E:B3:68:B4:46:EC:20:21:29`. È pubblicato nella guida utente e controllato dal workflow `Release`, che fallisce se l'APK risulta firmato con un altro certificato.
- **CI.** La chiave dell'app è nei segreti dell'environment GitHub `android-release` (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`), accessibile solo dal job Android del workflow `Release` e solo da tag `v*` e da `main`. Se la chiave cambia (non dovrebbe mai succedere) vanno aggiornati segreti, impronta nel workflow, guida utente e questo documento.
- **Aggiornamenti.** Un APK si installa sopra un altro solo se ha la stessa firma e un `versionCode` più alto, ricavato da `appVersion` (`1.2.0` → `10200`).
- **Play Store** (rinviato). Si potrà aggiungere in qualsiasi momento finché l'app non vi è mai stata pubblicata: alla creazione dell'app nella Play Console si carica la propria chiave dell'app con PEPK (Play App Signing) e si registra la chiave di upload, così APK GitHub e app Play hanno la stessa firma e gli stessi ID dispositivo. Un account sviluppatore personale deve prima completare un test chiuso con almeno 12 tester per 14 giorni. Prima della pubblicazione su Play servono anche scheda dello Store, informativa sulla privacy, modulo sulla sicurezza dei dati, dichiarazione del foreground service `mediaPlayback` (con video) e istruzioni per far provare ai revisori le funzioni con attivazione.
- **Verifica dello sviluppatore Android.** Google ha annunciato che anche le app installate fuori da Play dovranno appartenere a sviluppatori registrati, con nome del pacchetto e chiave di firma registrati; l'obbligo parte da alcuni paesi nel 2026 e si estende gradualmente. Prima che riguardi l'Italia va completata la registrazione, altrimenti i dispositivi certificati bloccheranno l'installazione dell'APK.

## Licenza

Il codice originale è distribuito secondo la [PolyForm Noncommercial License 1.0.0](../LICENSE): una licenza *source-available*, non open source, che consente uso, modifica e ridistribuzione per scopi non commerciali e non consente l'uso commerciale. Per le librerie LGPL distribuite con l'app vale il [permesso supplementare](../LICENSE-LGPL-EXCEPTION.md) richiesto dalla sezione 6 della LGPL 2.1. Le dipendenze mantengono le proprie licenze: vedi [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).

## Checklist di rilascio

Dalla versione successiva alla `1.0.1` gli installer pubblicati e i relativi allegati si producono **solo** con il workflow GitHub Actions `Release` (`.github/workflows/release.yml`). Non caricare mai su una release file compilati in locale: non avrebbero l'attestazione di provenienza e il loro hash non sarebbe in `SHA256SUMS.txt`. La build locale con `packageMsiRelease`/`packageExeRelease` resta per lo sviluppo e per provare modifiche al packaging.

1. Scegliere una nuova versione; non modificare mai il contenuto o gli asset di un tag già pubblicato. Aggiornare `appVersion` in `gradle.properties` e creare `docs/RELEASE_NOTES_<versione>.md` dalla [versione modello](./RELEASE_TEMPLATE.md): il workflow usa questo file come testo della release e si ferma se manca.
2. Se le dipendenze sono cambiate, aggiornare [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) e `docs/licenses/` seguendo la sezione "Manutenzione" di quel documento. Se cambiano le librerie LGPL incluse, aggiornare anche [LICENSE-LGPL-EXCEPTION.md](../LICENSE-LGPL-EXCEPTION.md) e l'elenco in `scripts/release/Prepare-ReleaseAssets.ps1`.
3. Eseguire `.\gradlew.bat :shared:desktopTest :composeApp:compileKotlinDesktop` e il controllo Android `.\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug`, fare commit e push su `main` e attendere che il workflow `CI` sia verde (job Windows e Android).
4. Facoltativo, consigliato se è cambiato il packaging: avviare `Release` da *Actions → Release → Run workflow* su `main`, scaricare l'artifact `release-assets-<versione>` e fare una prima prova.
5. Creare il tag annotato sul commit verificato e inviarlo:
   ```powershell
   git tag -a v<versione> -m "Release <versione>"
   git push origin v<versione>
   ```
   Il workflow `Release` verifica che il tag coincida con `appVersion`. Il job **Android** esegue test host e lint, compila l'APK di release firmato con la chiave dell'app e controlla certificato, `applicationId` e `versionName`. Il job **Windows** esegue i test, compila gli installer con Eclipse Temurin 21, controlla che il runtime incluso abbia licenza GPL, prepara gli allegati (installer, APK, `SHA256SUMS.txt`, `LICENSE`, `LICENSE-LGPL-EXCEPTION.md`, avvisi di terze parti, sorgenti LGPL), crea le attestazioni di provenienza e infine una GitHub Release **bozza**.
6. Scaricare dalla bozza i due installer e collaudarli su una macchina o VM pulita (Windows Sandbox): installazione interattiva; dialogo finale con le opzioni indipendenti di avvio e apertura delle note; annullamento del dialogo; installazione silenziosa senza dialogo; aggiornamento di una versione precedente; primo avvio e creazione del database. Verificare inoltre logging, messaggio copiabile in caso di errore, licenza, credenziali, notifiche e trascrizione.
   Scaricare dalla bozza l'APK e installarlo su un dispositivo Android **sopra la versione precedente**: l'aggiornamento deve riuscire senza disinstallare, con impostazioni, preferiti e attivazione conservati e lo stesso ID `GRAB-A-…`. Provare poi connessione, waterfall, preferiti del server, registrazione e ascolto in background.
7. Sui file scaricati dalla bozza verificare hash e provenienza (vedi [Verifica degli allegati](#verifica-degli-allegati)), APK compreso.
8. Inviare i due installer a Microsoft (vedi [Invio degli installer a Microsoft](#invio-degli-installer-a-microsoft)).
9. Pubblicare la bozza dall'interfaccia web di GitHub, poi riscaricare un installer pubblicato e confrontarne l'hash con `SHA256SUMS.txt`. Non dichiarare pubblicata la release finché questa verifica non è completata.
10. Aggiornare [VERSION_HISTORY.md](./VERSION_HISTORY.md), README (sezioni IT ed EN), [USER_GUIDE.md](./USER_GUIDE.md), [docs/README.md](./README.md) e questo documento; spostare le note della versione precedente in [releases/](./releases/README.md). Fare un controllo dei collegamenti e di `git diff --check`.

Se il collaudo fallisce prima della pubblicazione: eliminare la bozza e il tag (`git push --delete origin v<versione>` e `git tag -d v<versione>`), correggere e ripetere dal punto 3. Una release già pubblicata non si modifica: le correzioni ricevono una nuova versione.

### Verifica degli allegati

```powershell
# Hash: confrontare con la riga corrispondente di SHA256SUMS.txt
Get-FileHash -Algorithm SHA256 .\KiwiSDRRecorder-<versione>.msi, .\KiwiSDRRecorder-<versione>.exe, .\KiwiSDRGrabber-<versione>.apk

# Provenienza (richiede GitHub CLI: winget install GitHub.cli)
gh attestation verify .\KiwiSDRRecorder-<versione>.msi --repo Gig81/KiwiSDR-Grabber
gh attestation verify .\KiwiSDRRecorder-<versione>.exe --repo Gig81/KiwiSDR-Grabber
gh attestation verify .\KiwiSDRGrabber-<versione>.apk --repo Gig81/KiwiSDR-Grabber
```

Per l'APK si può controllare anche il certificato di firma nel container di build, dalla cartella in cui si trova il file; l'impronta `certificate SHA-256 digest` deve coincidere con quella indicata in [Versione Android](#versione-android):

```powershell
docker run --rm -v "${PWD}:/apk:ro" --entrypoint bash kiwigrabber-android-build:1 -c '$(ls -d $ANDROID_HOME/build-tools/*/ | tail -1)apksigner verify --print-certs /apk/KiwiSDRGrabber-<versione>.apk'
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

Crea `Rilascio-<versione>\` (ignorata da git). Le versioni delle librerie LGPL sono ricavate dai jar inclusi nell'app image, cioè da ciò che viene effettivamente distribuito, e non da `gradle/libs.versions.toml`, che non contiene le dipendenze transitive (`jlayer`, `tritonus-share`). Gli allegati prodotti così servono solo per prove: non vanno pubblicati.

### Note sui workflow GitHub Actions

- `CI` (`.github/workflows/ci.yml`) esegue su `windows-latest` lo stesso controllo standard del progetto a ogni push e pull request verso `main`; in caso di errore allega i report dei test. Il workflow `CodeQL` è separato.
- `Release` (`.github/workflows/release.yml`) parte dal push di un tag `v*` (crea la release bozza) oppure a mano con *Run workflow* (solo build, allegati come artifact per 30 giorni). Prima di compilare verifica che il tag coincida con `appVersion` e che esistano le note di rilascio.
- I permessi sono dichiarati per job e ridotti al minimo: `contents: write` per la bozza e gli allegati, `id-token: write` per il token OIDC usato da Sigstore, `attestations: write` per salvare l'attestazione. A livello di workflow resta `contents: read`.
- L'unico segreto usato è la chiave di firma dell'APK, nell'environment `android-release` (vedi [Versione Android](#versione-android)): lo legge solo il job Android, che decodifica il keystore in una cartella temporanea del runner e lo cancella subito dopo la build. Il job Windows e il workflow `CI` non usano segreti. La chiave privata Ed25519 delle licenze non deve mai entrare nella CI.
- I runner GitHub hanno l'SDK Android preinstallato: i job solo desktop (`CI` Windows, `CodeQL`, installer) passano `-Pkiwi.android=false` per non attivare il target Android; i job Android installano platform e build-tools con le versioni dell'immagine Docker (`docker/android-build/Dockerfile`). `CodeQL` analizza solo il codice desktop e condiviso.
- La cache Gradle è in sola lettura (`cache-read-only`), così la release non dipende da cache scritte da altri branch.
- WiX 3.11 non va installato sul runner: lo scarica il plugin Compose. Se il download non fosse più disponibile, installare WiX sul runner e impostare `WIX_PATH`.
- Il workflow usa solo azioni di GitHub e Gradle, fissate per versione principale; per maggior rigore si possono fissare per hash di commit.
- Se una release prodotta dalla CI si comporta diversamente da una build locale (JDK, WiX o percorsi diversi), confrontare il `.wxs` decompilato dei due MSI: `build\wix311\dark.exe -nologo <msi> out.wxs`.

## Possibili evoluzioni

- La firma digitale Authenticode degli installer non è prevista: i certificati sono a pagamento e il programma gratuito SignPath Foundation richiede una licenza approvata OSI. Verifica di hash e attestazioni e l'invio a Microsoft ne fanno le veci.
- Pubblicazione Android sul Play Store (AAB firmato con la chiave di upload) e registrazione alla verifica dello sviluppatore Android: vedi [Versione Android](#versione-android).
