package com.gig81.kiwisdrgrabber.ui.splash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.R
import com.gig81.kiwisdrgrabber.ui.theme.BrandKiwiColors
import com.grabber.app.AppLogger
import com.grabber.mobile.MobileAppController
import com.grabber.update.UpdateCheckState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Durata minima dello splash, perché logo e versione siano leggibili. */
private const val SPLASH_MIN_MS = 1_500L

/** Attesa massima dell'esito del controllo aggiornamenti prima di entrare comunque nell'app. */
private const val SPLASH_MAX_CHECK_MS = 4_000L

/**
 * Splash screen all'avvio: logo ufficiale, versione ed esito del controllo aggiornamenti su GitHub.
 * Si chiude da solo; se è disponibile una nuova versione resta aperto con il collegamento alla
 * release finché l'utente non sceglie "Continua". Un tocco o il tasto Indietro lo chiudono subito.
 */
@Composable
fun SplashScreen(controller: MobileAppController, onFinished: () -> Unit) {
    val update by controller.updateCheck.collectAsStateWithLifecycle()
    val finish by rememberUpdatedState(onFinished)
    val context = LocalContext.current

    LaunchedEffect(controller) {
        delay(SPLASH_MIN_MS)
        val result = withTimeoutOrNull(SPLASH_MAX_CHECK_MS - SPLASH_MIN_MS) {
            controller.updateCheck.first { it !is UpdateCheckState.Checking }
        }
        if (result !is UpdateCheckState.Available) finish()
    }
    BackHandler { finish() }

    Box(
        Modifier
            .fillMaxSize()
            .background(BrandKiwiColors.LogoBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo_full),
                contentDescription = "KiwiSDR Grabber",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(LOGO_ASPECT_RATIO).fadedEdges()
            )
            Text(
                "Versione ${controller.appVersion}",
                style = MaterialTheme.typography.titleMedium,
                color = BrandKiwiColors.OnLogoBackground
            )
            Spacer(Modifier.height(20.dp))
            UpdateStatus(
                state = update,
                onDownload = { url -> openInBrowser(context, url) },
                onContinue = { finish() }
            )
        }
    }
}

@Composable
private fun UpdateStatus(state: UpdateCheckState, onDownload: (String) -> Unit, onContinue: () -> Unit) {
    when (state) {
        UpdateCheckState.Checking -> {
            CircularProgressIndicator(Modifier.size(20.dp), color = BrandKiwiColors.LogoGreen, strokeWidth = 2.dp)
            Spacer(Modifier.height(8.dp))
            MutedText("Controllo aggiornamenti…")
        }
        UpdateCheckState.UpToDate -> MutedText("Stai usando la versione più recente.")
        is UpdateCheckState.Unavailable -> MutedText(state.reason)
        is UpdateCheckState.Available -> {
            Text(
                "È disponibile la versione ${state.latestVersion}",
                style = MaterialTheme.typography.titleSmall,
                color = BrandKiwiColors.LogoGreen,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onDownload(state.releaseUrl) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandKiwiColors.LogoGreen,
                    contentColor = BrandKiwiColors.LogoBackground
                )
            ) { Text("Scarica l'aggiornamento") }
            TextButton(onClick = onContinue) {
                Text("Continua", color = BrandKiwiColors.OnLogoBackground)
            }
        }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = BrandKiwiColors.OnLogoBackgroundMuted,
        textAlign = TextAlign.Center
    )
}

/** Apre la pagina della release con il browser; un errore viene solo registrato. */
private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        AppLogger.warn("Splash", "Nessuna app per aprire $url", e)
    }
}

/** Proporzioni dell'immagine del logo (1408 × 768). */
private const val LOGO_ASPECT_RATIO = 1408f / 768f

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
