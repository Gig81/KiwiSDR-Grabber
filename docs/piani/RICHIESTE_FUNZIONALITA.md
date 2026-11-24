# Richieste di funzionalità degli utenti

Questa pagina raccoglie le richieste ricevute dagli utenti. Sono proposte per sviluppi futuri: non implicano che le funzioni siano già disponibili o pianificate per una specifica versione, salvo dove indicato.

## Italiano

### Da valutare

#### 1. Scrollbar più visibile con il tema chiaro

Con il tema chiaro la scrollbar si nota poco: renderla più scura.

- Tutte le scrollbar passano da `AppVerticalScrollbar` (`ui/designsystem/AppFeedback.kt`): a riposo usano `TextPrimary` con alpha 0.2, troppo tenue su sfondo chiaro.
- Proposta: un token dedicato nella palette (es. `ScrollbarThumb`) con alpha più alta nel tema chiaro (0.45–0.5), coperto dal test di contrasto di `:composeApp:desktopTest`. Intervento piccolo, solo desktop.
- **Implementata:** token `ScrollbarThumb` (`#8C1F2328` nel tema chiaro, scuro invariato), contrasto ≥ 3:1 su tutte le superfici verificato da `PaletteContrastTests`.

#### 2. Pannello "Informazioni server KiwiSDR" più compatto

Nella scheda "Utenti & monitor" ridurre il pannello "Informazioni server KiwiSDR" in modo che non compaia la scrollbar e resti più spazio per la lista degli utenti.

- Oggi la colonna sinistra divide l'altezza con pesi 1.3 (utenti) e 1 (server), ma il pannello server mostra 8 righe fisse: lo spazio assegnato è spesso troppo o troppo poco.
- Proposta: altezza del pannello server pari al suo contenuto (niente `weight`), righe su due colonne (coppie etichetta/valore affiancate) o layout compatto; tutto lo spazio restante va alla lista degli utenti.
- **Implementata:** pannello alto quanto il contenuto (5 righe invece di 8: stazione e antenna su riga intera, le altre voci affiancate a coppie); la lista degli utenti occupa lo spazio restante.

#### 3. Registrazioni brevi con la sola parola "uso"

Vengono spesso registrati segmenti brevi (3–5 s) la cui trascrizione è solo "uso": eliminarli.

- Causa probabile: un disturbo breve (scarica, QRN) supera la soglia dello squelch RF; il segmento dura quanto l'impulso più l'hang time (3,5 s predefiniti) e Vosk trascrive il rumore come una parola corta e frequente.
- Oggi non c'è una durata minima: `AudioRecorder` salva ogni segmento e `KiwiAppController.archiveCompletedSegment` lo archivia e lo accoda alla trascrizione.
- Proposte, combinabili:
  - durata minima di segmento configurabile (al netto dell'hang time) sotto la quale il file viene scartato;
  - scarto dopo la trascrizione se il testo è vuoto o composto solo da parole di una lista "rumore" configurabile (es. "uso"), oppure se la confidenza Vosk è bassa (dato presente nel risultato JSON);
  - registrazione con Voice Gate (VAD) attivo, così i segmenti senza voce non partono (vedi punto 4).
- Da decidere: cancellare il file o solo nasconderlo/marcarlo nell'archivio.
- **Implementata:** filtro anti-rumore nel pannello di registrazione, solo per i segmenti di un gate; i segmenti scartati vengono cancellati dal disco e non entrano nell'archivio (nessun avviso Telegram di trascrizione). Controlli: durata minima al netto dell'hang time (attivo, 1,5 s), parole rumore dopo la trascrizione con testo vuoto incluso (attivo, "uso"), confidenza media Vosk minima (spento, 50%). Le trascrizioni avviate a mano non sono filtrate.

#### 4. Squelch e VAD: relazioni e funzionamento combinato

Approfondire come interagiscono squelch e VAD.

Comportamento attuale (`SquelchController`):

- **Ascolto:** se lo squelch di ascolto è attivo decide solo lo squelch RF; il VAD di ascolto conta solo quando lo squelch di ascolto è spento. Non esiste una modalità "RF **e** voce".
- **Registrazione:** l'apertura del segmento è sempre guidata dallo squelch RF. Il VAD di registrazione ha effetto solo se lo squelch di registrazione è spento, e serve solo a ritardare l'inizio del segmento finché compare voce mentre il gate RF è aperto. La chiusura dipende solo da RF + hang time, mai dal VAD.
- Le due abilitazioni (squelch e VAD) appaiono indipendenti nella UI, ma con entrambe attive il VAD viene ignorato: il comportamento non è evidente all'utente.

Da valutare:

- modalità esplicite (solo RF / solo voce / RF e voce) al posto di due interruttori indipendenti;
- chiusura anticipata del segmento quando il VAD non rileva voce per N secondi, anche con RF ancora aperto;
- documentare il comportamento nella guida utente e nei tooltip.

**Implementata:** modalità esplicite per ascolto e registrazione (Libero/Continua, Solo RF, Solo voce, RF e voce) con selettori nella scheda Audio e nel Registratore; in console SQL e VAD sono le due componenti della modalità di ascolto, con tooltip. In registrazione "Solo voce" non dipende dalla soglia RF e chiude dopo l'hang time senza voce; "RF e voce" chiude anche dopo N secondi senza voce con RF aperto (attivo, 5 s, configurabile). Le impostazioni precedenti vengono convertite mantenendo il comportamento di allora. Comportamento descritto nella guida utente.

#### 5. Controllo della radio tramite cavo CAT

Verificare la possibilità di controllare dal computer la radio dell'utente collegata con cavo CAT. Obiettivi:

- sincronizzare frequenza e modo tra radio e KiwiSDR (in una o in entrambe le direzioni);
- leggere lo stato del PTT (serve anche al punto 6);
- comandare la radio dall'app (frequenza, modo, PTT, eventualmente potenza).

Note tecniche:

- Via più semplice: Hamlib tramite `rigctld` (protocollo TCP testuale, copre la maggior parte degli apparati), senza librerie native nell'app; in alternativa porta seriale diretta con i protocolli dei singoli costruttori (Icom CI-V, Yaesu, Kenwood).
- Solo desktop; probabilmente funzione a licenza. Va gestita da un nuovo coordinatore dietro `KiwiAppController`.

Licenze (verificate sulle fonti, non è un parere legale):

- **Hamlib** ([LICENSE](https://github.com/Hamlib/Hamlib/blob/master/LICENSE)): la libreria (`libhamlib-4.dll`) è LGPL-2.1-or-later; i programmi, compresi `rigctld.exe` e `rigctl.exe`, sono GPL-2.0-or-later. Il pacchetto ufficiale Windows (`hamlib-w64-<versione>.zip`, [release GitHub](https://github.com/Hamlib/Hamlib/releases), ultima 4.7.2) contiene in `bin\` gli eseguibili, `libhamlib-4.dll`, `libusb-1.0.dll` (LGPL-2.1), `libwinpthread-1.dll` (MIT/BSD) e `libgcc_s_seh-1.dll` (GPL-3 con GCC Runtime Library Exception), senza i testi di licenza delle DLL di terze parti.
- Opzioni per la nostra app (PolyForm Noncommercial, non GPL):
  - **Download a cura dell'utente, come i modelli Vosk** (consigliata): l'app indica la pagina delle release Hamlib, l'utente scompatta lo zip e indica la cartella; l'app avvia `rigctld.exe` e ci parla in TCP. Nessun obbligo per noi.
  - **`rigctld.exe` incluso nell'installer, non modificato**, usato come processo separato via TCP: è "aggregazione", ammessa dalla GPL senza effetti sulla licenza dell'app. Obblighi: includere GPL/LGPL e gli avvisi di Hamlib e delle DLL, e rendere disponibile il sorgente esatto della versione distribuita (archivio sorgente nel pacchetto o offerta scritta + link al tag). Precedente: Log4OM 2 (freeware non open source) distribuisce Hamlib e avvia `rigctld.exe`.
  - **`libhamlib-4.dll` caricata via JNA**: ammessa dalla LGPL come le librerie LGPL già incluse (DLL sostituibile, avvisi, sorgente o offerta); il permesso aggiuntivo in `LICENSE-LGPL-EXCEPTION.md` andrebbe esteso a Hamlib. Più lavoro di integrazione rispetto a `rigctld`.
- **CAT diretto senza Hamlib:** [jSerialComm](https://github.com/Fazecast/jSerialComm/blob/master/NOTICE) è a scelta Apache-2.0 o LGPL-3.0+ (si usa Apache-2.0, nessun obbligo di copyleft); contiene librerie native che estrae in una cartella temporanea all'avvio, da verificare con il runtime jlink. I protocolli CAT sono pubblicati nei manuali dei costruttori; non risultano limiti alla reimplementazione (non verificato per ogni marca).
- **flrig** (server CAT alternativo, XML-RPC) è GPL-3: va bene solo se installato dall'utente come programma separato.

#### 6. Soppressione dell'eco quando si trasmette e si ascolta insieme

Quando l'utente trasmette con la propria radio e contemporaneamente ascolta il proprio segnale tramite la KiwiSDR dagli altoparlanti, il microfono riprende l'audio ritardato e si crea eco o fischio di ritorno. Servono filtri audio automatici.

- La cancellazione d'eco classica (AEC) è difficile: il ritardo della KiwiSDR è lungo (circa 1 s o più) e variabile, e il percorso passa per la propagazione radio.
- Proposta principale: silenziare automaticamente l'ascolto (o abbassarne il volume) durante la trasmissione, con ripristino dopo un breve ritardo. Rilevamento della trasmissione:
  - PTT letto via CAT (punto 5), la soluzione più affidabile;
  - in alternativa una "VOX" sul microfono del PC, oppure un comando manuale/scorciatoia da tastiera.
- In aggiunta: consigliare nella guida l'uso delle cuffie.

#### 7. Decodifica dei modi digitali

Decodificare i modi digitali (es. FT8/FT4, WSPR, RTTY, PSK31, CW, fax meteo, SSTV).

Possibili strade, dalla più semplice:

**a) Decoder esterni tramite cavo audio virtuale.** L'app invia l'audio a un dispositivo di uscita scelto dall'utente (es. VB-CABLE), da cui lo leggono WSJT-X, fldigi ecc.

- Oggi `DesktopAudioPlayer` usa solo il dispositivo predefinito di sistema: serve la scelta del dispositivo di uscita (eventualmente una seconda uscita, per ascoltare e decodificare insieme). Serve anche la sintonia USB con banda passante adeguata.
- WSJT-X e fldigi leggono l'audio solo da schede audio (l'UDP di WSJT-X trasporta decodifiche e stato, non audio): su Windows il cavo virtuale resta la via pratica.
- Licenze (verificate sulle fonti):
  - **VB-CABLE** è donationware. Dire all'utente di installarlo da sé è la via gratuita prevista dal produttore. [Le condizioni VB-Audio](https://vb-audio.com/Services/licensing.htm) permettono anche di distribuirlo "così com'è" con un'applicazione, purché l'utente lo riconosca come prodotto VB-Audio e possa fare una donazione (in ambito aziendale servono licenze a pagamento; i cavi A+B e C+D non si possono includere). Consiglio: non includerlo, rimandare al sito.
  - **Virtual Audio Cable (Muzychenko)** è commerciale (licenza Home circa 30 $): solo installato dall'utente.
  - I driver virtuali open source (VirtualDrivers Virtual-Audio-Driver, MIT; Scream, MS-PL) richiedono la modalità test di Windows 11 per la firma dei driver: non adatti agli utenti finali.
  - **WSJT-X, fldigi, MSHV** sono GPL-3, **MMSSTV** LGPL; JTDX (fork di WSJT-X) dovrebbe essere GPL-3 e MultiPSK freeware proprietario (questi due non verificati). Mandare loro l'audio, come programmi separati installati dall'utente, non comporta obblighi.

**b) Decoder integrati nel server KiwiSDR (estensioni).** Verificato sui sorgenti di [jks-prv/KiwiSDR](https://github.com/jks-prv/KiwiSDR) e [kiwiclient](https://github.com/jks-prv/kiwiclient):

- **Decodifica sul server**, con risultati inviati al client: FT8/FT4 (testo), WSPR (testo), CW_decoder e CW_skimmer (testo), FAX e SSTV (immagini come dati binari), HFDL, ALE_2G, DRM, FreeDV (formati dei messaggi non verificati).
- **Decodifica nel browser** (JavaScript sull'audio): FSK (RTTY, DSC, Selcall…) e NAVTEX. Il server non invia risultati: andrebbero reimplementati nell'app.
- **WebSocket `/EXT`:** si apre con lo stesso token di `/SND` già connesso, e viene agganciato al suo canale (la sintonia è quella di SND). Sequenza: `SET auth t=kiwi p=…`, `SET ext_switch_to_client=<NOME> first_time=1 rx_chan=<n>`, poi i comandi dell'estensione (es. `SET ft8_start=0` per FT8, `=1` per FT4) e `SET keepalive` periodico. I risultati arrivano come `EXT nome=valore` con valori URL-encoded; per FT8 `EXT chars=…` con righe tipo `22:40:30 +11.0 +2.80 1931 … CQ DH1NAS JO50`, che contengono HTML (link QRZ, colori) da ripulire.
- **Slot:** `/EXT` non occupa un canale del ricevitore; usa solo una delle connessioni di rete del server e cade con la connessione SND.
- **Limite per client non-browser:** l'app si collega già con la forma URL `<token>/SND`, che il server tratta come "kiwirecorder" (`rx_server.cpp`). Da IP non locali queste connessioni sono soggette all'impostazione di amministrazione `ext_api_nchans`: con 0 vengono rifiutate, altrimenti sono limitate in numero. Il limite vale già oggi per SND; va verificato su un server reale se anche `/EXT` lo consuma. Se un altro canale usa DRM, le estensioni sono bloccate.
- kiwiclient (`kiwirecorder.py --ext <NOME>`) apre già `/EXT` in modo generico e registra i messaggi: è un riferimento utile per il protocollo.
- **Licenze:** il codice KiwiSDR è misto GPL/LGPL/MIT per file. Implementare il protocollo senza copiarne il codice, come già fatto per SND e W/F, non comporta obblighi.

**c) Decoder integrati nell'app.** È la strada più costosa. WSJT-X, JTDX, MSHV e fldigi sono GPL-3: il loro codice non si può incorporare in un'app PolyForm Noncommercial. [ft8_lib](https://github.com/kgoba/ft8_lib/blob/master/LICENSE) è MIT, quindi compatibile (basta l'avviso nelle note di terze parti), ma è C nativo da integrare. Ragionevole solo per pochi modi semplici (es. CW, RTTY).

Proposta: partire da (a) per la massima copertura dei modi con poco lavoro, poi valutare (b) per FT8/WSPR/CW/FAX/SSTV mostrati direttamente nell'app. Da chiarire con gli utenti quali modi interessano davvero.

## English

This page tracks feature requests received from users. These are proposals for future development; they do not mean the features are already available or scheduled for a specific release, unless stated.

### Open

#### 1. More visible scrollbar in the light theme

In the light theme the scrollbar is hard to see: make it darker.

- All scrollbars go through `AppVerticalScrollbar` (`ui/designsystem/AppFeedback.kt`): when idle they use `TextPrimary` at alpha 0.2, too faint on a light background.
- Proposal: a dedicated palette token (e.g. `ScrollbarThumb`) with a higher alpha in the light theme (0.45–0.5), covered by the contrast test in `:composeApp:desktopTest`. Small, desktop-only change.
- **Implemented:** `ScrollbarThumb` token (`#8C1F2328` in the light theme, dark theme unchanged), contrast of at least 3:1 on every surface checked by `PaletteContrastTests`.

#### 2. More compact "KiwiSDR server info" panel

In the "Users & monitor" tab, shrink the "KiwiSDR server info" panel so it shows no scrollbar and leaves more room for the user list.

- The left column currently splits its height with weights 1.3 (users) and 1 (server), but the server panel shows 8 fixed rows: the allotted space is often too much or too little.
- Proposal: size the server panel to its content (no `weight`), with rows in two columns (label/value pairs side by side) or a compact layout; all remaining space goes to the user list.
- **Implemented:** the panel is as tall as its content (5 rows instead of 8: station and antenna on full rows, the other items side by side in pairs); the user list takes the remaining space.

#### 3. Short recordings containing only the word "uso"

Short segments (3–5 s) whose transcription is just "uso" are often recorded: get rid of them.

- Likely cause: a short burst of noise (static crash, QRN) exceeds the RF squelch threshold; the segment lasts as long as the burst plus the hang time (3.5 s by default), and Vosk transcribes the noise as a short, common word.
- There is no minimum duration today: `AudioRecorder` saves every segment and `KiwiAppController.archiveCompletedSegment` archives it and queues it for transcription.
- Proposals, which can be combined:
  - a configurable minimum segment length (excluding the hang time) below which the file is discarded;
  - discard after transcription when the text is empty or made only of words from a configurable "noise" list (e.g. "uso"), or when the Vosk confidence is low (available in the JSON result);
  - record with the Voice Gate (VAD) enabled, so segments without speech are not started (see item 4).
- To decide: delete the file, or only hide/flag it in the archive.
- **Implemented:** noise filter in the recorder panel, only for gate segments; discarded segments are deleted from disk and never enter the archive (no Telegram transcription alert). Controls: minimum length excluding the hang time (on, 1.5 s), noise words after transcription including empty text (on, "uso"), minimum average Vosk confidence (off, 50%). Manually started transcriptions are never filtered.

#### 4. Squelch and VAD: how they interact

Investigate how the squelch and the VAD work together.

Current behaviour (`SquelchController`):

- **Listening:** when listen squelch is on, only the RF squelch decides; the listen VAD only matters when listen squelch is off. There is no "RF **and** voice" mode.
- **Recording:** segment start is always driven by the RF squelch. The record VAD only has an effect when record squelch is off, and only delays the segment start until speech appears while the RF gate is open. The segment closes on RF + hang time only, never on the VAD.
- The two switches (squelch and VAD) look independent in the UI, but with both on the VAD is ignored: users cannot tell.

To evaluate:

- explicit modes (RF only / voice only / RF and voice) instead of two independent switches;
- closing the segment early when the VAD detects no speech for N seconds, even while RF is still open;
- documenting the behaviour in the user guide and tooltips.

**Implemented:** explicit modes for listening and recording (Open/Continuous, RF only, Voice only, RF and voice) with selectors in the Audio tab and the Recorder; in the console SQL and VAD are the two parts of the listening mode, with tooltips. In recording, "Voice only" does not depend on the RF threshold and closes after the hang time without speech; "RF and voice" also closes after N seconds without speech while RF is open (on, 5 s, configurable). Previous settings are converted keeping their old behaviour. Behaviour described in the user guide.

#### 5. Radio control over a CAT cable

Check whether the user's radio, connected to the computer with a CAT cable, can be controlled. Goals:

- sync frequency and mode between the radio and the KiwiSDR (one way or both ways);
- read the PTT state (also needed for item 6);
- control the radio from the app (frequency, mode, PTT, possibly power).

Technical notes:

- Simplest route: Hamlib via `rigctld` (plain-text TCP protocol, covers most radios), with no native libraries in the app; alternatively a direct serial port with each manufacturer's protocol (Icom CI-V, Yaesu, Kenwood).
- Desktop only; probably a licensed feature. It should be handled by a new coordinator behind `KiwiAppController`.

Licensing (checked against the sources; not legal advice):

- **Hamlib** ([LICENSE](https://github.com/Hamlib/Hamlib/blob/master/LICENSE)): the library (`libhamlib-4.dll`) is LGPL-2.1-or-later; the programs, including `rigctld.exe` and `rigctl.exe`, are GPL-2.0-or-later. The official Windows package (`hamlib-w64-<version>.zip`, [GitHub releases](https://github.com/Hamlib/Hamlib/releases), latest 4.7.2) has in `bin\` the executables, `libhamlib-4.dll`, `libusb-1.0.dll` (LGPL-2.1), `libwinpthread-1.dll` (MIT/BSD) and `libgcc_s_seh-1.dll` (GPL-3 with the GCC Runtime Library Exception), without license texts for the third-party DLLs.
- Options for our app (PolyForm Noncommercial, not GPL):
  - **User downloads it, like the Vosk models** (recommended): the app points to the Hamlib releases page, the user extracts the zip and selects the folder; the app starts `rigctld.exe` and talks to it over TCP. No obligations for us.
  - **Unmodified `rigctld.exe` in the installer**, run as a separate process over TCP: this is "aggregation", allowed by the GPL with no effect on the app's license. Obligations: include the GPL/LGPL and the Hamlib and DLL notices, and make the exact source of the shipped version available (source archive in the package, or a written offer plus a link to the tag). Precedent: Log4OM 2 (closed-source freeware) ships Hamlib and starts `rigctld.exe`.
  - **`libhamlib-4.dll` loaded through JNA**: allowed by the LGPL like the LGPL libraries we already ship (replaceable DLL, notices, source or offer); the additional permission in `LICENSE-LGPL-EXCEPTION.md` would need to cover Hamlib. More integration work than `rigctld`.
- **Direct CAT without Hamlib:** [jSerialComm](https://github.com/Fazecast/jSerialComm/blob/master/NOTICE) is Apache-2.0 or LGPL-3.0+ at our choice (we would use Apache-2.0: no copyleft obligations); it contains native libraries that it extracts to a temporary folder at startup, to be checked with the jlink runtime. CAT protocols are published in the manufacturers' manuals; no restrictions on reimplementing them were found (not checked for every brand).
- **flrig** (alternative CAT server, XML-RPC) is GPL-3: fine only if the user installs it as a separate program.

#### 6. Echo suppression while transmitting and listening

When users transmit with their own radio while listening to their own signal through the KiwiSDR on the PC speakers, the microphone picks up the delayed audio, causing echo or feedback howl. Automatic audio filtering is needed.

- Classic acoustic echo cancellation (AEC) is hard here: the KiwiSDR delay is long (about 1 s or more) and variable, and the path goes through radio propagation.
- Main proposal: automatically mute (or duck) the receive audio while transmitting, restoring it after a short delay. Detecting transmission:
  - PTT read over CAT (item 5), the most reliable option;
  - otherwise a "VOX" on the PC microphone, or a manual command/keyboard shortcut.
- Also: recommend headphones in the user guide.

#### 7. Digital mode decoding

Decode digital modes (e.g. FT8/FT4, WSPR, RTTY, PSK31, CW, weather fax, SSTV).

Possible routes, simplest first:

**a) External decoders through a virtual audio cable.** The app sends audio to an output device chosen by the user (e.g. VB-CABLE), from which WSJT-X, fldigi and similar read it.

- `DesktopAudioPlayer` currently only uses the system default device: output device selection is needed (possibly a second output, to listen and decode at the same time). USB tuning with a suitable passband is also needed.
- WSJT-X and fldigi only read audio from sound devices (WSJT-X's UDP carries decodes and status, not audio): on Windows a virtual cable remains the practical route.
- Licensing (checked against the sources):
  - **VB-CABLE** is donationware. Telling users to install it themselves is the free route the vendor intends. [VB-Audio's terms](https://vb-audio.com/Services/licensing.htm) also allow shipping it "as is" with an application, as long as users can recognise it as a VB-Audio product and can donate (corporate use needs paid licenses; the A+B and C+D cables cannot be bundled). Advice: do not bundle it, link to the website.
  - **Virtual Audio Cable (Muzychenko)** is commercial (Home license about $30): user-installed only.
  - Open-source virtual drivers (VirtualDrivers Virtual-Audio-Driver, MIT; Scream, MS-PL) need Windows 11 test mode for driver signing: not suitable for end users.
  - **WSJT-X, fldigi, MSHV** are GPL-3, **MMSSTV** is LGPL; JTDX (a WSJT-X fork) should be GPL-3 and MultiPSK proprietary freeware (these two not verified). Sending them audio, as separate programs installed by the user, creates no obligations.

**b) Decoders built into the KiwiSDR server (extensions).** Checked against the sources of [jks-prv/KiwiSDR](https://github.com/jks-prv/KiwiSDR) and [kiwiclient](https://github.com/jks-prv/kiwiclient):

- **Decoded on the server**, with results sent to the client: FT8/FT4 (text), WSPR (text), CW_decoder and CW_skimmer (text), FAX and SSTV (images as binary data), HFDL, ALE_2G, DRM, FreeDV (message formats not checked).
- **Decoded in the browser** (JavaScript on the audio): FSK (RTTY, DSC, Selcall…) and NAVTEX. The server sends no results: they would have to be reimplemented in the app.
- **`/EXT` WebSocket:** opened with the same token as an already connected `/SND`, and attached to its channel (tuning follows SND). Sequence: `SET auth t=kiwi p=…`, `SET ext_switch_to_client=<NAME> first_time=1 rx_chan=<n>`, then the extension's commands (e.g. `SET ft8_start=0` for FT8, `=1` for FT4) and a periodic `SET keepalive`. Results arrive as `EXT name=value` with URL-encoded values; for FT8, `EXT chars=…` with lines like `22:40:30 +11.0 +2.80 1931 … CQ DH1NAS JO50`, which contain HTML (QRZ links, colours) to strip.
- **Slots:** `/EXT` does not take a receiver channel; it only uses one of the server's network connections and drops with the SND connection.
- **Limit for non-browser clients:** the app already connects with the `<token>/SND` URL form, which the server treats as "kiwirecorder" (`rx_server.cpp`). From non-local IPs these connections are subject to the admin setting `ext_api_nchans`: with 0 they are refused, otherwise their number is capped. The limit already applies to SND today; whether `/EXT` also counts against it must be checked on a real server. When another channel is running DRM, extensions are locked.
- kiwiclient (`kiwirecorder.py --ext <NAME>`) already opens `/EXT` generically and logs the messages: a useful protocol reference.
- **Licensing:** KiwiSDR code is a per-file mix of GPL/LGPL/MIT. Implementing the protocol without copying its code, as already done for SND and W/F, creates no obligations.

**c) Decoders built into the app.** The most expensive route. WSJT-X, JTDX, MSHV and fldigi are GPL-3: their code cannot be embedded in a PolyForm Noncommercial app. [ft8_lib](https://github.com/kgoba/ft8_lib/blob/master/LICENSE) is MIT, so compatible (only its notice in the third-party notices), but it is native C to integrate. Only sensible for a few simple modes (e.g. CW, RTTY).

Proposal: start with (a) for the widest mode coverage with little work, then consider (b) to show FT8/WSPR/CW/FAX/SSTV directly in the app. Ask users which modes they actually need.
