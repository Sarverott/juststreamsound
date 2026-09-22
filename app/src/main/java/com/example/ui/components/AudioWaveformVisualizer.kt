package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.StreamMetrics
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.DarkSlate700
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.DarkSlate900
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.StreamingGreen
import kotlin.math.sin

@Composable
fun AudioWaveformVisualizer(
    metrics: StreamMetrics,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_idle")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idle_phase"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSlate800.copy(alpha = 0.6f))
            .border(1.dp, DarkSlate700.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("audio_waveform_card")
    ) {
        // Visualizer Header with real-time dB meter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (metrics.isStreaming) StreamingGreen else DarkSlate700)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (metrics.isStreaming) "LIVE AUDIO INPUT" else "AUDIO INPUT MONITOR",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (metrics.isStreaming) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Real-time dB indicator
            Text(
                text = if (metrics.isStreaming) {
                    "%.1f dB".format(metrics.currentRmsDb)
                } else {
                    "-- dB"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = when {
                    !metrics.isStreaming -> MaterialTheme.colorScheme.onSurfaceVariant
                    metrics.currentRmsDb > -6f -> RecordingRed
                    metrics.currentRmsDb > -18f -> AmberWarning
                    else -> StreamingGreen
                },
                modifier = Modifier.testTag("db_readout")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Waveform Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSlate900)
                .border(0.8.dp, DarkSlate700.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .testTag("waveform_canvas")
            ) {
                val width = size.width
                val height = size.height
                val midY = height / 2f

                if (!metrics.isStreaming) {
                    // Gentle ambient idle baseline
                    val path = Path()
                    path.moveTo(0f, midY)
                    val points = 50
                    for (i in 0..points) {
                        val x = (i / points.toFloat()) * width
                        val y = midY + (sin(i * 0.35f + idlePhase) * 3f)
                        path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = Cyan400.copy(alpha = 0.25f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                } else {
                    // Active recording: Render multi-bar EQ waveform
                    val amplitudes = metrics.recentAmplitudes
                    val totalBars = 32
                    val barSpacing = 4.dp.toPx()
                    val barWidth = ((width - (totalBars - 1) * barSpacing) / totalBars).coerceAtLeast(2.dp.toPx())

                    // Prepare bar data
                    val barHeights = FloatArray(totalBars) { index ->
                        val amp = if (amplitudes.isNotEmpty()) {
                            val dataIdx = (index * amplitudes.size / totalBars).coerceIn(0, amplitudes.lastIndex)
                            amplitudes[dataIdx]
                        } else {
                            0.05f
                        }
                        // Scale amplitude to height
                        val baseH = (amp * height * 0.95f).coerceIn(4.dp.toPx(), height - 4.dp.toPx())
                        baseH
                    }

                    for (i in 0 until totalBars) {
                        val barH = barHeights[i]
                        val x = i * (barWidth + barSpacing)
                        val topY = midY - (barH / 2f)

                        val barBrush = Brush.verticalGradient(
                            colors = listOf(
                                Cyan400,
                                Cyan500,
                                StreamingGreen.copy(alpha = 0.8f)
                            ),
                            startY = topY,
                            endY = topY + barH
                        )

                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(x, topY),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-stage VU Level Meter Bar
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-60 dB", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-24 dB", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-12 dB", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0 dB (PEAK)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = RecordingRed)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkSlate900)
            ) {
                val levelFraction = if (metrics.isStreaming) {
                    ((metrics.currentRmsDb + 60f) / 60f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(levelFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    StreamingGreen,
                                    AmberWarning,
                                    RecordingRed
                                )
                            )
                        )
                )
            }
        }
    }
}
