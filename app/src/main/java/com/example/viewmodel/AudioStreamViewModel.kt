package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioStreamConfig
import com.example.audio.AudioStreamManager
import com.example.audio.ConnectionStatus
import com.example.audio.StreamMetrics
import com.example.audio.StreamProtocol
import com.example.audio.TranscriptItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AudioStreamViewModel : ViewModel() {

    private val audioStreamManager = AudioStreamManager()

    private val _config = MutableStateFlow(AudioStreamConfig())
    val config: StateFlow<AudioStreamConfig> = _config.asStateFlow()

    val metrics: StateFlow<StreamMetrics> = audioStreamManager.metrics
    val transcripts: StateFlow<List<TranscriptItem>> = audioStreamManager.transcripts

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    fun updateServerUrl(url: String) {
        _config.update { it.copy(serverUrl = url.trim()) }
    }

    fun updateProtocol(protocol: StreamProtocol) {
        _config.update { it.copy(protocol = protocol) }
    }

    fun updateSampleRate(rate: Int) {
        _config.update { it.copy(sampleRate = rate) }
    }

    fun updateChunkDuration(ms: Int) {
        _config.update { it.copy(chunkDurationMs = ms) }
    }

    fun toggleHandshake(enabled: Boolean) {
        _config.update { it.copy(sendHandshake = enabled) }
    }

    fun setShowSettings(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun toggleStreaming() {
        val currentMetrics = metrics.value
        if (currentMetrics.isStreaming) {
            audioStreamManager.stopStreaming()
        } else {
            audioStreamManager.startStreaming(_config.value)
        }
    }

    fun clearTranscripts() {
        audioStreamManager.clearTranscripts()
    }

    fun addManualNote(text: String) {
        audioStreamManager.appendTranscript(
            TranscriptItem(
                text = text,
                isFinal = true,
                isSystemEvent = false
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioStreamManager.stopStreaming()
    }
}
