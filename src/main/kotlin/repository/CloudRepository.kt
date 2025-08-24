package repository

import dev.langchain4j.model.input.Prompt

interface CloudRepository {

    suspend fun generateAnswerByGoogleAI(
        model: String,
        apiKey: String,
        text: String
    ): Result<String>

    fun generateAnswerByLangChainByGoogleAI(
        model: String,
        apiKey: String,
        prompt: Prompt,
    ): Result<String>


}