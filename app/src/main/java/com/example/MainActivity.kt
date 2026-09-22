package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.ConnectionStatus
import com.example.audio.StreamProtocol
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.ServerConfigCard
import com.example.ui.components.StreamControlBar
import com.example.ui.components.TranscriptCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.Cyan400
import com.example.ui.theme.DarkSlate700
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.DarkSlate850
import com.example.ui.theme.DarkSlate900
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.StreamingGreen
import com.example.viewmodel.AudioStreamViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AudioStreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AudioStreamerScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AudioStreamerScreen(viewModel: AudioStreamViewModel) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val transcripts by viewModel.transcripts.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.toggleStreaming()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("audio_streamer_screen"),
        containerColor = DarkSlate900,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Adaptive Container max width for tablets and foldables
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top App Bar
                TopBrandingHeader(
                    isStreaming = metrics.isStreaming,
                    connectionStatus = metrics.connectionStatus,
                    sampleRate = config.sampleRate,
                    protocol = config.protocol
                )

                // Permission Warning Banner if not granted
                if (!hasAudioPermission) {
                    PermissionNoticeBanner(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }

                // Waveform & VU Level Visualizer
                AudioWaveformVisualizer(
                    metrics = metrics
                )

                // Main Stream Control Bar (Big Button & Telemetry)
                StreamControlBar(
                    metrics = metrics,
                    hasAudioPermission = hasAudioPermission,
                    onToggleStream = { viewModel.toggleStreaming() },
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )

                // Server Configuration Card
                ServerConfigCard(
                    config = config,
                    isStreaming = metrics.isStreaming,
                    onUrlChange = { viewModel.updateServerUrl(it) },
                    onProtocolChange = { viewModel.updateProtocol(it) },
                    onSampleRateChange = { viewModel.updateSampleRate(it) },
                    onChunkDurationChange = { viewModel.updateChunkDuration(it) },
                    onHandshakeToggle = { viewModel.toggleHandshake(it) }
                )

                // Live STT Transcripts & Logs Card
                TranscriptCard(
                    transcripts = transcripts,
                    isStreaming = metrics.isStreaming,
                    onClear = { viewModel.clearTranscripts() }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TopBrandingHeader(
    isStreaming: Boolean,
    connectionStatus: ConnectionStatus,
    sampleRate: Int,
    protocol: StreamProtocol
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSlate800)
                    .border(1.dp, DarkSlate700, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Audio Streamer App",
                    tint = Cyan400,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Audio Streamer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Phone-to-Server STT Stream",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Connection Status Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSlate800,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = when (connectionStatus) {
                    is ConnectionStatus.Streaming -> StreamingGreen
                    is ConnectionStatus.Connecting -> Cyan400
                    is ConnectionStatus.Error -> RecordingRed
                    else -> DarkSlate700
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionStatus) {
                                is ConnectionStatus.Streaming -> StreamingGreen
                                is ConnectionStatus.Connecting -> Cyan400
                                is ConnectionStatus.Error -> RecordingRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (connectionStatus) {
                        is ConnectionStatus.Streaming -> "LIVE STREAM"
                        is ConnectionStatus.Connecting -> "CONNECTING"
                        is ConnectionStatus.Error -> "ERROR"
                        else -> "IDLE"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 10.sp
                    ),
                    color = when (connectionStatus) {
                        is ConnectionStatus.Streaming -> StreamingGreen
                        is ConnectionStatus.Connecting -> Cyan400
                        is ConnectionStatus.Error -> RecordingRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionNoticeBanner(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Permission Required",
                    tint = AmberWarning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Microphone Access Required",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Grant audio recording permission to stream live microphone input to the STT server.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("grant_permission_button")
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }
        }
    }
}
