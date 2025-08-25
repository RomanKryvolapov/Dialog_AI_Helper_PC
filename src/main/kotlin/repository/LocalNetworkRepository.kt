package repository

import dev.langchain4j.model.input.Prompt
import models.domain.LlmModel

interface LocalNetworkRepository {

    suspend fun getLmStudioModels(
        ip: String,
        port: String,
    ): Result<List<LlmModel>>

    suspend fun getOllamaModels(
        ip: String,
        port: String,
    ): Result<List<LlmModel>>

    suspend fun generateAnswerByLmStudio(
        port: String,
        model: String,
        text: String,
    ): Result<String>

    fun generateAnswerByLangChainLmStudio(
        port: String,
        model: String,
        prompt: Prompt,
    ): Result<String>

    fun generateAnswerByLangChainOllama(
        port: String,
        model: String,
        prompt: Prompt,
    ): Result<String>

}