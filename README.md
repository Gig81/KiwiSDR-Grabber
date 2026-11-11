# KiwiSDR Grabber & Station Recorder

Applicazione Desktop multipiattaforma in Kotlin Multiplatform (KMP) e Compose Multiplatform (CMP) per la connessione, l'ascolto in tempo reale, il monitoraggio utenti e la registrazione audio (MP3 nativo / WAV) da qualsiasi ricevitore KiwiSDR.

Il progetto è in fase di sviluppo desktop-first, ma la sua base condivisa è stata progettata per un futuro allineamento verso Android o altre piattaforme.

## Funzionalità principali

- Connessione diretta a server KiwiSDR configurabili
- Sintonia VFO con frequenze e modalità radio
- Streaming audio live e player locale
- Monitoraggio utenti e stato della stazione
- Registrazione continua e segmentata in MP3/WAV
- Squelch RF, rilevamento VAD e registrazione intelligente con pre-buffer e hang time
- Spettrometro e waterfall in tempo reale
- Trascrizione Vosk offline (con modello esterno scaricato dall'utente) e notifiche Telegram configurabili
- Logging diagnostico e persistenza locale

## Stack tecnologico

- Kotlin Multiplatform 2.4.21
- Compose Multiplatform 1.12.2
- JDK 17 `java.net.http` per HTTP e WebSocket
- Kotlin Coroutines 1.11.0
- SQLite JDBC 3.54.1.0
- Java Sound / SourceDataLine
- Jump3r + JLayer per MP3

## Struttura del repository

- `shared/`: logica condivisa e servizi applicativi core
- `composeApp/`: interfaccia desktop e entry point
- `docs/`: documentazione architetturale e sviluppo

## Avvio rapido

### Requisiti

- JDK 17+
- Windows 10 / 11

### Eseguire l'applicazione

```powershell
./gradlew.bat :composeApp:run
```

### Generare l'installer Windows

```powershell
./gradlew.bat :composeApp:packageExe
```

oppure

```powershell
./gradlew.bat :composeApp:packageMsi
```

## Documentazione ufficiale

- [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) — architettura del sistema, stack tecnologico, componenti riutilizzabili e regole di sviluppo
- [docs/DEVELOPMENT.md](./docs/DEVELOPMENT.md) — setup, build, test e procedure di sviluppo
- [docs/ANDROID.md](./docs/ANDROID.md) — ambito iniziale della futura versione Android
- [docs/DISTRIBUZIONE_E_RILASCI.md](./docs/DISTRIBUZIONE_E_RILASCI.md) — stato e verifiche per repository e canali di distribuzione
- [docs/RELEASE_NOTES_1.0.0.md](./docs/RELEASE_NOTES_1.0.0.md) — note della prima release
- [docs/THIRD_PARTY_NOTICES.md](./docs/THIRD_PARTY_NOTICES.md) — componenti di terze parti, licenze, sorgenti LGPL; testi integrali in [docs/licenses/](./docs/licenses/)

- [docs/README.md](./docs/README.md) — indice della documentazione ufficiale

Le password KiwiSDR e il token del bot Telegram vengono inseriti dall'utente e salvati nel file settings locale cifrati con Windows DPAPI, legati all'account Windows corrente. La password del ricevitore viene inviata tramite il protocollo KiwiSDR non cifrato; usa una password dedicata e non riutilizzata.

## Licenza

I sorgenti originali del progetto sono distribuiti secondo i termini della [PolyForm Noncommercial License 1.0.0](./LICENSE). La licenza non è una licenza open source: consente l'uso, la modifica e la ridistribuzione per scopi non commerciali (uso personale, hobby, radioamatori, studio, organizzazioni non profit) e non consente l'uso commerciale. Per usi commerciali è necessario un accordo separato con l'autore.

Per le librerie distribuite secondo la GNU LGPL 2.1 vale anche il [permesso supplementare](./LICENSE-LGPL-EXCEPTION.md) richiesto dalla sezione 6 di quella licenza. Le dipendenze di terze parti restano soggette alle rispettive licenze: vedi [docs/THIRD_PARTY_NOTICES.md](./docs/THIRD_PARTY_NOTICES.md).

L'attivazione delle funzioni avanzate è gratuita: il Machine ID comunicato per l'attivazione serve solo a emettere il codice di licenza e a tenere il registro delle attivazioni, e non viene usato per altri scopi.

## Note legali

Progetto indipendente, non affiliato né approvato dagli autori o dai produttori di KiwiSDR. "KiwiSDR" è un marchio dei rispettivi titolari ed è citato solo per indicare la compatibilità.

## Sviluppo e manutenzione

Il progetto attuale è orientato a una architettura modulare con una base condivisa (`shared`) e un frontend desktop (`composeApp`). Per mantenere una struttura sana, le nuove funzionalità devono rispettare le regole definite in [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md), evitando duplicazioni tra layer condivisi e piattaforma-specifici.
