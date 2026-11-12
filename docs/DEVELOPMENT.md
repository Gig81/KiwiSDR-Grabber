# Guida allo sviluppo

## 1. Requisiti

- JDK 17 o superiore
- Windows 10 / 11 per lo sviluppo desktop target
- Git
- Gradle Wrapper incluso nel repository

## 2. Setup locale

Da terminale PowerShell, nella root del progetto:

```powershell
./gradlew.bat --version
```

Se il toolchain JDK non è presente, installare Java 17+ e verificare che `JAVA_HOME` sia configurato correttamente.

## 3. Comandi principali

### Avvio dell'app desktop

```powershell
./gradlew.bat :composeApp:run
```

### Esecuzione dei test

```powershell
.\gradlew.bat :shared:desktopTest
```

Per verificare le modifiche della GUI desktop e i test di regressione del modulo condiviso:

```powershell
.\gradlew.bat :composeApp:compileKotlinDesktop :shared:desktopTest
```

Le prove di compilazione/test non sostituiscono la verifica interattiva con un KiwiSDR raggiungibile, né il controllo del layout su monitor e scaling DPI differenti.

La suite desktop comprende 65 test automatici. Al 10 novembre 2026 è stata eseguita senza errori, anche dopo l'aggiornamento della toolchain.

I test di regressione coprono, tra l'altro, parser e trasformazioni waterfall, preferiti DX e loro persistenza, impostazioni e cifratura DPAPI delle credenziali, logging, Squelch/VAD, cooldown Telegram, codifica e oscuramento dei comandi KiwiSDR, parser dei messaggi del server (`user_cb`, `load_dxcfg`, frame `SND`), coda dei comandi WebSocket, Machine ID e licenze, migrazione del database. Le verifiche manuali di protocollo/audio restano separate dai test automatici.

### Collaudi desktop riportati (10 novembre 2026)

- Trascrizione Vosk di registrazioni WAV e MP3 su Windows: verificata.
- Invio Telegram di allegati WAV/MP3 e cooldown tra messaggi, inclusi gli alert generati dalla coda STT: verificati dall'utente con esito positivo il 10 novembre 2026.
- Stress test e installazione/avvio dei pacchetti Windows: verificati.
- Contrasto dei pulsanti colorati nella scheda Impostazioni: verificato dall'utente con esito positivo il 10 novembre 2026.
- I test automatici condivisi coprono anche i limiti temporali del cooldown.
- Revisione del codice e aggiornamento della toolchain (Gradle 9.9.1, Kotlin 2.4.21, Compose 1.12.2, JNA 5.19.1): verificati dall'utente con esito positivo il 10 novembre 2026, compresi connessione con password cifrata, oscuramento della password nei log, migrazione di impostazioni e database, attivazione della licenza sul nuovo Machine ID, trascrizione Vosk con la nuova JNA, registrazione e riproduzione, aspetto di tutte le schede.

### Diagnostica UI Windows

Se su Windows compaiono ritardi di input insieme a errori di selezione del renderer DirectX e stack trace in OpenGL, verificare prima il renderer Skiko configurato in `Main.kt` e i relativi override. Non disabilitare VSync: il precedente tentativo ha aumentato il carico e peggiorato la consegna degli input. Per le regole sulle ricomposizioni e sul rendering per frame, consultare [GUI_DESIGN_SYSTEM.md](./GUI_DESIGN_SYSTEM.md).

### Build locale

```powershell
./gradlew.bat build
```

### Packaging Windows

Per una build locale di sviluppo:

```powershell
.\gradlew.bat :composeApp:packageExe
```

oppure

```powershell
.\gradlew.bat :composeApp:packageMsi
```

Per i pacchetti destinati a una release, usare invece `:composeApp:packageExeRelease` e/o `:composeApp:packageMsiRelease`: includono la scelta post-installazione per avviare l'app e/o aprire le note della versione. La procedura è descritta in [DISTRIBUZIONE_E_RILASCI.md](./DISTRIBUZIONE_E_RILASCI.md).

Gli installer vengono generati sotto `composeApp\build\compose\binaries\main\exe` e `composeApp\build\compose\binaries\main\msi`.

## 4. Struttura dei moduli

- `shared`: logica cross-platform, modelli, protocollo, client SDR, audio e monitoraggio
- `composeApp`: UI desktop e entry point dell'applicazione

La GUI desktop è in `composeApp/src/desktopMain/kotlin/com/grabber/ui/`: `App.kt` compone la console radio persistente (`RadioConsole`), la barra delle schede (`MainTabBar`) e la scheda attiva; ogni scheda ha il proprio package in `ui/feature/<nome>/`. I token e i controlli visuali riutilizzabili sono in `ui/designsystem/`. Consultare `GUI_DESIGN_SYSTEM.md` per layout, palette e regole di coerenza; mantenere lo stato funzionale nel controller/impostazioni, non duplicarlo nei composable.

## 5. Convenzioni di sviluppo

- Kotlin con style idiomatico e classi con responsabilità chiare
- package coerenti con il dominio e non con la UI
- `StateFlow`/`Flow` come meccanismo di reattività
- i servizi platform-specific vanno tenuti fuori dal modulo condiviso
- ogni nuova funzionalità va prima valutata in termini di riuso e modularità

## 6. Debugging e diagnostica

- usare i log applicativi in `~/.kiwigrabber/logs`
- controllare lo stato di connessione dei client KiwiSDR e Waterfall tramite i `StateFlow`
- verificare la persistenza dei dati nel database locale `~/.kiwigrabber/kiwigrabber_history.db` (spostato automaticamente da `~/kiwigrabber_history.db` al primo avvio)
- utilizzare i test di regressione per rompere i cambiamenti di protocollo e audio

## 7. Procedure consigliate

- aggiungere nuove utility in `shared` solo se possono essere usate tra desktop e futuro mobile
- lasciare la parte desktop-specifica in `desktopMain`, non in `commonMain`
- fare attenzione alle dipendenze circolari e ai callback tra UI e servizi di business
- documentare le nuove API pubbliche con KDoc chiaro, non solo per la sintassi ma per il contesto d'uso

## 8. Note di manutenzione

- Il build mostra un avviso di deprecazione interno al plugin Compose (`Configuration.setVisible`): non dipende dal progetto e sparirà aggiornando il plugin.
- `material-icons-extended` è ferma alla 1.7.3, ultima versione pubblicata; per nuove icone valutare la migrazione a Material Symbols come risorse vettoriali.
- `InitialSetupDialog` usa volutamente i controlli Material standard (campi con etichetta flottante): è coerente al suo interno e si vede solo al primo avvio.
- Le funzioni di disegno su `Canvas` (spettro, cascata, righello) sono lunghe per natura: dividerle non semplifica e rischia di alterare il rendering.
- Prima di un aggiornamento delle dipendenze verificare i moduli JDK del pacchetto con `.\gradlew.bat :composeApp:suggestRuntimeModules` e aggiornare `THIRD_PARTY_NOTICES.md` dai JAR presenti nell'immagine generata.

## 9. Documentazione di riferimento

- `../README.md`: overview del progetto
- `ARCHITECTURE.md`: architettura ufficiale e regole di sviluppo
- `GUI_DESIGN_SYSTEM.md`: riferimenti visivi e struttura della GUI desktop
- `TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md`: ambiente, build e packaging
- `docs/`: documentazione ufficiale, guide operative e appunti per gli sviluppi Android e distribuzione
