package repository

import client
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import mappers.network.TranslateWithGoogleAiRequestMapper
import models.network.TranslateWithGoogleAiResponse

object CloudRepository {

    private const val GOOGLE_AI_BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val GOOGLE_TRANSLATE_BASE_URL = "https://translation.googleapis.com/"

    private val GENERATE_ANSWER_BY_GOOGLE_AIURL = "${GOOGLE_AI_BASE_URL}v1beta/models/{model}:generateContent"

    suspend fun generateAnswerByGoogleAI(
        model: String,
        key: String,
        text: String
    ): TranslateWithGoogleAiResponse {
        val requestBody = TranslateWithGoogleAiRequestMapper.map(text)
        val url = GENERATE_ANSWER_BY_GOOGLE_AIURL.replace("{model}", model)
        val response = client.post(url) {
            url {
                parameters.append("key", key)
            }
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        return response.body()
    }


}