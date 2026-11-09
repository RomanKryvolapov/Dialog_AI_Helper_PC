package models.domain

/**
 * The speech recogniser of a text translation method (the first level of a [TranslationMethod]; a voice
 * method has none — its model hears the phrase itself).
 */
enum class VoiceRecognitionEngine(
    val title: String,
    /** How it appears in a saved method key: "<slug>:<model id>". */
    val slug: String,
    /** Offered in Settings; Speechmatics is not wired yet and transcribes nothing. */
    val offered: Boolean,
    /** Cuts the audio into recorded phrases, which a voice method can hear too. */
    val recordsPhrases: Boolean,
) {
    WHISPER_BATCH("Whisper, batch (per phrase)", "whisper-batch", offered = true, recordsPhrases = true),
    WHISPER_STREAMING("Whisper, streaming (live partials)", "whisper-streaming", offered = true, recordsPhrases = false),
    SHERPA_ONNX("Sherpa-ONNX Zipformer (streaming, in-process)", "sherpa-onnx", offered = true, recordsPhrases = false),
    SPEECHMATICS("Speechmatics (realtime cloud STT + translation)", "speechmatics", offered = false, recordsPhrases = false);

    companion object {
        fun bySlug(slug: String): VoiceRecognitionEngine? = entries.firstOrNull { it.slug == slug }
    }
}
