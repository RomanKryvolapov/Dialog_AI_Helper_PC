package repository

import client
import extensions.normalizeAndRemoveEmptyLines
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import mappers.network.TranslateWithGoogleAiRequestMapper
import mappers.network.TranslateWithGoogleAiResponseMapper
import models.network.TranslateWithGoogleAiResponse
import org.slf4j.LoggerFactory

object CloudRepository {

    private val log = LoggerFactory.getLogger("CloudRepositoryTag")

    private const val GOOGLE_AI_BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val GOOGLE_TRANSLATE_BASE_URL = "https://translation.googleapis.com/"

    private val GENERATE_ANSWER_BY_GOOGLE_AIURL = "${GOOGLE_AI_BASE_URL}v1beta/models/{model}:generateContent"

    suspend fun generateAnswerByGoogleAI(
        model: String,
        apiKey: String,
        text: String
    ): Result<String> {
        log.debug("generateAnswerByGoogleAI text: $text")
        val requestBody = TranslateWithGoogleAiRequestMapper.map(text)
        val url = GENERATE_ANSWER_BY_GOOGLE_AIURL.replace("{model}", model)
        try {
            val response = client.post(url) {
                url {
                    parameters.append("key", apiKey)
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<TranslateWithGoogleAiResponse>()
            val model = TranslateWithGoogleAiResponseMapper.map(body).normalizeAndRemoveEmptyLines()
            return Result.success(model)
        } catch (e: CancellationException) {
            log.error("generateAnswerByGoogleAI CancellationException")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("generateAnswerByGoogleAI exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }


}