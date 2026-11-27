package com.grabber.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.grabber.app.AppLogger
import com.grabber.app.KiwiAppController
import com.grabber.model.UiThemeMode
import com.grabber.ui.designsystem.BrandColors
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberTheme
import com.grabber.ui.designsystem.PrimaryButton
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.feature.settings.openInBrowser
import com.grabber.update.UpdateCheckState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Image as SkiaImage

/** Durata minima dello splash, perché logo e versione siano leggibili. */
private const val SPLASH_MIN_MS = 1_500L

/** Attesa massima dell'esito del controllo aggiornamenti prima di aprire comunque l'app. */
private const val SPLASH_MAX_CHECK_MS = 4_000L

/** Immagini del marchio generate dal logo ufficiale (`scripts/branding/Generate-Icons.ps1`). */
internal object BrandImages {
    /** Logo completo (1408 × 768) dello splash screen. */
    val logo: ImageBitmap? by lazy { load("brand/logo.jpg") }

    /** Icona della finestra e della barra delle applicazioni. */
    val appIcon: Painter? by lazy { load("brand/app_icon.png")?.let(::BitmapPainter) }

    private fun load(resource: String): ImageBitmap? = try {
        val bytes = BrandImages::class.java.classLoader.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: error("risorsa $resource non trovata")
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        AppLogger.error("BrandImages", "Impossibile caricare l'immagine $resource", e)
        null
    }
}

/**
 * Finestra di avvio senza bordi: logo ufficiale, versione ed esito del controllo aggiornamenti su
 * GitHub. Si chiude da sola; se è disponibile una nuova versione resta aperta con il pulsante per la
 * pagina di download finché l'utente non sceglie "Continua". Un clic o Esc la chiudono subito.
 */
@Composable
fun SplashWindow(controller: KiwiAppController, onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    val update by controller.updateCheck.collectAsState()

    LaunchedEffect(controller) {
        delay(SPLASH_MIN_MS)
        val result = withTimeoutOrNull(SPLASH_MAX_CHECK_MS - SPLASH_MIN_MS) {
            controller.updateCheck.first { it !is UpdateCheckState.Checking }
        }
        if (result !is UpdateCheckState.Available) finish()
    }

    Window(
        onCloseRequest = { finish() },
        title = "KiwiSDR Grabber",
        icon = BrandImages.appIcon,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(560.dp, 420.dp)),
        onKeyEvent = { event ->
            if (event.key == Key.Escape) finish()
            false
        }
    ) {
        // Lo splash ha i colori del logo in entrambi i temi; il tema scuro serve solo ai componenti Material.
        GrabberTheme(mode = UiThemeMode.DARK) {
            SplashContent(
                version = controller.appVersion,
                update = update,
                onDownload = { url -> openInBrowser(url, "la pagina della nuova versione") },
                onContinue = { finish() }
            )
        }
    }
}

@Composable
private fun SplashContent(
    version: String,
    update: UpdateCheckState,
    onDownload: (String) -> Unit,
    onContinue: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BrandColors.LogoBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onContinue)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandImages.logo?.let { logo ->
                Image(
                    bitmap = logo,
                    contentDescription = "KiwiSDR Grabber",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 480.dp, height = 262.dp).fadedEdges()
                )
            }
            Text(
                "Station Recorder · versione $version",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.OnLogoBackground
            )
            Spacer(Modifier.height(14.dp))
            UpdateStatus(update, onDownload, onContinue)
        }
    }
}

@Composable
private fun UpdateStatus(state: UpdateCheckState, onDownload: (String) -> Unit, onContinue: () -> Unit) {
    when (state) {
        UpdateCheckState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), color = BrandColors.LogoGreen, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            MutedText("Controllo aggiornamenti…")
        }
        UpdateCheckState.UpToDate -> MutedText("Stai usando la versione più recente.")
        is UpdateCheckState.Unavailable -> MutedText(state.reason)
        is UpdateCheckState.Available -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "È disponibile la versione ${state.latestVersion}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BrandColors.LogoGreen
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Scarica l'aggiornamento",
                    onClick = { onDownload(state.releaseUrl) },
                    icon = Icons.Default.Download,
                    size = ControlSize.Compact,
                    containerColor = BrandColors.LogoGreen,
                    contentColor = BrandColors.LogoBackground
                )
                SecondaryButton(
                    text = "Continua",
                    onClick = onContinue,
                    size = ControlSize.Compact,
                    contentColor = BrandColors.OnLogoBackground
                )
            }
        }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(text, fontSize = 12.sp, color = BrandColors.OnLogoBackgroundMuted, textAlign = TextAlign.Center)
}

/**
 * Sfuma i bordi del logo verso la trasparenza: l'immagine ha una leggera vignettatura che altrimenti
 * resterebbe visibile come un riquadro sullo sfondo dello splash.
 */
private fun Modifier.fadedEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 0.12f
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, fade to Color.Black, 1f - fade to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black, 1f - fade to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
    }
