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
