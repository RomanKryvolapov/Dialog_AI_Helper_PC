package utils

import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.sound.sampled.Mixer

interface AudioChunker {

    val noiceLevelFlow: Flow<Int>

    /**
     * When true, the audio loop processes voice chunks (batch: saves WAV + fires
     * [onResultListener]; streaming: emits frames). When false, it only updates [noiceLevelFlow].
     */
    var chunkProcessingEnabled: Boolean

    /**
     * When true, the loop streams raw PCM frames via [onAudioFrame] and signals utterance
     * boundaries via [onUtteranceStart]/[onUtteranceEnd] instead of saving WAV files and
     * firing [onResultListener].
     */
    var streamingModeEnabled: Boolean

    var onRecordStart: (() -> Unit)?
    var onRecordStop: (() -> Unit)?
    var onStopListener: (() -> Unit)?
    var onStartListener: (() -> Unit)?
    var onResultListener: ((wavFile: File) -> Unit)?

    var onAudioFrame: ((pcm: ByteArray) -> Unit)?
    var onUtteranceStart: (() -> Unit)?
    var onUtteranceEnd: (() -> Unit)?

    fun startListening(selectedMixerInfo: Mixer.Info)
    fun stopListening()
    fun inRunning(): Boolean
    fun getAvailableInputDevices(): List<Mixer.Info>

}
