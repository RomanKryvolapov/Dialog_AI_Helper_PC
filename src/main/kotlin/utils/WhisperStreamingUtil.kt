package utils

/**
 * Streaming speech-to-text backend: a persistent faster-whisper process that
 * accepts raw PCM frames and emits incremental [onPartialListener] results plus
 * an [onFinalListener] result when an utterance is flushed.
 *
 * Audio format must match [AudioChunker]: 16 kHz, 16-bit signed, mono, little-endian.
 */
interface WhisperStreamingUtil {

    var onPartialListener: ((String) -> Unit)?
    var onFinalListener: ((String) -> Unit)?

    /**
     * Called when the recogniser stops working on its own — it could not start, reported an error, or its process
     * went away — with what happened; never after [stop].
     */
    var onFailureListener: ((String) -> Unit)?

    /** (Re)starts the recognizer process. Safe to call repeatedly; restarts on change. */
    fun start(modelPath: String, language: String, stepMillis: Long, minChars: Int, maxUtteranceMillis: Long)

    /** Feeds one PCM frame (16 kHz, 16-bit, mono) into the current utterance. */
    fun feedAudio(pcm: ByteArray)

    /** Marks the end of the current utterance; triggers a final result. */
    fun flush()

    /** Drops the current utterance buffer without emitting a result. */
    fun reset()

    /** Stops the recognizer process. */
    fun stop()

    fun isRunning(): Boolean
}
