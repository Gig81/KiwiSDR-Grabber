# Cronologia delle versioni

Registro sintetico delle versioni (desktop e, dalla 1.2.0, Android) distribuite e in preparazione. Le date e gli asset diventano definitivi solo quando la relativa GitHub Release viene pubblicata.

| Versione | Stato | Tipo | Riepilogo | Note |
|---|---|---|---|---|
| `1.3.0` | Pubblicata (19/11/2026) | Minor | Tema chiaro, scansione di un intervallo di frequenze, equalizzatore e filtri DSP per ascolto e nuove registrazioni, collegamento alla mappa dei ricevitori di rx-tx.info, su Windows e Android. | [Note 1.3.0](./RELEASE_NOTES_1.3.0.md) |
| `1.2.0` | Pubblicata (16/11/2026) | Minor | Prima versione Android (ascolto, waterfall, preferiti, utenti, registrazione WAV, attivazione `GRAB-A-…`), distribuita come APK; correzione di **Sincronizza** dei preferiti del server sul desktop. | [Archivio 1.2.0](./releases/RELEASE_NOTES_1.2.0.md) |
| `1.1.0` | Pubblicata (16/11/2026) | Minor | Scansione dei canali preferiti selezionati con arresto sui segnali sopra la soglia dello squelch e ripresa automatica (funzione avanzata). | [Archivio 1.1.0](./releases/RELEASE_NOTES_1.1.0.md) |
| `1.0.1` | Pubblicata (12/11/2026) | Patch | Correzione del primo avvio e del logging degli errori; scelta di avviare l'app e/o aprire le note dopo l'installazione Windows; nessuna finestra di console. | [Archivio 1.0.1](./releases/RELEASE_NOTES_1.0.1.md) |
| `1.0.0` | Pubblicata | Prima release desktop | Prima release pubblica per Windows. | [Archivio 1.0.0](./releases/RELEASE_NOTES_1.0.0.md) |

## Convenzione

Il progetto usa la versione applicativa in `gradle.properties` (`appVersion`) e tag Git annotati nel formato `v<versione>`. La versione di una release già pubblicata non si modifica: correzioni successive ricevono una nuova versione. Applicare Semantic Versioning quando compatibile con le API e le funzionalità del progetto:

- **Patch** (`1.0.x`): correzioni compatibili e miglioramenti circoscritti.
- **Minor** (`1.x.0`): nuove funzionalità compatibili.
- **Major** (`x.0.0`): cambiamenti incompatibili o migrazioni importanti.

Per la procedura operativa e i controlli, vedere [Distribuzione e rilasci](./DISTRIBUZIONE_E_RILASCI.md).
