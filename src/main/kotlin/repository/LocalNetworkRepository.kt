package repository

import dev.langchain4j.model.input.Prompt
import models.domain.LlmModel

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

    fun generateAnswerByLangChainOllama(
        baseUrl: String,
        model: String,
        prompt: Prompt,
    ): Result<String>

}