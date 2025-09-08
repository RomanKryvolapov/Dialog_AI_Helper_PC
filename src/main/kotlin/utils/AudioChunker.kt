package utils

import kotlinx.coroutines.flow.Flow
import javax.sound.sampled.Mixer

interface AudioChunker {

    val noiceLevelFlow: Flow<Int>
    var onStopListener: (() -> Unit)?
    var onStartListener: (() -> Unit)?
    var onResultListener: ((String) -> Unit)?

    fun startListening(selectedMixerInfo: Mixer.Info)
    fun stopListening()
    fun inRunning(): Boolean

}