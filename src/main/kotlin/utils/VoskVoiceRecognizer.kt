package utils

import javax.sound.sampled.Mixer

interface VoskVoiceRecognizer {

    var onInitInProcess: (() -> Unit)?
    var onInitReady: (() -> Unit)?
    var onStopListener: (() -> Unit)?
    var onStartListener: (() -> Unit)?
    var onErrorListener: ((String) -> Unit)?
    var onResultListener: ((String) -> Unit)?
    var onPartialResultListener: ((String) -> Unit)?

    fun init(voskModelPath: String)
    fun runRecognition(selectedMixerInfo: Mixer.Info)
    fun stopRecognition()
    fun close()
    fun inInit(): Boolean
    fun inRunning(): Boolean
    fun splitUtterance()
    fun getAvailableInputDevices(): List<Mixer.Info>
    fun selectModelFolder()

}