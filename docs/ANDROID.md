# Sviluppo Android — appunti iniziali

## Stato

La versione Android è un'evoluzione futura e non è attualmente implementata. La configurazione Gradle attuale espone target JVM desktop per `shared` e `composeApp`; non sono configurati target Android, sorgenti `androidMain` o pacchetti APK/AAB.

Questo documento raccoglie i requisiti già individuati. Architettura, dipendenze, compatibilità e piano di verifica saranno approfonditi prima di iniziare l'implementazione.

## Obiettivo e ambito previsto

Realizzare una versione mobile interattiva e attenta ai consumi, distinta dalla modalità di monitoraggio continuo desktop:

- connessione e controllo del ricevitore KiwiSDR;
- interfaccia responsive e controlli adatti a smartphone e tablet;
- avvio e arresto manuale dell'ascolto e della registrazione;
- continuità di ascolto/registrazione a schermo spento tramite Android Foreground Service e notifica persistente;
- salvataggio delle registrazioni tramite Scoped Storage / MediaStore, nella raccolta audio pubblica appropriata.

Il piano corrente esclude per il mobile il carico intensivo della trascrizione STT offline e la segmentazione automatica complessa.

## Attività individuate

1. Attivare i target Android in `composeApp` e `shared`, mantenendo separate le implementazioni specifiche di piattaforma.
2. Verificare che protocollo, networking e logica condivisa usino dipendenze compatibili con Android e scegliere le implementazioni audio native necessarie.
3. Implementare interfaccia touch-friendly e comportamento responsive.
4. Implementare il Foreground Service con notifica persistente e gestione corretta del ciclo di vita.
5. Richiedere e gestire i permessi necessari; integrare MediaStore/Scoped Storage per la registrazione.
6. Collaudare su dispositivi o emulatori Android, incluso il comportamento a schermo spento e la gestione delle interruzioni.
7. Generare e firmare i pacchetti Android (`.apk` e, se necessario, `.aab`).

## Criteri di verifica iniziali

- Connessione stabile a un server KiwiSDR e controllo della sintonia.
- Avvio/arresto manuale dell'ascolto e della registrazione.
- Audio e registrazione mantenuti a schermo spento finché il servizio è attivo, con notifica visibile.
- Registrazioni accessibili tramite la raccolta multimediale prevista e senza aggirare i permessi Android.
- Interfaccia fruibile su dimensioni di schermo differenti.
- Build e pacchetto firmato verificati su un dispositivo Android supportato.

## Configurazione e rilascio

La guida [TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md](./TOOLCHAIN_E_AMBIENTE_DI_SVILUPPO.md) contiene già una proposta preliminare per Android Studio/SDK e per i comandi APK/AAB. Tali indicazioni non sono ancora una configurazione attiva né sono state validate su questo progetto; versioni SDK, minSdk/targetSdk, firma e destinazione di distribuzione restano da decidere.
