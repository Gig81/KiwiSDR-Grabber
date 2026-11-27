package com.grabber.audio

import com.grabber.app.AppLogger
import com.grabber.model.AudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Copia dei frame audio originali (prima di DSP e squelch) per analisi e decoder locali.
 *
 * [submit] è chiamato dalla pipeline audio: se nessun consumatore è attivo non fa nulla, altrimenti
 * copia il frame in una coda limitata consumata da un proprio dispatcher seriale, così la
 * riproduzione non rallenta. Se la coda è piena si scarta il frame più vecchio: i consumatori
 * vedono il buco dal numero di sequenza e risincronizzano.
 */
class AudioAnalysisTap(
    scope: CoroutineScope,
    capacity: Int = DEFAULT_CAPACITY
) {
    companion object {
        const val DEFAULT_CAPACITY = 64
        private const val TAG = "AudioAnalysisTap"
        private const val WARN_INTERVAL_MS = 30_000L
    }

    /** Consumatore: [isActive] decide se riceve frame, [consume] è chiamato in ordine da un solo flusso. */
    class Consumer(val name: String, val isActive: () -> Boolean, val consume: (AudioFrame) -> Unit)

    private val consumers = CopyOnWriteArrayList<Consumer>()
    private val dropped = AtomicLong()
    private val lastWarn = AtomicLong()
    private val channel = Channel<AudioFrame>(capacity, BufferOverflow.DROP_OLDEST) { dropped.incrementAndGet() }

    init {
        scope.launch(Dispatchers.Default.limitedParallelism(1)) {
            for (frame in channel) {
                for (consumer in consumers) {
                    if (!consumer.isActive()) continue
                    try {
                        consumer.consume(frame)
                    } catch (e: Exception) {
                        AppLogger.error(TAG, "Errore nel consumatore audio ${consumer.name}", e)
                    }
                }
                reportDrops()
            }
        }
    }

    fun addConsumer(consumer: Consumer) {
        consumers += consumer
    }

    /** Vero se almeno un consumatore vuole l'audio. */
    val isActive: Boolean get() = consumers.any { it.isActive() }

    fun submit(frame: AudioFrame) {
        if (!isActive) return
        channel.trySend(frame.copy(pcmBytes = frame.pcmBytes.copyOf()))
    }

    private fun reportDrops() {
        val count = dropped.get()
        if (count == 0L) return
        val now = System.currentTimeMillis()
        val last = lastWarn.get()
        if (now - last >= WARN_INTERVAL_MS && lastWarn.compareAndSet(last, now)) {
            AppLogger.warn(TAG, "Analisi audio in ritardo: scartati ${dropped.getAndSet(0)} frame")
        }
    }
}
