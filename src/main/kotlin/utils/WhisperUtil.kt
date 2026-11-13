package utils

import java.io.File

interface WhisperUtil {

    /**
     * One phrase recorded at pauses ([wavFile], deleted afterwards), transcribed by the one-shot script.
     * Each call answers for its own phrase, so the result goes to that phrase however the runs overlap.
     */
    suspend fun recognize(wavFile: File, language: String): WhisperResult

    /**
     * Like [recognize] for a phrase held in memory — one a voice method could not translate that falls
     * through to a text method. The text, or null when Whisper failed or heard nothing.
     */
    suspend fun transcribe(wav: ByteArray, language: String): WhisperResult

}

/** What Whisper made of one phrase. */
sealed interface WhisperResult {
    data class Heard(val text: String) : WhisperResult

    /** It ran and heard no speech. */
    data object Silence : WhisperResult

    /** Whisper itself failed: no Python, the process did not start, crashed, or was evicted by the concurrency limit. */
    data object Failed : WhisperResult
}
