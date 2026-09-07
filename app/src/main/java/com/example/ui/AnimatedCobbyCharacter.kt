package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Animated, responsive Cobby Character component.
 * Reacts with playful animations and speech bubble feedback when the user:
 * - Taps Cobby directly
 * - Adds a task
 * - Marks a task completed (Celebration mode)
 * - Deletes a task
 */
@Composable
fun AnimatedCobbyCharacterCard(
    mood: CobbyMood,
    speechText: String,
    onCharacterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isUserBouncing by remember { mutableStateOf(false) }

    // Continuous idle breathing & floating transition
    val infiniteTransition = rememberInfiniteTransition(label = "cobby_idle")

    val idleFloatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_y"
    )

    val idleBreathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_scale"
    )

    val talkingTilt by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "talking_tilt"
    )

    val celebrationJump by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -22f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "celebrate_jump"
    )

    // Compute dynamic translation and rotation based on active mood
    val currentTranslationY = when {
        isUserBouncing -> -18f
        mood == CobbyMood.CELEBRATING -> celebrationJump
        mood == CobbyMood.EXCITED -> celebrationJump * 0.7f
        else -> idleFloatY
    }

    val currentRotation = when (mood) {
        CobbyMood.TALKING -> talkingTilt
        CobbyMood.THINKING -> 5f
        CobbyMood.CELEBRATING -> talkingTilt * 1.5f
        else -> 0f
    }

    val currentScale = when (mood) {
        CobbyMood.CELEBRATING -> 1.05f
        CobbyMood.EXCITED -> 1.04f
        else -> idleBreathingScale
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("cobby_companion_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Cobby Character Visual
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 110.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isUserBouncing = true
                        onCharacterClick()
                        coroutineScope.launch {
                            delay(600)
                            isUserBouncing = false
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                // Glow aura behind Cobby
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = if (mood == CobbyMood.CELEBRATING) 1.25f else 1.0f
                            scaleY = if (mood == CobbyMood.CELEBRATING) 1.25f else 1.0f
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    when (mood) {
                                        CobbyMood.CELEBRATING -> Color(0xFFFFD700).copy(alpha = 0.45f)
                                        CobbyMood.EXCITED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        CobbyMood.THINKING -> Color(0xFF00E5FF).copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    },
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // The Cobby 3D Character image with physics animations
                Image(
                    painter = painterResource(id = R.drawable.img_cobby_character),
                    contentDescription = "Cobby the AI Productivity Companion",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 82.dp, height = 104.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .graphicsLayer {
                            translationY = currentTranslationY
                            rotationZ = currentRotation
                            scaleX = currentScale
                            scaleY = currentScale
                        }
                )

                // Reactive status badge (Celebration / Waving / Heart)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (mood) {
                        CobbyMood.CELEBRATING -> {
                            Icon(
                                Icons.Default.Celebration,
                                contentDescription = "Celebration",
                                tint = Color.Yellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        CobbyMood.EXCITED -> {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Excited",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        CobbyMood.TALKING -> {
                            Icon(
                                Icons.Default.WavingHand,
                                contentDescription = "Waving",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        CobbyMood.THINKING -> {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = "Thinking",
                                tint = Color.Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Friendly",
                                tint = Color(0xFFFF80AB),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Interactive Speech Bubble
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCharacterClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Cobby",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = when (mood) {
                                CobbyMood.CELEBRATING -> "Celebrating! 🎉"
                                CobbyMood.EXCITED -> "Ready to Roll! ⚡"
                                CobbyMood.THINKING -> "AI Thinking... 💭"
                                CobbyMood.TALKING -> "Tip of the day 💡"
                                else -> "Online & Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Speech Bubble Card
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = speechText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Tap Cobby or speech bubble for quick motivation",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
