package models.domain

import models.common.TypeEnum

enum class VoiceRecognizer (
    override val type: String,
) : TypeEnum {
    VOSK("Vosk"),
    WHISPER("Whisper"),
}