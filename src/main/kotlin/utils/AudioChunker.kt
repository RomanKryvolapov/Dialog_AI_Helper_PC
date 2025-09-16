package utils

import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.sound.sampled.Mixer

interface AudioChunker {

    val noiceLevelFlow: Flow<Int>

    var onRecordStart: (() -> Unit)?
    var onRecordStop: (() -> Unit)?
    var onStopListener: (() -> Unit)?
    var onStartListener: (() -> Unit)?
    var onResultListener: ((wavFile: File) -> Unit)?

    fun startListening(selectedMixerInfo: Mixer.Info)
    fun stopListening()
    fun inRunning(): Boolean

}