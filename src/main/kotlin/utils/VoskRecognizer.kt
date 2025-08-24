package utils

import models.domain.VoskModels
import javax.sound.sampled.Mixer

interface VoskRecognizer {

    var onInitReady: (() -> Unit)?
    var onStopListener: (() -> Unit)?
    var onStartListener: (() -> Unit)?
    var onErrorListener: ((String) -> Unit)?
    var onResultListener: ((String) -> Unit)?
    var onPartialResultListener: ((String) -> Unit)?

    fun init(voskModel: VoskModels)
    fun runRecognition(selectedMixerInfo: Mixer.Info)
    fun stopRecognition()
    fun close()
    fun inInit(): Boolean
    fun inRunning(): Boolean
    fun splitUtterance()
    fun getAvailableInputDevices(): List<Mixer.Info>

}