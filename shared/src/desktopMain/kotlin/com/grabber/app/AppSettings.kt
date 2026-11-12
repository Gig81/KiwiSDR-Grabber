package com.grabber.app

import com.grabber.kiwi.KiwiServerDefaults
import com.grabber.model.ModulationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class FavoriteFrequency(
    val name: String,
    val frequencyKhz: Double,
    val modeCode: String, // "am", "usb", "lsb", "cw", "nbfm"
    val group: String = "" // "LW", "MW", "HAM", "CB", "BC", ecc.
) {
    val mode: ModulationMode get() = ModulationMode.entries.find { it.code == modeCode } ?: ModulationMode.AM
}

const val DEFAULT_SERVER_ADDRESS = KiwiServerDefaults.ADDRESS

fun parseServerAddress(address: String): Pair<String, Int> {
    var clean = address.trim()
    if (clean.startsWith("http://", ignoreCase = true)) clean = clean.substring(7)
    if (clean.startsWith("https://", ignoreCase = true)) clean = clean.substring(8)
    if (clean.startsWith("ws://", ignoreCase = true)) clean = clean.substring(5)
    if (clean.startsWith("wss://", ignoreCase = true)) clean = clean.substring(6)
    while (clean.endsWith("/")) clean = clean.substring(0, clean.length - 1)

    val parts = clean.split(":")
    val host = parts[0].trim().ifEmpty { KiwiServerDefaults.HOST }
    val port = if (parts.size > 1) parts[1].filter { it.isDigit() }.toIntOrNull() ?: KiwiServerDefaults.PORT else KiwiServerDefaults.PORT
    return Pair(host, port)
}

@Serializable
data class SettingsData(
    val serverAddress: String = DEFAULT_SERVER_ADDRESS,
    val serverPassword: String = "",
    val callsign: String = "KiwiGrabberDesktop",
    val logLevelName: String = "WARN",
    val lastFrequencyKhz: Double = 27205.0,        // Ultima frequenza sintonizzata (default: Ch 20 CB Puglia)
    val lastModeCode: String = "am",               // Ultima modalità di ricezione
    val favorites: List<FavoriteFrequency> = defaultFavorites(),
    val favoritesCustomized: Boolean = false,
    val squelchThresholdDbm: Double = -103.0,     // Default S4 (-103 dBm)
    val squelchListenEnabled: Boolean = false,    // Mute automatico altoparlante sotto soglia
    val squelchRecordEnabled: Boolean = false,    // Registrazione solo in presenza di segnale
    val softMuteMs: Int = 10,                     // Durata rampa fade-in/fade-out anti-pop (ms)
    val preBufferMs: Int = 400,                   // Durata ring buffer circolare anti-troncamento (ms)
    val hangTimeSeconds: Double = 3.5,            // Cooldown FSM per unire passaggi QSO consecutivi (secondi)
    val vadListenEnabled: Boolean = false,        // Voice-Gate su ascolto live
    val vadRecordEnabled: Boolean = false,        // Voice-Gate su registrazione intelligente
    val vadSensitivity: Float = 0.45f,            // Soglia sensibilità VAD (0.1 - 0.9)
    val vadHangoverMs: Int = 250,                 // Hangover VAD anti-troncamento sillabe (ms)
    // Configurazione Bot Telegram Allerte Personale
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val telegramAlertsEnabled: Boolean = false,
    val telegramNotifyAnyQso: Boolean = false,    // Avviso per qualsiasi QSO rilevato (anche senza termini sensibili)
    val telegramNotifyQsoStart: Boolean = false,
    val telegramNotifyQsoEnd: Boolean = false,
    val telegramSensitiveWords: String = "emergenza, mayday, soccorso, pan pan, alluvione, frana",
    val telegramMessageIntervalMinutes: Int = 5,  // Cooldown / frequenza minima tra messaggi
    val telegramSendAudioAttachment: Boolean = true,
    val telegramSendTranscription: Boolean = true,
    val sttModelPath: String = "",
    val setupCompleted: Boolean = false,
    // Persistenza dello storico degli utenti connessi
    val recordConnectedUsers: Boolean = false,
    // Licenza di attivazione firmata e vincolata al Machine ID
    val licenseKey: String = "",
    // Parametri Cascata Spettrale (Waterfall)
    val waterfallPaletteName: String = "KIWI_CLASSIC",
    val waterfallMinDb: Int = -110,
    val waterfallMaxDb: Int = -10,
    val waterfallSpeed: Int = 2,
    val waterfallAutoFollowVfo: Boolean = true,
    val waterfallZoom: Int = 0
) {
    val logLevel: LogLevel get() = LogLevel.entries.find { it.name == logLevelName } ?: LogLevel.WARN
    val lastMode: ModulationMode get() = ModulationMode.entries.find { it.code == lastModeCode } ?: ModulationMode.AM
    val serverHost: String get() = parseServerAddress(serverAddress).first
    val serverPort: Int get() = parseServerAddress(serverAddress).second
    val waterfallPalette: com.grabber.waterfall.WaterfallPaletteType
        get() = try {
            com.grabber.waterfall.WaterfallPaletteType.valueOf(waterfallPaletteName)
        } catch (_: Exception) {
            com.grabber.waterfall.WaterfallPaletteType.KIWI_CLASSIC
        }
}

fun defaultFavorites(): List<FavoriteFrequency> = listOf(
    // -------------------------------------------------------------
    // Banda Cittadina (CB 27 MHz) - Tutti i 40 Canali Completi
    // -------------------------------------------------------------
    FavoriteFrequency("CB Ch 1", 26965.0, "am", "CB"),
    FavoriteFrequency("CB Ch 2", 26975.0, "am", "CB"),
    FavoriteFrequency("CB Ch 3", 26985.0, "am", "CB"),
    FavoriteFrequency("CB Ch 4", 27005.0, "am", "CB"),
    FavoriteFrequency("CB Ch 5", 27015.0, "am", "CB"),
    FavoriteFrequency("CB Ch 6", 27025.0, "am", "CB"),
    FavoriteFrequency("CB Ch 7", 27035.0, "am", "CB"),
    FavoriteFrequency("CB Ch 8", 27055.0, "am", "CB"),
    FavoriteFrequency("CB Ch 9 SOS", 27065.0, "am", "CB"),
    FavoriteFrequency("CB Ch 10", 27075.0, "am", "CB"),
    FavoriteFrequency("CB Ch 11 Chiamata", 27085.0, "am", "CB"),
    FavoriteFrequency("CB Ch 12", 27105.0, "am", "CB"),
    FavoriteFrequency("CB Ch 13", 27115.0, "am", "CB"),
    FavoriteFrequency("CB Ch 14", 27125.0, "am", "CB"),
    FavoriteFrequency("CB Ch 15", 27135.0, "am", "CB"),
    FavoriteFrequency("CB Ch 16", 27155.0, "am", "CB"),
    FavoriteFrequency("CB Ch 17", 27165.0, "am", "CB"),
    FavoriteFrequency("CB Ch 18", 27175.0, "am", "CB"),
    FavoriteFrequency("CB Ch 19 Camionisti", 27185.0, "am", "CB"),
    FavoriteFrequency("CB Ch 20 Puglia", 27205.0, "am", "CB"),
    FavoriteFrequency("CB Ch 21", 27215.0, "am", "CB"),
    FavoriteFrequency("CB Ch 22", 27225.0, "am", "CB"),
    FavoriteFrequency("CB Ch 23", 27255.0, "am", "CB"),
    FavoriteFrequency("CB Ch 24", 27235.0, "am", "CB"),
    FavoriteFrequency("CB Ch 25", 27245.0, "am", "CB"),
    FavoriteFrequency("CB Ch 26", 27265.0, "am", "CB"),
    FavoriteFrequency("CB Ch 27", 27275.0, "am", "CB"),
    FavoriteFrequency("CB Ch 28", 27285.0, "am", "CB"),
    FavoriteFrequency("CB Ch 29", 27295.0, "am", "CB"),
    FavoriteFrequency("CB Ch 30", 27305.0, "am", "CB"),
    FavoriteFrequency("CB Ch 31", 27315.0, "am", "CB"),
    FavoriteFrequency("CB Ch 32", 27325.0, "am", "CB"),
    FavoriteFrequency("CB Ch 33", 27335.0, "am", "CB"),
    FavoriteFrequency("CB Ch 34", 27345.0, "am", "CB"),
    FavoriteFrequency("CB Ch 35", 27355.0, "am", "CB"),
    FavoriteFrequency("CB Ch 36", 27365.0, "am", "CB"),
    FavoriteFrequency("CB Ch 37", 27375.0, "am", "CB"),
    FavoriteFrequency("CB Ch 38 DX LSB", 27385.0, "lsb", "CB"),
    FavoriteFrequency("CB Ch 39", 27395.0, "am", "CB"),
    FavoriteFrequency("CB Ch 40", 27405.0, "am", "CB"),

    // -------------------------------------------------------------
    // Bande Radioamatoriali HF Principali (HAM)
    // -------------------------------------------------------------
    FavoriteFrequency("160m LSB Calling", 1840.0, "lsb", "HAM"),
    FavoriteFrequency("80m LSB Calling", 3650.0, "lsb", "HAM"),
    FavoriteFrequency("80m FT8", 3573.0, "usb", "HAM"),
    FavoriteFrequency("60m USB Calling", 5357.0, "usb", "HAM"),
    FavoriteFrequency("40m LSB Calling", 7100.0, "lsb", "HAM"),
    FavoriteFrequency("40m FT8", 7074.0, "usb", "HAM"),
    FavoriteFrequency("40m CW Calling", 7030.0, "cw", "HAM"),
    FavoriteFrequency("30m CW Calling", 10116.0, "cw", "HAM"),
    FavoriteFrequency("30m FT8", 10136.0, "usb", "HAM"),
    FavoriteFrequency("20m USB Calling", 14200.0, "usb", "HAM"),
    FavoriteFrequency("20m FT8", 14074.0, "usb", "HAM"),
    FavoriteFrequency("20m CW Calling", 14060.0, "cw", "HAM"),
    FavoriteFrequency("17m USB Calling", 18130.0, "usb", "HAM"),
    FavoriteFrequency("17m FT8", 18100.0, "usb", "HAM"),
    FavoriteFrequency("15m USB Calling", 21200.0, "usb", "HAM"),
    FavoriteFrequency("15m FT8", 21074.0, "usb", "HAM"),
    FavoriteFrequency("12m USB Calling", 24950.0, "usb", "HAM"),
    FavoriteFrequency("12m FT8", 24915.0, "usb", "HAM"),
    FavoriteFrequency("10m USB Calling", 28400.0, "usb", "HAM"),
    FavoriteFrequency("10m FM Calling", 29600.0, "nbfm", "HAM"),
    FavoriteFrequency("10m FT8", 28074.0, "usb", "HAM"),

    // -------------------------------------------------------------
    // Principali Radio Internazionali (Broadcast, Onde Lunghe, Medie & Corte)
    // -------------------------------------------------------------
    FavoriteFrequency("BBC Radio 4 (Droitwich)", 198.0, "am", "LW"),
    FavoriteFrequency("Magyar Rádió Kossuth (Solt)", 540.0, "am", "MW"),
    FavoriteFrequency("Radio Caroline (Orford Ness)", 648.0, "am", "MW"),
    FavoriteFrequency("BBC Radio 5 Live (Moorside Edge)", 909.0, "am", "MW"),
    FavoriteFrequency("Talksport (Droitwich)", 1053.0, "am", "MW"),
    FavoriteFrequency("Talksport (Moorside Edge)", 1089.0, "am", "MW"),
    FavoriteFrequency("Absolute Radio (Moorside Edge)", 1215.0, "am", "MW"),
    FavoriteFrequency("BBC Radio 4 (Tatsfield)", 1413.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (531)", 531.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (657)", 657.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (900)", 900.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (999)", 999.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (1062)", 1062.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (1107)", 1107.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (1332)", 1332.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (1449)", 1449.0, "am", "MW"),
    FavoriteFrequency("Radio Rai 1 (1575)", 1575.0, "am", "MW"),
    FavoriteFrequency("Radio Romania Int (9510)", 9510.0, "am", "INT"),
    FavoriteFrequency("Radio Romania Int (11800)", 11800.0, "am", "INT"),
    FavoriteFrequency("Radio Vaticana (7305)", 7305.0, "am", "INT"),
    FavoriteFrequency("Radio Vaticana (15565)", 15565.0, "am", "INT"),
    FavoriteFrequency("Voice of America (6080)", 6080.0, "am", "INT"),
    FavoriteFrequency("Voice of America (9760)", 9760.0, "am", "INT"),
    FavoriteFrequency("China Radio Int (7245)", 7245.0, "am", "INT"),
    FavoriteFrequency("China Radio Int (9880)", 9880.0, "am", "INT"),
    FavoriteFrequency("Segnale Orario Time 5 MHz", 5000.0, "am", "INT"),
    FavoriteFrequency("Segnale Orario Time 10 MHz", 10000.0, "am", "INT"),
    FavoriteFrequency("Segnale Orario Time 15 MHz", 15000.0, "am", "INT")
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

private const val TAG = "AppSettings"
private const val SETTINGS_FILE_NAME = "settings.json"
private const val LEGACY_FAVORITES_MIN_SIZE = 40
private const val LEGACY_CB_NAME_PREFIX = "CB Ch1 - 26965"

class AppSettings(
    private val settingsDir: File,
    private val secretCipher: SecretCipher = SecretCipher.forCurrentPlatform()
) {
    private val settingsFile = File(settingsDir, SETTINGS_FILE_NAME)
    private val saveLock = Any()

    private val _settings = MutableStateFlow(SettingsData())
    val settings: StateFlow<SettingsData> = _settings.asStateFlow()

    init {
        settingsDir.mkdirs()
        load()
    }

    private fun load() {
        try {
            if (!settingsFile.exists()) return
            val settingsJson = json.parseToJsonElement(settingsFile.readText(Charsets.UTF_8))
            val stored = json.decodeFromJsonElement<SettingsData>(settingsJson)
                .let { if ("setupCompleted" in settingsJson.jsonObject) it else it.copy(setupCompleted = true) }
            val upgradeFavorites = isLegacyDefaultFavoriteList(stored)
            val loaded = if (upgradeFavorites) stored.copy(favorites = defaultFavorites()) else stored
            _settings.value = revealSecrets(loaded)
            if (upgradeFavorites || hasUnprotectedSecrets(stored)) save()
        } catch (e: Exception) {
            AppLogger.error(TAG, "Impossibile caricare impostazioni", e)
        }
    }

    private fun hasUnprotectedSecrets(stored: SettingsData): Boolean =
        secretCipher.isUnprotected(stored.serverPassword) || secretCipher.isUnprotected(stored.telegramBotToken)

    private fun revealSecrets(stored: SettingsData): SettingsData = stored.copy(
        serverPassword = decryptOrEmpty(stored.serverPassword, "password del ricevitore"),
        telegramBotToken = decryptOrEmpty(stored.telegramBotToken, "token Telegram")
    )

    private fun protectSecrets(settings: SettingsData): SettingsData = settings.copy(
        serverPassword = encryptOrEmpty(settings.serverPassword, "password del ricevitore"),
        telegramBotToken = encryptOrEmpty(settings.telegramBotToken, "token Telegram")
    )

    /** Un valore non decifrabile (es. file copiato da un altro account Windows) va reinserito dall'utente. */
    private fun decryptOrEmpty(storedValue: String, label: String): String =
        runSecretOperation(label, "decifrare") { secretCipher.decrypt(storedValue) }

    /** Se la cifratura non è disponibile il valore non viene scritto su disco, mai in chiaro. */
    private fun encryptOrEmpty(plainText: String, label: String): String =
        runSecretOperation(label, "cifrare") { secretCipher.encrypt(plainText) }

    private fun runSecretOperation(label: String, action: String, operation: () -> String): String =
        try {
            operation()
        } catch (e: Exception) {
            AppLogger.error(TAG, "Impossibile $action $label", e)
            ""
        } catch (e: LinkageError) {
            AppLogger.error(TAG, "DPAPI non disponibile per $label", e)
            ""
        }

    /**
     * Riconosce le liste predefinite delle versioni precedenti (vuote, ridotte o con la vecchia
     * nomenclatura CB) da aggiornare. Una lista modificata dall'utente non viene mai sostituita.
     */
    private fun isLegacyDefaultFavoriteList(data: SettingsData): Boolean {
        if (data.favoritesCustomized) return false
        val favorites = data.favorites
        return favorites.size < LEGACY_FAVORITES_MIN_SIZE ||
            favorites.any { it.name.startsWith(LEGACY_CB_NAME_PREFIX) }
    }

    /** Aggiornamento atomico dello stato (sicuro tra thread diversi) seguito dal salvataggio. */
    private fun updateAndSave(transform: (SettingsData) -> SettingsData) {
        _settings.update(transform)
        save()
    }

    /**
     * Scrive l'ultimo stato su un file temporaneo e lo sostituisce in un'unica operazione:
     * un'interruzione durante la scrittura non lascia mai un `settings.json` troncato.
     */
    private fun save() = synchronized(saveLock) {
        try {
            val content = json.encodeToString(protectSecrets(_settings.value))
            val tempFile = File(settingsDir, "$SETTINGS_FILE_NAME.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            replaceFile(tempFile, settingsFile)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Impossibile salvare impostazioni", e)
        }
    }

    private fun replaceFile(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun setCallsign(callsign: String) {
        updateAndSave { it.copy(callsign = callsign) }
    }

    fun setSttModelPath(path: String) {
        updateAndSave { it.copy(sttModelPath = path.trim()) }
    }

    fun setSetupCompleted(completed: Boolean) {
        updateAndSave { it.copy(setupCompleted = completed) }
    }

    fun setLastTuned(freqKhz: Double, mode: ModulationMode) {
        val current = _settings.value
        if (current.lastFrequencyKhz == freqKhz && current.lastModeCode == mode.code) return
        updateAndSave { it.copy(lastFrequencyKhz = freqKhz, lastModeCode = mode.code) }
    }

    fun setLogLevel(level: LogLevel) {
        AppLogger.setLevel(level)
        updateAndSave { it.copy(logLevelName = level.name) }
    }

    fun addFavorite(fav: FavoriteFrequency) = updateCustomFavorites { it + fav }

    fun removeFavorite(fav: FavoriteFrequency) = updateCustomFavorites { favorites ->
        favorites.takeIf { fav in it }?.minus(fav)
    }

    fun editFavorite(oldFav: FavoriteFrequency, newFav: FavoriteFrequency) = updateCustomFavorites { favorites ->
        val index = favorites.indexOf(oldFav)
        if (index == -1) null else favorites.toMutableList().also { it[index] = newFav }
    }

    fun resetToDefaultFavorites() = updateAndSave {
        it.copy(favorites = defaultFavorites(), favoritesCustomized = false)
    }

    fun updateFavorites(favorites: List<FavoriteFrequency>) = updateCustomFavorites { favorites }

    /** `transform` restituisce null quando la lista non cambia: in quel caso non si salva nulla. */
    private fun updateCustomFavorites(transform: (List<FavoriteFrequency>) -> List<FavoriteFrequency>?) {
        var changed = false
        _settings.update { current ->
            val updated = transform(current.favorites)
            changed = updated != null
            if (updated == null) current else current.copy(favorites = updated, favoritesCustomized = true)
        }
        if (changed) save()
    }

    fun setSquelchThreshold(dbm: Double) {
        updateAndSave { it.copy(squelchThresholdDbm = dbm) }
    }

    fun setSquelchListenEnabled(enabled: Boolean) {
        updateAndSave { it.copy(squelchListenEnabled = enabled) }
    }

    fun setSquelchRecordEnabled(enabled: Boolean) {
        updateAndSave { it.copy(squelchRecordEnabled = enabled) }
    }

    fun setRecordConnectedUsers(enabled: Boolean) {
        updateAndSave { it.copy(recordConnectedUsers = enabled) }
    }

    fun setSoftMuteMs(ms: Int) {
        updateAndSave { it.copy(softMuteMs = ms.coerceIn(2, 50)) }
    }

    fun setPreBufferMs(ms: Int) {
        updateAndSave { it.copy(preBufferMs = ms.coerceIn(50, 1000)) }
    }

    fun setHangTimeSeconds(seconds: Double) {
        updateAndSave { it.copy(hangTimeSeconds = seconds.coerceIn(0.5, 10.0)) }
    }

    fun setVadListenEnabled(enabled: Boolean) {
        updateAndSave { it.copy(vadListenEnabled = enabled) }
    }

    fun setVadRecordEnabled(enabled: Boolean) {
        updateAndSave { it.copy(vadRecordEnabled = enabled) }
    }

    fun setVadSensitivity(sensitivity: Float) {
        updateAndSave { it.copy(vadSensitivity = sensitivity.coerceIn(0.1f, 0.9f)) }
    }

    fun setVadHangoverMs(ms: Int) {
        updateAndSave { it.copy(vadHangoverMs = ms.coerceIn(50, 1000)) }
    }

    // Configurazione Telegram Alerting
    fun updateTelegramSettings(
        botToken: String,
        chatId: String,
        enabled: Boolean,
        notifyAnyQso: Boolean,
        sensitiveWords: String,
        intervalMinutes: Int,
        sendAudio: Boolean,
        sendTranscription: Boolean,
        notifyQsoStart: Boolean = false,
        notifyQsoEnd: Boolean = false
    ) {
        updateAndSave {
            it.copy(
                telegramBotToken = botToken.trim(),
                telegramChatId = chatId.trim(),
                telegramAlertsEnabled = enabled,
                telegramNotifyAnyQso = notifyAnyQso,
                telegramNotifyQsoStart = notifyQsoStart,
                telegramNotifyQsoEnd = notifyQsoEnd,
                telegramSensitiveWords = sensitiveWords.trim(),
                telegramMessageIntervalMinutes = intervalMinutes.coerceIn(0, 1440),
                telegramSendAudioAttachment = sendAudio,
                telegramSendTranscription = sendTranscription
            )
        }
    }

    // Configurazione Server Grabber KiwiSDR
    fun setServerAddress(address: String) {
        val clean = address.trim().ifEmpty { DEFAULT_SERVER_ADDRESS }
        updateAndSave { it.copy(serverAddress = clean) }
    }

    fun setServerPassword(password: String) {
        updateAndSave { it.copy(serverPassword = password) }
    }

    fun getServerHostAndPort(): Pair<String, Int> {
        return parseServerAddress(_settings.value.serverAddress)
    }

    // Configurazione Licenza di Utilizzo
    fun setLicense(key: String) {
        updateAndSave {
            it.copy(
                licenseKey = key.trim()
            )
        }
    }

    // Configurazione Waterfall / Spettrogramma
    fun setWaterfallPalette(palette: com.grabber.waterfall.WaterfallPaletteType) {
        updateAndSave { it.copy(waterfallPaletteName = palette.name) }
    }

    fun setWaterfallDbRange(minDb: Int, maxDb: Int) {
        updateAndSave {
            it.copy(
                waterfallMinDb = minDb,
                waterfallMaxDb = maxDb
            )
        }
    }

    fun setWaterfallSpeed(speed: Int) {
        updateAndSave { it.copy(waterfallSpeed = speed.coerceIn(1, 4)) }
    }

    fun setWaterfallZoom(zoom: Int) {
        updateAndSave { it.copy(waterfallZoom = zoom.coerceIn(0, 14)) }
    }

    fun setWaterfallAutoFollow(autoFollow: Boolean) {
        updateAndSave { it.copy(waterfallAutoFollowVfo = autoFollow) }
    }
}
