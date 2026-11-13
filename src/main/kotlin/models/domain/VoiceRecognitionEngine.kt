package models.domain

/**
 * The speech recogniser of a text translation method (the first level of a [TranslationMethod]; a voice
 * method has none — its model hears the phrase itself).
 */
enum class VoiceRecognitionEngine(
    val title: String,
    /** For tight places: the top bar, the status line. */
    val shortTitle: String,
    /** How it appears in a saved method key: "<slug>:<model id>". */
    val slug: String,
    /** Offered in Settings as a way to hear the speech. */
    val offered: Boolean,
) {
    WHISPER_BATCH("Whisper, batch (per phrase)", "Whisper batch", "whisper-batch", offered = true),
    WHISPER_STREAMING("Whisper, streaming (live partials)", "Whisper streaming", "whisper-streaming", offered = true),
    SHERPA_ONNX("Sherpa-ONNX Zipformer (streaming, in-process)", "Sherpa-ONNX", "sherpa-onnx", offered = true),
    SPEECHMATICS("Speechmatics (realtime, cloud)", "Speechmatics", "speechmatics", offered = true);

    companion object {
        fun bySlug(slug: String): VoiceRecognitionEngine? = entries.firstOrNull { it.slug == slug }
    }
}
