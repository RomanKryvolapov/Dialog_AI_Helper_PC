package repository

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.input.Prompt
import extensions.normalizeAndRemoveEmptyLines
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import mappers.network.TranslateWithGoogleAiRequestMapper
import mappers.network.TranslateWithGoogleAiResponseMapper
import models.network.googleai.TranslateWithGoogleAiResponse
import org.slf4j.LoggerFactory
import utils.ChatLogger
import java.time.Duration

class CloudRepositoryImpl(
    private val httpClient: HttpClient,
    private val jdkClientBuilder: JdkHttpClientBuilder,
    private val translateWithGoogleAiRequestMapper: TranslateWithGoogleAiRequestMapper,
    private val translateWithGoogleAiResponseMapper: TranslateWithGoogleAiResponseMapper,
): CloudRepository {

    private val log = LoggerFactory.getLogger("CloudRepository")

    companion object {
        private const val GOOGLE_AI_BASE_URL = "https://generativelanguage.googleapis.com/"
        private const val GOOGLE_TRANSLATE_BASE_URL = "https://translation.googleapis.com/"
        private const val GOOGLE_AI_BASE_URL_LANG_CHAIN = "https://generativelanguage.googleapis.com/v1beta"
    }

    override suspend fun generateAnswerByGoogleAI(
        model: String,
        apiKey: String,
        text: String
    ): Result<String> {
        log.debug("Generate answer with Google AI\nmodel: $model\ntext: $text")
        val requestBody = translateWithGoogleAiRequestMapper.map(text)
        val url = "${GOOGLE_AI_BASE_URL}v1beta/models/$model:generateContent"
        try {
            val response = httpClient.post(url) {
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
            val model = translateWithGoogleAiResponseMapper.map(body).normalizeAndRemoveEmptyLines()
            log.debug("Generate answer with Google AI result size: ${model.length}")
            return Result.success(model)
        } catch (e: CancellationException) {
            log.error("Generate answer with Google AI Cancelled")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("Generate answer with Google AI exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun generateAnswerByLangChainByGoogleAI(
        model: String,
        apiKey: String,
        prompt: Prompt,
    ): Result<String> {
        log.debug("Generate answer with Google AI and LangChain\nmodel: $model\ntext: ${prompt.text()}")
        return try {
            val chatModel = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(jdkClientBuilder)
                .baseUrl(GOOGLE_AI_BASE_URL_LANG_CHAIN)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.8)
                .logRequestsAndResponses(true)
                .timeout(Duration.ofSeconds(90))
                .listeners(listOf(ChatLogger()))
                .build()
            val response = chatModel.chat(prompt.text())
            log.debug("Generate answer with Google AI and LangChain result size: ${response.length}")
            Result.success(response)
        } catch (e: CancellationException) {
            log.error("Generate answer with Google AI and LangChain Cancelled")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("Generate answer with Google AI and LangChain exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

}