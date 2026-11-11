package com.grabber.ui.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dialogo di conferma per operazioni distruttive (eliminazioni, svuotamenti, ripristini). */
@Composable
fun AppConfirmDialog(
    title: String,
    icon: ImageVector?,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "Annulla",
    accentColor: Color = GrabberColors.Error,
    confirmContentColor: Color = Color.White
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, color = accentColor, fontSize = 13.sp)
            }
        },
        text = { Text(message, color = GrabberColors.TextPrimary, fontSize = 11.sp) },
        confirmButton = {
            PrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                containerColor = accentColor,
                contentColor = confirmContentColor
            )
        },
        dismissButton = { OutlinedButton(text = dismissText, onClick = onDismiss) },
        containerColor = GrabberColors.SurfaceElevated,
        textContentColor = GrabberColors.TextPrimary
    )
}
