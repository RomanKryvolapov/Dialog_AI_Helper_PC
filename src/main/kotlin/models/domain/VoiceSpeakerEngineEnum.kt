package models.domain

import models.common.TypeEnum

enum class VoiceSpeakerEngineEnum(
    override val type: String,
) : TypeEnum {
    NONE(""),
    CHATTERBOX("Chatterbox"),
    XTTS("Xtts V2"),
}