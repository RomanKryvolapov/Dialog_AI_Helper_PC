package models.domain

import models.common.TypeEnum

enum class GoogleAiModelsEnum(
    override val type: String,
    val title: String,
    val shortTitle: String,
    /** Takes a recorded phrase as input, so it can carry a voice translation method. */
    val hearsAudio: Boolean,
    val limitRequestsInMinute: Int?,
    val limitRequestsInDay: Int?,
    val limitTokensInMinute: Int?,
    val limitTokensInDay: Int?,
) : TypeEnum {
    // Free tier only. These are the free-tier limits of the project the app was set up with, as AI Studio
    // (Rate limit page) showed them on 2026-11-08; Google publishes no fixed numbers, and a paid tier
    // raises them. Gemini 3.6/3.8 Flash are left out: their free tier allows 20 requests a day, a few
    // minutes of conversation. Gemini 3.5 Flash-Lite is the Gemini that fits — 500 a day — and hears
    // audio, so it carries the free voice method; Gemma 4 answers audio with a 400 and carries text only.
    GEMINI_3_5_FLASH_LITE(
        type = "gemini-3.5-flash-lite",
        title = "Gemini 3.5 Flash-Lite",
        shortTitle = "Flash-Lite",
        hearsAudio = true,
        limitRequestsInMinute = 15,
        limitRequestsInDay = 500,
        limitTokensInMinute = 250_000,
        limitTokensInDay = null,
    ),
    // Gemma 4: 30 requests a minute and 14,400 a day, but only 16,000 input tokens a minute — about ten
    // phrases a minute with the conversation context, the binding limit in a fast conversation.
    GEMMA_4_31B(
        type = "gemma-4-31b-it",
        title = "Gemma 4 31B",
        shortTitle = "Gemma 31B",
        hearsAudio = false,
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14_400,
        limitTokensInMinute = 16_000,
        limitTokensInDay = null,
    ),
    GEMMA_4_26B_A4B(
        type = "gemma-4-26b-a4b-it",
        title = "Gemma 4 26B A4B",
        shortTitle = "Gemma 26B",
        hearsAudio = false,
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14_400,
        limitTokensInMinute = 16_000,
        limitTokensInDay = null,
    );

    fun getDescription() = "$title — $type (RPM=$limitRequestsInMinute, RPD=$limitRequestsInDay, " +
        "TPM=${limitTokensInMinute ?: "∞"}, TPD=${limitTokensInDay ?: "∞"})"
}
