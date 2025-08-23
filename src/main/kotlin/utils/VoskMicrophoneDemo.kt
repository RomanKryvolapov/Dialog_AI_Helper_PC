package utils

import org.vosk.Recognizer
import org.vosk.Model
import javax.sound.sampled.*
import java.util.concurrent.atomic.AtomicBoolean

object VoskMicrophoneDemo {

    private var recognitionThread: Thread? = null
    private val running = AtomicBoolean(false)
    private var microphone: TargetDataLine? = null

    var onStartListener: (() -> Unit)? = null
    var onStopListener: (() -> Unit)? = null
    var onErrorListener: ((Exception) -> Unit)? = null
    var onResultListener: ((String) -> Unit)? = null
    var onPartialResultListener: ((String) -> Unit)? = null

    fun runRecognition(selectedMixerInfo: Mixer.Info) {
        if (running.get()) return

        running.set(true)
        recognitionThread = Thread {
            try {
                onStartListener?.invoke()

                val sampleRate = 16000f
                val audioFormat = AudioFormat(sampleRate, 16, 1, true, false)
                val selectedMixer = AudioSystem.getMixer(selectedMixerInfo)
                val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

                if (!selectedMixer.isLineSupported(info)) {
                    throw IllegalStateException("Selected device does not support the required format.")
                }

                microphone = selectedMixer.getLine(info) as TargetDataLine
                microphone?.open(audioFormat)
                microphone?.start()

                val modelPath = "model/vosk-model-en-us-0.22-lgraph"
                val model = Model(modelPath)
                val recognizer = Recognizer(model, sampleRate)
                val buffer = ByteArray(4096)

                while (running.get()) {
                    val bytesRead = microphone?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            onResultListener?.invoke(recognizer.result)
                        } else {
                            onPartialResultListener?.invoke(recognizer.partialResult)
                        }
                    }
                }

            } catch (e: Exception) {
                onErrorListener?.invoke(e)
                e.printStackTrace()
            } finally {
                microphone?.stop()
                microphone?.close()
                microphone = null
                running.set(false)
                onStopListener?.invoke()
            }
        }
        recognitionThread?.start()
    }

    fun stopRecognition() {
        running.set(false)
        recognitionThread?.join()
        recognitionThread = null
    }

    fun isRunning(): Boolean = running.get()

}
