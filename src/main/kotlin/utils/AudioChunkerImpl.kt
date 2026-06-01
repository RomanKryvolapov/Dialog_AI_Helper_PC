package utils

import backgroundThreadScope
import defaultApplicationInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.sound.sampled.*
import kotlin.math.sqrt

class AudioChunkerImpl(
    private val preferencesRepository: PreferencesRepository,
) : AudioChunker {

    companion object {
        private const val SAMPLE_RATE = 16000.0f
        private const val CHUNK_MILLIS = 100
        private const val MIN_CHUNK_DURATION_MILLISECONDS = 500L
        private val format = AudioFormat(
            SAMPLE_RATE,
            16,
            1,
            true,
            false,
        )
        private val chunkSize = ((format.sampleRate * CHUNK_MILLIS / 1000).toInt()) * 2
    }

    private val log = LoggerFactory.getLogger("AudioChunker")
    private var recognitionJob: Job? = null

    override val noiceLevelFlow = MutableStateFlow(0)

    @Volatile
    override var chunkProcessingEnabled: Boolean = false

    override var onStopListener: (() -> Unit)? = null
    override var onStartListener: (() -> Unit)? = null
    override var onRecordStart: (() -> Unit)? = null
    override var onRecordStop: (() -> Unit)? = null
    override var onResultListener: ((wavFile: File) -> Unit)? = null

    override var streamingModeEnabled: Boolean = false

    override var onAudioFrame: ((pcm: ByteArray) -> Unit)? = null
    override var onUtteranceStart: (() -> Unit)? = null
    override var onUtteranceEnd: (() -> Unit)? = null

    @Volatile
    private var silenceThresholdPercents = defaultApplicationInfo.whisperModelConfig.silenceThresholdPercents

    @Volatile
    private var maxSilenceMilliseconds = defaultApplicationInfo.whisperModelConfig.maxSilenceMilliseconds

    @Volatile
    private var maxChunkDurationMilliseconds = defaultApplicationInfo.whisperModelConfig.maxChunkDurationMilliseconds

    init {
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow.collect { appInfo ->
                silenceThresholdPercents = appInfo.whisperModelConfig.silenceThresholdPercents
                maxSilenceMilliseconds = appInfo.whisperModelConfig.maxSilenceMilliseconds
                maxChunkDurationMilliseconds = appInfo.whisperModelConfig.maxChunkDurationMilliseconds
            }
        }
    }

    override fun startListening(selectedMixerInfo: Mixer.Info) {
        log.debug("start listening on device: ${selectedMixerInfo.name}")
        recognitionJob?.cancel()
        recognitionJob = backgroundThreadScope.launch {
            onStartListener?.invoke()

            val mixer = AudioSystem.getMixer(selectedMixerInfo)
            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, format)
            if (!mixer.isLineSupported(dataLineInfo)) {
                log.error("Selected device does not support the required format.")
                onStopListener?.invoke()
                return@launch
            }

            var line: TargetDataLine? = null

            try {
                line = mixer.getLine(dataLineInfo) as TargetDataLine
                line.open(format)
                line.start()
                log.debug("line opened, chunkSize=$chunkSize, format=$format")

                val buffer = ByteArray(chunkSize)
                var isRecording = false
                val currentChunk = mutableListOf<ByteArray>()
                var recordingStartTime: Instant? = null
                var lastSoundTime: Instant = Instant.now()
                var chunkIndex = 0

                while (isActive) {
                    val bytesRead = line.read(buffer, 0, buffer.size)
                    if (bytesRead <= 0) {
                        log.warn("line.read returned $bytesRead")
                        continue
                    }

                    val amplitude = calculateVoiceLevel(buffer)
                    noiceLevelFlow.value = amplitude

                    if (!chunkProcessingEnabled) {
                        if (isRecording) {
                            if (streamingModeEnabled) onUtteranceEnd?.invoke()
                            currentChunk.clear()
                            isRecording = false
                            recordingStartTime = null
                            onRecordStop?.invoke()
                        }
                        continue
                    }

                    val currentTime = Instant.now()

                    if (!isRecording && amplitude > silenceThresholdPercents) {
                        onRecordStart?.invoke()
                        isRecording = true
                        recordingStartTime = currentTime
                        lastSoundTime = currentTime
                        if (streamingModeEnabled) {
                            onAudioFrame?.invoke(buffer.copyOf())
                        } else {
                            currentChunk.add(buffer.copyOf())
                        }
                    } else if (isRecording) {
                        if (streamingModeEnabled) {
                            onAudioFrame?.invoke(buffer.copyOf())
                        } else {
                            currentChunk.add(buffer.copyOf())
                        }

                        if (amplitude > silenceThresholdPercents) {
                            lastSoundTime = currentTime
                        }

                        val silenceDuration = Duration.between(lastSoundTime, currentTime).toMillis()
                        val recordingDuration = Duration.between(recordingStartTime, currentTime).toMillis()

                        val shouldStopDueToSilence = silenceDuration > maxSilenceMilliseconds
                        val shouldStopDueToMaxDuration = recordingDuration >= maxChunkDurationMilliseconds

                        if (streamingModeEnabled) {
                            // Streaming: Whisper (Python side) segments by sentence and bounds the
                            // buffer itself, so we only flush the trailing phrase on a real pause.
                            if (shouldStopDueToSilence) {
                                onUtteranceEnd?.invoke()
                                isRecording = false
                                recordingStartTime = null
                                onRecordStop?.invoke()
                            }
                        } else if (shouldStopDueToSilence || shouldStopDueToMaxDuration) {
                            if (recordingDuration >= MIN_CHUNK_DURATION_MILLISECONDS) {
                                val chunkFile = File("cache/chunk_$chunkIndex.wav")
                                chunkIndex++
                                saveChunk(currentChunk, format, chunkFile)
                            }

                            currentChunk.clear()
                            isRecording = false
                            recordingStartTime = null
                            onRecordStop?.invoke()

                            if (shouldStopDueToMaxDuration && chunkProcessingEnabled) {
                                onRecordStart?.invoke()
                                isRecording = true
                                recordingStartTime = currentTime
                                currentChunk.add(buffer.copyOf())
                                lastSoundTime = currentTime
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                log.error("Audio capture error: ${e.message}", e)
            } finally {
                try {
                    line?.stop()
                    line?.close()
                    log.debug("Microphone closed")
                } catch (e: Exception) {
                    log.warn("Error while closing microphone: ${e.message}", e)
                }
                noiceLevelFlow.value = 0
                onRecordStop?.invoke()
                onStopListener?.invoke()
            }
        }
    }

    override fun stopListening() {
        log.debug("stop listening")
        recognitionJob?.cancel()
        recognitionJob = null
    }

    override fun inRunning() = recognitionJob?.isActive == true

    override fun getAvailableInputDevices(): List<Mixer.Info> {
        val mixers = AudioSystem.getMixerInfo()
        return mixers.filter {
            AudioSystem.getMixer(it).targetLineInfo.any { info -> info.lineClass == TargetDataLine::class.java }
        }
    }

    private fun calculateVoiceLevel(audioData: ByteArray): Int {
        var sum = 0L
        var count = 0
        for (i in audioData.indices step 2) {
            if (i + 1 >= audioData.size) break
            val low = audioData[i].toInt() and 0xFF
            val high = audioData[i + 1].toInt()
            val sample = (high shl 8) or low
            val signedSample = if (sample and 0x8000 != 0) sample or -0x10000 else sample
            sum += signedSample * signedSample
            count++
        }
        if (count == 0) return 0
        val rms = sqrt(sum / count.toDouble())
        val maxRms = 3000.0
        val scaled = (rms / maxRms * 100).coerceIn(0.0, 100.0)
        return scaled.toInt()
    }

    private fun saveChunk(chunks: List<ByteArray>, format: AudioFormat, outputFile: File) {
        log.debug("save chunks")
        try {
            val output = ByteArrayOutputStream()
            chunks.forEach { output.write(it) }
            val audioBytes = output.toByteArray()
            val bais = ByteArrayInputStream(audioBytes)
            val audioStream = AudioInputStream(bais, format, (audioBytes.size / format.frameSize).toLong())
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile)
            onResultListener?.invoke(outputFile)
        } catch (e: IOException) {
            log.error("Failed to save chunk: ${e.message}", e)
        }
    }

}
