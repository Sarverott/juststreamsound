package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioStreamConfig
import com.example.audio.StreamProtocol
import com.example.ui.theme.Cyan400
import com.example.ui.theme.DarkSlate700
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.DarkSlate850
import com.example.ui.theme.DarkSlate900
import com.example.ui.theme.StreamingGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerConfigCard(
    config: AudioStreamConfig,
    isStreaming: Boolean,
    onUrlChange: (String) -> Unit,
    onProtocolChange: (StreamProtocol) -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onChunkDurationChange: (Int) -> Unit,
    onHandshakeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val presets = listOf(
        Pair("Emulator (10.0.2.2)", "ws://10.0.2.2:8000/ws/audio"),
        Pair("Local Host (8000)", "ws://192.168.1.100:8000/stt"),
        Pair("Public Echo", "wss://echo.websocket.org"),
        Pair("Whisper Live", "ws://10.0.2.2:9090")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSlate800.copy(alpha = 0.6f))
            .border(1.dp, DarkSlate700.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("server_config_card")
    ) {
        // Card Header with toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSlate900),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server Settings",
                        tint = Cyan400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "STT SERVER ENDPOINT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (config.protocol == StreamProtocol.SIMULATION) {
                            "Simulation Mode (Local Test)"
                        } else {
                            config.serverUrl
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.testTag("expand_config_button")
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Expanded Configuration Controls
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(14.dp))

                // Protocol selector chips
                Text(
                    text = "Streaming Protocol",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamProtocol.values().forEach { proto ->
                        FilterChip(
                            selected = config.protocol == proto,
                            onClick = { if (!isStreaming) onProtocolChange(proto) },
                            enabled = !isStreaming,
                            label = { Text(proto.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan400.copy(alpha = 0.2f),
                                selectedLabelColor = Cyan400
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = config.protocol == proto,
                                borderColor = DarkSlate700,
                                selectedBorderColor = Cyan400
                            )
                        )
                    }
                }

                if (config.protocol != StreamProtocol.SIMULATION) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // URL Input Field
                    OutlinedTextField(
                        value = config.serverUrl,
                        onValueChange = { onUrlChange(it) },
                        label = { Text("Server WebSocket / Stream URL") },
                        placeholder = { Text("ws://10.0.2.2:8000/ws/audio") },
                        enabled = !isStreaming,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400,
                            unfocusedBorderColor = DarkSlate700,
                            focusedContainerColor = DarkSlate900,
                            unfocusedContainerColor = DarkSlate900
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset quick buttons
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (name, url) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSlate900,
                                modifier = Modifier
                                    .clickable(enabled = !isStreaming) {
                                        onUrlChange(url)
                                    }
                                    .border(0.5.dp, DarkSlate700, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (config.serverUrl == url) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Audio Format Specifications
                Text(
                    text = "Audio Sampling Rate (STT Standard is 16 kHz)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(16000, 8000, 44100).forEach { rate ->
                        val label = when (rate) {
                            16000 -> "16 kHz (STT)"
                            8000 -> "8 kHz"
                            else -> "44.1 kHz"
                        }
                        FilterChip(
                            selected = config.sampleRate == rate,
                            onClick = { if (!isStreaming) onSampleRateChange(rate) },
                            enabled = !isStreaming,
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan400.copy(alpha = 0.2f),
                                selectedLabelColor = Cyan400
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Packet chunk latency
                Text(
                    text = "Packet Buffer Chunk Size",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200, 500).forEach { ms ->
                        FilterChip(
                            selected = config.chunkDurationMs == ms,
                            onClick = { if (!isStreaming) onChunkDurationChange(ms) },
                            enabled = !isStreaming,
                            label = { Text("${ms}ms", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan400.copy(alpha = 0.2f),
                                selectedLabelColor = Cyan400
                            )
                        )
                    }
                }

                if (config.protocol != StreamProtocol.SIMULATION) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Send Initial STT Handshake JSON",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sends audio format & sample rate on connect",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.sendHandshake,
                            onCheckedChange = { onHandshakeToggle(it) },
                            enabled = !isStreaming,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Cyan400,
                                checkedTrackColor = Cyan400.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}
