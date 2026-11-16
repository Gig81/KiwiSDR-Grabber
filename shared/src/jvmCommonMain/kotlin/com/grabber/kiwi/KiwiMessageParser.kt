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
    val connected: String,
    val location: String = ""
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
    /** Tipo `DX_MKRS` nell'intestazione della risposta `mkr` (sorgente KiwiSDR `rx_cmd.cpp`). */
    private const val DX_MKRS_RESPONSE = 4

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Voci senza slot (`i`) o frequenza (`f`) rappresentano canali liberi e vengono scartate. */
    fun parseUserList(userCbJson: String): List<KiwiUserEntry> {
        val entries = json.parseToJsonElement(userCbJson.trim()) as? JsonArray ?: return emptyList()
        return entries.mapNotNull { (it as? JsonObject)?.toUserEntry() }
    }

    /**
     * Payload di `mkr=`: array JSON il cui primo elemento è un'intestazione (`t` = tipo di risposta),
     * seguito dalle etichette `{"g","f","fl","i","n",...}` con testi URL-encoded.
     * Restituisce `null` se la risposta non è un elenco di marcatori (es. `DX_STEP`).
     */
    fun parseDxMarkers(payload: String): List<KiwiDxLabel>? {
        val entries = json.parseToJsonElement(payload.trim()) as? JsonArray ?: return null
        val header = entries.firstOrNull() as? JsonObject ?: return null
        if (header.stringField("t")?.toIntOrNull() != DX_MKRS_RESPONSE) return null
        return entries.drop(1).mapNotNull { (it as? JsonObject)?.toDxLabel() }
    }

    private fun JsonObject.toDxLabel(): KiwiDxLabel? {
        val frequencyKhz = stringField("f")?.toDoubleOrNull() ?: return null
        return KiwiDxLabel(
            id = stringField("g")?.toIntOrNull() ?: -1,
            frequencyKhz = frequencyKhz,
            mode = KiwiDxLabel.modeFromFlags(stringField("fl")?.toIntOrNull() ?: 0),
            name = stringField("i")?.let(::urlDecodeOrRaw).orEmpty().trim(),
            notes = stringField("n")?.let(::urlDecodeOrRaw).orEmpty().trim()
        )
    }

    /**
     * Header: tag `SND` (3 byte), flag (1), sequenza little-endian (4), S-meter big-endian (2).
     * KiwiSDR trasmette i campioni in big-endian salvo che il flag indichi il contrario.
     */
    fun decodeSoundFrame(bytes: ByteArray): SoundFrame? {
        if (bytes.size < SND_HEADER_SIZE) return null
        // Letture assolute: Buffer.position(int) covariante non esiste su Android 10.
        val buffer = ByteBuffer.wrap(bytes)
        val flags = buffer.get(SND_FLAGS_OFFSET).toInt() and 0xFF
        val sequence = buffer.order(ByteOrder.LITTLE_ENDIAN).getInt(SND_FLAGS_OFFSET + 1).toLong() and 0xFFFFFFFFL
        val sMeter = buffer.order(ByteOrder.BIG_ENDIAN).getShort(SND_FLAGS_OFFSET + 5).toInt() and 0xFFFF
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
            connected = stringField("t").orEmpty(),
            location = stringField("g")?.let(::urlDecodeOrRaw).orEmpty()
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
