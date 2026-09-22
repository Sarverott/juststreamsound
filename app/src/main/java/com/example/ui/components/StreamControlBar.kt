package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioStreamManager
import com.example.audio.ConnectionStatus
import com.example.audio.StreamMetrics
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.DarkSlate700
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.DarkSlate900
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.RecordingRedGlow
import com.example.ui.theme.StreamingGreen

@Composable
fun StreamControlBar(
    metrics: StreamMetrics,
    hasAudioPermission: Boolean,
    onToggleStream: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val buttonBgColor by animateColorAsState(
        targetValue = if (metrics.isStreaming) RecordingRed else Cyan500,
        label = "btn_bg"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSlate800.copy(alpha = 0.75f))
            .border(1.dp, DarkSlate700.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .padding(18.dp)
            .testTag("stream_control_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Telemetry telemetry row (Timer, Bytes, Packets)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelemetryStat(
                label = "DURATION",
                value = AudioStreamManager.formatDuration(metrics.durationSeconds),
                color = if (metrics.isStreaming) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(DarkSlate700)
            )
            TelemetryStat(
                label = "SENT",
                value = AudioStreamManager.formatBytes(metrics.totalBytesSent),
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(DarkSlate700)
            )
            TelemetryStat(
                label = "PACKETS",
                value = "${metrics.packetsSent}",
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Big Tactile Record / Stream Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp)
        ) {
            // Outer glowing animated halo when active
            if (metrics.isStreaming) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(RecordingRedGlow.copy(alpha = pulseAlpha))
                )
            }

            // Main circular action button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(buttonBgColor)
                    .clickable {
                        if (!hasAudioPermission) {
                            onRequestPermission()
                        } else {
                            onToggleStream()
                        }
                    }
                    .testTag("stream_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !hasAudioPermission -> Icons.Default.MicOff
                        metrics.isStreaming -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = if (metrics.isStreaming) "Stop Streaming" else "Start Streaming",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Stream Action Text Label
        Text(
            text = when {
                !hasAudioPermission -> "GRANT MIC PERMISSION"
                metrics.isStreaming -> "TAP TO STOP STREAMING"
                else -> "TAP TO START STREAMING"
            },
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = when {
                !hasAudioPermission -> AmberWarning
                metrics.isStreaming -> RecordingRed
                else -> Cyan400
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Status text
        Text(
            text = metrics.statusMessage,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TelemetryStat(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = DarkSlate700.copy(alpha = 1.2f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}
