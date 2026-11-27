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
    val parameters: ModelParameterSupport,
) : TypeEnum {
    // The GPT-6 family comes in four tiers: astra (flagship), sol, terra (mid) and luna
    // (cost-optimized). Only terra is listed: the top tiers cost more than translation needs, and luna
    // translated misrecognized words literally in a test run where terra recovered them. Terra is a
    // reasoning model that accepts reasoning effort "none", which the app sends. It does not hear audio:
    // both API entry points refuse an audio part (checked 2026-11-09).
    // Limits are per account tier: these are the ones OpenAI's x-ratelimit headers gave this account on
    // 2026-11-27 (10,000 requests and 4M tokens a minute, no daily cap), for both models. Lower ones throttle
    // the app on its own side: the questions mode then takes the minute's budget and translations wait. An
    // account on a lower tier gets a 429, which rests the method for its retry-after.
    GPT_6_1_TERRA(
        type = "gpt-6.1-terra",
        title = "GPT-6.1 Terra",
        shortTitle = "Terra",
        hearsAudio = false,
        readsText = true,
        limitRequestsInMinute = 10_000,
        limitRequestsInDay = null,
        limitTokensInMinute = 4_000_000,
        limitTokensInDay = null,
        // Reasoning effort none–xhigh ("minimal" is refused); temperature and top P only at "none", a 400
        // at any other effort; max_tokens is refused in favour of max_completion_tokens.
        parameters = ModelParameterSupport(
            temperature = true,
            topP = true,
            thinkingLevels = listOf("none", "low", "medium", "high", "xhigh"),
            defaultThinking = "none",
            verbosityLevels = listOf("low", "medium", "high"),
            samplingOnlyAtThinking = "none",
        ),
    ),
    // OpenAI's model that hears audio through Chat Completions: the voice method's OpenAI side, paid
    // (text $2.50 and audio $32 per 1M tokens, 10 audio tokens a second — about half a cent a phrase).
    // It answers a text-only request with a 400 ("requires that either input content or output modality
    // contain audio") and rejects reasoning effort, so it carries voice only. GPT-Audio Mini is cheaper
    // but deprecated. Limits as above.
    GPT_AUDIO_1_5(
        type = "gpt-audio-1.5",
        title = "GPT-Audio-1.5",
        shortTitle = "GPT-Audio",
        hearsAudio = true,
        readsText = false,
        limitRequestsInMinute = 10_000,
        limitRequestsInDay = null,
        limitTokensInMinute = 4_000_000,
        limitTokensInDay = null,
        // Temperature yes; top P makes the server fail (500); no reasoning effort at all.
        parameters = ModelParameterSupport(temperature = true),
    );

    fun getDescription() = "OpenAI: $type (RPM=$limitRequestsInMinute, RPD=$limitRequestsInDay, " +
        "TPM=${limitTokensInMinute ?: "∞"}, TPD=${limitTokensInDay ?: "∞"})"
}
