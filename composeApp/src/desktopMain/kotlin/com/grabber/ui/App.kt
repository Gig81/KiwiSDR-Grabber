package com.grabber.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import com.grabber.ui.designsystem.FeatureLockedOverlay
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberTheme
import com.grabber.ui.feature.audio.AudioTab
import com.grabber.ui.feature.console.RadioConsole
import com.grabber.ui.feature.console.FrequencyStep
import com.grabber.ui.feature.favorites.FavoritesTab
import com.grabber.ui.feature.recordings.RecordingsTab
import com.grabber.ui.feature.settings.SettingsTab
import com.grabber.ui.feature.spectrum.SpectrumTab
import com.grabber.ui.feature.users.UsersTab
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Finestra principale: console radio in alto, barra delle schede e area di lavoro della scheda attiva,
 * con le notifiche di connessione e la configurazione guidata al primo avvio.
 */
@Composable
fun App(
    controller: KiwiAppController,
    onExitApplication: () -> Unit = { kotlin.system.exitProcess(0) }
) {
    val appSettingsData by controller.settings.collectAsState()
    val currentFrequency by controller.currentFrequencyKhz.collectAsState()
    val currentMode by controller.currentMode.collectAsState()
    var activeTab by remember { mutableStateOf(MainTab.FAVORITES) }
    var consoleStep by remember { mutableStateOf(FrequencyStep.STEP_1KHZ) }

    val activeUsersCount by remember(controller) {
        controller.activeUsers.map { it.size }.distinctUntilChanged()
    }.collectAsState(initial = 0)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(controller) {
        controller.connectionNotifications.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    val isMachineLicensed = remember(appSettingsData.licenseKey) {
        controller.isLicenseValid(appSettingsData.licenseKey)
    }
    val openSettings = { activeTab = MainTab.SETTINGS }

    GrabberTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(GrabberColors.Background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioConsole(
                    controller = controller,
                    serverAddress = appSettingsData.serverAddress,
                    currentStep = consoleStep,
                    onStepChange = { consoleStep = it },
                    modifier = Modifier.fillMaxWidth().weight(1.4f)
                )
                MainTabBar(
                    activeTab = activeTab,
                    onSelect = { tab ->
                        activeTab = tab
                        if (tab == MainTab.RECORDER) controller.refreshHistory()
                    },
                    activeUsersCount = activeUsersCount
                )
                Box(modifier = Modifier.fillMaxWidth().weight(2.6f)) {
                    when (activeTab) {
                        MainTab.FAVORITES -> FavoritesTab(
                            controller = controller,
                            favorites = appSettingsData.favorites,
                            currentFreqKhz = currentFrequency,
                            currentMode = currentMode
                        )
                        MainTab.AUDIO -> AudioTab(controller = controller, onActivate = openSettings)
                        MainTab.RECORDER -> if (isMachineLicensed) {
                            RecordingsTab(controller = controller)
                        } else {
                            FeatureLockedOverlay(
                                message = "Registrazione e archivio sono funzioni avanzate.",
                                onActivate = openSettings
                            )
                        }
                        MainTab.SPECTRUM -> SpectrumTab(controller = controller)
                        MainTab.USERS -> UsersTab(
                            controller = controller,
                            isMachineLicensed = isMachineLicensed,
                            onActivate = openSettings
                        )
                        MainTab.SETTINGS -> SettingsTab(controller = controller)
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )

            if (!appSettingsData.setupCompleted) {
                InitialSetupDialog(
                    settings = appSettingsData,
                    onComplete = { callsign, serverAddress, serverPassword, telegramToken, telegramChatId ->
                        controller.setIdentUser(callsign)
                        controller.setServerAddress(serverAddress)
                        controller.setServerPassword(serverPassword)
                        if (telegramToken != null && telegramChatId != null) {
                            controller.updateTelegramSettings(
                                botToken = telegramToken,
                                chatId = telegramChatId,
                                enabled = true,
                                notifyAnyQso = appSettingsData.telegramNotifyAnyQso,
                                sensitiveWords = appSettingsData.telegramSensitiveWords,
                                intervalMinutes = appSettingsData.telegramMessageIntervalMinutes,
                                sendAudio = appSettingsData.telegramSendAudioAttachment,
                                sendTranscription = appSettingsData.telegramSendTranscription
                            )
                        }
                        controller.completeInitialSetup()
                    }
                )
            }

        }
    }
}
