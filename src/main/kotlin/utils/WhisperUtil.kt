package utils

import java.io.File

interface WhisperUtil {

    var onResultListener: ((String) -> Unit)?

    /**
     * Called when a chunk produced no text: `whisperFailed` is true when Whisper itself failed (no Python,
     * the process crashed, was cancelled or evicted by the concurrency limit), false when it heard nothing.
     */
    var onFailureListener: ((whisperFailed: Boolean) -> Unit)?

    fun runFasterWhisper(wavFile: File, language: String)

    /**
     * One recorded phrase transcribed on its own, outside the listener flow — for a phrase a voice method
     * could not translate that falls through to a text method. The text, or null when Whisper failed or
     * heard nothing.
     */
    suspend fun transcribe(wav: ByteArray, language: String): String?

}