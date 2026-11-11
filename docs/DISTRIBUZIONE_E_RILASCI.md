# Distribuzione e rilasci

## Canale ufficiale

- Repository ufficiale: [Gig81/KiwiSDR-Grabber](https://github.com/Gig81/KiwiSDR-Grabber).
- I pacchetti si distribuiscono solo tramite **GitHub Releases** di questo repository. Ogni release contiene gli installer, gli hash SHA-256, la licenza, gli avvisi di terze parti e i sorgenti delle librerie LGPL.
- I pacchetti Android `.apk` / `.aab` e la relativa firma sono rinviati; vedere [ANDROID.md](./ANDROID.md).

## Stato della versione desktop

- La versione desktop Windows `1.0.0` si impacchetta come installer autosufficiente `.exe` e `.msi`, con runtime Java incluso.
- La generazione locale usa `:composeApp:packageExe` e `:composeApp:packageMsi`; i file vengono prodotti in `composeApp\build\compose\binaries\main`. Per le release pubbliche il packaging va eseguito con Eclipse Temurin (vedi [TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md](./TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md)).
- Indirizzo e password KiwiSDR sono configurabili dall'utente, anche nel setup iniziale. Password KiwiSDR e token del bot Telegram vengono salvati nelle impostazioni locali cifrati con Windows DPAPI, legati all'account Windows corrente; i valori in chiaro delle versioni precedenti vengono cifrati al primo avvio. Se il file viene copiato su un altro account o PC, le credenziali vanno reinserite.
- La libreria Vosk è inclusa; il modello linguistico no: l'utente lo scarica dal sito ufficiale e sceglie la cartella nell'app.
- Se il server rifiuta l'autenticazione (`badp=1`), l'app spiega che la causa può essere la password errata oppure l'esaurimento dei canali e indica di controllare le impostazioni.

## Attivazione offline

L'attivazione delle funzioni avanzate (registrazione, VAD, trascrizione, storico utenti, notifiche Telegram) è gratuita. L'utente copia il Machine ID dalla sezione Licenza dell'app e lo comunica all'autore tramite il canale ufficiale. Il Machine ID deriva dal `MachineGuid` di Windows: non cambia con rete, VPN o rinomina del PC, ma cambia reinstallando Windows, nel qual caso va richiesta una nuova attivazione.

L'autore genera offline un codice `KSR1`, firmato con una chiave privata Ed25519 conservata separatamente da questo repository. L'utente inserisce il codice nell'app, che lo verifica localmente con la chiave pubblica inclusa (`LicenseManager.kt`); non avviene alcuna comunicazione con server dell'autore. Una nuova coppia di chiavi richiede di aggiornare la chiave pubblica nell'app e di distribuire una nuova versione.

## Licenza

Il codice originale è distribuito secondo la [PolyForm Noncommercial License 1.0.0](../LICENSE): una licenza *source-available*, non open source, che consente uso, modifica e ridistribuzione per scopi non commerciali e non consente l'uso commerciale. Per le librerie LGPL distribuite con l'app vale il [permesso supplementare](../LICENSE-LGPL-EXCEPTION.md) richiesto dalla sezione 6 della LGPL 2.1. Le dipendenze mantengono le proprie licenze: vedi [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).

## Checklist di rilascio

1. Aggiornare `appVersion` in `gradle.properties` e preparare `docs/RELEASE_NOTES_<versione>.md`.
2. Eseguire `.\gradlew.bat build` (test inclusi).
3. Se le dipendenze sono cambiate, aggiornare [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) e `docs/licenses/` seguendo la sezione "Manutenzione" di quel documento.
4. Generare EXE e MSI con Eclipse Temurin. Verifica: dopo `:composeApp:createDistributable`, il file `runtime\legal\java.base\LICENSE` dell'immagine deve iniziare con "The GNU General Public License (GPL)" e non con "Oracle No-Fee Terms".
5. Collaudare l'installer candidato: installazione, primo avvio con e senza password Kiwi, recupero su profilo esistente, attivazione, notifiche Telegram personali, trascrizione Vosk end-to-end.
6. Preparare gli allegati: installer, `SHA256SUMS.txt`, `LICENSE.txt`, `LICENSE-LGPL-EXCEPTION.md`, `third-party-licenses-<versione>.zip` (avvisi e cartella `licenses/`), `lgpl-sources-<versione>.zip`.
7. Creare il tag annotato `v<versione>`, pubblicare la GitHub Release con le note e gli hash, quindi scaricare l'installer dalla release e confrontarne l'hash.

### Comandi utili

```powershell
# Hash SHA-256
Get-FileHash -Algorithm SHA256 *.exe, *.msi |
    ForEach-Object { '{0}  {1}' -f $_.Hash.ToLower(), (Split-Path $_.Path -Leaf) } |
    Set-Content -Encoding ascii SHA256SUMS.txt

# Avvisi di terze parti
Compress-Archive -Path docs\THIRD_PARTY_NOTICES.md, docs\licenses -DestinationPath third-party-licenses-1.0.0.zip

# Sorgenti delle librerie LGPL (da Maven Central)
$m = 'https://repo1.maven.org/maven2'
$jars = 'com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4',
        'com/googlecode/soundlibs/mp3spi/1.9.5.4/mp3spi-1.9.5.4', 'com/googlecode/soundlibs/tritonus-share/0.3.7.4/tritonus-share-0.3.7.4',
        'io/github/raphimc/jump3r/1.0.6/jump3r-1.0.6'
New-Item -ItemType Directory lgpl-sources | Out-Null
foreach ($j in $jars) { Invoke-WebRequest "$m/$j-sources.jar" -OutFile ("lgpl-sources\" + (Split-Path $j -Leaf) + '-sources.jar') }
Copy-Item docs\licenses\LGPL-2.1.txt lgpl-sources\
Compress-Archive -Path lgpl-sources\* -DestinationPath lgpl-sources-1.0.0.zip
```

## Possibili evoluzioni

- Automatizzare build e pubblicazione con GitHub Actions (`actions/setup-java` con `distribution: temurin`) dopo aver consolidato la procedura manuale.
- Firmare digitalmente gli installer per evitare l'avviso di Windows SmartScreen.
- Per Android, coordinare firma e distribuzione con quanto definito in [ANDROID.md](./ANDROID.md).
