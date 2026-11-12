# Third-Party Notices — KiwiSDR Grabber & Station Recorder 1.0.1

Questo documento elenca i componenti di terze parti inclusi nell'installer Windows x64 della versione `1.0.1`, le loro licenze e il modo per ottenerne i sorgenti dove la licenza lo richiede. L'inventario è stato verificato il 12 novembre 2026 confrontando i JAR, le DLL e i file di dati effettivamente presenti nell'immagine dell'applicazione generata da `:composeApp:createDistributable`. La versione 1.0.1 non modifica le dipendenze rispetto alla 1.0.0.

Il codice originale dell'applicazione è distribuito secondo la [PolyForm Noncommercial License 1.0.0](../LICENSE), integrata dal [permesso supplementare per le librerie LGPL](../LICENSE-LGPL-EXCEPTION.md). I componenti elencati qui sotto restano soggetti alle rispettive licenze: nessuna di esse viene modificata o sostituita dalla licenza dell'applicazione.

I testi integrali delle licenze sono nella cartella [`docs/licenses/`](./licenses/) e vanno allegati a ogni release.

> Questo documento descrive gli adempimenti predisposti dall'autore; non è una certificazione legale.

## 1. Librerie Java (cartella `app\` dell'installazione)

| Componente | Versione | Licenza | Titolari principali | Testo |
|---|---|---|---|---|
| Kotlin Standard Library | 2.4.21 | Apache-2.0 | JetBrains s.r.o. and Kotlin Programming Language contributors | [Apache-2.0](./licenses/Apache-2.0.txt), [NOTICE](./licenses/Kotlin-NOTICE.txt) |
| kotlinx.coroutines (core, swing) | 1.11.0 | Apache-2.0 | JetBrains s.r.o. and contributors | [Apache-2.0](./licenses/Apache-2.0.txt), [NOTICE](./licenses/kotlinx-coroutines-NOTICE.txt) |
| kotlinx.serialization (core, json) | 1.12.0 | Apache-2.0 | JetBrains s.r.o. and contributors | [Apache-2.0](./licenses/Apache-2.0.txt), [NOTICE](./licenses/kotlinx-serialization-NOTICE.txt) |
| kotlinx-datetime | 0.7.1 | Apache-2.0 | JetBrains s.r.o. and contributors | [Apache-2.0](./licenses/Apache-2.0.txt) |
| kotlinx-atomicfu | 0.28.0 | Apache-2.0 | JetBrains s.r.o. and contributors | [Apache-2.0](./licenses/Apache-2.0.txt), [NOTICE](./licenses/kotlinx-atomicfu-NOTICE.txt) |
| Compose Multiplatform (runtime, ui, foundation, animation, material, material3, material-icons, ui-tooling-preview, desktop) | 1.12.x; material3 1.9.0; material-icons 1.7.3 | Apache-2.0 | JetBrains s.r.o.; The Android Open Source Project | [Apache-2.0](./licenses/Apache-2.0.txt) |
| AndroidX (annotation, collection, arch core-common, lifecycle, savedstate, navigationevent) | 1.9.1; 1.5.0; 2.2.0; 2.9.6–2.11.0; 1.3.6–1.4.0; 1.1.x | Apache-2.0 | The Android Open Source Project | [Apache-2.0](./licenses/Apache-2.0.txt) |
| Skiko (`skiko-awt`, `skiko-awt-runtime-windows-x64`) | 0.150.2 | Apache-2.0 | JetBrains s.r.o. | [Apache-2.0](./licenses/Apache-2.0.txt), [NOTICE](./licenses/Skiko-NOTICE.txt) |
| JetBrains Runtime API (`jbr-api`) | 1.9.0 | Apache-2.0 | JetBrains s.r.o. | [Apache-2.0](./licenses/Apache-2.0.txt) |
| JetBrains Java Annotations | 23.0.0 | Apache-2.0 | JetBrains s.r.o. | [Apache-2.0](./licenses/Apache-2.0.txt) |
| JSpecify | 1.0.0 | Apache-2.0 | The JSpecify Authors | [Apache-2.0](./licenses/Apache-2.0.txt) |
| SQLite JDBC (`org.xerial:sqlite-jdbc`) | 3.54.1.0 | Apache-2.0; parti BSD-3-Clause (Zentus) | Taro L. Saito; David Crawshaw | [LICENSE](./licenses/sqlite-jdbc-LICENSE.txt), [LICENSE.zentus](./licenses/sqlite-jdbc-LICENSE.zentus.txt) |
| Vosk (`com.alphacephei:vosk`) | 0.3.45 | Apache-2.0 | Alpha Cephei Inc. | [Apache-2.0](./licenses/Apache-2.0.txt) |
| JNA e JNA Platform | 5.19.1 | Apache-2.0 **oppure** LGPL-2.1-or-later, a scelta: **si applica Apache-2.0** | Timothy Wall and JNA contributors | [Apache-2.0](./licenses/Apache-2.0.txt) |
| JLayer (`com.googlecode.soundlibs:jlayer`) | 1.0.1.4 | LGPL-2.0-or-later, **usata secondo LGPL-2.1** | JavaZoom; Tobias Bading e altri | [LGPL-2.1](./licenses/LGPL-2.1.txt) |
| MP3SPI (`com.googlecode.soundlibs:mp3spi`) | 1.9.5.4 | LGPL-2.0-or-later, **usata secondo LGPL-2.1** | JavaZoom | [LGPL-2.1](./licenses/LGPL-2.1.txt) |
| Tritonus Share (`com.googlecode.soundlibs:tritonus-share`) | 0.3.7.4 | LGPL-2.0-or-later, **usata secondo LGPL-2.1** | Matthias Pfisterer; Florian Bomers | [LGPL-2.1](./licenses/LGPL-2.1.txt) |
| Jump3r (`io.github.raphimc:jump3r`), porting Java di LAME | 1.0.6 | LGPL-2.1-or-later | The LAME project (Mark Taylor, Robert Hegemann, Gabriel Bouvigne e altri); autori del porting Java | [LGPL-2.1](./licenses/LGPL-2.1.txt) |

`com.googlecode.soundlibs:jlayer` dichiara JUnit 3.8.2 come dipendenza: è escluso dal build (`shared/build.gradle.kts`) e non è presente nell'installer.

## 2. Componenti nativi e dati

| File nell'installazione | Contenuto | Licenze | Testi |
|---|---|---|---|
| `app\skiko-windows-x64.dll` | Skia (build JetBrains `m150-a3f509bf52`) con le librerie compilate staticamente: FreeType, HarfBuzz, ICU, libpng, zlib, Expat 2.7.4, libjpeg-turbo, libwebp | BSD-3-Clause (Skia); FreeType License (FTL); Old MIT (HarfBuzz); Unicode License V3 (ICU); libpng License; zlib License; MIT (Expat); IJG + BSD-3-Clause (libjpeg-turbo); BSD-3-Clause + concessione brevetti (libwebp) | [Skia](./licenses/Skia-BSD-3-Clause.txt), [FreeType](./licenses/FreeType-FTL.txt), [HarfBuzz](./licenses/HarfBuzz-COPYING.txt), [ICU](./licenses/ICU-74-LICENSE.txt), [libpng](./licenses/libpng-LICENSE.txt), [zlib](./licenses/zlib-LICENSE.txt), [Expat](./licenses/expat-COPYING.txt), [libjpeg-turbo](./licenses/libjpeg-turbo-LICENSE.md) e [README.ijg](./licenses/libjpeg-turbo-README.ijg), [libwebp](./licenses/libwebp-COPYING.txt) e [PATENTS](./licenses/libwebp-PATENTS.txt) |
| `app\icudtl.dat` | Dati ICU 74 (`icudt74l`) | Unicode License V3, con gli avvisi dei dati di terze parti contenuti nel file ICU | [ICU 74 LICENSE](./licenses/ICU-74-LICENSE.txt) |
| `jnidispatch.dll` (dentro il JAR JNA) | Libreria nativa JNA | Apache-2.0 (vedi sopra) | [Apache-2.0](./licenses/Apache-2.0.txt) |
| `sqlitejdbc.dll` (dentro il JAR SQLite JDBC) | Motore SQLite e binding JDBC | SQLite: pubblico dominio; binding: Apache-2.0 / BSD-3-Clause | [LICENSE](./licenses/sqlite-jdbc-LICENSE.txt), [LICENSE.zentus](./licenses/sqlite-jdbc-LICENSE.zentus.txt) |
| `libvosk.dll` (dentro il JAR Vosk) | Vosk, Kaldi, OpenFst, OpenBLAS, CLAPACK | Apache-2.0 (Vosk, Kaldi, OpenFst); BSD-3-Clause (OpenBLAS); BSD modificata (LAPACK/CLAPACK) | [Apache-2.0](./licenses/Apache-2.0.txt), [Kaldi](./licenses/Kaldi-COPYING.txt), [OpenBLAS](./licenses/OpenBLAS-LICENSE.txt), [LAPACK](./licenses/LAPACK-LICENSE.txt) |
| `libstdc++-6.dll`, `libgcc_s_seh-1.dll` (dentro il JAR Vosk) | Librerie di runtime GCC 10 (MinGW-w64) | GPL-3.0 con GCC Runtime Library Exception 3.1 | [GPL-3.0](./licenses/GPL-3.0.txt), [GCC RLE 3.1](./licenses/GCC-Runtime-Library-Exception-3.1.txt) |
| `libwinpthread-1.dll` (dentro il JAR Vosk) | MinGW-w64 winpthreads | MIT e licenze BSD-style indicate nel file | [winpthreads COPYING](./licenses/winpthreads-COPYING.txt) |

Le DLL GCC sono distribuite senza modifiche, così come incluse nel JAR Vosk. Il sorgente corrispondente è quello di GCC 10 e di MinGW-w64, disponibile su <https://gcc.gnu.org/> e <https://www.mingw-w64.org/>. Su richiesta l'autore fornisce i riferimenti precisi o una copia dei sorgenti tramite le Issues del repository, per almeno tre anni dalla release.

I testi di FreeType, HarfBuzz, libpng, zlib, libjpeg-turbo e libwebp sono presi dai repository ufficiali (versione corrente). Le versioni esatte incluse sono fissate dalla build Skia `m150-a3f509bf52` di JetBrains (<https://github.com/JetBrains/skia-pack>), che può contenere anche componenti minori di Skia con licenze compatibili BSD/MIT/Apache.

## 3. Runtime Java incluso

L'installer contiene un runtime Java ridotto, generato con `jlink` dal JDK usato per il packaging. Il runtime ha licenza propria e i relativi avvisi si trovano nella cartella `runtime\legal\` dell'installazione: **non va rimossa**.

Per le release pubbliche il packaging va eseguito con **Eclipse Temurin** (OpenJDK, GPL-2.0 con Classpath Exception), non con Oracle JDK. La release 1.0.1 include il runtime **Eclipse Temurin 21.0.13+9**. Verifica: `runtime\legal\java.base\LICENSE` inizia con "The GNU General Public License (GPL)". Il sorgente di OpenJDK è disponibile su <https://github.com/adoptium/jdk21u>.

## 4. Librerie LGPL: sorgenti e sostituzione

JLayer, MP3SPI, Tritonus Share e Jump3r sono distribuiti **senza modifiche**, come JAR separati.

**Sorgenti corrispondenti**, allegati a ogni release nel file `lgpl-sources-<versione>.zip` e comunque disponibili su Maven Central:

- <https://repo1.maven.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4-sources.jar>
- <https://repo1.maven.org/maven2/com/googlecode/soundlibs/mp3spi/1.9.5.4/mp3spi-1.9.5.4-sources.jar>
- <https://repo1.maven.org/maven2/com/googlecode/soundlibs/tritonus-share/0.3.7.4/tritonus-share-0.3.7.4-sources.jar>
- <https://repo1.maven.org/maven2/io/github/raphimc/jump3r/1.0.6/jump3r-1.0.6-sources.jar>

**Sostituire una libreria con una versione modificata:**

1. Apri la cartella di installazione (di norma `C:\Program Files\KiwiSDRRecorder\app\`).
2. Individua il JAR da sostituire: il nome contiene un suffisso di hash, ad esempio `jump3r-1.0.6-<hash>.jar`.
   Nota: `mp3spi` cerca le classi di decodifica in `jlayer-1.0.1.4-<hash>.jar`; se sostituisci JLayer, mantieni un'implementazione compatibile del package `javazoom.jl.decoder`.
3. Sostituiscilo con il tuo JAR, **mantenendo lo stesso nome file**; in alternativa aggiorna il nome nella riga `app.classpath` di `KiwiSDRRecorder.cfg`.
4. Riavvia l'applicazione.

Il [permesso supplementare](../LICENSE-LGPL-EXCEPTION.md) consente a chiunque riceva l'applicazione di modificarla per uso proprio e di effettuare reverse engineering per il debug di tali modifiche, come richiesto dalla sezione 6 della LGPL 2.1.

## 5. Componenti non inclusi

- **Modelli linguistici Vosk**: l'utente li scarica separatamente da <https://alphacephei.com/vosk/models>; ciascun modello ha la propria licenza.
- **Strumenti di build** (Gradle, compilatore Kotlin, plugin Compose, `jpackage`, WiX Toolset): non sono inclusi nell'installer e non impongono obblighi sugli artefatti prodotti. Il `gradle-wrapper.jar` presente nel repository sorgente è distribuito secondo Apache-2.0.

## 6. Manutenzione di questo documento

A ogni aggiornamento delle dipendenze:

1. genera l'immagine con `.\gradlew.bat :composeApp:createDistributable`;
2. elenca JAR e file nativi in `composeApp\build\compose\binaries\main\app\KiwiSDRRecorder\app\` e confrontali con le tabelle sopra;
3. per ogni componente nuovo o con versione cambiata, verifica la licenza nel POM e nei file `LICENSE`/`NOTICE` del JAR e aggiungi il testo in `docs/licenses/` se manca;
4. aggiorna versioni e link ai sorgenti LGPL e rigenera `lgpl-sources-<versione>.zip`.
