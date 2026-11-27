package com.grabber.decoder

import com.grabber.model.digital.DecodedImage
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecodedTextLine
import com.grabber.model.digital.DigitalMode
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/**
 * Salvataggio su richiesta delle uscite dei decoder in `<registrazioni>/Decodifiche/`:
 * testi e messaggi in `<modo>_<AAAAMMGG_hhmmss>.txt`, immagini FAX/SSTV in PNG.
 */
class DecodedOutputStore(private val recordingsDirectory: () -> File) {
    private val fileTime = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val lineTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val directory: File get() = File(recordingsDirectory(), "Decodifiche")

    /** Scrive [items] in un file di testo; restituisce il file, o null se non c'è nulla da salvare. */
    fun saveText(mode: DigitalMode, frequencyKhz: Double, items: List<DecodedItem>): File? {
        if (items.isEmpty()) return null
        val file = newFile(mode, "txt")
        val header = "# KiwiSDR Grabber — ${mode.displayName} — ${"%.3f".format(java.util.Locale.US, frequencyKhz)} kHz — " +
            "salvato il ${LocalDateTime.now().format(lineTime)}\n"
        val body = buildString {
            append(header)
            var previousWasText = false
            for (item in items) {
                if (item is DecodedTextLine && !item.isStatus) {
                    // Testo continuo: le righe restano come ricevute, con l'ora della prima riga di ogni blocco.
                    if (!previousWasText) append("[").append(utc(item.receivedAtMillis)).append(" UTC]\n")
                    append(item.text).append('\n')
                    previousWasText = true
                } else {
                    append(utc(item.receivedAtMillis)).append(" UTC  ").append(item.plainText).append('\n')
                    previousWasText = false
                }
            }
        }
        file.writeText(body, Charsets.UTF_8)
        return file
    }

    /** Salva l'immagine in PNG; null se l'immagine è vuota. */
    fun saveImage(image: DecodedImage): File? {
        if (image.rows.isEmpty() || image.width <= 0) return null
        val buffered = BufferedImage(image.width, image.rows.size, BufferedImage.TYPE_INT_RGB)
        image.rows.forEachIndexed { y, row ->
            val width = minOf(row.size, image.width)
            buffered.setRGB(0, y, width, 1, row, 0, width)
        }
        val file = newFile(image.mode, "png")
        ImageIO.write(buffered, "png", file)
        return file
    }

    private fun newFile(mode: DigitalMode, extension: String): File {
        val dir = directory.also { it.mkdirs() }
        val base = "${mode.name}_${LocalDateTime.now().format(fileTime)}"
        var file = File(dir, "$base.$extension")
        var index = 2
        while (file.exists()) file = File(dir, "${base}_${index++}.$extension")
        return file
    }

    private fun utc(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).format(lineTime)
}
