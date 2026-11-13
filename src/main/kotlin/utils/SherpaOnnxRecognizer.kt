package utils

/**
 * In-process streaming speech-to-text via sherpa-onnx (next-gen Kaldi, ONNX Runtime).
 * Runs entirely in the JVM (JNI) — no subprocess. The recognizer does its own
 * endpoint detection, so [feedAudio] should receive a continuous stream (including
 * silence): it emits incremental [onPartialListener] results and an [onFinalListener]
 * result when the recognizer detects the end of a phrase.
 *
 * Audio format must match [AudioChunker]: 16 kHz, 16-bit signed, mono, little-endian.
 */
interface SherpaOnnxRecognizer {

    var onPartialListener: ((String) -> Unit)?
    var onFinalListener: ((String) -> Unit)?

    /**
     * Called when the recogniser stops working on its own — it could not start, reported an error, or its process
     * went away — with what happened; never after [stop].
     */
    var onFailureListener: ((String) -> Unit)?

    /** Loads the streaming model from [modelDir] (encoder/decoder/joiner .onnx + tokens.txt). */
    fun start(modelDir: String, numThreads: Int, decodingMethod: String, maxActivePaths: Int, splitMaxWords: Int, maxHangMillis: Long)

    /** Feeds one PCM frame (16 kHz, 16-bit, mono). */
    fun feedAudio(pcm: ByteArray)

    fun stop()

    fun isRunning(): Boolean
}
