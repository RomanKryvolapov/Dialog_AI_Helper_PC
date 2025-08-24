package repository

import client
import com.sun.org.apache.xml.internal.serializer.utils.Utils.messages
import extensions.normalizeAndRemoveEmptyLines
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mappers.network.OpenAIGenerationResponseMapper
import mappers.network.OpenAIModelsResponseMapper
import mappers.network.TranslateWithGoogleAiResponseMapper
import models.domain.LlmModel
import models.network.OpenAIGenerationRequest
import models.network.OpenAIGenerationRequestMessage
import models.network.OpenAIGenerationResponse
import models.network.OpenAIModelsResponse
import models.network.TranslateWithGoogleAiResponse
import org.slf4j.LoggerFactory

object LocalNetworkRepository {

    private val log = LoggerFactory.getLogger("LocalNetworkRepositoryTag")

    suspend fun getLmStudioModels(
        port: String,
    ): Result<List<LlmModel>> {
        log.debug("getLmStudioModels port: $port")
        val url = "http://localhost:$port/v1/models"
        try {
            val response = client.get(url) {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<OpenAIModelsResponse>()
            val models = OpenAIModelsResponseMapper.map(body)
            log.debug("getLmStudioModels size: ${models.size}")
            return Result.success(models)
        } catch (e: Exception) {
            log.error("getLmStudioModels error: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    suspend fun generateAnswerByLmStudio(
        port: String,
        model: String,
        text: String,
    ): Result<String> {
        log.debug("generateAnswerByLmStudio port: $port, model: $model")
        val requestBody = OpenAIGenerationRequest(
            model = model,
            messages = listOf(
                OpenAIGenerationRequestMessage(
                    role = "user",
                    content = text,
                )
            ),
            stream = false,
        )
        val url = "http://localhost:$port/v1/chat/completions"
        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<OpenAIGenerationResponse>()
            val model = OpenAIGenerationResponseMapper.map(body).normalizeAndRemoveEmptyLines()
            log.debug("generateAnswerByLmStudio result size: ${model.length}")
            return Result.success(model)
        } catch (e: Exception) {
            log.error("generateAnswerByLmStudio error: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

}