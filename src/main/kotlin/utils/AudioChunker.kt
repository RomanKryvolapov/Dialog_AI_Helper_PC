package utils

import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.sound.sampled.Mixer

interface AudioChunker {

    val noiceLevelFlow: Flow<Int>

    /**
     * When true, the audio loop saves voice chunks to WAV files and
     * fires [onResultListener]. When false, it only updates [noiceLevelFlow].
     */
    var chunkProcessingEnabled: Boolean

    var onRecordStart: (() -> Unit)?
    var onRecordStop: (() -> Unit)?
    var onStopListener: (() -> Unit)?
    var onStartListener: (() -> Unit)?
    var onResultListener: ((wavFile: File) -> Unit)?

    fun startListening(selectedMixerInfo: Mixer.Info)
    fun stopListening()
    fun inRunning(): Boolean
    fun getAvailableInputDevices(): List<Mixer.Info>

}
