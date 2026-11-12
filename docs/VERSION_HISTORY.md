# Cronologia delle versioni

Registro sintetico delle versioni desktop distribuite e in preparazione. Le date e gli asset diventano definitivi solo quando la relativa GitHub Release viene pubblicata.

| Versione | Stato | Tipo | Riepilogo | Note |
|---|---|---|---|---|
| `1.0.1` | Pubblicata (12/11/2026) | Patch | Correzione del primo avvio e del logging degli errori; scelta di avviare l'app e/o aprire le note dopo l'installazione Windows; nessuna finestra di console. | [Note 1.0.1](./RELEASE_NOTES_1.0.1.md) |
| `1.0.0` | Pubblicata | Prima release desktop | Prima release pubblica per Windows. | [Archivio 1.0.0](./releases/RELEASE_NOTES_1.0.0.md) |

## Convenzione

Il progetto usa la versione applicativa in `gradle.properties` (`appVersion`) e tag Git annotati nel formato `v<versione>`. La versione di una release già pubblicata non si modifica: correzioni successive ricevono una nuova versione. Applicare Semantic Versioning quando compatibile con le API e le funzionalità del progetto:

- **Patch** (`1.0.x`): correzioni compatibili e miglioramenti circoscritti.
- **Minor** (`1.x.0`): nuove funzionalità compatibili.
- **Major** (`x.0.0`): cambiamenti incompatibili o migrazioni importanti.

Per la procedura operativa e i controlli, vedere [Distribuzione e rilasci](./DISTRIBUZIONE_E_RILASCI.md).
