package utils

import models.domain.SpeechmaticsConfig

/**
 * Realtime speech-to-text in the Speechmatics cloud, over a WebSocket. Audio goes in only while someone speaks
 * ([feedAudio], 16 kHz 16-bit mono like [AudioChunker]); at a pause [flush] asks the service to settle the phrase.
 * Partials and finished phrases come back, each phrase with its speaker label when speaker labels are on.
 */
interface SpeechmaticsRecognizer {

    var onPartialListener: ((String) -> Unit)?

    /** A finished phrase and who said it ("S1", "S2"…; null without speaker labels). */
    var onFinalListener: ((text: String, speaker: String?) -> Unit)?

    /**
     * Called when the recogniser stops working on its own — it could not connect or start, or the service ended
     * the session — with what happened; never after [stop].
     */
    var onFailureListener: ((String) -> Unit)?

    fun start(apiKey: String, language: String, config: SpeechmaticsConfig)

    fun feedAudio(pcm: ByteArray)

    /** The speaker paused: the phrase so far is finished. */
    fun flush()

    fun stop()

    fun isRunning(): Boolean
}
