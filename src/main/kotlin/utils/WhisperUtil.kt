package utils

import java.io.File

interface WhisperUtil {

    var onResultListener: ((String) -> Unit)?

    /** Called when a chunk did not produce a result (process crashed, empty text, cancelled, limit-evicted). */
    var onFailureListener: (() -> Unit)?

    fun runFasterWhisper(wavFile: File, language: String)

}