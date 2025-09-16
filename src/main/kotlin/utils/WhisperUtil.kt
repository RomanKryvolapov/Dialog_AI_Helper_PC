package utils

import java.io.File

interface WhisperUtil {

    var onResultListener: ((String) -> Unit)?

    fun runFasterWhisper(wavFile: File, language: String)

}