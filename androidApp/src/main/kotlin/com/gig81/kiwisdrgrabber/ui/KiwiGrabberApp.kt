package com.gig81.kiwisdrgrabber.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.ui.favorites.FavoritesScreen
import com.gig81.kiwisdrgrabber.ui.radio.RadioScreen
import com.gig81.kiwisdrgrabber.ui.settings.SettingsScreen
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.gig81.kiwisdrgrabber.ui.theme.KiwiGrabberTheme
import com.gig81.kiwisdrgrabber.ui.users.UsersScreen
import com.gig81.kiwisdrgrabber.ui.waterfall.WaterfallScreen
import com.grabber.license.GatedFeature
import com.grabber.mobile.MobileAppController
import com.grabber.model.ConnectionState

enum class AppTab(val label: String, val icon: ImageVector) {
    RADIO("Radio", Icons.Filled.Radio),
    WATERFALL("Waterfall", Icons.Filled.Waves),
    FAVORITES("Preferiti", Icons.Filled.Favorite),
    USERS("Utenti", Icons.Filled.Group),
    SETTINGS("Impostazioni", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiwiGrabberApp(controller: MobileAppController) {
    val themeMode by controller.uiTheme.collectAsStateWithLifecycle()
    KiwiGrabberTheme(mode = themeMode) {
        var tab by rememberSaveable { mutableStateOf(AppTab.RADIO) }
        var lockedFeature by remember { mutableStateOf<GatedFeature?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val connection by controller.connectionState.collectAsStateWithLifecycle()
        val requestNotifications = rememberNotificationPermissionRequest()

        LaunchedEffect(controller) { controller.notifications.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(controller) { controller.lockedFeatureRequests.collect { lockedFeature = it } }

        // Il socket W/F è aperto solo mentre la schermata Waterfall è visibile.
        DisposableEffect(tab) {
            controller.setWaterfallVisible(tab == AppTab.WATERFALL)
            onDispose { controller.setWaterfallVisible(false) }
        }

        val connect = {
            // La notifica serve solo al servizio di ascolto in background (versione attivata).
            if (controller.isActivated.value) requestNotifications()
            controller.connect()
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("KiwiSDR Grabber", style = MaterialTheme.typography.titleMedium) },
                    actions = { ConnectionBadge(connection) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KiwiColors.Surface)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = KiwiColors.Surface) {
                    AppTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    AppTab.RADIO -> RadioScreen(controller, onConnect = connect)
                    AppTab.WATERFALL -> WaterfallScreen(controller, onConnect = connect)
                    AppTab.FAVORITES -> FavoritesScreen(controller)
                    AppTab.USERS -> UsersScreen(controller)
                    AppTab.SETTINGS -> SettingsScreen(controller)
                }
            }
        }

        lockedFeature?.let { feature ->
            FeatureLockedDialog(
                feature = feature,
                onDismiss = { lockedFeature = null },
                onOpenLicense = {
                    lockedFeature = null
                    tab = AppTab.SETTINGS
                }
            )
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (label, color) = when (state) {
        is ConnectionState.Connected -> "Connesso" to KiwiColors.Success
        is ConnectionState.Connecting -> "Connessione…" to KiwiColors.Warning
        is ConnectionState.Reconnecting -> "Riconnessione ${state.secondsLeft}s" to KiwiColors.Warning
        is ConnectionState.Error -> "Errore" to KiwiColors.Error
        is ConnectionState.Disconnected -> "Disconnesso" to KiwiColors.TextDisabled
    }
    Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = KiwiColors.TextSecondary)
    }
}

/** Overlay equivalente a `FeatureLockedOverlay` del desktop, con collegamento alla sezione Licenza. */
@Composable
fun FeatureLockedDialog(feature: GatedFeature, onDismiss: () -> Unit, onOpenLicense: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Funzione riservata alla versione attivata") },
        text = {
            Text(
                "${feature.lockedMessage}\n\nL'attivazione è gratuita: copia l'ID dispositivo dalla sezione " +
                    "Licenza, invialo allo sviluppatore e inserisci il codice ricevuto."
            )
        },
        confirmButton = { TextButton(onClick = onOpenLicense) { Text("Vai a Licenza") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

/** Android 13+: il permesso per le notifiche serve a mostrare quella del servizio di ascolto. */
@Composable
private fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
