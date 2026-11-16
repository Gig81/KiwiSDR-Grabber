# Guida Strumenti di Sviluppo, Ambiente e Rilascio

Questo documento raccoglie tutti i requisiti di sistema, i tool necessari per lo sviluppo locale e gli strumenti di build/rilascio sia per la versione **Desktop (Windows)** sia per la versione **Mobile (Android)**.

---

## 1. Architettura Tecnologica Generale

| Componente | Tecnologia | Versione Attuale |
| :--- | :--- | :--- |
| **Linguaggio** | Kotlin (Multiplatform) | `2.4.21` |
| **Framework UI** | Compose Multiplatform | `1.12.2` |
| **Build System** | Gradle Wrapper | `9.9.1` |
| **JDK (Java)** | Qualsiasi OpenJDK 17+ per lo sviluppo; **Eclipse Temurin 21** per creare gli installer di release | `17` (minimo richiesto 17) |
| **Networking** | `java.net.http` del JDK (HttpClient, WebSocket) | JDK `17` |
| **Concorrenza** | Kotlinx Coroutines & Flow | `1.11.0` |
| **Persistenza Locale** | SQLite JDBC (solo Desktop); JSON atomico in `filesDir` su Android | `3.54.1.0` |
| **Networking Android** | OkHttp (WebSocket) | `5.5.1` |
| **Android** | AGP / compileSdk / targetSdk / minSdk | `9.5.1` / `37` / `36` / `29` |

La versione dell'applicazione è centralizzata nella proprietà `appVersion` di `gradle.properties`; viene usata per la versione Gradle, il titolo della finestra e i pacchetti desktop.

---

## 2. Sviluppo e Rilascio — Desktop (Windows)

### Tool Necessari per lo Sviluppo
1. **Java JDK 17+**: Verificare dal terminale con:
   ```powershell
   java -version
   javac -version
   ```
2. **Gradle Wrapper**: Già integrato nel repository (`gradlew.bat`).
3. **IDE Consigliato**: IntelliJ IDEA Community o Ultimate, oppure Android Studio (con supporto desktop abilitato).

### Esecuzione in Ambiente di Sviluppo
```powershell
# Avvio dell'app Desktop in modalità sviluppo
.\gradlew.bat :composeApp:run
```

### Strumenti e Procedura di Rilascio Windows
Compose Multiplatform integra il plugin di packaging nativo basato sul tool `jpackage` del JDK:

> **JDK per le release pubbliche.** L'installer include un runtime Java ricavato dal JDK con cui viene eseguito Gradle. Per le release pubbliche usa **Eclipse Temurin** (OpenJDK, GPL-2.0 con Classpath Exception), non Oracle JDK: gli aggiornamenti di Oracle JDK 21 successivi a luglio 2026 sono soggetti alla licenza OTN, che non ne consente la libera ridistribuzione.
>
> ```powershell
> winget install EclipseAdoptium.Temurin.21.JDK
> $env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory | Where-Object Name -like 'jdk-21*' | Select-Object -Last 1).FullName
> $env:Path = "$env:JAVA_HOME\bin;$env:Path"
> .\gradlew.bat --stop
> ```
>
> Verifica: dopo `.\gradlew.bat :composeApp:createDistributable`, il file `composeApp\build\compose\binaries\main\app\KiwiSDRRecorder\runtime\legal\java.base\LICENSE` deve iniziare con "The GNU General Public License (GPL)" e non con "Oracle No-Fee Terms". Il file `runtime\release` generato da `jlink` non indica il fornitore.

1. **Creazione dell'Applicativo Distribuibile Autonomo (Cartella con .exe e JRE incorporato):**
   ```powershell
   .\gradlew.bat :composeApp:createDistributable
   ```
   L'output si troverà in:
   `composeApp\build\compose\binaries\main\app\KiwiSDRRecorder`  
   *Vantaggio:* Non richiede che l'utente finale abbia Java installato, poiché include un runtime Java compresso dedicato.

2. **Creazione del pacchetto Installer `.msi`:**
   - **WiX Toolset:** il plugin Compose scarica da solo WiX 3.11 in `build\wix311` alla prima esecuzione; non serve installarlo né aggiungerlo al `PATH`.
   - **Comando:**
     ```powershell
     .\gradlew.bat :composeApp:packageMsiRelease
     ```
   - **Output:**    `composeApp\build\compose\binaries\main\msi\KiwiSDRRecorder-<appVersion>.msi`

3. **Creazione dell'Installer Singolo `.exe`:**
   ```powershell
   .\gradlew.bat :composeApp:packageExeRelease
   ```
   - **Output:** `composeApp\build\compose\binaries\main\exe\KiwiSDRRecorder-<appVersion>.exe`
   - Gli installer di release mostrano, dopo un'installazione interattiva, una finestra in cui l'utente può scegliere indipendentemente se avviare l'applicazione e/o aprire nel browser le note della versione. La finestra non viene mostrata nelle installazioni silenziose.

Il 10 novembre 2026 l'utente ha verificato con esito positivo installazione e avvio dei pacchetti Windows generati.

---

## 3. Sviluppo e Rilascio — Mobile (Android)

Ambito, attivazione e decisioni di progetto della versione mobile: [ARCHITECTURE.md §4.10](./ARCHITECTURE.md#410-android-ambito-attivazione-e-decisioni-di-progetto); distribuzione e chiavi: [DISTRIBUZIONE_E_RILASCI.md](./DISTRIBUZIONE_E_RILASCI.md#versione-android).

### Parametri della build Android

| Parametro | Valore | Dove |
| :--- | :--- | :--- |
| `applicationId` / `namespace` app | `com.gig81.kiwisdrgrabber` (definitivo: cambiarlo cambia gli ID dispositivo) | `androidApp/build.gradle.kts` |
| `minSdk` | `29` (Android 10, nessun permesso di storage) | `gradle/libs.versions.toml` |
| `compileSdk` | `37` (richiesto da AndroidX Core 1.19) | `gradle/libs.versions.toml` |
| `targetSdk` | `36` (requisito Play Store attuale) | `gradle/libs.versions.toml` |
| Android Gradle Plugin | `9.5.1` (`com.android.application`, `com.android.kotlin.multiplatform.library`) | `gradle/libs.versions.toml` |
| `versionName` / `versionCode` | da `appVersion` (`1.2.3` → `10203`) | `androidApp/build.gradle.kts` |

### Attivazione condizionale del target Android

Il modulo `androidApp` e il target Android di `:shared` si attivano **solo se l'SDK Android è disponibile**: variabile `ANDROID_HOME`/`ANDROID_SDK_ROOT` oppure `sdk.dir` in `local.properties` (non versionato). In alternativa si forza con `-Pkiwi.android=true|false`. Sul PC di sviluppo desktop senza SDK la build resta identica a prima (`settings.gradle.kts`).

### Build nel container Docker (consigliata, nessuna installazione sul PC)

L'ambiente Android (JDK 21 Temurin + SDK a riga di comando, platform 37, build-tools 37) è descritto in `docker/android-build/Dockerfile`. Lo script lo costruisce al primo uso e lancia Gradle nel container:

```powershell
.\scripts\android\build.ps1                                   # :androidApp:assembleDebug
.\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
.\scripts\android\build.ps1 :androidApp:assembleRelease :androidApp:bundleRelease
.\scripts\android\build.ps1 -Rebuild                          # ricostruisce l'immagine
```

- Il repository è montato in sola lettura e copiato nel volume `kiwigrabber-android-work`: le cartelle `build/` e `.gradle/` di Windows non vengono toccate.
- La cache Gradle resta nel volume `kiwigrabber-android-gradle` (build successive più rapide).
- APK, AAB e report (test, lint) vengono copiati in `build\android-out\`.
- Docker Desktop su Windows non espone KVM: l'emulatore Android non è utilizzabile nel container. Il collaudo si fa su dispositivo fisico installando l'APK di debug (`build\android-out\apk\debug\androidApp-debug.apk`).

### Build con Android Studio (alternativa)

Con Android Studio installato (SDK Platform 37, Build-Tools 37, Platform-Tools) e `local.properties` con `sdk.dir`, il target Android si attiva da solo e i comandi sono gli stessi:

```powershell
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:testAndroidHostTest
```

### Firma e rilascio Android

1. **Due chiavi**, custodite con backup offline **fuori dal repository** (`*.jks` è già in `.gitignore`), con le password in un password manager:
   - **chiave di firma dell'app** (`kiwigrabber-release.jks`, alias `kiwigrabber`): firma l'APK GitHub ed è caricata una sola volta su Play App Signing, quindi è la stessa sui due canali (vedi [ARCHITECTURE.md §4.10](./ARCHITECTURE.md#410-android-ambito-attivazione-e-decisioni-di-progetto)). Non è sostituibile: perderla impedisce gli aggiornamenti e invalida tutte le licenze Android;
   - **chiave di upload** (`kiwigrabber-upload.jks`, alias `upload`): firma solo l'AAB caricato su Play; in caso di perdita Google la sostituisce.
   ```powershell
   keytool -genkeypair -v -keystore kiwigrabber-release.jks -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias kiwigrabber
   keytool -genkeypair -v -keystore kiwigrabber-upload.jks -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias upload
   ```
   `keytool` è incluso nel JDK. Con PKCS12 la password della chiave coincide con quella del keystore.
2. **Configurazione della firma** senza segreti nel repository, in `~/.gradle/gradle.properties` oppure come variabili d'ambiente:

   | Proprietà Gradle | Variabile d'ambiente |
   | :--- | :--- |
   | `kiwi.signing.storeFile` | `KIWI_SIGNING_STORE_FILE` |
   | `kiwi.signing.storePassword` | `KIWI_SIGNING_STORE_PASSWORD` |
   | `kiwi.signing.keyAlias` | `KIWI_SIGNING_KEY_ALIAS` |
   | `kiwi.signing.keyPassword` | `KIWI_SIGNING_KEY_PASSWORD` |

   Senza queste proprietà la release viene prodotta **non firmata** (`androidApp-release-unsigned.apk`). Nel container lo script fa tutto da solo con `-Keystore`: monta in sola lettura la cartella del keystore (che deve stare fuori dal repository), chiede la password senza mostrarla (oppure la legge da `$env:KIWI_SIGNING_STORE_PASSWORD`) e la passa al container come variabile d'ambiente, senza scriverla su disco né nella riga di comando:
   ```powershell
   .\scripts\android\build.ps1 -Keystore "$env:USERPROFILE\KiwiGrabberKeys\kiwigrabber-release.jks" :androidApp:assembleRelease
   .\scripts\android\build.ps1 -Keystore "$env:USERPROFILE\KiwiGrabberKeys\kiwigrabber-upload.jks" -KeyAlias upload :androidApp:bundleRelease
   ```
3. **APK** per GitHub (`:androidApp:assembleRelease`, chiave dell'app) e **AAB** per Play Store (`:androidApp:bundleRelease`, chiave di upload), con R8 attivo (APK di release ≈ 3 MB).
4. **Verifica della firma**: dopo una build con `-Keystore` lo script esegue `apksigner verify --print-certs` sugli APK di release. L'impronta SHA-256 del certificato dell'APK GitHub e di quello installato da Play deve coincidere con quella della chiave dell'app.
5. Nessun APK/AAB va committato nel repository.

---

## 4. Riepilogo Comandi Utili (Quick Reference)

| Obiettivo | Comando |
| :--- | :--- |
| **Compilare tutto il progetto** | `.\gradlew.bat assemble` |
| **Avviare App Desktop Windows** | `.\gradlew.bat :composeApp:run` |
| **Verificare compilazione GUI desktop e test condivisi** | `.\gradlew.bat :composeApp:compileKotlinDesktop :shared:desktopTest` |
| **Creare cartella portabile Desktop** | `.\gradlew.bat :composeApp:createDistributable` |
| **Creare installer di release Windows (.msi)** | `.\gradlew.bat :composeApp:packageMsiRelease` |
| **Creare installer di release Windows (.exe)** | `.\gradlew.bat :composeApp:packageExeRelease` |
| **Compilare APK Android (Debug) nel container** | `.\scripts\android\build.ps1` |
| **Test host, lint e APK Android nel container** | `.\scripts\android\build.ps1 :shared:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug` |
| **Compilare APK e AAB Android di release nel container** | `.\scripts\android\build.ps1 :androidApp:assembleRelease :androidApp:bundleRelease` |
| **Pulire cache di build** | `.\gradlew.bat clean` |
