package com.grabber.decoder

import com.grabber.model.digital.DecodedImage
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecodedTextLine
import com.grabber.model.digital.DecoderMetrics
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.SkimmerSignal

/**
 * Destinazione delle uscite di un decoder (server o locale). Le chiamate arrivano da un solo flusso
 * alla volta (dispatcher seriale del decoder).
 */
interface DecoderSink {
    /** Elemento completo (riga FT8, spot WSPR, messaggio NAVTEX…). */
    fun add(item: DecodedItem)

    /** Testo continuo (CW, RTTY…): i caratteri `\n` chiudono la riga corrente. */
    fun appendText(text: String)

    /** Riga di stato (intestazioni, cambi di frequenza), esclusa dalle notifiche. */
    fun status(text: String)

    /** Aggiornamento di un segnale dello skimmer, identificato dalla frequenza audio. */
    fun skimmer(audioHz: Int, frequencyKhz: Double, wpm: Int, char: Char)

    fun metrics(update: (DecoderMetrics) -> DecoderMetrics)

    /** Nuova immagine: la precedente, se aveva righe, è considerata completata. */
    fun newImage(width: Int, title: String)

    fun imageRow(pixels: IntArray)

    /** L'immagine corrente è terminata (es. arresto automatico del FAX). */
    fun imageCompleted()
}

/**
 * Accumulo in memoria delle uscite del decoder attivo, con limiti di dimensione: [maxItems] elementi
 * e [maxImageRows] righe d'immagine. Non è thread-safe: va usato da un solo flusso.
 */
class DecoderOutputBuffer(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxItems: Int = MAX_ITEMS,
    private val maxImageRows: Int = MAX_IMAGE_ROWS,
    private val maxLineLength: Int = MAX_LINE_LENGTH
) {
    companion object {
        const val MAX_ITEMS = 2000
        const val MAX_IMAGE_ROWS = 4096
        const val MAX_LINE_LENGTH = 132
        const val SKIMMER_MAX_CHARS = 120
    }

    var items: List<DecodedItem> = emptyList()
        private set
    var image: DecodedImage? = null
        private set
    var metrics: DecoderMetrics = DecoderMetrics()
        private set

    /** Elementi completati dall'ultima lettura, per notifiche e salvataggi. */
    private val completed = ArrayList<DecodedItem>()
    private var openLine: DecodedTextLine? = null
    private var imageVersion = 0L
    private val completedImages = ArrayList<DecodedImage>()

    fun clear() {
        items = emptyList()
        image = null
        openLine = null
        metrics = DecoderMetrics()
        completed.clear()
        completedImages.clear()
    }

    /** Restituisce e svuota gli elementi completati (righe di testo chiuse, messaggi). */
    fun drainCompleted(): List<DecodedItem> {
        if (completed.isEmpty()) return emptyList()
        val copy = ArrayList(completed)
        completed.clear()
        return copy
    }

    fun drainCompletedImages(): List<DecodedImage> {
        if (completedImages.isEmpty()) return emptyList()
        val copy = ArrayList(completedImages)
        completedImages.clear()
        return copy
    }

    val sink: DecoderSink = object : DecoderSink {
        override fun add(item: DecodedItem) {
            closeOpenLine()
            push(item)
            completed += item
        }

        override fun appendText(text: String) {
            for (ch in text) {
                when (ch) {
                    '\n' -> closeOpenLine(force = true)
                    '\r', '\u0000' -> Unit
                    '\u000C' -> clearTextOnly()
                    else -> appendChar(ch)
                }
            }
        }

        override fun status(text: String) {
            closeOpenLine()
            push(DecodedTextLine(clock(), text, isStatus = true))
        }

        override fun skimmer(audioHz: Int, frequencyKhz: Double, wpm: Int, char: Char) {
            val list = items.toMutableList()
            val index = list.indexOfFirst { it is SkimmerSignal && kotlin.math.abs(it.frequencyKhz - frequencyKhz) < 0.0005 }
            val now = clock()
            if (index >= 0) {
                val current = list[index] as SkimmerSignal
                list[index] = current.copy(
                    receivedAtMillis = now,
                    wpm = if (wpm > 0) wpm else current.wpm,
                    text = (current.text + char).takeLast(SKIMMER_MAX_CHARS)
                )
            } else {
                if (char.isWhitespace()) return
                list += SkimmerSignal(now, frequencyKhz, wpm, char.toString())
                list.sortBy { (it as? SkimmerSignal)?.frequencyKhz ?: 0.0 }
            }
            // Le righe non aggiornate da un tempo proporzionale alla loro lunghezza scompaiono, come nel client web.
            items = list.filter { it !is SkimmerSignal || now - it.receivedAtMillis < 5_000L * it.text.length.coerceAtLeast(6) }
                .takeLast(maxItems)
        }

        override fun metrics(update: (DecoderMetrics) -> DecoderMetrics) {
            metrics = update(metrics)
        }

        override fun newImage(width: Int, title: String) {
            finishImage()
            image = DecodedImage(
                mode = image?.mode ?: DigitalMode.FAX,
                width = width,
                rows = emptyList(),
                title = title,
                startedAtMillis = clock(),
                version = ++imageVersion
            )
        }

        override fun imageRow(pixels: IntArray) {
            val current = image ?: DecodedImage(DigitalMode.FAX, pixels.size, emptyList(), "", clock(), ++imageVersion)
            val rows = if (current.rows.size >= maxImageRows) current.rows.drop(1) + pixels else current.rows + pixels
            image = current.copy(rows = rows, width = pixels.size, version = ++imageVersion)
        }

        override fun imageCompleted() = finishImage()
    }

    /** Modo a cui assegnare le immagini create da [DecoderSink.newImage]. */
    fun setImageMode(mode: DigitalMode) {
        image = image?.copy(mode = mode) ?: DecodedImage(mode, 0, emptyList(), "", clock(), ++imageVersion)
    }

    private fun finishImage() {
        val current = image ?: return
        if (current.rows.size >= MIN_COMPLETED_ROWS) completedImages += current
        image = current.copy(rows = emptyList(), version = ++imageVersion)
    }

    private fun appendChar(ch: Char) {
        val now = clock()
        val line = openLine
        val updated = if (line == null) DecodedTextLine(now, ch.toString()) else line.copy(text = line.text + ch)
        openLine = updated
        val list = items.toMutableList()
        if (line != null && list.isNotEmpty() && list.last() === line) list[list.lastIndex] = updated else list += updated
        items = list.takeLast(maxItems)
        if (updated.text.length >= maxLineLength) closeOpenLine(force = true)
    }

    private fun closeOpenLine(force: Boolean = false) {
        val line = openLine ?: run {
            if (force) return
            return
        }
        openLine = null
        if (line.text.isNotBlank()) completed += line
    }

    private fun clearTextOnly() {
        openLine = null
        items = items.filter { it !is DecodedTextLine }
    }

    private fun push(item: DecodedItem) {
        items = (items + item).let { if (it.size > maxItems) it.takeLast(maxItems) else it }
    }

    private val MIN_COMPLETED_ROWS = 20
}
