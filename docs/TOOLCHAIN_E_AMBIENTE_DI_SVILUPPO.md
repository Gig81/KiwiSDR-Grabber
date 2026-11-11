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
| **Persistenza Locale** | SQLite JDBC (Desktop) / Room-SQLDelight | `3.54.1.0` |

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
     .\gradlew.bat :composeApp:packageMsi
     ```
   - **Output:**    `composeApp\build\compose\binaries\main\msi\KiwiSDRRecorder-<appVersion>.msi`

3. **Creazione dell'Installer Singolo `.exe`:**
   ```powershell
   .\gradlew.bat :composeApp:packageExe
   ```
   - **Output:** `composeApp\build\compose\binaries\main\exe\KiwiSDRRecorder-<appVersion>.exe`

Il 10 novembre 2026 l'utente ha verificato con esito positivo installazione e avvio dei pacchetti Windows generati.

---

## 3. Sviluppo e Rilascio — Mobile (Android)

*Nota: Questa sezione guiderà la configurazione non appena il download e l'installazione di Android Studio saranno completati.*

Lo stato e l'ambito iniziale della versione mobile sono riepilogati in [ANDROID.md](./ANDROID.md). I parametri e i comandi riportati qui sotto sono proposte preliminari: il target Android del progetto non è ancora configurato.

### Tool Necessari per lo Sviluppo Android
1. **Android Studio** (versione Koala / Ladybug o superiore).
2. **Android SDK (Software Development Kit)**:
   - Installare tramite l'SDK Manager di Android Studio:
     - **Android SDK Platform 34 o 35** (Android 14 / 15).
     - **Android SDK Build-Tools** (versione 34.0.0 o successiva).
     - **Android SDK Platform-Tools** (include `adb` per il debug su dispositivo fisico o emulatore).
3. **Configurazione Percorso SDK**:
   Assicurarsi che la variabile d'ambiente `ANDROID_HOME` sia impostata, oppure creare un file `local.properties` nella radice del progetto:
   ```properties
   sdk.dir=C\:\\Users\\<TUO_UTENTE>\\AppData\\Local\\Android\\Sdk
   ```

### Integrazione nel Progetto Gradle per Android (Fase successiva)
Per attivare il target Android:
- Aggiunta del plugin `com.android.application` in `composeApp/build.gradle.kts` e `com.android.library` in `shared/build.gradle.kts`.
- Configurazione dei parametri Android:
  ```kotlin
  android {
      namespace = "com.grabber"
      compileSdk = 35
      defaultConfig {
          applicationId = "com.grabber.kiwirecorder"
          minSdk = 24       // Android 7.0+
          targetSdk = 35
          versionCode = 1
          versionName = "1.0.0"
      }
  }
  ```

### Strumenti e Procedura di Rilascio Android

1. **Generazione della Chiave di Firma (Keystore Release):**
   Utilizzare lo strumento `keytool` (fornito dal JDK):
   ```powershell
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias kiwi-release
   ```

2. **Compilazione del Pacchetto APK per Installazione Diretta:**
   ```powershell
   .\gradlew.bat :composeApp:assembleRelease
   ```
   - Genera il file `.apk` firmabile e installabile su qualsiasi smartphone o tablet Android (anche tramite sideloading o comando `adb install`).

3. **Compilazione del Pacchetto AAB (Android App Bundle) per Google Play Store:**
   ```powershell
   .\gradlew.bat :composeApp:bundleRelease
   ```
   - Genera il file `.aab` ottimizzato da caricare sulla Google Play Console.

4. **Verifica e Allineamento del Pacchetto:**
   Gli strumenti inclusi nei Build-Tools dell'SDK:
   - `zipalign`: ottimizza l'allineamento dei byte dell'archivio APK.
   - `apksigner`: verifica la validità della firma crittografica v2/v3 dell'APK prima della distribuzione.

---

## 4. Riepilogo Comandi Utili (Quick Reference)

| Obiettivo | Comando |
| :--- | :--- |
| **Compilare tutto il progetto** | `.\gradlew.bat assemble` |
| **Avviare App Desktop Windows** | `.\gradlew.bat :composeApp:run` |
| **Verificare compilazione GUI desktop e test condivisi** | `.\gradlew.bat :composeApp:compileKotlinDesktop :shared:desktopTest` |
| **Creare cartella portabile Desktop** | `.\gradlew.bat :composeApp:createDistributable` |
| **Creare installer Windows (.msi)** | `.\gradlew.bat :composeApp:packageMsi` |
| **Creare installer Windows (.exe)** | `.\gradlew.bat :composeApp:packageExe` |
| **Compilare APK Android (Debug)** | `.\gradlew.bat :composeApp:assembleDebug` |
| **Compilare APK Android (Release)** | `.\gradlew.bat :composeApp:assembleRelease` |
| **Pulire cache di build** | `.\gradlew.bat clean` |
