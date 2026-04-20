package models.domain

import models.common.TypeEnum

enum class GoogleAiModelsEnum(
    override val type: String,
    val limitRequestsInMinute: Int?,
    val limitRequestsInDay: Int?,
    val limitTokensInMinute: Int?,
) : TypeEnum {
    GEMMA_4_31B(
        type = "gemma-4-31b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_4_26B_A4B(
        type = "gemma-4-26b-a4b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_3N_E4B(
        type = "gemma-3n-e4b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_3N_E2B(
        type = "gemma-3n-e2b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_3_27B(
        type = "gemma-3-27b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_3_12B(
        type = "gemma-3-12b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_3_4B(
        type = "gemma-3-4b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_3_1B(
        type = "gemma-3-1b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMINI_2_5_FLASH(
        type = "gemini-2.5-flash",
        limitRequestsInMinute = 10,
        limitRequestsInDay = 250,
        limitTokensInMinute = 250000,
    ),
    GEMINI_2_5_FLASH_LITE(
        type = "gemini-2.5-flash-lite",
        limitRequestsInMinute = 15,
        limitRequestsInDay = 1000,
        limitTokensInMinute = 250000,
    ),
    GEMINI_2_0_FLASH(
        type = "gemini-2.0-flash",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 200,
        limitTokensInMinute = null,
    ),
    GEMINI_2_0_FLASH_LITE(
        type = "gemini-2.0-flash-lite",
        limitRequestsInMinute = 0,
        limitRequestsInDay = 200,
        limitTokensInMinute = 1000000,
    );

    fun getDescription() = "Google Cloud: $type (Free: Requests/min=$limitRequestsInMinute, Requests/day=$limitRequestsInDay, Tokens/min=$limitTokensInMinute)"
}
