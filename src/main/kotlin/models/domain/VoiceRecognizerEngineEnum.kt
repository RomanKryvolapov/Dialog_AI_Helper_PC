package models.domain

import models.common.TypeEnum

enum class VoiceRecognizerEngineEnum(
    override val type: String,
) : TypeEnum {
    VOSK("Vosk"),
    WHISPER("Whisper"),
}