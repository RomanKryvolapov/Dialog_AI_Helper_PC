package utils

import backgroundThreadScope
import defaultApplicationInfo
import defaultThreadScope
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
    private val preferencesRepository: PreferencesRepository
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
            false
        )
        private val chunkSize = ((format.sampleRate * CHUNK_MILLIS / 1000).toInt()) * 2
    }

    private val log = LoggerFactory.getLogger("AudioChunker")
    private var recognitionJob: Job? = null

    override val noiceLevelFlow = MutableStateFlow(0)

    override var onStopListener: (() -> Unit)? = null
    override var onStartListener: (() -> Unit)? = null

    override var onRecordStart: (() -> Unit)? = null
    override var onRecordStop: (() -> Unit)? = null

    override var onResultListener: ((wavFile: File) -> Unit)? = null

    private var silenceThresholdPercents = defaultApplicationInfo.whisperModelConfig.silenceThresholdPercents
    private var maxSilenceMilliseconds = defaultApplicationInfo.whisperModelConfig.maxSilenceMilliseconds
    private var maxChunkDurationMilliseconds = defaultApplicationInfo.whisperModelConfig.maxChunkDurationMilliseconds
    private var modelPath = ""

    init {
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow.collect { appInfo ->
                modelPath = appInfo.whisperModelPath
                silenceThresholdPercents = appInfo.whisperModelConfig.silenceThresholdPercents
                maxSilenceMilliseconds = appInfo.whisperModelConfig.maxSilenceMilliseconds
                maxChunkDurationMilliseconds = appInfo.whisperModelConfig.maxChunkDurationMilliseconds
            }
        }
    }

    override fun startListening(selectedMixerInfo: Mixer.Info) {
        log.debug("start listening")
        recognitionJob?.cancel()
        recognitionJob = defaultThreadScope.launch {
            onStartListener?.invoke()

            val appInfo = preferencesRepository.getAppInfo()
            modelPath = appInfo.whisperModelPath
            silenceThresholdPercents = appInfo.whisperModelConfig.silenceThresholdPercents
            maxSilenceMilliseconds = appInfo.whisperModelConfig.maxSilenceMilliseconds
            maxChunkDurationMilliseconds = appInfo.whisperModelConfig.maxChunkDurationMilliseconds

            val mixer = AudioSystem.getMixer(selectedMixerInfo)
            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, format)
            if (!mixer.isLineSupported(dataLineInfo)) {
                log.error("Run recognition but selected device does not support the required format.")
                return@launch
            }

            var line: TargetDataLine? = null

            try {
                line = mixer.getLine(dataLineInfo) as TargetDataLine
                line.open(format)
                line.start()

                val buffer = ByteArray(chunkSize)
                var isRecording = false
                val currentChunk = mutableListOf<ByteArray>()
                var recordingStartTime: Instant? = null
                var lastSoundTime: Instant = Instant.now()
                var chunkIndex = 0

                while (isActive) {
                    val bytesRead = line.read(buffer, 0, buffer.size)
                    if (bytesRead <= 0) continue

                    val amplitude = calculateVoiceLevel(buffer)
                    noiceLevelFlow.value = amplitude

                    val currentTime = Instant.now()

                    if (!isRecording && amplitude > silenceThresholdPercents) {
                        onRecordStart?.invoke()
                        isRecording = true
                        recordingStartTime = currentTime
                        currentChunk.add(buffer.copyOf())
                        lastSoundTime = currentTime
                    } else if (isRecording) {
                        currentChunk.add(buffer.copyOf())

                        if (amplitude > silenceThresholdPercents) {
                            lastSoundTime = currentTime
                        }

                        val silenceDuration = Duration.between(lastSoundTime, currentTime).toMillis()
                        val recordingDuration = Duration.between(recordingStartTime, currentTime).toMillis()

                        val shouldStopDueToSilence = silenceDuration > maxSilenceMilliseconds
                        val shouldStopDueToMaxDuration = recordingDuration >= maxChunkDurationMilliseconds

                        if (shouldStopDueToSilence || shouldStopDueToMaxDuration) {
                            if (recordingDuration >= MIN_CHUNK_DURATION_MILLISECONDS) {
                                val chunkFile = File("cache/chunk_$chunkIndex.wav")
                                chunkIndex++
                                saveChunk(currentChunk, format, chunkFile)
                            }

                            currentChunk.clear()
                            isRecording = false
                            recordingStartTime = null
                            onRecordStop?.invoke()

                            if (shouldStopDueToMaxDuration) {
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

    override fun inRunning() = recognitionJob?.isActive == true

    override fun stopListening() {
        log.debug("stop listening")
        recognitionJob?.cancel()
        onStopListener?.invoke()
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
            e.printStackTrace()
        }
    }

}
