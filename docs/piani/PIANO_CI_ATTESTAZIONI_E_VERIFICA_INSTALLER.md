# Piano — CI su GitHub Actions, attestazioni e verifica degli installer

**Stato:** implementati workflow, script e documentazione (12/11/2026). Verificati su GitHub il 12/11/2026: `CI` e `CodeQL` verdi, `Release` avviato a mano su `main` completato con successo, installer prodotti dalla CI collaudati in Windows Sandbox. Restano da verificare con la prima release reale: tag, release bozza, `gh attestation verify` e invio a Microsoft. **Prima versione interessata:** la prossima release dopo la `1.0.1`.

File introdotti: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `scripts/release/Prepare-ReleaseAssets.ps1`. Differenza rispetto alla bozza della sezione 4: le versioni delle librerie LGPL sono ricavate dai jar presenti nell'app image (quello che viene effettivamente distribuito) invece che da `gradle/libs.versions.toml`, che non contiene le dipendenze transitive `jlayer` e `tritonus-share`.

## 1. Obiettivo e ambito

Gli installer Windows non sono firmati digitalmente e non lo saranno: i certificati di code signing sono a pagamento e il programma gratuito SignPath Foundation richiede una licenza approvata OSI, mentre il progetto usa la PolyForm Noncommercial 1.0.0. Il piano rende comunque verificabile la provenienza degli installer, a costo zero:

1. **CI di controllo** su ogni push e pull request: compilazione e test su Windows.
2. **Build di release su GitHub Actions** con Eclipse Temurin: installer, allegati e `SHA256SUMS.txt` vengono prodotti da GitHub, non dal PC di sviluppo.
3. **Attestazioni di provenienza** (GitHub artifact attestations, basate su Sigstore) per ogni allegato: chiunque può verificare che il file sia stato prodotto dal workflow di questo repository, da un commit e un tag precisi.
4. **Invio degli installer a Microsoft** a ogni release, per chiedere la rimozione dell'avviso SmartScreen sui file pubblicati.
5. **Documentazione**: la nota sull'installer non firmato resta, affiancata dalle istruzioni di verifica (hash e attestazione).

Fuori ambito: firma Authenticode, pacchetti MSIX/Microsoft Store, cambio di licenza, pubblicazione automatica senza collaudo manuale.

### Cosa cambia e cosa no

| Aspetto | Oggi | Dopo |
|---|---|---|
| Dove si compilano gli installer | PC dello sviluppatore | Runner `windows-latest` di GitHub |
| JDK di release | Temurin installato a mano | `actions/setup-java` con `distribution: temurin` |
| Allegati (zip licenze, sorgenti LGPL, hash) | Comandi PowerShell manuali | Script versionato eseguito dal workflow |
| Pubblicazione | Release creata a mano dall'interfaccia web | Il workflow crea una release **bozza**; la pubblicazione resta manuale dopo il collaudo |
| Collaudo in Windows Sandbox | Manuale | Invariato, ma sui file scaricati dalla bozza |
| Avviso SmartScreen | Presente | Ancora possibile; ridotto con l'invio a Microsoft |

## 2. Prerequisiti

- [ ] Il repository `Gig81/KiwiSDR-Grabber` è **pubblico**: sui repository privati le attestazioni richiedono GitHub Enterprise Cloud. Se il repository dovesse diventare privato, le attestazioni smettono di essere disponibili e il passo 5.4 va rimosso.
- [ ] In *Settings → Actions → General* sono consentite le azioni di GitHub e quelle verificate (servono `actions/*`, `gradle/actions/*`) e i workflow hanno permesso di lettura e scrittura solo dove dichiarato nel file (lasciare il default "Read repository contents" e dare i permessi per job, come nel workflow sotto).
- [ ] Un account Microsoft personale per il portale di invio file (passo 7).
- [ ] Facoltativo, per provare la verifica in locale: GitHub CLI (`winget install GitHub.cli`). Oggi `gh` non è installato sul PC di sviluppo; non è indispensabile per pubblicare.

## 3. Step 1 — Workflow CI di controllo (`.github/workflows/ci.yml`)

Scopo: intercettare regressioni prima della release, eseguendo il controllo standard del progetto su Windows (lo stesso sistema dei pacchetti).

1. Creare `.github/workflows/ci.yml`:

   ```yaml
   name: CI

   on:
     push:
       branches: [ "main" ]
     pull_request:
       branches: [ "main" ]

   permissions:
     contents: read

   concurrency:
     group: ci-${{ github.ref }}
     cancel-in-progress: true

   jobs:
     build-test:
       name: Compilazione e test (Windows)
       runs-on: windows-latest
       steps:
         - uses: actions/checkout@v5
         - uses: actions/setup-java@v5
           with:
             distribution: temurin
             java-version: '21'
         - uses: gradle/actions/setup-gradle@v5
         - name: Compilazione e test
           run: .\gradlew.bat --no-daemon :composeApp:compileKotlinDesktop :shared:desktopTest
         - name: Report dei test
           if: failure()
           uses: actions/upload-artifact@v4
           with:
             name: test-reports
             path: shared/build/reports/tests/
   ```

2. Fare il push su un branch, aprire una pull request e verificare che il job passi.
3. Facoltativo: in *Settings → Branches* rendere il controllo obbligatorio per il merge su `main`.

Note:
- Il workflow CodeQL esistente resta invariato (gira su Ubuntu e compila soltanto).
- Le versioni delle azioni (`@v5`, `@v4`) vanno allineate a quelle di `codeql.yml` e verificate al momento dell'implementazione.

## 4. Step 2 — Script di preparazione degli allegati

Scopo: sostituire i comandi PowerShell manuali di [DISTRIBUZIONE_E_RILASCI.md](../DISTRIBUZIONE_E_RILASCI.md) con uno script versionato, usato sia dal workflow sia, se serve, in locale.

1. Creare `scripts/release/Prepare-ReleaseAssets.ps1` con parametri `-Version` e `-OutputDir` (predefinito `Rilascio-<versione>`, già ignorato da git). Lo script deve:
   1. verificare che `appVersion` in `gradle.properties` coincida con `-Version` e fermarsi in caso contrario;
   2. verificare la licenza del runtime incluso: `composeApp\build\compose\binaries\main\app\KiwiSDRRecorder\runtime\legal\java.base\LICENSE` deve iniziare con `The GNU General Public License (GPL)`, altrimenti uscire con errore (protegge dal caso Oracle JDK);
   3. copiare `KiwiSDRRecorder-<versione>.msi` ed `.exe` da `composeApp\build\compose\binaries\main\{msi,exe}` in `OutputDir`, fallendo se mancano;
   4. creare `third-party-licenses-<versione>.zip` da `docs\THIRD_PARTY_NOTICES.md` e `docs\licenses`;
   5. scaricare da Maven Central i sorgenti delle librerie LGPL, aggiungere `docs\licenses\LGPL-2.1.txt` e creare `lgpl-sources-<versione>.zip`. Le coordinate (oggi scritte a mano nella documentazione) vanno lette da `gradle/libs.versions.toml`, così non si disallineano quando le versioni cambiano;
   6. copiare `LICENSE` e `LICENSE-LGPL-EXCEPTION.md`;
   7. generare per ultimo `SHA256SUMS.txt` (ASCII, formato `<hash minuscolo>  <nome file>`) su **tutti** i file della cartella, escluso il file stesso.
2. Usare `Set-StrictMode -Version Latest` e `$ErrorActionPreference = 'Stop'`, così un errore interrompe lo script invece di produrre allegati incompleti.
3. Provarlo in locale dopo un `packageMsiRelease`/`packageExeRelease` (non serve avviare gli eseguibili: vedi la nota su Defender for Endpoint nella skill `windows-release`).

## 5. Step 3 — Workflow di release (`.github/workflows/release.yml`)

### 5.1 Attivazione

- **Push di un tag** `v*` (es. `v1.0.2`): è il flusso normale.
- **`workflow_dispatch`** manuale, senza creare la release, solo per provare la build: produce gli installer come artifact del workflow.

### 5.2 Permessi

Dichiarati a livello di job, il minimo indispensabile:

| Permesso | Perché |
|---|---|
| `contents: write` | creare la release bozza e caricare gli allegati |
| `id-token: write` | ottenere il token OIDC usato da Sigstore per firmare l'attestazione |
| `attestations: write` | salvare l'attestazione nel repository |

### 5.3 Bozza del workflow

```yaml
name: Release

on:
  push:
    tags: [ "v*" ]
  workflow_dispatch:

jobs:
  release:
    name: Installer Windows
    runs-on: windows-latest
    permissions:
      contents: write
      id-token: write
      attestations: write
    steps:
      - uses: actions/checkout@v5

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '21'

      - uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: true   # la release non deve dipendere da cache scritte da altri branch

      - name: Versione
        id: version
        shell: pwsh
        run: |
          $v = (Select-String -Path gradle.properties -Pattern '^appVersion=(.+)$').Matches[0].Groups[1].Value.Trim()
          if ('${{ github.ref_type }}' -eq 'tag' -and '${{ github.ref_name }}' -ne "v$v") {
            throw "Il tag ${{ github.ref_name }} non corrisponde ad appVersion=$v"
          }
          "version=$v" >> $env:GITHUB_OUTPUT

      - name: Test
        run: .\gradlew.bat --no-daemon :shared:desktopTest :composeApp:compileKotlinDesktop

      - name: Installer
        run: .\gradlew.bat --no-daemon :composeApp:packageMsiRelease :composeApp:packageExeRelease

      - name: Allegati e hash
        shell: pwsh
        run: ./scripts/release/Prepare-ReleaseAssets.ps1 -Version ${{ steps.version.outputs.version }} -OutputDir release-assets

      - name: Attestazione di provenienza
        uses: actions/attest-build-provenance@v2
        with:
          subject-path: |
            release-assets/*.msi
            release-assets/*.exe
            release-assets/*.zip
            release-assets/SHA256SUMS.txt

      - name: Artifact del workflow
        uses: actions/upload-artifact@v4
        with:
          name: release-assets-${{ steps.version.outputs.version }}
          path: release-assets/
          retention-days: 30

      - name: Release bozza
        if: github.ref_type == 'tag'
        shell: pwsh
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          $v = '${{ steps.version.outputs.version }}'
          gh release create "v$v" release-assets/* --draft --verify-tag `
            --title "KiwiSDR Grabber $v" `
            --notes-file "docs/RELEASE_NOTES_$v.md"
```

Note sulla bozza:
- `gh` è preinstallato sui runner GitHub; non serve installarlo in locale.
- WiX non va installato sul runner: il task `unzipWix` del plugin Compose scarica WiX 3.11 da sé; se il download fallisse, installare WiX sul runner e impostare `WIX_PATH`.
- Verificare al momento dell'implementazione l'ultima versione major di `actions/attest-build-provenance` (in alternativa `actions/attest`) e allineare le altre azioni.
- Non usare segreti nel workflow: non servono. La chiave Ed25519 delle licenze **non** deve mai entrare nella CI.

### 5.4 Cosa garantisce l'attestazione

L'attestazione lega l'hash SHA-256 di ogni file a: repository, workflow, commit, tag ed esecuzione che lo ha prodotto, ed è firmata tramite Sigstore con un certificato a vita breve emesso a GitHub Actions. **Non** è una firma Authenticode: Windows non la legge e SmartScreen può comunque avvisare. Serve a chi vuole verificare che il file scaricato sia proprio quello costruito da questo repository.

## 6. Step 4 — Nuova procedura di rilascio

Sostituisce i passi 4–9 della checklist attuale in [DISTRIBUZIONE_E_RILASCI.md](../DISTRIBUZIONE_E_RILASCI.md):

1. Aggiornare `appVersion`, creare `docs/RELEASE_NOTES_<versione>.md` dal modello, aggiornare notices/licenze se le dipendenze sono cambiate (passi 1–3 invariati).
2. Fare commit e push su `main`; attendere la CI verde.
3. Facoltativo: avviare `Release` con *Run workflow* su `main` per una prova; scaricare l'artifact e collaudare.
4. Creare il tag annotato e inviarlo:
   ```powershell
   git tag -a v<versione> -m "Release <versione>"
   git push origin v<versione>
   ```
5. Attendere il workflow `Release`: crea la release **bozza** con installer, zip, licenze e `SHA256SUMS.txt`.
6. Scaricare dalla bozza i due installer e collaudarli in Windows Sandbox (passo 6 attuale: installazione interattiva e silenziosa, dialogo finale, aggiornamento, primo avvio, logging, licenza, credenziali, notifiche, trascrizione).
7. Verificare hash e attestazione sui file scaricati (sezione 8).
8. Inviare gli installer a Microsoft (sezione 7).
9. Pubblicare la bozza dall'interfaccia web.
10. Aggiornare `VERSION_HISTORY.md`, README (IT e EN), `USER_GUIDE.md`, `docs/README.md`, archiviare le note precedenti in `docs/releases/`.

Regole:
- **Non caricare a mano file compilati in locale** su una release prodotta dal workflow: non avrebbero l'attestazione e il loro hash non sarebbe in `SHA256SUMS.txt`.
- Se il collaudo fallisce: eliminare la bozza e il tag (non ancora pubblicati), correggere e ripetere. Se la release è già pubblicata vale la regola attuale: nuova versione, nessuna modifica agli asset.
- La build locale con `packageMsiRelease`/`packageExeRelease` resta per lo sviluppo e per provare modifiche al packaging.

## 7. Step 5 — Invio degli installer a Microsoft a ogni release

SmartScreen valuta i file non firmati soprattutto in base all'hash e alla reputazione: ogni nuova versione riparte da zero. L'invio a Microsoft è gratuito e può rimuovere l'avviso per quei file specifici, ma non ci sono garanzie né tempi certi.

1. Usare **esattamente** i file scaricati dalla release bozza (stesso hash di `SHA256SUMS.txt`). Un installer ricompilato ha un hash diverso e l'esito dell'analisi non vale per lui.
2. Aprire il portale Microsoft Security Intelligence, *Submit a file for malware analysis*: <https://www.microsoft.com/wdsi/filesubmission>.
3. Accedere come **Software developer** con l'account Microsoft.
4. Per ciascun installer (`.msi` ed `.exe`, un invio per file):
   - prodotto: Microsoft Defender SmartScreen (se non presente, Microsoft Defender Antivirus);
   - tipo: file segnalato in modo errato / avviso non corretto (*incorrectly detected*);
   - descrizione: nome dell'applicazione, versione, link alla release su GitHub, hash SHA-256 e nota che l'installer è prodotto da GitHub Actions con attestazione di provenienza.
5. Annotare gli ID di invio e l'esito **fuori dal repository** (insieme agli altri materiali di rilascio). Non serve citarli nelle note di rilascio.
6. Verificare l'esito dopo qualche giorno. Se l'avviso persiste, non ripetere l'invio dello stesso file: si mantiene la nota "non firmato" nella documentazione.

Il portale e le sue voci possono cambiare: aggiornare questa sezione se i nomi dei campi non coincidono più.

## 8. Step 6 — Documentazione per gli utenti

La nota sull'installer non firmato resta in tutti i punti in cui compare oggi (README IT/EN, `USER_GUIDE.md` IT/EN). Va affiancata da istruzioni di verifica brevi.

### 8.1 Testo da aggiungere (italiano)

> **Verifica dell'installer.** L'installer non è firmato digitalmente: Windows potrebbe mostrare un avviso sul publisher o SmartScreen. Gli installer sono compilati da GitHub Actions su questo repository; prima di eseguirli puoi verificarli.
>
> 1. **Hash** (PowerShell, nella cartella del download):
>    ```powershell
>    Get-FileHash .\KiwiSDRRecorder-<versione>.msi -Algorithm SHA256
>    ```
>    Il valore deve coincidere, ignorando maiuscole e minuscole, con la riga corrispondente di `SHA256SUMS.txt`.
> 2. **Provenienza** (facoltativa, richiede [GitHub CLI](https://cli.github.com/)):
>    ```powershell
>    gh attestation verify .\KiwiSDRRecorder-<versione>.msi --repo Gig81/KiwiSDR-Grabber
>    ```
>    L'esito positivo conferma che il file è stato prodotto dal workflow di questo repository. Le attestazioni sono visibili anche nella pagina *Attestations* del repository su GitHub.
>
> Se uno dei due controlli non va a buon fine, non eseguire il file e segnala il problema nelle GitHub Issues.

Aggiungere la traduzione inglese equivalente nella sezione English di README e `USER_GUIDE.md`.

### 8.2 Documenti da aggiornare

| Documento | Modifica |
|---|---|
| `README.md` (IT + EN) | Sostituire il paragrafo su SmartScreen con il testo della sezione 8.1 (o un riassunto con link alla guida utente) |
| `docs/USER_GUIDE.md` (IT + EN) | Inserire le istruzioni di verifica nei passi di installazione |
| `docs/DISTRIBUZIONE_E_RILASCI.md` | Nuova checklist (sezione 6), invio a Microsoft (sezione 7), sostituire i "Comandi utili" con il richiamo allo script; in "Possibili evoluzioni" togliere la voce sulla CI e lasciare la firma come non prevista per motivi di costo |
| `docs/RELEASE_TEMPLATE.md` | Nella tabella dei file indicare che ogni allegato ha un'attestazione di provenienza e rimandare alle istruzioni di verifica |
| `docs/DEVELOPMENT.md` | Breve sezione "CI" con i due workflow e il comando locale equivalente |
| `.claude/skills/windows-release/SKILL.md` | Gli installer pubblici si producono con il workflow `Release`; `gh` non è richiesto in locale |
| `CLAUDE.md` | Una riga: installer pubblici solo dal workflow `Release`, mai caricati a mano |
| `docs/README.md` | Collegamento a questo piano finché è in corso; rimuoverlo a lavoro concluso |

## 9. Verifica del lavoro

- [ ] `ci.yml` passa su una pull request e fallisce se si introduce volutamente un test rotto.
- [ ] `release.yml` avviato a mano produce l'artifact con: `.msi`, `.exe`, due zip, `LICENSE`, `LICENSE-LGPL-EXCEPTION.md`, `SHA256SUMS.txt`.
- [ ] Il workflow si ferma se il tag non coincide con `appVersion` (provare con un tag di prova su un fork o un branch, poi eliminarlo).
- [ ] Il runtime nell'installer ha la licenza GPL (controllo dello script superato).
- [ ] `Get-FileHash` sui file scaricati dalla bozza coincide con `SHA256SUMS.txt`.
- [ ] `gh attestation verify` dà esito positivo su `.msi`, `.exe` e sugli zip; dà esito negativo su un file modificato o compilato in locale.
- [ ] Gli installer della CI superano lo stesso collaudo in Windows Sandbox di quelli compilati in locale (dialogo post-installazione, aggiornamento dalla `1.0.1`, stesso `upgradeUuid`).
- [ ] La documentazione IT/EN contiene la nota sull'installer non firmato e le istruzioni di verifica.

## 10. Rischi e punti aperti

- **Differenze tra build locale e CI**: JDK, WiX o percorsi diversi possono cambiare il comportamento dell'installer. Mitigazione: primo rilascio con collaudo completo, confronto del `.wxs` decompilato (`dark.exe`) con quello di una build locale.
- **Disponibilità di WiX 3.11**: è scaricato dal plugin Compose; se il download sparisse, fissare la versione in `WIX_PATH` sul runner.
- **SmartScreen**: l'invio a Microsoft non garantisce la rimozione dell'avviso; la documentazione deve continuare a dirlo senza promettere altro.
- **Repository privato**: farebbe perdere le attestazioni gratuite (vedi prerequisiti).
- **Azioni di terze parti**: il workflow usa solo azioni di GitHub e Gradle; per rigore si possono fissare per hash di commit invece che per tag di versione.

## 11. Ordine di lavoro proposto

1. Step 1 — `ci.yml` (indipendente, si può fare subito).
2. Step 2 — script degli allegati, provato in locale.
3. Step 3 — `release.yml`, provato con `workflow_dispatch`.
4. Step 6 — aggiornamento della documentazione.
5. Prima release reale con la nuova procedura (step 4 e 5), poi rimozione del piano dall'indice.
