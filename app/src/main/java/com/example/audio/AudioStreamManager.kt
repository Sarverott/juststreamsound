package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class AudioStreamManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _metrics = MutableStateFlow(StreamMetrics())
    val metrics: StateFlow<StreamMetrics> = _metrics.asStateFlow()

    private val _transcripts = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<TranscriptItem>> = _transcripts.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var simulationJob: Job? = null

    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null

    private val recentAmplitudesQueue = ArrayDeque<Float>(40)
    private var currentConfig = AudioStreamConfig()

    @SuppressLint("MissingPermission")
    fun startStreaming(config: AudioStreamConfig) {
        if (_metrics.value.isStreaming) return
        currentConfig = config

        _metrics.update {
            it.copy(
                isStreaming = true,
                durationSeconds = 0L,
                totalBytesSent = 0L,
                packetsSent = 0L,
                currentRmsDb = -60f,
                normalizedAmplitude = 0f,
                recentAmplitudes = emptyList(),
                connectionStatus = ConnectionStatus.Connecting,
                statusMessage = if (config.protocol == StreamProtocol.SIMULATION) {
                    "Test simulation mode active"
                } else {
                    "Connecting to ${config.serverUrl}..."
                }
            )
        }

        addSystemMessage("Initiated audio stream (${config.protocol.displayName}, ${config.sampleRate}Hz)")

        if (config.protocol == StreamProtocol.SIMULATION) {
            _metrics.update { it.copy(connectionStatus = ConnectionStatus.Streaming, statusMessage = "Streaming audio (Simulation Mode)") }
            startLocalRecording(config)
            startSimulationSpeechGen()
        } else {
            connectWebSocketAndRecord(config)
        }

        startTimer()
    }

    private fun connectWebSocketAndRecord(config: AudioStreamConfig) {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
            .writeTimeout(5, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val request = try {
            Request.Builder()
                .url(config.serverUrl)
                .build()
        } catch (e: Exception) {
            _metrics.update {
                it.copy(
                    isStreaming = false,
                    connectionStatus = ConnectionStatus.Error("Invalid URL: ${e.localizedMessage ?: e.message}")
                )
            }
            addSystemMessage("Error: Invalid server URL: ${e.message}")
            return
        }

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _metrics.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Streaming,
                        statusMessage = "Connected & streaming live"
                    )
                }
                addSystemMessage("WebSocket connected to ${config.serverUrl}")

                if (config.sendHandshake) {
                    val handshake = JSONObject().apply {
                        put("type", "config")
                        put("sample_rate", config.sampleRate)
                        put("channels", 1)
                        put("format", "pcm_s16le")
                        put("chunk_duration_ms", config.chunkDurationMs)
                    }.toString()
                    webSocket.send(handshake)
                }

                startLocalRecording(config)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingServerText(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Some servers return binary audio or tokens
                addSystemMessage("Received binary payload from server (${bytes.size} bytes)")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                addSystemMessage("Server closing stream: $reason ($code)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _metrics.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Disconnected,
                        statusMessage = "Connection closed ($code)"
                    )
                }
                addSystemMessage("WebSocket closed: $reason ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = t.localizedMessage ?: "Connection error"
                _metrics.update {
                    it.copy(
                        isStreaming = false,
                        connectionStatus = ConnectionStatus.Error(errorMsg),
                        statusMessage = "Error: $errorMsg"
                    )
                }
                addSystemMessage("WebSocket failed: $errorMsg")
                stopRecordingEngine()
            }
        })
    }

    private fun handleIncomingServerText(text: String) {
        // Parse standard STT JSON payloads or fallback to plain text
        try {
            val json = JSONObject(text)
            val transcript = when {
                json.has("text") -> json.optString("text")
                json.has("transcript") -> json.optString("transcript")
                json.has("partial") -> json.optString("partial")
                json.has("result") -> json.optString("result")
                json.has("message") -> json.optString("message")
                else -> text
            }
            val isFinal = json.optBoolean("is_final", json.optBoolean("final", true))
            val confidence = if (json.has("confidence")) json.optDouble("confidence").toFloat() else null

            if (transcript.isNotBlank()) {
                appendTranscript(
                    TranscriptItem(
                        text = transcript,
                        isFinal = isFinal,
                        confidence = confidence
                    )
                )
            }
        } catch (_: Exception) {
            // Plain text transcript
            if (text.isNotBlank()) {
                appendTranscript(TranscriptItem(text = text, isFinal = true))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocalRecording(config: AudioStreamConfig) {
        recordingJob?.cancel()
        recordingJob = scope.launch {
            val sampleRate = config.sampleRate
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                _metrics.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Error("Unsupported audio configuration"),
                        statusMessage = "AudioRecord parameter error"
                    )
                }
                return@launch
            }

            // Chunk duration: e.g. 100ms -> samples = sampleRate * 0.1
            val chunkSamples = (sampleRate * (config.chunkDurationMs / 1000.0)).toInt()
            val chunkBytes = chunkSamples * 2 // 16-bit = 2 bytes per sample
            val bufferSize = max(minBufferSize, chunkBytes * 2)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    _metrics.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Error("Failed to initialize microphone"),
                            statusMessage = "Microphone init failed"
                        )
                    }
                    addSystemMessage("Error: AudioRecord failed to initialize")
                    return@launch
                }

                audioRecord?.startRecording()
                addSystemMessage("Microphone capture started ($sampleRate Hz, 16-bit PCM)")

                val audioBuffer = ByteArray(chunkBytes)

                while (isActive && _metrics.value.isStreaming) {
                    var totalRead = 0
                    while (totalRead < chunkBytes && isActive) {
                        val read = audioRecord?.read(audioBuffer, totalRead, chunkBytes - totalRead) ?: -1
                        if (read > 0) {
                            totalRead += read
                        } else if (read < 0) {
                            break
                        }
                    }

                    if (totalRead > 0) {
                        // Calculate RMS amplitude
                        val (rmsDb, normAmp) = calculateAudioLevels(audioBuffer, totalRead)

                        updateWaveformMetrics(rmsDb, normAmp, totalRead)

                        // Transmit audio chunk
                        sendAudioChunk(audioBuffer, totalRead, config)
                    }
                }
            } catch (e: SecurityException) {
                _metrics.update {
                    it.copy(
                        isStreaming = false,
                        connectionStatus = ConnectionStatus.Error("Microphone permission denied"),
                        statusMessage = "Permission error"
                    )
                }
                addSystemMessage("Microphone permission denied: ${e.message}")
            } catch (e: Exception) {
                addSystemMessage("Recording loop error: ${e.message}")
            } finally {
                stopRecordingEngine()
            }
        }
    }

    private fun sendAudioChunk(buffer: ByteArray, length: Int, config: AudioStreamConfig) {
        val ws = webSocket
        if (config.protocol == StreamProtocol.WEBSOCKET_BINARY && ws != null) {
            val byteString = buffer.toByteString(0, length)
            ws.send(byteString)
        } else if (config.protocol == StreamProtocol.WEBSOCKET_JSON && ws != null) {
            val base64Audio = Base64.encodeToString(buffer, 0, length, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("type", "audio_chunk")
                put("data", base64Audio)
                put("samples", length / 2)
            }.toString()
            ws.send(json)
        }
        // In simulation mode, bytes and packets are recorded without sending network frames
    }

    private fun calculateAudioLevels(buffer: ByteArray, length: Int): Pair<Float, Float> {
        var sumSquares = 0.0
        val sampleCount = length / 2
        for (i in 0 until length step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val signedSample = sample.toShort()
            sumSquares += (signedSample * signedSample)
        }
        val rms = sqrt(sumSquares / max(1, sampleCount))
        val maxAmp = 32767.0
        val norm = (rms / maxAmp).coerceIn(0.0, 1.0).toFloat()

        val db = if (rms > 1.0) {
            (20 * log10(rms / maxAmp)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }
        return Pair(db, norm)
    }

    private fun updateWaveformMetrics(rmsDb: Float, normAmp: Float, bytesCount: Int) {
        synchronized(recentAmplitudesQueue) {
            if (recentAmplitudesQueue.size >= 36) {
                recentAmplitudesQueue.removeFirst()
            }
            recentAmplitudesQueue.addLast(normAmp)
        }

        _metrics.update { current ->
            current.copy(
                currentRmsDb = rmsDb,
                normalizedAmplitude = normAmp,
                recentAmplitudes = recentAmplitudesQueue.toList(),
                totalBytesSent = current.totalBytesSent + bytesCount,
                packetsSent = current.packetsSent + 1
            )
        }
    }

    private fun startSimulationSpeechGen() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            val samplePhrases = listOf(
                "Streaming audio buffer initialized.",
                "Microphone captured vocal resonance.",
                "Speech-to-text frame decoded successfully.",
                "Continuous low-latency audio transmission active.",
                "Server recognized incoming speech sequence."
            )
            var phraseIndex = 0
            while (isActive && _metrics.value.isStreaming) {
                delay(3500)
                // If the user made sound or periodic simulation
                val phrase = samplePhrases[phraseIndex % samplePhrases.size]
                phraseIndex++
                appendTranscript(
                    TranscriptItem(
                        text = phrase,
                        isFinal = true,
                        confidence = 0.95f
                    )
                )
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _metrics.value.isStreaming) {
                delay(1000)
                _metrics.update { it.copy(durationSeconds = it.durationSeconds + 1) }
            }
        }
    }

    fun stopStreaming() {
        if (!_metrics.value.isStreaming) return

        timerJob?.cancel()
        simulationJob?.cancel()
        recordingJob?.cancel()

        try {
            webSocket?.close(1000, "Stream stopped by user")
        } catch (_: Exception) {}
        webSocket = null
        okHttpClient = null

        stopRecordingEngine()

        _metrics.update {
            it.copy(
                isStreaming = false,
                normalizedAmplitude = 0f,
                currentRmsDb = -60f,
                connectionStatus = ConnectionStatus.Idle,
                statusMessage = "Streaming stopped"
            )
        }
        addSystemMessage("Stream stopped. Total sent: ${formatBytes(_metrics.value.totalBytesSent)} (${_metrics.value.packetsSent} packets)")
    }

    private fun stopRecordingEngine() {
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    fun appendTranscript(item: TranscriptItem) {
        _transcripts.update { list ->
            // Keep latest 100 items
            (list + item).takeLast(100)
        }
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    fun addSystemMessage(text: String) {
        appendTranscript(
            TranscriptItem(
                text = text,
                isFinal = true,
                isSystemEvent = true
            )
        )
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
                else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            }
        }

        fun formatDuration(seconds: Long): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return "%02d:%02d".format(mins, secs)
        }
    }
}
