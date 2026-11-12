# Distribuzione e rilasci

## Canale ufficiale

- Repository ufficiale: [Gig81/KiwiSDR-Grabber](https://github.com/Gig81/KiwiSDR-Grabber).
- I pacchetti si distribuiscono solo tramite **GitHub Releases** di questo repository. Ogni release contiene gli installer, gli hash SHA-256, la licenza, gli avvisi di terze parti e i sorgenti delle librerie LGPL.
- I pacchetti Android `.apk` / `.aab` e la relativa firma sono rinviati; vedere [ANDROID.md](./ANDROID.md).

## Stato della versione desktop

- `1.0.0` è la release Windows pubblicata attualmente; `1.0.1` è in preparazione e non è ancora pubblicata.
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

1. Scegliere una nuova versione; non modificare mai il contenuto o gli asset di un tag già pubblicato. Aggiornare `appVersion` in `gradle.properties` e creare le note dalla [versione modello](./RELEASE_TEMPLATE.md).
2. Eseguire `.\gradlew.bat :shared:desktopTest :composeApp:compileKotlinDesktop`.
3. Se le dipendenze sono cambiate, aggiornare [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) e `docs/licenses/` seguendo la sezione "Manutenzione" di quel documento.
4. Generare il runtime e verificare la licenza del JDK:
   ```powershell
   .\gradlew.bat :composeApp:createDistributable
   ```
   Il file `composeApp\build\compose\binaries\main\app\KiwiSDRRecorder\runtime\legal\java.base\LICENSE` deve iniziare con "The GNU General Public License (GPL)", non "Oracle No-Fee Terms".
5. Generare gli installer di release con Eclipse Temurin:
   ```powershell
   .\gradlew.bat :composeApp:packageMsiRelease :composeApp:packageExeRelease
   ```
   Verificare che siano prodotti `KiwiSDRRecorder-<versione>.msi` ed `.exe` nelle rispettive cartelle `msi` ed `exe`.
6. Collaudare entrambi i pacchetti su una macchina o VM pulita: installazione interattiva; dialogo finale con le opzioni indipendenti di avvio e apertura delle note; annullamento del dialogo; installazione silenziosa senza dialogo; aggiornamento di una versione precedente; primo avvio e creazione del database. Verificare inoltre logging, messaggio copiabile in caso di errore, licenza, credenziali, notifiche e trascrizione.
7. Preparare gli allegati: installer, `SHA256SUMS.txt`, licenza, avvisi di terze parti e sorgenti LGPL. Calcolare gli hash sugli stessi file che saranno caricati.
8. Aggiornare [VERSION_HISTORY.md](./VERSION_HISTORY.md) e spostare le note della versione precedente in [releases/](./releases/README.md). Fare un controllo dei collegamenti e di `git diff --check`.
9. Creare un tag annotato `v<versione>` sul commit verificato, pubblicare la GitHub Release con note e allegati, quindi scaricare gli asset pubblicati e confrontarne gli hash. Non dichiarare pubblicata la release finché questa verifica non è completata.

### Comandi utili

```powershell
# Hash SHA-256
Get-FileHash -Algorithm SHA256 *.exe, *.msi |
    ForEach-Object { '{0}  {1}' -f $_.Hash.ToLower(), (Split-Path $_.Path -Leaf) } |
    Set-Content -Encoding ascii SHA256SUMS.txt

# Avvisi di terze parti
$versione = '1.0.1'
Compress-Archive -Path docs\THIRD_PARTY_NOTICES.md, docs\licenses -DestinationPath "third-party-licenses-$versione.zip"

# Sorgenti delle librerie LGPL (da Maven Central)
$m = 'https://repo1.maven.org/maven2'
$jars = 'com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4',
        'com/googlecode/soundlibs/mp3spi/1.9.5.4/mp3spi-1.9.5.4', 'com/googlecode/soundlibs/tritonus-share/0.3.7.4/tritonus-share-0.3.7.4',
        'io/github/raphimc/jump3r/1.0.6/jump3r-1.0.6'
New-Item -ItemType Directory lgpl-sources | Out-Null
foreach ($j in $jars) { Invoke-WebRequest "$m/$j-sources.jar" -OutFile ("lgpl-sources\" + (Split-Path $j -Leaf) + '-sources.jar') }
Copy-Item docs\licenses\LGPL-2.1.txt lgpl-sources\
Compress-Archive -Path lgpl-sources\* -DestinationPath "lgpl-sources-$versione.zip"
```

## Possibili evoluzioni

- Automatizzare build e pubblicazione con GitHub Actions (`actions/setup-java` con `distribution: temurin`) dopo aver consolidato la procedura manuale.
- Firmare digitalmente gli installer per evitare l'avviso di Windows SmartScreen.
- Per Android, coordinare firma e distribuzione con quanto definito in [ANDROID.md](./ANDROID.md).
