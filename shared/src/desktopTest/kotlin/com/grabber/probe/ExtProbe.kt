package com.grabber.probe

import com.grabber.kiwi.JdkWebSocketConnector
import com.grabber.kiwi.KiwiProtocol
import com.grabber.kiwi.KiwiServerDefaults
import com.grabber.kiwi.KiwiTokenGenerator
import com.grabber.kiwi.KiwiWebSocket
import com.grabber.kiwi.KiwiWebSocketListener
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Programma di prova della fase 0 del piano dei modi digitali (non è un test automatico e non viene
 * distribuito): apre SND, facoltativamente W/F e `/EXT` verso un KiwiSDR, invia i comandi indicati e
 * salva in `shared/build/probe/<data>/` le tracce di protocollo (testo, frame `DAT`, audio WAV).
 *
 * Avvio: `.\gradlew.bat :shared:extProbe -PprobeArgs="freq=14074 mode=usb lc=100 hc=3100 ext=FT8 cmds=SET~ft8_start=0~debug=0 secs=60"`
 * (nei valori `~` sostituisce lo spazio e `;` separa più comandi).
 */
fun main(args: Array<String>) {
    val options = args.associate { arg -> arg.substringBefore('=') to arg.substringAfter('=', "") }
    ExtProbe(options).run()
}

private class ExtProbe(private val options: Map<String, String>) {
    private val host = options["host"] ?: KiwiServerDefaults.HOST
    private val port = options["port"]?.toInt() ?: KiwiServerDefaults.PORT
    private val password = options["password"] ?: ""
    private val freqKhz = options["freq"]?.toDouble() ?: 10000.0
    private val mode = options["mode"] ?: "am"
    private val lowCut = options["lc"]?.toInt()
    private val highCut = options["hc"]?.toInt()
    private val extName = options["ext"]
    private val extCommands = commandList("cmds")
    private val stopCommands = commandList("stop")
    private val seconds = options["secs"]?.toLong() ?: 20L
    private val recordWav = options["wav"] == "true"
    private val openWaterfall = options["wf"] == "true"
    private val latencyFreq2 = options["latency"]?.toDouble()
    private val name = options["name"] ?: "KiwiGrabberProbe"

    private val outDir = File(
        "build/probe/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
            (extName?.let { "_$it" } ?: "")
    ).also { it.mkdirs() }
    private val log = File(outDir, "trace.log").printWriter()
    private val start = System.currentTimeMillis()
    private val connector = JdkWebSocketConnector()
    private val token = KiwiTokenGenerator.generateToken()

    @Volatile private var snd: KiwiWebSocket? = null
    @Volatile private var ext: KiwiWebSocket? = null
    @Volatile private var wf: KiwiWebSocket? = null
    @Volatile private var rxChan = -1
    @Volatile private var audioRate = 0
    private val sndAuth = CountDownLatch(1)
    private val extReady = CountDownLatch(1)
    private val datOut = DataOutputStream(FileOutputStream(File(outDir, "dat.bin")))
    private val datCount = AtomicInteger()
    private val audioBytes = AtomicLong()
    private val wav: RandomAccessFile? = if (recordWav) RandomAccessFile(File(outDir, "audio.wav"), "rw").also { it.setLength(0); it.write(ByteArray(44)) } else null

    @Volatile private var lastRssi = -127.0
    @Volatile private var lastTuneAt = 0L

    private fun commandList(key: String): List<String> =
        options[key]?.split(';')?.map { it.replace('~', ' ').trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun t() = System.currentTimeMillis() - start

    @Synchronized private fun trace(line: String) {
        log.println("%7d %s".format(t(), line))
        log.flush()
        println("%7d %s".format(t(), line.take(400)))
    }

    private fun send(ws: KiwiWebSocket?, label: String, command: String) {
        if (ws == null) return
        trace("$label >> ${KiwiProtocol.redactSecrets(command)}")
        ws.sendText(command).join()
    }

    private fun tuneCommand(f: Double): String {
        val lc = lowCut ?: 300
        val hc = highCut ?: 2700
        return "SET mod=$mode low_cut=$lc high_cut=$hc freq=${"%.3f".format(java.util.Locale.US, f)}"
    }

    fun run() {
        trace("probe host=$host:$port token=$token options=$options")
        snd = connector.connect("ws://$host:$port/$token/SND", headers(), SndListener()).get(10, TimeUnit.SECONDS)
        send(snd, "SND", KiwiProtocol.auth(password))
        send(snd, "SND", KiwiProtocol.identUser(name))
        val keepalive = Thread {
            try {
                while (true) {
                    Thread.sleep(5000)
                    send(snd, "SND", "SET keepalive")
                    if (ext != null) send(ext, "EXT", "SET keepalive")
                    if (wf != null) send(wf, "W/F", "SET keepalive")
                }
            } catch (_: InterruptedException) {
                trace("keepalive fermato")
            } catch (e: Exception) {
                trace("keepalive errore: $e")
            }
        }.also { it.isDaemon = true; it.start() }

        if (!sndAuth.await(10, TimeUnit.SECONDS)) trace("!! SND badp=0 non ricevuto")
        Thread.sleep(1500)

        if (openWaterfall) openWf()
        if (extName != null) openExt()

        if (latencyFreq2 != null) measureLatency(latencyFreq2) else Thread.sleep(seconds * 1000)

        if (ext != null) {
            stopCommands.forEach { send(ext, "EXT", it) }
            send(ext, "EXT", "SET ext_blur=$rxChan")
            Thread.sleep(300)
            runCatching { ext?.close(1000, "bye")?.get(2, TimeUnit.SECONDS) }.onFailure { trace("EXT close: $it") }
        }
        runCatching { wf?.close(1000, "bye")?.get(2, TimeUnit.SECONDS) }
        runCatching { snd?.close(1000, "bye")?.get(2, TimeUnit.SECONDS) }
        keepalive.interrupt()
        finishWav()
        datOut.close()
        trace("fine: DAT=${datCount.get()} audioBytes=${audioBytes.get()} audioRate=$audioRate")
        log.close()
        println("Tracce in ${outDir.absolutePath}")
        System.exit(0)
    }

    private fun headers() = mapOf("Origin" to "http://$host:$port", "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GrabberClient/1.0")

    private fun openWf() {
        try {
            wf = connector.connect("ws://$host:$port/$token/W/F", headers(), TextLogger("W/F")).get(10, TimeUnit.SECONDS)
            send(wf, "W/F", KiwiProtocol.auth(password))
            send(wf, "W/F", KiwiProtocol.identUser(name))
            send(wf, "W/F", "SET wf_comp=0")
            send(wf, "W/F", "SET wf_speed=1")
            // Il server chiude W/F (e il canale) se dopo 5 keepalive mancano zoom, avvio, dB o velocità.
            send(wf, "W/F", "SET maxdb=-10 mindb=-110")
            send(wf, "W/F", "SET zoom=10 cf=${"%.3f".format(java.util.Locale.US, freqKhz)}")
        } catch (e: Exception) {
            trace("!! W/F rifiutato: $e")
        }
    }

    private fun openExt() {
        try {
            ext = connector.connect("ws://$host:$port/$token/EXT", headers(), ExtListener()).get(10, TimeUnit.SECONDS)
            trace("EXT aperto")
            send(ext, "EXT", KiwiProtocol.auth(password))
            send(ext, "EXT", KiwiProtocol.identUser(name))
            send(ext, "EXT", "SET ext_switch_to_client=$extName first_time=1 rx_chan=$rxChan")
            if (!extReady.await(6, TimeUnit.SECONDS)) trace("!! EXT ready non ricevuto entro 6 s")
            extCommands.forEach { send(ext, "EXT", it) }
        } catch (e: Exception) {
            trace("!! EXT rifiutato: $e")
        }
    }

    /** Alterna la sintonia tra due frequenze e misura quando l'S-meter dei frame cambia livello. */
    private fun measureLatency(freq2: Double) {
        repeat(6) { i ->
            val target = if (i % 2 == 0) freq2 else freqKhz
            val before = lastRssi
            lastTuneAt = t()
            send(snd, "SND", tuneCommand(target))
            val deadline = System.currentTimeMillis() + 4000
            var changedAt = -1L
            while (System.currentTimeMillis() < deadline) {
                if (kotlin.math.abs(lastRssi - before) > 10.0 && changedAt < 0) {
                    changedAt = t() - lastTuneAt
                }
                Thread.sleep(5)
            }
            trace("LATENZA ${"%.3f".format(java.util.Locale.US, target)} kHz: rssi ${"%.1f".format(before)} -> ${"%.1f".format(lastRssi)}, variazione dopo $changedAt ms")
        }
    }

    private fun finishWav() {
        val file = wav ?: return
        val dataLen = file.length() - 44
        val rate = if (audioRate > 0) audioRate else 12000
        file.seek(0)
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt((36 + dataLen).toInt()).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataLen.toInt())
        file.write(header.array())
        file.close()
    }

    private fun decodeParts(text: String): String =
        text.split(' ').joinToString(" ") { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (value.contains('%')) "$key=«${runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)}»" else part
        }

    private inner class SndListener : KiwiWebSocketListener {
        override fun onText(text: String) = trace("SND text << $text")
        override fun onBinary(bytes: ByteArray) {
            val tag = String(bytes, 0, 3)
            if (tag == "SND") {
                val flags = bytes[3].toInt() and 0xFF
                val smeter = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
                lastRssi = 0.1 * smeter - 127.0
                val pcm = bytes.copyOfRange(10, bytes.size)
                if (flags and 0x80 == 0) for (i in 0 until pcm.size / 2) { val b = pcm[2 * i]; pcm[2 * i] = pcm[2 * i + 1]; pcm[2 * i + 1] = b }
                audioBytes.addAndGet(pcm.size.toLong())
                wav?.write(pcm)
                return
            }
            val msg = String(bytes, 4, bytes.size - 4, Charsets.UTF_8)
            if (msg.startsWith("load_cfg=") || msg.startsWith("load_dxcfg=") || msg.startsWith("load_dxcomm_cfg=") || msg.startsWith("user_cb=") || msg.startsWith("mkr=")) {
                val key = msg.substringBefore('=')
                File(outDir, "snd_$key.txt").writeText(msg.substringAfter('='))
                trace("SND $tag << $key=(${msg.length} car., salvato)")
                return
            }
            trace("SND $tag << $msg")
            msg.split(' ').forEach { part ->
                val key = part.substringBefore('=')
                val value = part.substringAfter('=', "")
                when (key) {
                    "badp" -> if (value == "0") sndAuth.countDown()
                    "rx_chan" -> rxChan = value.toIntOrNull() ?: rxChan
                    "audio_rate" -> {
                        audioRate = value.toInt()
                        Thread {
                            send(snd, "SND", "SET AR OK in=$audioRate out=$audioRate")
                            send(snd, "SND", "SET compression=0")
                            send(snd, "SND", tuneCommand(freqKhz))
                            send(snd, "SND", KiwiProtocol.agc(true))
                        }.start()
                    }
                    "sample_rate" -> trace("SND sample_rate=$value")
                }
            }
        }
        override fun onClosed(code: Int, reason: String) = trace("SND chiuso $code $reason")
        override fun onError(error: Throwable) = trace("SND errore $error")
    }

    private inner class TextLogger(private val label: String) : KiwiWebSocketListener {
        override fun onText(text: String) = trace("$label text << $text")
        override fun onBinary(bytes: ByteArray) {
            val tag = String(bytes, 0, 3)
            if (tag == "W/F") return
            val msg = String(bytes, 4, bytes.size - 4, Charsets.UTF_8)
            if (msg.startsWith("extint_list_json=")) File(outDir, "wf_extint_list_json.txt").writeText(msg)
            trace("$label $tag << ${decodeParts(msg).take(3000)}")
        }
        override fun onClosed(code: Int, reason: String) = trace("$label chiuso $code $reason")
        override fun onError(error: Throwable) = trace("$label errore $error")
    }

    private inner class ExtListener : KiwiWebSocketListener {
        override fun onText(text: String) = trace("EXT text << $text")
        override fun onBinary(bytes: ByteArray) {
            val tag = String(bytes, 0, 3)
            if (tag == "DAT") {
                val n = datCount.incrementAndGet()
                synchronized(datOut) {
                    datOut.writeInt(bytes.size)
                    datOut.write(bytes)
                }
                if (n <= 5 || n % 50 == 0) {
                    val head = bytes.take(24).joinToString(" ") { "%02x".format(it) }
                    trace("EXT DAT #$n len=${bytes.size} cmd=${bytes.getOrNull(4)?.toInt()?.and(0xFF)} head=$head")
                }
                return
            }
            val msg = String(bytes, 4, bytes.size - 4, Charsets.UTF_8)
            trace("EXT $tag << ${decodeParts(msg).take(3000)}")
            if (tag == "EXT" && msg.contains("ready")) extReady.countDown()
            File(outDir, "ext_raw.txt").appendText("$tag $msg\n")
        }
        override fun onClosed(code: Int, reason: String) = trace("EXT chiuso $code $reason")
        override fun onError(error: Throwable) = trace("EXT errore $error")
    }
}
