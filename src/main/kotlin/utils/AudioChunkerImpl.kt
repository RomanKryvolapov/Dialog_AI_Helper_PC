package utils

import backgroundThreadScope
import defaultApplicationInfo
import defaultThreadScope
import extensions.normalizeAndRemoveEmptyLines
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import javax.sound.sampled.*
import kotlin.math.sqrt

class AudioChunkerImpl(
    private val preferencesRepository: PreferencesRepository
) : AudioChunker {

    companion object {
        private const val SAMPLE_RATE = 16000.0f
        private const val CHUNK_MILLIS = 100
    }

    private val log = LoggerFactory.getLogger("AudioChunker")
    private var recognitionJob: Job? = null

    override val noiceLevelFlow = MutableStateFlow(0)

    override var onStopListener: (() -> Unit)? = null
    override var onStartListener: (() -> Unit)? = null
    override var onResultListener: ((String) -> Unit)? = null

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
            val format = AudioFormat(
                SAMPLE_RATE,
                16,
                1,
                true,
                false
            )
            val mixer = AudioSystem.getMixer(selectedMixerInfo)
            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, format)
            if (!mixer.isLineSupported(dataLineInfo)) {
                log.error("Run recognition but selected device does not support the required format.")
                return@launch
            }
            val line = mixer.getLine(dataLineInfo) as TargetDataLine
            line.open(format)
            line.start()
            val chunkSize = ((format.sampleRate * CHUNK_MILLIS / 1000).toInt()) * 2
            val buffer = ByteArray(chunkSize)
            val currentChunk = mutableListOf<ByteArray>()
            var chunkIndex = 0
            var speechDetected = false
            var lastVoiceTime = 0L
            var chunkStartTime = 0L
            while (isActive) {
                val bytesRead = line.read(buffer, 0, buffer.size)
                if (bytesRead <= 0) continue
                val amplitude = calculateRMS(buffer)
                noiceLevelFlow.value = amplitude
                val now = System.currentTimeMillis()
                if (amplitude > silenceThresholdPercents) {
                    currentChunk.add(buffer.copyOf())
                    if (!speechDetected) {
                        speechDetected = true
                        chunkStartTime = now
                        lastVoiceTime = now
                    } else {
                        lastVoiceTime = now
                    }
                } else {
                    if (speechDetected) {
                        currentChunk.add(buffer.copyOf())
                    }
                }
                if (speechDetected) {
                    val maxDurationReached = now - chunkStartTime > maxChunkDurationMilliseconds
                    val silenceReached = now - lastVoiceTime > maxSilenceMilliseconds
                    if (maxDurationReached || silenceReached) {
                        val chunkFile = File("cache/chunk_$chunkIndex.wav")
                        saveChunk(currentChunk, format, chunkFile)
                        chunkIndex++
                        currentChunk.clear()
                        if (silenceReached) {
                            speechDetected = false
                        } else {
                            chunkStartTime = now
                        }
                    }
                }
            }
        }
    }

    override fun inRunning() = recognitionJob?.isActive == true

    override fun stopListening() {
        log.debug("stop listening")
        recognitionJob?.cancel()
        onStopListener?.invoke()
    }

    private fun calculateRMS(audioData: ByteArray): Int {
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
            runFasterWhisper(outputFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun runFasterWhisper(wavFile: File) {
        defaultThreadScope.launch {
            log.debug("run whisper")
            try {
                val projectRoot = File("").absolutePath
                val scriptPath = "$projectRoot/python/transcribe.py"
                val processBuilder = ProcessBuilder(
                    "python",
                    "-Xutf8",
                    scriptPath,
                    wavFile.absolutePath,
                    modelPath
                ).apply {
                    directory(File(projectRoot))
                    redirectErrorStream(true)
                }
                val process = processBuilder.start()
                val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                val result = StringBuilder()
                var line: String?
                while (output.readLine().also { line = it } != null) {
                    result.appendLine(line)
                }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    log.error("Python script exited with code $exitCode")
                } else {
                    val text = result.toString().normalizeAndRemoveEmptyLines()
                    log.debug("text: $text")
                    if (text.isNotBlank()) {
                        onResultListener?.invoke(text)
                    }
                }
                wavFile.delete()
            } catch (e: Exception) {
                log.error("Exception during whisper execution", e)
            }
        }
    }

}
