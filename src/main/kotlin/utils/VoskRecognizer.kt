package utils

import defaultThreadScope
import extensions.showAlert
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.vosk.Recognizer
import org.vosk.Model
import javax.sound.sampled.*

object VoskRecognizer {

    private val log = LoggerFactory.getLogger("VoskRecognizerTag")

    private const val SAMPLE_RATE = 16000.0f
    private const val MODEL_PATH = "model/vosk-model-en-us-0.22"

    private var model: Model? = null
    private var recognizer: Recognizer? = null

    var onInitReady: (() -> Unit)? = null
    var onStopListener: (() -> Unit)? = null
    var onStartListener: (() -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null
    var onResultListener: ((String) -> Unit)? = null
    var onPartialResultListener: ((String) -> Unit)? = null

    private var initJob: Job? = null
    private var recognitionJob: Job? = null

    fun init() {
        log.debug("init")
        initJob?.cancel()
        initJob = defaultThreadScope.launch {
            try {
                model?.close()
                model = Model(MODEL_PATH)
                recognizer?.close()
                recognizer = Recognizer(model, SAMPLE_RATE)
                onInitReady?.invoke()
            } catch (e: Exception) {
                onErrorListener?.invoke(e.message ?: "Error")
                e.printStackTrace()
                log.error("init error: ${e.message}")
            }
        }
    }

    fun runRecognition(selectedMixerInfo: Mixer.Info) {
        log.debug("runRecognition")
        if (initJob?.isActive == true) {
            log.error("runRecognition init is running")
            showAlert(
                alertTitle = "Recognizer error",
                alertContent = "Please wait for the initialization to complete."
            )
            return
        }
        recognitionJob?.cancel()
        recognitionJob = defaultThreadScope.launch {
            val recognizer = recognizer
            if (recognizer == null) {
                log.error("runRecognition recognizer is null")
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
                    log.error("runRecognition selected device does not support the required format.")
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
                            onResultListener?.invoke(recognizer.result)
                        } else {
                            onPartialResultListener?.invoke(recognizer.partialResult)
                        }
                    }
                }

            } catch (e: Exception) {
                log.error("runRecognition error: ${e.message}")
                e.printStackTrace()
                onErrorListener?.invoke(e.message ?: "")
            } finally {
                microphone?.stop()
                microphone?.close()
                onStopListener?.invoke()
            }
        }
    }

    fun stopRecognition() {
        log.debug("stopRecognition")
        recognizer?.let {
            runCatching {
                onResultListener?.invoke(it.finalResult)
            }
        }
        onStopListener?.invoke()
    }

    fun close() {
        log.debug("close")
        recognitionJob?.cancel()
        recognitionJob = null
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
    }

    fun inInit() = initJob?.isActive == true

    fun inRunning() = initJob?.isActive == true || recognitionJob?.isActive == true


    fun splitUtterance() {
        log.debug("splitUtterance")
        if (initJob?.isActive == true) {
            log.error("runRecognition init is running")
            showAlert(
                alertTitle = "Recognizer error",
                alertContent = "Please wait for the initialization to complete."
            )
            return
        }
        if (recognitionJob?.isActive != true) {
            log.error("runRecognition recognition is not running")
            showAlert(
                alertTitle = "Recognizer error",
                alertContent = "Please first run recognition"
            )
            return
        }
        val recognizer = recognizer ?: return
        defaultThreadScope.launch {
            val partial = recognizer.partialResult
            if (partial.isNotBlank()) onResultListener?.invoke(partial)
            recognizer.reset()
        }
    }

    fun getAvailableInputDevices(): List<Mixer.Info> {
        val mixers = AudioSystem.getMixerInfo()
        return mixers.filter {
            AudioSystem.getMixer(it).targetLineInfo.any { info -> info.lineClass == TargetDataLine::class.java }
        }
    }

}
