package com.grabber.mobile

import com.grabber.app.AppLogger
import com.grabber.app.DEFAULT_SERVER_ADDRESS
import com.grabber.app.FavoriteFrequency
import com.grabber.app.SecretCipher
import com.grabber.app.WaterfallPreferences
import com.grabber.app.parseServerAddress
import com.grabber.model.ModulationMode
import com.grabber.waterfall.WaterfallPaletteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

const val DEFAULT_ANDROID_CLIENT_NAME = "KiwiGrabberAndroid"
val FREQUENCY_STEPS_KHZ = listOf(0.1, 0.5, 1.0, 5.0, 9.0, 10.0, 100.0)

/** Modello ridotto delle impostazioni Android (vedi `docs/piani/ANDROID.md` §4.6). */
@Serializable
data class MobileSettingsData(
    val serverAddress: String = DEFAULT_SERVER_ADDRESS,
    val serverPassword: String = "",
    val callsign: String = DEFAULT_ANDROID_CLIENT_NAME,
    val lastFrequencyKhz: Double = 27205.0,
    val lastModeCode: String = "am",
    val frequencyStepKhz: Double = 5.0,
    val volume: Float = 0.8f,
    val squelchEnabled: Boolean = false,
    val squelchThresholdDbm: Double = -103.0,
    val personalFavorites: List<FavoriteFrequency> = emptyList(),
    val licenseKey: String = "",
    val waterfallPaletteName: String = WaterfallPaletteType.KIWI_CLASSIC.name,
    val waterfallMinDb: Int = -110,
    val waterfallMaxDb: Int = -10,
    val waterfallSpeed: Int = 1,
    val waterfallZoom: Int = 3,
    val waterfallAutoFollow: Boolean = true,
    /** Registrazione MediaStore in corso: se resta valorizzata all'avvio, il file va riparato. */
    val pendingRecordingUri: String? = null
) {
    val lastMode: ModulationMode get() = ModulationMode.entries.find { it.code == lastModeCode } ?: ModulationMode.AM
    val serverHost: String get() = parseServerAddress(serverAddress).first
    val serverPort: Int get() = parseServerAddress(serverAddress).second
    val waterfallPalette: WaterfallPaletteType
        get() = WaterfallPaletteType.entries.find { it.name == waterfallPaletteName } ?: WaterfallPaletteType.KIWI_CLASSIC
}

/**
 * Impostazioni persistite in `filesDir/settings.json`, scritte in modo atomico come su desktop.
 * La password è cifrata con [SecretCipher] (Android Keystore) e mai scritta in chiaro.
 */
class MobileSettings(
    private val settingsDir: File,
    private val secretCipher: SecretCipher
) : WaterfallPreferences {
    private val settingsFile = File(settingsDir, FILE_NAME)
    private val saveLock = Any()
    private val savePending = AtomicBoolean(false)
    private val saveExecutor = Executors.newSingleThreadExecutor { Thread(it, "KiwiSettingsWriter").apply { isDaemon = true } }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(MobileSettingsData())
    val settings: StateFlow<MobileSettingsData> = _settings.asStateFlow()
    val value: MobileSettingsData get() = _settings.value

    init {
        settingsDir.mkdirs()
        load()
    }

    private fun load() {
        if (!settingsFile.exists()) return
        try {
            val stored = json.decodeFromString<MobileSettingsData>(settingsFile.readText(Charsets.UTF_8))
            _settings.value = stored.copy(serverPassword = decryptOrEmpty(stored.serverPassword))
            if (secretCipher.isUnprotected(stored.serverPassword)) save()
        } catch (e: Exception) {
            AppLogger.error(TAG, "Impossibile caricare le impostazioni", e)
        }
    }

    /** Un valore non decifrabile (es. backup ripristinato su un altro dispositivo) va reinserito. */
    private fun decryptOrEmpty(stored: String): String =
        try {
            secretCipher.decrypt(stored)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Impossibile decifrare la password del ricevitore", e)
            ""
        }

    private fun encryptOrEmpty(plain: String): String =
        try {
            secretCipher.encrypt(plain)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Impossibile cifrare la password del ricevitore: non verrà salvata", e)
            ""
        }

    fun update(transform: (MobileSettingsData) -> MobileSettingsData) {
        var changed = false
        _settings.update { current -> transform(current).also { changed = it != current } }
        if (changed) save()
    }

    /**
     * Salvataggio asincrono e coalescente: slider e sintonia possono generare molte modifiche
     * al secondo, ma su disco finisce solo l'ultimo stato e mai dal thread della UI.
     */
    private fun save() {
        if (savePending.compareAndSet(false, true)) {
            saveExecutor.execute {
                savePending.set(false)
                writeToDisk()
            }
        }
    }

    private fun writeToDisk() = synchronized(saveLock) {
        try {
            val current = _settings.value
            val content = json.encodeToString(MobileSettingsData.serializer(), current.copy(serverPassword = encryptOrEmpty(current.serverPassword)))
            val tempFile = File(settingsDir, "$FILE_NAME.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            try {
                Files.move(tempFile.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Impossibile salvare le impostazioni", e)
        }
    }

    // ── WaterfallPreferences ─────────────────────────────────────
    override val palette: WaterfallPaletteType get() = value.waterfallPalette
    override val autoFollow: Boolean get() = value.waterfallAutoFollow
    override fun savePalette(palette: WaterfallPaletteType) = update { it.copy(waterfallPaletteName = palette.name) }
    override fun saveDbRange(minDb: Int, maxDb: Int) = update { it.copy(waterfallMinDb = minDb, waterfallMaxDb = maxDb) }
    override fun saveSpeed(speed: Int) = update { it.copy(waterfallSpeed = speed.coerceIn(1, 4)) }
    override fun saveZoom(zoom: Int) = update { it.copy(waterfallZoom = zoom.coerceIn(0, 14)) }
    override fun saveAutoFollow(autoFollow: Boolean) = update { it.copy(waterfallAutoFollow = autoFollow) }

    private companion object {
        const val TAG = "MobileSettings"
        const val FILE_NAME = "settings.json"
    }
}
