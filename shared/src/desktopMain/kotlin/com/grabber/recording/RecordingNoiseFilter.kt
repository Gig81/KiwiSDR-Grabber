package com.grabber.recording

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Filtro dei segmenti QSO "rumore" (scariche, QRN) registrati con un gate attivo.
 * Un segmento scartato viene cancellato dal disco e non entra nell'archivio.
 *
 * - [minDurationEnabled]: scarta prima della trascrizione i segmenti la cui durata attiva
 *   (senza la coda di hang time o di silenzio) è sotto [minDurationSeconds].
 * - [noiseWordsEnabled]: dopo la trascrizione scarta se il testo è vuoto o fatto solo di
 *   parole della lista [noiseWords] (separate da virgole o spazi).
 * - [minConfidenceEnabled]: dopo la trascrizione scarta se la confidenza media delle parole
 *   riconosciute da Vosk è sotto [minConfidence].
 *
 * Si applica solo ai segmenti chiusi dallo squelch o dal VAD, mai alla registrazione continua
 * o a intervalli, né alle trascrizioni richieste a mano dall'archivio.
 */
@Serializable
data class RecordingNoiseFilterConfig(
    val minDurationEnabled: Boolean = true,
    val minDurationSeconds: Double = DEFAULT_MIN_DURATION_SECONDS,
    val noiseWordsEnabled: Boolean = true,
    val noiseWords: String = DEFAULT_NOISE_WORDS,
    val minConfidenceEnabled: Boolean = false,
    val minConfidence: Double = DEFAULT_MIN_CONFIDENCE
) {
    fun normalized(): RecordingNoiseFilterConfig = copy(
        minDurationSeconds = minDurationSeconds.coerceIn(MIN_DURATION_RANGE),
        noiseWords = noiseWords.trim(),
        minConfidence = minConfidence.coerceIn(MIN_CONFIDENCE_RANGE)
    )

    /** Vero se almeno un controllo richiede il testo della trascrizione. */
    val needsTranscription: Boolean get() = noiseWordsEnabled || minConfidenceEnabled

    companion object {
        const val DEFAULT_MIN_DURATION_SECONDS = 1.5
        const val DEFAULT_NOISE_WORDS = "uso"
        const val DEFAULT_MIN_CONFIDENCE = 0.5
        val MIN_DURATION_RANGE = 0.5..10.0
        val MIN_CONFIDENCE_RANGE = 0.1..0.9
    }
}

object RecordingNoiseFilter {
    private val WORD_SEPARATORS = Regex("[^\\p{L}\\p{N}']+")
    private val LIST_SEPARATORS = Regex("[,;\\s]+")

    /** Vero se il segmento va scartato prima della trascrizione perché troppo breve. */
    fun isTooShort(config: RecordingNoiseFilterConfig, activeMillis: Long): Boolean =
        config.minDurationEnabled && activeMillis < config.normalized().minDurationSeconds * 1000.0

    /**
     * Motivo dello scarto dopo la trascrizione, oppure `null` se il segmento va tenuto.
     * [meanConfidence] è la confidenza media delle parole (assente se Vosk non ha riconosciuto parole).
     */
    fun discardReason(config: RecordingNoiseFilterConfig, text: String, meanConfidence: Double?): String? {
        val normalized = config.normalized()
        if (normalized.noiseWordsEnabled) {
            val words = tokenize(text)
            if (words.isEmpty()) return "trascrizione vuota"
            val noise = parseNoiseWords(normalized.noiseWords)
            if (noise.isNotEmpty() && words.all { it in noise }) {
                return "solo parole rumore (${words.distinct().joinToString(", ")})"
            }
        }
        if (normalized.minConfidenceEnabled && meanConfidence != null && meanConfidence < normalized.minConfidence) {
            return "confidenza media %.2f sotto la soglia %.2f".format(Locale.ROOT, meanConfidence, normalized.minConfidence)
        }
        return null
    }

    fun parseNoiseWords(list: String): Set<String> =
        list.split(LIST_SEPARATORS)
            .flatMap(::tokenize)
            .toSet()

    private fun tokenize(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(WORD_SEPARATORS).filter { it.isNotBlank() }
}
