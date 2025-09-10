package repository

import dev.langchain4j.model.input.Prompt
import models.domain.LlmModel
import models.network.AnswerWithEmotion

interface LocalNetworkRepository {

    suspend fun getLmStudioModels(
        baseUrl: String,
    ): Result<List<LlmModel>>

    suspend fun getOllamaModels(
        baseUrl: String,
    ): Result<List<LlmModel>>

    suspend fun generateAnswerByLmStudio(
        baseUrl: String,
        model: String,
        text: String,
    ): Result<String>

    fun generateAnswerByLangChainLmStudio(
        baseUrl: String,
        model: String,
        prompt: Prompt,
    ): Result<String>

    fun generateAnswerByLangChainWithGradeLmStudio(
        baseUrl: String,
        model: String,
        prompt: Prompt,
    ): Result<AnswerWithEmotion>

    fun generateAnswerByLangChainOllama(
        baseUrl: String,
        model: String,
        prompt: Prompt,
    ): Result<String>

}