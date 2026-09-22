package com.example.audio

enum class StreamProtocol(val displayName: String, val description: String) {
    WEBSOCKET_BINARY("WebSocket (Binary PCM)", "Sends raw 16-bit PCM byte buffers directly over WebSocket (Whisper/Vosk standard)"),
    WEBSOCKET_JSON("WebSocket (Base64 JSON)", "Sends JSON frames with base64 encoded PCM chunks and metadata"),
    SIMULATION("Test Echo & STT Simulation", "Records local mic audio, visualizes levels, and simulates STT responses for offline testing")
}

data class AudioStreamConfig(
    val serverUrl: String = "ws://10.0.2.2:8000/ws/audio",
    val protocol: StreamProtocol = StreamProtocol.WEBSOCKET_BINARY,
    val sampleRate: Int = 16000,
    val chunkDurationMs: Int = 100, // 100ms chunks = 10 packets per sec
    val autoReconnect: Boolean = true,
    val sendHandshake: Boolean = true
)

sealed interface ConnectionStatus {
    object Idle : ConnectionStatus
    object Connecting : ConnectionStatus
    object Streaming : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
    object Disconnected : ConnectionStatus
}

data class StreamMetrics(
    val isStreaming: Boolean = false,
    val durationSeconds: Long = 0L,
    val totalBytesSent: Long = 0L,
    val packetsSent: Long = 0L,
    val currentRmsDb: Float = -60f, // -60dB (silence) to 0dB (clipping)
    val normalizedAmplitude: Float = 0f, // 0.0f to 1.0f
    val recentAmplitudes: List<Float> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val statusMessage: String = "Ready to stream"
)

data class TranscriptItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val isFinal: Boolean = true,
    val confidence: Float? = null,
    val isSystemEvent: Boolean = false
)
