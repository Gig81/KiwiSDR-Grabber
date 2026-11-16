package com.grabber.app

import com.grabber.waterfall.WaterfallPaletteType

/**
 * Impostazioni persistite del waterfall usate da [WaterfallCoordinator]; ogni piattaforma
 * le collega al proprio archivio (`AppSettings` su desktop, impostazioni mobili su Android).
 */
interface WaterfallPreferences {
    val palette: WaterfallPaletteType
    val autoFollow: Boolean
    fun savePalette(palette: WaterfallPaletteType)
    fun saveDbRange(minDb: Int, maxDb: Int)
    fun saveSpeed(speed: Int)
    fun saveZoom(zoom: Int)
    fun saveAutoFollow(autoFollow: Boolean)
}
