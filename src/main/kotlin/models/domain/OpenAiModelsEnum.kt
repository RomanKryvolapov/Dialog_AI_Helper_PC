package models.domain

import models.common.TypeEnum

enum class OpenAiModelsEnum(
    override val type: String,
    val limitRequestsInMinute: Int?,
    val limitRequestsInDay: Int?,
    val limitTokensInMinute: Int?,
    val limitTokensInDay: Int?,
) : TypeEnum {
    // Conservative placeholder limits, deliberately set well below a typical paid Tier-1 allowance
    // so that parallel requests stay comfortably within quota. Real per-model limits are
    // account/tier-specific and not published for gpt-5.4 — confirm them at
    // platform.openai.com/settings/organization/limits and raise these values.
    GPT_5_4_MINI(
        type = "gpt-5.4-mini",
        limitRequestsInMinute = 60,
        limitRequestsInDay = 10000,
        limitTokensInMinute = 60000,
        limitTokensInDay = null,
    ),
    GPT_5_4_NANO(
        type = "gpt-5.4-nano",
        limitRequestsInMinute = 60,
        limitRequestsInDay = 10000,
        limitTokensInMinute = 100000,
        limitTokensInDay = null,
    );

    fun getDescription() = "OpenAI: $type (RPM=$limitRequestsInMinute, RPD=$limitRequestsInDay, " +
        "TPM=${limitTokensInMinute ?: "∞"}, TPD=${limitTokensInDay ?: "∞"})"
}
