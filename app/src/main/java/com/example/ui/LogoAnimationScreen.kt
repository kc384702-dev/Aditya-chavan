package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun LogoAnimationScreen(
    onAnimationComplete: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var currentStatusIndex by remember { mutableStateOf(0) }
    var isShaking by remember { mutableStateOf(false) }

    val statusMessages = listOf(
        "Initializing secure interface...",
        "Connecting to GPT core matrix...",
        "Synchronizing local memory vaults...",
        "Neural handshake complete. Enjoy!"
    )

    // Smoothly animate progress bar and active status indicators over 4.5 seconds
    LaunchedEffect(Unit) {
        val durationMs = 4500L
        val intervals = 100
        val delayPerInterval = durationMs / intervals

        for (i in 1..intervals) {
            delay(delayPerInterval)
            progress = i / 100f
            
            // Periodically shift the displayed loading messages
            currentStatusIndex = when {
                progress < 0.25f -> 0
                progress < 0.55f -> 1
                progress < 0.85f -> 2
                else -> 3
            }
            
            // Trigger a subtle shake or burst during transition moments
            if (i in listOf(25, 55, 85)) {
                isShaking = true
                delay(80)
                isShaking = false
            }
        }
        
        // Wait a small extra fraction to let progress fully settle
        delay(200)
        onAnimationComplete()
    }

    // Infinite rotations & scale pulses
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Cosmic Shimmer Gradient offset moving dynamically
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF06070B),
            Color(0xFF0F1015),
            Color(0xFF1E212A)
        )
    )

    val neonGlowBrush = Brush.sweepGradient(
        colors = listOf(
            Color(0xFF10A37F),
            Color(0xFF059669),
            Color(0xFF2E7D32),
            Color(0xFF00E676),
            Color(0xFF10A37F)
        )
    )

    val textShimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE0E0E0),
            Color(0xFF10A37F),
            Color(0xFF81C784),
            Color(0xFFE0E0E0)
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerOffset, shimmerOffset),
        end = androidx.compose.ui.geometry.Offset(shimmerOffset + 300f, shimmerOffset + 300f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
        ) {
            // Pulsing Glowing Frame holding the Nexus GPT branding
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .scale(scalePulse + if (isShaking) 0.08f else 0f)
            ) {
                // Spinning gradient neon ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotation)
                        .border(4.dp, neonGlowBrush, CircleShape)
                        .padding(6.dp)
                )

                // High fidelity outer ring shadow halo
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10A37F).copy(alpha = 0.08f))
                )

                // Render the premium compiled Nexus GPT Logo
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF121214))
                        .border(2.dp, Color(0xFF10A37F).copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val logoResId = remember {
                        context.resources.getIdentifier("ic_nexus_gpt_logo", "drawable", context.packageName)
                    }

                    if (logoResId != 0) {
                        Image(
                            painter = painterResource(id = logoResId),
                            contentDescription = "Nexus GPT Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        FallbackRobotIcon()
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Shudder/Sparkle decoration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF10A37F),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation * 0.5f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                
                Text(
                    text = "A I   N E U R A L   L I N K",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10A37F).copy(alpha = 0.85f),
                    letterSpacing = 2.sp
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF10A37F),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(-rotation * 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Brand title text with high performance gradient shifting shimmer
            Text(
                text = "NEXUS GPT",
                style = androidx.compose.ui.text.TextStyle(
                    brush = textShimmerBrush,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            )

            Text(
                text = "Secure AI Chat Companion",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Progress Section Container
            Column(
                modifier = Modifier.width(240.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sleek, futuristic linear progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0DCD9D), Color(0xFF10A37F))
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fading text transitions of live system actions
                AnimatedContent(
                    targetState = statusMessages[currentStatusIndex],
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + slideInVertically(animationSpec = tween(220)) { it / 2 }) togetherWith
                        fadeOut(animationSpec = tween(180))
                    },
                    label = "statusMessage"
                ) { targetStatus ->
                    Text(
                        text = targetStatus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Numerical speed calculations
                Text(
                    text = "${(progress * 100).toInt()}% synchronized",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10A37F).copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun FallbackRobotIcon() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = listOf(Color(0xFF10A37F), Color(0xFF059669)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = "AI Brain Core",
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
    }
}
