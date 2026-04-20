package models.domain

import models.common.TypeEnum

enum class OpenAiModelsEnum(
    override val type: String,
) : TypeEnum {
    GPT_5_4_MINI(type = "gpt-5.4-mini"),
    GPT_5_4_NANO(type = "gpt-5.4-nano");

    fun getDescription() = "OpenAI: $type"
}
