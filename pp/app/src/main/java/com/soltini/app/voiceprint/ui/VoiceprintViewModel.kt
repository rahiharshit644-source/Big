package com.soltini.app.voiceprint.ui

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soltini.app.settings.AppSettings
import com.soltini.app.voiceprint.data.VoiceProfileEntity
import com.soltini.app.voiceprint.data.VoiceprintRepository
import com.soltini.app.voiceprint.domain.EnrollmentManager
import com.soltini.app.voiceprint.domain.VoiceprintVerifier
import com.soltini.app.voiceprint.gate.VoiceGateMode
import com.soltini.app.voiceprint.ml.OnnxSpeakerEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * VoiceprintViewModel
 *
 * ViewModel driving VoicePrint settings, enrollment, live testing, and threshold tuning.
 */
class VoiceprintViewModel(application: Application) : AndroidViewModel(application) {

    private val appSettings = AppSettings(application)
    val repository = VoiceprintRepository.getInstance(application)
    val embedder = OnnxSpeakerEmbedder(application)
    val enrollmentManager = EnrollmentManager(embedder, repository)
    val verifier = VoiceprintVerifier(embedder, repository)

    val activeProfile: StateFlow<VoiceProfileEntity?> = repository.activeProfileFlow
        .catch { e ->
            android.util.Log.e("VoiceprintViewModel", "activeProfileFlow failed: ${e.message}", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _gateMode = MutableStateFlow(VoiceGateMode.fromString(appSettings.voiceGateMode))
    val gateMode: StateFlow<VoiceGateMode> = _gateMode.asStateFlow()

    private val _sensitivityOffset = MutableStateFlow(appSettings.voiceprintSensitivityOffset)
    val sensitivityOffset: StateFlow<Float> = _sensitivityOffset.asStateFlow()

    private val _modelStatus = MutableStateFlow("Initializing...")
    val modelStatus: StateFlow<String> = _modelStatus.asStateFlow()

    private val _isModelAvailable = MutableStateFlow(false)
    val isModelAvailable: StateFlow<Boolean> = _isModelAvailable.asStateFlow()

    // Live audio recording state for enrollment & test panel
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveInputLevel = MutableStateFlow(0f)
    val liveInputLevel: StateFlow<Float> = _liveInputLevel.asStateFlow()

    private val _testResult = MutableStateFlow<VoiceprintVerifier.VerificationState?>(null)
    val testResult: StateFlow<VoiceprintVerifier.VerificationState?> = _testResult.asStateFlow()

    private var audioRecordJob: Job? = null
    private var recordedAudioBuffer = ByteArrayOutputStream()

    init {
        viewModelScope.launch {
            try {
                val available = embedder.initialize()
                _isModelAvailable.value = available
                _modelStatus.value = embedder.statusDescription
                verifier.reloadProfile()
                verifier.sensitivityOffset = _sensitivityOffset.value
            } catch (e: Exception) {
                android.util.Log.e("VoiceprintViewModel", "init failed: ${e.message}", e)
                _isModelAvailable.value = false
                _modelStatus.value = "Voice ID init error: ${e.message}"
            }
        }
    }

    fun setGateMode(mode: VoiceGateMode) {
        _gateMode.value = mode
        appSettings.voiceGateMode = mode.name
    }

    fun setSensitivityOffset(offset: Float) {
        _sensitivityOffset.value = offset
        appSettings.voiceprintSensitivityOffset = offset
        verifier.sensitivityOffset = offset
        viewModelScope.launch {
            verifier.reloadProfile()
        }
    }

    fun deleteProfile(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteProfile()
            verifier.reloadProfile()
            enrollmentManager.reset()
            _testResult.value = null
            withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
    }

    // ─── Audio Recording for Enrollment & Test Panel ─────────────────────────

    @SuppressLint("MissingPermission")
    fun startRecording(forTestPanel: Boolean = false) {
        if (_isRecording.value) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                getApplication(), android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _modelStatus.value = "Microphone permission missing - grant it in Settings > Permissions & Agent"
            return
        }
        recordedAudioBuffer.reset()
        _isRecording.value = true

        audioRecordJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = max(minBuf, sampleRate / 5)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    recorder = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                }

                recorder.startRecording()
                val chunk = ByteArray(1024)

                while (isActive && _isRecording.value) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        recordedAudioBuffer.write(chunk, 0, read)

                        // Compute peak amplitude for level meter
                        var maxSample = 0
                        var i = 0
                        while (i < read - 1) {
                            val sample = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
                            val s = sample.toShort()
                            if (abs(s.toInt()) > maxSample) {
                                maxSample = abs(s.toInt())
                            }
                            i += 2
                        }
                        _liveInputLevel.value = (maxSample / 32768.0f).coerceIn(0f, 1f)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceprintViewModel", "Recording failed: ${e.message}", e)
                _isRecording.value = false
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
                _liveInputLevel.value = 0f
            }
        }
    }

    fun stopRecordingAndProcessPhrase() {
        if (!_isRecording.value) return
        _isRecording.value = false
        audioRecordJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            val bytes = recordedAudioBuffer.toByteArray()
            recordedAudioBuffer.reset()
            val shorts = bytesToShorts(bytes)
            try {
                enrollmentManager.processAudioSample(shorts)
                verifier.reloadProfile()
            } catch (e: Exception) {
                android.util.Log.e("VoiceprintViewModel", "Enrollment step failed: ${e.message}", e)
            }
        }
    }

    fun stopRecordingAndTest() {
        if (!_isRecording.value) return
        _isRecording.value = false
        audioRecordJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            val bytes = recordedAudioBuffer.toByteArray()
            recordedAudioBuffer.reset()
            val shorts = bytesToShorts(bytes)
            try {
                _testResult.value = verifier.verifyUtterance(shorts)
            } catch (e: Exception) {
                android.util.Log.e("VoiceprintViewModel", "Test verification failed: ${e.message}", e)
                _testResult.value = VoiceprintVerifier.VerificationState()
            }
        }
    }

    fun cancelRecording() {
        _isRecording.value = false
        audioRecordJob?.cancel()
        recordedAudioBuffer.reset()
        _liveInputLevel.value = 0f
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val count = bytes.size / 2
        val shorts = ShortArray(count)
        var b = 0
        for (i in 0 until count) {
            val b0 = bytes[b].toInt() and 0xFF
            val b1 = bytes[b + 1].toInt()
            shorts[i] = ((b1 shl 8) or b0).toShort()
            b += 2
        }
        return shorts
    }

    override fun onCleared() {
        super.onCleared()
        audioRecordJob?.cancel()
        embedder.close()
    }
}
