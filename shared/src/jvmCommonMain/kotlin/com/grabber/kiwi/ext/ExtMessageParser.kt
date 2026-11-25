package com.grabber.kiwi.ext

import com.grabber.model.digital.Ft8Message
import com.grabber.model.digital.WsprSpot
import java.io.ByteArrayOutputStream

/** Messaggio testuale `/EXT`: prefisso (`MSG` o `EXT`) e coppie chiave/valore nell'ordine ricevuto. */
data class ExtTextMessage(val tag: String, val params: List<Pair<String, String?>>) {
    fun value(key: String): String? = params.firstOrNull { it.first == key }?.second
    fun has(key: String): Boolean = params.any { it.first == key }
}

/** Frame binario `DAT`: [command] (primo byte) e il resto del payload. */
class ExtDataFrame(val command: Int, val payload: ByteArray)

/**
 * Decodifica dei messaggi del WebSocket `/EXT`, senza stato e senza I/O.
 *
 * Formati verificati con le tracce della fase 0 (KiwiSDR v1.902): i frame sono binari con prefisso
 * di 3 byte e uno spazio; `MSG`/`EXT` contengono coppie `chiave=valore` separate da spazi, con i
 * valori di testo percent-encoded; `DAT` ha un byte di comando seguito dal payload.
 */
object ExtMessageParser {
    private val ansiEscape = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val htmlTag = Regex("<[^>]*>")
    private val spaces = Regex("[ \\t]+")
    private val locatorPattern = Regex("^[A-R]{2}[0-9]{2}([a-x]{2})?$", RegexOption.IGNORE_CASE)
    private val callsignPattern = Regex("^[A-Z0-9/]*[0-9][A-Z0-9/]*[A-Z][A-Z0-9/]*$|^[A-Z0-9/]*[A-Z][A-Z0-9/]*[0-9][A-Z0-9/]*$")
    private val ft8Line = Regex(
        "^(\\d{2}:\\d{2}:\\d{2})\\s+([+-]?\\d+(?:\\.\\d+)?)\\s+([+-]?\\d+(?:\\.\\d+)?)\\s+(\\d+)((?:\\s+\\d+)*)\\s+(\\d\\.\\d\\*?)\\s+(.+)$"
    )
    private val wsprLine = Regex(
        "^(\\d{4})\\s+([+-]?\\d+)\\s+([+-]?\\d+(?:\\.\\d+)?)\\s+(\\d+\\.\\d+)\\s+([+-]?\\d+)\\s+(.+)$"
    )

    /** Separa prefisso e contenuto; null se il frame è troppo corto. */
    fun tagOf(bytes: ByteArray): String? = if (bytes.size < 3) null else bytes.decodeToString(0, 3)

    /** Messaggio `MSG`/`EXT` con i valori ancora codificati (vedi [decodeComponent]). */
    fun parseText(bytes: ByteArray): ExtTextMessage? {
        val tag = tagOf(bytes) ?: return null
        val body = if (bytes.size > 4) bytes.decodeToString(4, bytes.size) else ""
        val params = body.split(' ').filter { it.isNotEmpty() }.map { part ->
            val index = part.indexOf('=')
            if (index < 0) part to null else part.substring(0, index) to part.substring(index + 1)
        }
        return ExtTextMessage(tag, params)
    }

    fun parseData(bytes: ByteArray): ExtDataFrame? {
        if (bytes.size < 5 || tagOf(bytes) != "DAT") return null
        return ExtDataFrame(bytes[4].toInt() and 0xFF, bytes.copyOfRange(5, bytes.size))
    }

    /**
     * Decodifica percentuale UTF-8 (`%XX`), come `decodeURIComponent`: `+` resta `+`.
     * Sequenze non valide restano invariate.
     */
    fun decodeComponent(value: String): String {
        if ('%' !in value) return value
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hi = Character.digit(value[i + 1], 16)
                val lo = Character.digit(value[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    out.write(hi * 16 + lo)
                    i += 3
                    continue
                }
            }
            val bytes = c.toString().encodeToByteArray()
            out.write(bytes, 0, bytes.size)
            i++
        }
        return out.toByteArray().decodeToString()
    }

    /** Toglie codici colore ANSI, tag HTML ed entità più comuni. */
    fun stripMarkup(text: String): String =
        text.replace(ansiEscape, "")
            .replace(htmlTag, "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")

    /**
     * Riga FT8/FT4 (`chars=` già decodificato e ripulito):
     * `HH:MM:SS ±SNR ±dT Hz [km] [età] tipo messaggio`. Restituisce null per intestazioni e righe di stato.
     */
    fun parseFt8Line(line: String, receivedAtMillis: Long): Ft8Message? {
        val clean = stripMarkup(line).trim()
        val match = ft8Line.matchEntire(clean) ?: return null
        val (utc, snr, dt, hz, _, _, rest) = match.destructured
        val message = rest.replace(spaces, " ").replace("(RR73)", "RR73").trim()
        val (callsign, locator) = ft8Stations(message)
        return Ft8Message(
            receivedAtMillis = receivedAtMillis,
            utc = utc,
            snrDb = snr.toDoubleOrNull() ?: 0.0,
            dtSeconds = dt.toDoubleOrNull() ?: 0.0,
            audioHz = hz.toIntOrNull() ?: 0,
            message = message,
            callsign = callsign,
            locator = locator
        )
    }

    /** Stazione che trasmette e locatore di un messaggio FT8 standard (`CQ [DX] CALL GRID`, `TO DE GRID|REPORT`). */
    internal fun ft8Stations(message: String): Pair<String?, String?> {
        val tokens = message.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null to null
        val locator = tokens.last().takeIf { locatorPattern.matches(it) && it != "RR73" }?.uppercase()
        val de = when {
            tokens[0] == "CQ" || tokens[0] == "QRZ" || tokens[0] == "DE" -> {
                val afterModifier = if (tokens.size >= 4) tokens[2] else tokens.getOrNull(1)
                afterModifier
            }
            else -> tokens.getOrNull(1)
        }?.takeIf { isCallsign(it) }
        return de to locator
    }

    private fun isCallsign(token: String): Boolean =
        token.length in 3..11 && !token.startsWith("<") && callsignPattern.matches(token.uppercase())

    /**
     * Spot `WSPR_DECODED` (decodificato e ripulito): `HHMM SNR dT MHz drift CALL [GRID] [km] dBm (potenza)`.
     */
    fun parseWsprSpot(line: String, receivedAtMillis: Long): WsprSpot? {
        val clean = stripMarkup(line).replace(spaces, " ").trim()
        val match = wsprLine.matchEntire(clean) ?: return null
        val (utc, snr, dt, mhz, drift, rest) = match.destructured
        val powerLabel = Regex("\\(([^)]*)\\)\\s*$").find(rest)?.groupValues?.get(1)
        val fields = rest.substringBefore('(').trim().split(' ').filter { it.isNotEmpty() }
        if (fields.isEmpty()) return null
        val callsign = fields[0]
        val locator = fields.getOrNull(1)?.takeIf { locatorPattern.matches(it) }
        val powerDbm = fields.lastOrNull()?.toIntOrNull()?.takeIf { fields.size >= 2 }
        return WsprSpot(
            receivedAtMillis = receivedAtMillis,
            utc = utc,
            snrDb = snr.toIntOrNull() ?: 0,
            dtSeconds = dt.toDoubleOrNull() ?: 0.0,
            frequencyMhz = mhz.toDoubleOrNull() ?: 0.0,
            drift = drift.toIntOrNull() ?: 0,
            callsign = callsign,
            locator = locator?.uppercase(),
            powerDbm = powerDbm,
            powerLabel = powerLabel
        )
    }

    /**
     * Carattere del CW Skimmer: `<carattere><Hz>,<wpm>` già decodificato. Hz è la frequenza audio
     * rispetto alla portante; null se il formato non è riconosciuto.
     */
    fun parseSkimmerChar(decoded: String): SkimmerChar? {
        if (decoded.length < 2) return null
        val char = decoded[0]
        val rest = decoded.substring(1)
        val hz = rest.substringBefore(',').trim().toIntOrNull() ?: return null
        val wpm = rest.substringAfter(',', "").trim().toIntOrNull() ?: 0
        return SkimmerChar(char, hz, wpm)
    }

    data class SkimmerChar(val char: Char, val audioHz: Int, val wpm: Int)
}
