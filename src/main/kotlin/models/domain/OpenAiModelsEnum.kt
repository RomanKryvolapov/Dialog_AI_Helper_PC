package models.domain

import models.common.TypeEnum

enum class OpenAiModelsEnum(
    override val type: String,
    val title: String,
    val shortTitle: String,
    /** Takes a recorded phrase as input, so it can carry a voice translation method. */
    val hearsAudio: Boolean,
    /** Answers a text-only request, so it can carry a text method (and write conversation summaries). */
    val readsText: Boolean,
    val limitRequestsInMinute: Int?,
    val limitRequestsInDay: Int?,
    val limitTokensInMinute: Int?,
    val limitTokensInDay: Int?,
) : TypeEnum {
    // The GPT-6 family comes in four tiers: astra (flagship), sol, terra (mid) and luna
    // (cost-optimized). Only terra is listed: the top tiers cost more than translation needs, and luna
    // translated misrecognized words literally in a test run where terra recovered them. Terra is a
    // reasoning model that accepts reasoning effort "none", which the app sends. It does not hear audio:
    // both API entry points refuse an audio part (checked 2026-11-09).
    // Conservative placeholder limits, deliberately set well below a typical paid Tier-1 allowance
    // so that parallel requests stay comfortably within quota. Real per-model limits are
    // account/tier-specific — confirm them at platform.openai.com/settings/organization/limits and
    // raise these values.
    GPT_6_1_TERRA(
        type = "gpt-6.1-terra",
        title = "GPT-6.1 Terra",
        shortTitle = "Terra",
        hearsAudio = false,
        readsText = true,
        limitRequestsInMinute = 60,
        limitRequestsInDay = 10000,
        limitTokensInMinute = 60000,
        limitTokensInDay = null,
    ),
    // OpenAI's model that hears audio through Chat Completions: the voice method's OpenAI side, paid
    // (text $2.50 and audio $32 per 1M tokens, 10 audio tokens a second — about half a cent a phrase).
    // It answers a text-only request with a 400 ("requires that either input content or output modality
    // contain audio") and rejects reasoning effort, so it carries voice only. GPT-Audio Mini is cheaper
    // but deprecated. Same placeholder limits as above.
    GPT_AUDIO_1_5(
        type = "gpt-audio-1.5",
        title = "GPT-Audio-1.5",
        shortTitle = "GPT-Audio",
        hearsAudio = true,
        readsText = false,
        limitRequestsInMinute = 60,
        limitRequestsInDay = 10000,
        limitTokensInMinute = 60000,
        limitTokensInDay = null,
    );

    fun getDescription() = "OpenAI: $type (RPM=$limitRequestsInMinute, RPD=$limitRequestsInDay, " +
        "TPM=${limitTokensInMinute ?: "∞"}, TPD=${limitTokensInDay ?: "∞"})"
}
