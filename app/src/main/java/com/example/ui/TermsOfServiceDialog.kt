package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TermsOfServiceDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Terms of Service & Privacy Agreement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = """
1. LOCAL-FIRST PRIVACY & DATA PERSISTENCE:
Your tasks, schedules, and custom saved places are stored securely in your local Room database. No personal task history is sold or shared with external data brokers.

2. GEMINI AI VOICE & NATURAL LANGUAGE PARSING:
Spoken and written task queries use Gemini AI models to automatically determine dates, times, locations, and priorities. No raw microphone audio is permanently stored.

3. LOCATION SERVICES & GEOFENCING ALARMS:
Location data is processed strictly on-device to trigger arrival/departure geofences, calculate live proximity radar distances, and provide venue recommendations.

4. MICROPHONE & SYSTEM PERMISSIONS:
Microphone access is used exclusively during active voice recording sessions. Google Assistant App Actions utilize standard system channels.

5. USER RESPONSIBILITY & ALARM MANAGEMENT:
You are responsible for ensuring alarm volume and notification permissions remain enabled on your device for time-critical reminders.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_terms_dialog_button")
            ) {
                Text("Close")
            }
        }
    )
}
