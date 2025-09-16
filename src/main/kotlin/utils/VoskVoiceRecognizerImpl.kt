package utils

import app.DialogApplication.Companion.ownerStage
import defaultThreadScope
import extensions.showAlert
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.charset.Charset
import javax.sound.sampled.*

class VoskVoiceRecognizerImpl : VoskVoiceRecognizer {

    private val log = LoggerFactory.getLogger("VoskVoiceRecognizer")

    companion object {
        private const val SAMPLE_RATE = 16000.0f
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null

    override var onInitInProcess: (() -> Unit)? = null
    override var onInitReady: (() -> Unit)? = null
    override var onStopListener: (() -> Unit)? = null
    override var onStartListener: (() -> Unit)? = null
    override var onErrorListener: ((String) -> Unit)? = null
    override var onResultListener: ((String) -> Unit)? = null
    override var onPartialResultListener: ((String) -> Unit)? = null
    override var onCloseReady: (() -> Unit)? = null

    private var initJob: Job? = null
    private var recognitionJob: Job? = null

    override fun init(voskModelPath: String) {
        log.debug("Init with model path: $voskModelPath")
        initJob?.cancel()
        initJob = defaultThreadScope.launch {
            onInitInProcess?.invoke()
            try {
                model?.close()
                model = Model(voskModelPath)
                recognizer?.close()
                recognizer = Recognizer(model, SAMPLE_RATE)
                onInitReady?.invoke()
                log.debug("init ready")
            } catch (e: Exception) {
                onErrorListener?.invoke(e.message ?: "Error")
                e.printStackTrace()
                log.error("init error: ${e.message}")
            }
        }
    }

    override fun runRecognition(selectedMixerInfo: Mixer.Info) {
        log.debug("Run recognition")
        println("Default charset: ${Charset.defaultCharset()}")
        println("file.encoding: ${Charset.defaultCharset().displayName()}")
        if (initJob?.isActive == true) {
            log.error("Run recognition but init is running, wait")
            ownerStage?.showAlert(
                alertTitle = "Recognizer error",
                alertContent = "Please wait for the initialization to complete."
            )
            return
        }
        recognitionJob?.cancel()
        recognitionJob = defaultThreadScope.launch {
            val recognizer = recognizer
            if (recognizer == null) {
                log.error("Run recognition but recognizer is null")
                onErrorListener?.invoke("Recognizer is null")
                return@launch
            }
            onStartListener?.invoke()
            var microphone: TargetDataLine? = null
            try {
                val audioFormat = AudioFormat(
                    SAMPLE_RATE,
                    16,
                    1,
                    true,
                    false
                )
                val selectedMixer = AudioSystem.getMixer(selectedMixerInfo)
                val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

                if (!selectedMixer.isLineSupported(info)) {
                    log.error("Run recognition but selected device does not support the required format.")
                    onErrorListener?.invoke("Selected device does not support the required format.")
                    return@launch
                }

                microphone = selectedMixer.getLine(info) as TargetDataLine
                if (microphone.isOpen) {
                    microphone.stop()
                    microphone.close()
                }
                microphone.open(audioFormat)
                microphone.start()

                val buffer = ByteArray(4096)

                while (isActive) {
                    val bytesRead = microphone.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            val result = recognizer.result
                            log.debug("Vosk result: $result")
                            onResultListener?.invoke(result)
                        } else {
                            val result = recognizer.partialResult
                            log.debug("Vosk partial result: $result")
                            onPartialResultListener?.invoke(result)
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Run recognition error: ${e.message}")
                e.printStackTrace()
                onErrorListener?.invoke(e.message ?: "")
            } finally {
                microphone?.stop()
                microphone?.close()
                onStopListener?.invoke()
            }
        }
    }

    override fun stopRecognition() {
        log.debug("Stop recognition")
        val job = recognitionJob
        if (job == null || !job.isActive) {
            log.warn("Recognition job is not active")
            onStopListener?.invoke()
            return
        }
        recognitionJob?.cancel()
        defaultThreadScope.launch {
            job.join()
            recognizer?.let {
                runCatching {
                    val result = recognizer?.finalResult
                    if (result != null && result.isNotBlank() && result != "{\"text\": \"\"}") {
                        onResultListener?.invoke(result)
                    }
                }.onFailure {
                    log.error("Stop recognition error getting final result: ${it.message}")
                }
            }
            onStopListener?.invoke()
        }
    }

    override fun close() {
        log.debug("Close")
        recognitionJob?.cancel()
        recognitionJob = null
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        onCloseReady?.invoke()
    }

    override fun inInit() = initJob?.isActive == true

    override fun inRunning() = initJob?.isActive == true || recognitionJob?.isActive == true

    override fun splitUtterance() {
        log.debug("Split utterance")
        if (initJob?.isActive == true || recognitionJob?.isActive != true) {
            log.error("splitUtterance called while recognizer is not ready")
            return
        }
        val recognizer = recognizer ?: return
        defaultThreadScope.launch {
            try {
                val partial = recognizer.partialResult
                if (partial.isNotBlank() && partial != "{}") {
                    onResultListener?.invoke(partial)
                }
                recognizer.reset()
            } catch (e: Exception) {
                log.error("splitUtterance error: ${e.message}")
                onErrorListener?.invoke("Failed to split utterance: ${e.message}")
            }
        }
    }

    override fun getAvailableInputDevices(): List<Mixer.Info> {
        val mixers = AudioSystem.getMixerInfo()
        return mixers.filter {
            AudioSystem.getMixer(it).targetLineInfo.any { info -> info.lineClass == TargetDataLine::class.java }
        }
    }

}
