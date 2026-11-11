package com.grabber.kiwi

import com.grabber.model.KiwiDxLabel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Utente connesso al ricevitore così come descritto dal messaggio `user_cb`. */
internal data class KiwiUserEntry(
    val slot: Int,
    val name: String,
    val freqHz: Long,
    val mode: String,
    val connected: String
)

/** Frame audio `SND` decodificato, con campioni PCM 16 bit già convertiti in little-endian. */
internal class SoundFrame(
    val sequence: Long,
    val rssiDbm: Double,
    val pcmLittleEndian: ByteArray
)

/**
 * Decodifica dei messaggi ricevuti dal server KiwiSDR, senza stato e senza I/O.
 */
internal object KiwiMessageParser {
    private const val SND_HEADER_SIZE = 10
    private const val SND_FLAGS_OFFSET = 3
    private const val SND_FLAG_LITTLE_ENDIAN = 0x80

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Voci senza slot (`i`) o frequenza (`f`) rappresentano canali liberi e vengono scartate. */
    fun parseUserList(userCbJson: String): List<KiwiUserEntry> {
        val entries = json.parseToJsonElement(userCbJson.trim()) as? JsonArray ?: return emptyList()
        return entries.mapNotNull { (it as? JsonObject)?.toUserEntry() }
    }

    /** Il payload di `load_dxcfg` può arrivare URL-encoded e racchiuso in un oggetto. */
    fun parseDxLabels(rawPayload: String): List<KiwiDxLabel> {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        val decoded = if (trimmed.contains("%")) urlDecodeOrRaw(trimmed) else trimmed
        val arrayStart = decoded.indexOf('[')
        val arrayEnd = decoded.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd <= arrayStart) return emptyList()
        return json.decodeFromString<List<KiwiDxLabel>>(decoded.substring(arrayStart, arrayEnd + 1))
    }

    /**
     * Header: tag `SND` (3 byte), flag (1), sequenza little-endian (4), S-meter big-endian (2).
     * KiwiSDR trasmette i campioni in big-endian salvo che il flag indichi il contrario.
     */
    fun decodeSoundFrame(bytes: ByteArray): SoundFrame? {
        if (bytes.size < SND_HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(SND_FLAGS_OFFSET)
        val flags = buffer.get().toInt() and 0xFF
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val sequence = buffer.getInt().toLong() and 0xFFFFFFFFL
        buffer.order(ByteOrder.BIG_ENDIAN)
        val sMeter = buffer.getShort().toInt() and 0xFFFF
        val isLittleEndian = (flags and SND_FLAG_LITTLE_ENDIAN) != 0
        return SoundFrame(
            sequence = sequence,
            rssiDbm = 0.1 * sMeter - 127.0,
            pcmLittleEndian = extractPcm(bytes, isLittleEndian)
        )
    }

    private fun extractPcm(bytes: ByteArray, isLittleEndian: Boolean): ByteArray {
        val pcm = ByteArray(bytes.size - SND_HEADER_SIZE)
        if (isLittleEndian) {
            System.arraycopy(bytes, SND_HEADER_SIZE, pcm, 0, pcm.size)
        } else {
            for (sample in 0 until pcm.size / 2) {
                pcm[sample * 2] = bytes[SND_HEADER_SIZE + sample * 2 + 1]
                pcm[sample * 2 + 1] = bytes[SND_HEADER_SIZE + sample * 2]
            }
        }
        return pcm
    }

    private fun JsonObject.toUserEntry(): KiwiUserEntry? {
        val slot = stringField("i")?.toIntOrNull() ?: return null
        val freqHz = longField("f") ?: return null
        return KiwiUserEntry(
            slot = slot,
            name = stringField("n")?.let(::urlDecodeOrRaw).orEmpty(),
            freqHz = freqHz,
            mode = stringField("m").orEmpty(),
            connected = stringField("t").orEmpty()
        )
    }

    private fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.longField(key: String): Long? {
        val content = stringField(key) ?: return null
        return content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong()
    }

    private fun urlDecodeOrRaw(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            value
        }
}
