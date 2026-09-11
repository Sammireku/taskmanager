package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppTourDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("App Tour & Syntax Guide", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("🗣️ Wording Examples", modifier = Modifier.padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("🔘 Buttons Guide", modifier = Modifier.padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("📍 Geofences", modifier = Modifier.padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (selectedTab) {
                    0 -> {
                        Text(
                            text = "Natural Language Syntax Examples:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val examples = listOf(
                            Pair("📅 Date & Time", "\"Submit report tomorrow at 3 PM\""),
                            Pair("📍 Arrival Geofence", "\"Buy groceries when I arrive at Costco\""),
                            Pair("📍 Departure Alarm", "\"Get keys when leaving Home\""),
                            Pair("🔴 Priority & Tags", "\"Meeting at Starbucks 4pm high priority #work\""),
                            Pair("🔁 Habit", "\"Workout every morning at 7 AM\"")
                        )

                        examples.forEach { (cat, phrase) ->
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text(cat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                ) {
                                    Text(
                                        phrase,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        Text(
                            text = "Main Features & Buttons:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val buttons = listOf(
                            Pair("🤖 Cobby Character Bar", "Tap on Home for greeting audio or name customization."),
                            Pair("➕ FAB (+) Button", "Tap to create a task manually or holding for voice."),
                            Pair("🎙️ Floating Mic", "Tap to speak a task directly to Gemini AI."),
                            Pair("📍 Proximity Radar", "Shows live distance to nearby tasks with 1-tap Google Maps routing."),
                            Pair("🗣️ Hands-Free Assistant", "Say \"Hey Google, add task in Daily Planner...\" anytime!")
                        )

                        buttons.forEach { (title, desc) ->
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Geofencing & Location Alarms:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Set arrival or departure alarms for any saved or searched venue.\n• When you enter or exit the geofence boundary, Cobby sounds a notification or alarm.\n• Saved Places (Home, Office, Gym) allow instant 1-tap geofence selection.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_tour_dialog_button")
            ) {
                Text("Got It!")
            }
        }
    )
}
