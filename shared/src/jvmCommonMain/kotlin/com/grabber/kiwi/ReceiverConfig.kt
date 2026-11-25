package com.grabber.kiwi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Parti della configurazione del ricevitore (`MSG load_cfg=`, JSON percent-encoded) usate dall'app.
 *
 * @param extensionEnabled abilitazioni `<chiave>.enable` delle estensioni (es. `ft8`, `WSPR`, `cws`):
 * il client web le applica nel menu, l'app le rispetta allo stesso modo. Chiave assente = abilitata.
 * @param extApiChannels limite `ext_api_nchans` delle connessioni dei client non browser (SND, W/F e
 * `/EXT` contano tutte): 0 = nessuna, -1 = mai impostato.
 */
data class ReceiverConfig(
    val extensionEnabled: Map<String, Boolean>,
    val extApiChannels: Int?
) {
    fun isExtensionEnabled(configKey: String): Boolean = extensionEnabled[configKey] ?: true

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(encodedJson: String): ReceiverConfig {
            val decoded = com.grabber.kiwi.ext.ExtMessageParser.decodeComponent(encodedJson)
            val root = json.parseToJsonElement(decoded) as? JsonObject ?: return ReceiverConfig(emptyMap(), null)
            val enables = root.entries.mapNotNull { (key, value) ->
                val enable = ((value as? JsonObject)?.get("enable") as? JsonPrimitive)?.booleanOrNull
                enable?.let { key to it }
            }.toMap()
            val nchans = (root["ext_api_nchans"] as? JsonPrimitive)?.intOrNull
            return ReceiverConfig(enables, nchans)
        }
    }
}
