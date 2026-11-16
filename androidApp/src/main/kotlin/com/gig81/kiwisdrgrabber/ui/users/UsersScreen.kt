package com.gig81.kiwisdrgrabber.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.ui.components.EmptyState
import com.gig81.kiwisdrgrabber.ui.components.Panel
import com.gig81.kiwisdrgrabber.ui.components.formatFrequencyKhz
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.grabber.mobile.MobileAppController
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode

/** Utenti connessi in tempo reale (da `user_cb`); solo vista live, nessuno storico. */
@Composable
fun UsersScreen(controller: MobileAppController) {
    val connection by controller.connectionState.collectAsStateWithLifecycle()
    val users by controller.activeUsers.collectAsStateWithLifecycle()

    if (connection !is ConnectionState.Connected) {
        EmptyState("L'elenco degli utenti connessi è disponibile durante la connessione al ricevitore.")
        return
    }
    if (users.isEmpty()) {
        EmptyState("In attesa dell'elenco degli utenti dal ricevitore…")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${users.size} utenti connessi", style = MaterialTheme.typography.titleSmall, color = KiwiColors.TextSecondary)
        }
        items(users, key = { it.slot }) { user ->
            val frequencyKhz = user.freqHz / 1000.0
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (user.isMySession) Icons.Filled.Star else Icons.Filled.Person,
                        contentDescription = null,
                        tint = if (user.isMySession) KiwiColors.SignalAmber else KiwiColors.TextSecondary
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(user.name, style = MaterialTheme.typography.bodyLarge)
                        if (user.location.isNotBlank()) {
                            Text(user.location, style = MaterialTheme.typography.bodySmall, color = KiwiColors.Secondary)
                        }
                        Text(
                            "Canale ${user.slot} · ${user.mode.uppercase()} · connesso da ${user.connected.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KiwiColors.TextSecondary
                        )
                    }
                    // Tocco sulla frequenza: sintonizza sulla stessa frequenza e modo dell'utente.
                    TextButton(onClick = {
                        val mode = ModulationMode.entries.find { it.code.equals(user.mode, ignoreCase = true) }
                        controller.tune(frequencyKhz, mode)
                    }) {
                        Text("${formatFrequencyKhz(frequencyKhz)} kHz")
                    }
                }
            }
        }
    }
}

