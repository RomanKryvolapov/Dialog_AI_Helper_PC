package models.domain

/** Every model the app can talk to, built from the per-provider enums. */
fun allLlmModels(): List<LlmModel> {
    return buildList {
        GoogleAiModelsEnum.entries.forEach {
            add(
                LlmModel(
                    id = it.type,
                    description = it.title,
                    engine = LlmModelEngine.GOOGLE,
                    googleAiModel = it,
                )
            )
        }
        OpenAiModelsEnum.entries.forEach {
            add(
                LlmModel(
                    id = it.type,
                    description = it.getDescription(),
                    engine = LlmModelEngine.OPENAI,
                    openAiModel = it,
                )
            )
        }
        ClaudeModelsEnum.entries.forEach {
            add(
                LlmModel(
                    id = it.type,
                    description = it.getDescription(),
                    engine = LlmModelEngine.CLAUDE,
                    claudeModel = it,
                )
            )
        }
    }
}

fun findLlmModelById(id: String): LlmModel? = allLlmModels().firstOrNull { it.id == id }

/**
 * Models the catalog has dropped, each mapped to its closest current successor from the same provider,
 * so a saved or `.env` choice keeps working with the API key the user already has.
 */
private val REPLACED_MODEL_IDS = mapOf(
    // Gemini Flash models leave the free tier after 20 requests a day; Flash-Lite is the Gemini that fits it.
    "gemini-2.0-flash" to GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.type,
    "gemini-2.0-flash-lite" to GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.type,
    "gemini-2.5-flash" to GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.type,
    "gemini-2.5-flash-lite" to GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.type,
    "gemini-3.6-flash" to GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.type,
    "gemini-3.8-flash" to GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.type,
    "gemma-3-27b-it" to GoogleAiModelsEnum.GEMMA_4_31B.type,
    "gemma-3-12b-it" to GoogleAiModelsEnum.GEMMA_4_26B_A4B.type,
    "gemma-3-4b-it" to GoogleAiModelsEnum.GEMMA_4_26B_A4B.type,
    "gemma-3-1b-it" to GoogleAiModelsEnum.GEMMA_4_26B_A4B.type,
    "gemma-3n-e4b-it" to GoogleAiModelsEnum.GEMMA_4_26B_A4B.type,
    "gemma-3n-e2b-it" to GoogleAiModelsEnum.GEMMA_4_26B_A4B.type,
    "gpt-5.6-terra" to OpenAiModelsEnum.GPT_6_1_TERRA.type,
    "gpt-5.4-mini" to OpenAiModelsEnum.GPT_6_1_TERRA.type,
    "gpt-5.6-luna" to OpenAiModelsEnum.GPT_6_1_TERRA.type,
    "gpt-5.4-nano" to OpenAiModelsEnum.GPT_6_1_TERRA.type,
    "claude-sonnet-5" to ClaudeModelsEnum.CLAUDE_SONNET_5_5.type,
    "claude-haiku-4-5" to ClaudeModelsEnum.CLAUDE_SONNET_5_5.type,
)

/** The catalog model for a saved or configured [id], following a dropped model to its successor. */
fun resolveLlmModel(id: String): LlmModel? =
    findLlmModelById(id) ?: REPLACED_MODEL_IDS[id]?.let(::findLlmModelById)
