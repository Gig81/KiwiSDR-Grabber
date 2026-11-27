package com.grabber.decoder.fsk

/**
 * Decodifica asincrona start/stop (RTTY): attende il fronte mark→space del bit di start, poi legge
 * [dataBits] bit (il primo ricevuto è il meno significativo) e verifica il bit di stop.
 *
 * Lavora sul valore morbido del [FskDemodulator]: con il filtro adattato a finestra di un bit, il
 * passaggio per lo zero cade mezzo bit dopo la transizione e ogni bit si legge alla fine della propria
 * finestra, cioè un bit intero dopo il suo inizio.
 */
class AsyncBitDecoder(
    private val samplesPerBit: Int,
    private val dataBits: Int = 5,
    private val stopBits: Double = 1.5
) {
    /** Carattere ricevuto: [code] con il primo bit nel bit 0; [framingError] se manca lo stop. */
    data class Frame(val code: Int, val framingError: Boolean)

    private var state = State.IDLE
    private var counter = 0.0
    private var bitIndex = 0
    private var code = 0
    private var previous = 1.0
    private var markRun = 0

    private enum class State { IDLE, RECEIVING, STOP_WAIT }

    /** Elabora un valore morbido; restituisce un carattere quando è completo. */
    fun process(soft: Double): Frame? {
        var result: Frame? = null
        when (state) {
            State.IDLE -> {
                if (soft > 0) markRun++ else if (previous > 0 && markRun >= samplesPerBit / 2) {
                    // Passaggio mark→space: l'inizio dello start bit è mezzo bit prima.
                    state = State.RECEIVING
                    counter = samplesPerBit / 2.0
                    bitIndex = 0
                    code = 0
                }
                if (soft <= 0) markRun = 0
            }
            State.RECEIVING -> {
                counter += 1.0
                // Bit k (0 = start) letto alla fine della sua finestra: (k + 1) bit dopo l'inizio.
                if (counter >= samplesPerBit * (bitIndex + 1)) {
                    val bit = if (soft > 0) 1 else 0
                    when {
                        bitIndex == 0 && bit == 1 -> reset() // falso start: era un disturbo
                        bitIndex in 1..dataBits -> code = code or (bit shl (bitIndex - 1))
                        bitIndex == dataBits + 1 -> {
                            result = Frame(code, framingError = bit == 0)
                            state = State.STOP_WAIT
                            // Il prossimo start arriva dopo il resto dello stop: si torna in attesa poco prima.
                            counter = 0.0
                        }
                    }
                    if (state == State.RECEIVING) bitIndex++
                }
            }
            State.STOP_WAIT -> {
                counter += 1.0
                if (counter >= samplesPerBit * (stopBits - 1.0).coerceAtLeast(0.0)) {
                    state = State.IDLE
                    markRun = if (soft > 0) samplesPerBit else 0
                }
            }
        }
        previous = soft
        return result
    }

    fun reset() {
        state = State.IDLE
        counter = 0.0
        bitIndex = 0
        code = 0
        markRun = 0
    }
}

/**
 * Recupero del clock per trasmissioni sincrone (SITOR-B): i bit si leggono alla fine di ogni finestra
 * del filtro adattato; ogni passaggio per lo zero del valore morbido (atteso a metà tra due letture)
 * corregge la fase con un anello del primo ordine.
 */
class SyncBitClock(private val samplesPerBit: Int, private val gain: Double = 0.08) {
    private var phase = 0.0
    private var previous = 0.0

    /** Elabora un valore morbido; restituisce 1/0 quando è il momento di leggere un bit, altrimenti null. */
    fun process(soft: Double): Int? {
        phase += 1.0
        if ((soft > 0) != (previous > 0)) {
            // Lo zero dovrebbe cadere a mezzo bit dalla lettura: l'errore sposta la fase.
            var error = phase - samplesPerBit / 2.0
            if (error > samplesPerBit / 2.0) error -= samplesPerBit
            if (error < -samplesPerBit / 2.0) error += samplesPerBit
            phase -= error * gain
        }
        previous = soft
        if (phase >= samplesPerBit) {
            phase -= samplesPerBit
            return if (soft > 0) 1 else 0
        }
        return null
    }

    fun reset() {
        phase = 0.0
        previous = 0.0
    }
}
