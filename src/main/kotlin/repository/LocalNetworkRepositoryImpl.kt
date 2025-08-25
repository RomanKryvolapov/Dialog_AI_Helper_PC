package repository

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import extensions.normalizeAndRemoveEmptyLines
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import mappers.network.OllamaModelsResponseMapper
import mappers.network.OpenAIGenerationResponseMapper
import mappers.network.OpenAIModelsResponseMapper
import models.domain.LlmModel
import models.network.ollama.OllamaModelsResponse
import models.network.openai.OpenAIGenerationRequest
import models.network.openai.OpenAIGenerationRequestMessage
import models.network.openai.OpenAIGenerationResponse
import models.network.openai.OpenAIModelsResponse
import org.slf4j.LoggerFactory
import utils.ChatLogger
import java.time.Duration

class LocalNetworkRepositoryImpl(
    private val httpClient: HttpClient,
    private val jdkClientBuilder: JdkHttpClientBuilder,
    private val openAIModelsResponseMapper: OpenAIModelsResponseMapper,
    private val ollamaModelsResponseMapper: OllamaModelsResponseMapper,
    private val openAIGenerationResponseMapper: OpenAIGenerationResponseMapper,
): LocalNetworkRepository {

    private val log = LoggerFactory.getLogger("LocalNetworkRepositoryTag")

    override suspend fun getLmStudioModels(
        ip: String,
        port: String,
    ): Result<List<LlmModel>> {
        log.debug("getLmStudioModels port: $port")
        val url = "http://$ip:$port/v1/models"
        try {
            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<OpenAIModelsResponse>()
            val models = openAIModelsResponseMapper.map(body)
            log.debug("getLmStudioModels size: ${models.size}")
            return Result.success(models)
        } catch (e: CancellationException) {
            log.error("getLmStudioModels CancellationException")
            e.printStackTrace()
            return Result.success(emptyList())
        } catch (e: Exception) {
            log.error("getLmStudioModels exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override suspend fun getOllamaModels(
        ip: String,
        port: String,
    ): Result<List<LlmModel>> {
        log.debug("getOllamaModels port: $port")
        val url = "http://$ip:$port/api/tags"
        try {
            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<OllamaModelsResponse>()
            val models = ollamaModelsResponseMapper.map(body)
            log.debug("getOllamaModels size: ${models.size}")
            return Result.success(models)
        } catch (e: CancellationException) {
            log.error("getOllamaModels CancellationException")
            e.printStackTrace()
            return Result.success(emptyList())
        } catch (e: Exception) {
            log.error("getOllamaModels exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override suspend fun generateAnswerByLmStudio(
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
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<OpenAIGenerationResponse>()
            val model = openAIGenerationResponseMapper.map(body).normalizeAndRemoveEmptyLines()
            log.debug("generateAnswerByLmStudio result size: ${model.length}")
            return Result.success(model)
        } catch (e: CancellationException) {
            log.error("generateAnswerByLmStudio CancellationException")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("generateAnswerByLmStudio exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun generateAnswerByLangChainLmStudio(
        port: String,
        model: String,
        prompt: Prompt,
    ): Result<String> {
        log.debug("generateAnswerByLangChainLmStudio port: $port, model: $model")
        val url = "http://localhost:$port/v1"
        return try {
            val chatModel = OpenAiChatModel.builder()
                .httpClientBuilder(jdkClientBuilder)
                .baseUrl(url)
                .apiKey("lm-studio")
                .modelName(model)
                .temperature(0.8)
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(90))
                .listeners(listOf(ChatLogger()))
                .build()
            val response = chatModel.chat(prompt.text())
            log.debug("generateAnswerByLangChainLmStudio result size: ${response.length}")
            Result.success(response)
        } catch (e: CancellationException) {
            log.error("generateAnswerByLangChainLmStudio CancellationException")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("generateAnswerByLangChainLmStudio exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun generateAnswerByLangChainOllama(
        port: String,
        model: String,
        prompt: Prompt,
    ): Result<String> {
        log.debug("generateAnswerByLangChainOllama port: $port, model: $model")
        val url = "http://localhost:$port"
        return try {
            val chatModel = OllamaChatModel.builder()
                .baseUrl(url)
                .modelName(model)
                .temperature(0.8)
                .timeout(Duration.ofSeconds(90))
                .logRequests(true)
                .logResponses(true)
                .build()
            val response = chatModel.chat(prompt.text())
            log.debug("generateAnswerByLangChainOllama result size: ${response.length}")
            Result.success(response)
        } catch (e: CancellationException) {
            log.error("generateAnswerByLangChainOllama CancellationException")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("generateAnswerByLangChainOllama exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

}