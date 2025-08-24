package models.common

enum class GoogleAiModelsEnum(
    override val type: String,
    val limitRequestsInMinute: Int?,
    val limitRequestsInDay: Int?,
    val limitTokensInMinute: Int?,
) : TypeEnum {
    LM_STUDIO_LOCAL_MODEL(
        type = "LM Studio Local Model",
        limitRequestsInMinute = null,
        limitRequestsInDay = null,
        limitTokensInMinute = null,
    ),
    GEMINI_2_5_FLASH(
        type = "gemini-2.5-flash",
        limitRequestsInMinute = 10, // ok
        limitRequestsInDay = 250, // ok
        limitTokensInMinute = 250000, // ok
    ),
    GEMINI_2_5_FLASH_LITE(
        type = "gemini-2.5-flash-lite",
        limitRequestsInMinute = 15, // ok
        limitRequestsInDay = 1000, // ok
        limitTokensInMinute = 250000, // ok
    ),
    GEMINI_2_0_FLASH(
        type = "gemini-2.0-flash",
        limitRequestsInMinute = 30, // ok
        limitRequestsInDay = 200, // ok
        limitTokensInMinute = null,
    ),
    GEMINI_2_0_FLASH_LITE(
        type = "gemini-2.0-flash-lite",
        limitRequestsInMinute = 0,
        limitRequestsInDay = 200,
        limitTokensInMinute = 1000000,
    ),
    GEMMA_3_1B(
        type = "gemma-3-1b-it",
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
    GEMMA_3_12B(
        type = "gemma-3-12b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    ),
    GEMMA_3_27B(
        type = "gemma-3-27b-it",
        limitRequestsInMinute = 30,
        limitRequestsInDay = 14400,
        limitTokensInMinute = 15000,
    );

    fun getDescription() = "Google Cloud: $type (Free: Requests/min=$limitRequestsInMinute, Requests/day=$limitRequestsInDay, Tokens/min=$limitTokensInMinute)"

}