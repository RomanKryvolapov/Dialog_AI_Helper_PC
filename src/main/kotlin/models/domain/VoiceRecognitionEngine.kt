package models.domain

enum class VoiceRecognitionEngine(val title: String) {
    WHISPER_BATCH("Whisper (batch — per phrase)"),
    WHISPER_STREAMING("Whisper (streaming — live partials)"),
    SHERPA_ONNX("Sherpa-ONNX Zipformer (streaming, in-process)"),
}
