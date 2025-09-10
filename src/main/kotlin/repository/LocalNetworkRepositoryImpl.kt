package repository

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import extensions.normalizeAndRemoveEmptyLines
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import mappers.network.OllamaModelsResponseMapper
import mappers.network.OpenAIGenerationResponseMapper
import mappers.network.OpenAIModelsResponseMapper
import models.domain.LlmModel
import models.network.AnswerWithEmotion
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

    private val log = LoggerFactory.getLogger("LocalNetworkRepositor")

    override suspend fun getLmStudioModels(
        baseUrl: String,
    ): Result<List<LlmModel>> {
        log.debug("get LM Studio models, base url: $baseUrl")
        val url = "$baseUrl/v1/models"
        try {
            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<OpenAIModelsResponse>()
            val models = openAIModelsResponseMapper.map(body)
            log.debug("get LM Studio models result size: ${models.size}")
            return Result.success(models)
        } catch (e: CancellationException) {
            log.error("get LM Studio models Cancelled")
            e.printStackTrace()
            return Result.success(emptyList())
        } catch (e: Exception) {
            log.error("get LM Studio models exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override suspend fun getOllamaModels(
        baseUrl: String,
    ): Result<List<LlmModel>> {
        log.debug("get Ollama models, base url: $baseUrl")
        val url = "$baseUrl/api/tags"
        try {
            val response = httpClient.get(url) {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Send to model error: ${response.status}"))
            }
            val body = response.body<OllamaModelsResponse>()
            val models = ollamaModelsResponseMapper.map(body)
            log.debug("get Ollama models result size: ${models.size}")
            return Result.success(models)
        } catch (e: CancellationException) {
            log.error("get Ollama models Cancelled")
            e.printStackTrace()
            return Result.success(emptyList())
        } catch (e: Exception) {
            log.error("get Ollama models exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override suspend fun generateAnswerByLmStudio(
        baseUrl: String,
        model: String,
        text: String,
    ): Result<String> {
        log.debug("Generate answer with LM Studio\nmodel: $model\ntext: $text")
        val url = "$baseUrl/v1/chat/completions"
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
            log.debug("Generate answer with LM Studio result size: ${model.length}")
            return Result.success(model)
        } catch (e: CancellationException) {
            log.error("Generate answer with LM Studio Cancelled")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("Generate answer with LM Studio exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun generateAnswerByLangChainLmStudio(
        baseUrl: String,
        model: String,
        prompt: Prompt,
    ): Result<String> {
        log.debug("Generate answer with LM Studio and LangChain\nmodel: $model\ntext: ${prompt.text()}")
        val url = "$baseUrl/v1"
        return try {
            val chatModel = OpenAiChatModel.builder()
                .httpClientBuilder(jdkClientBuilder)
                .baseUrl(url)
                .apiKey("lm-studio")
                .modelName(model)
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(90))
                .listeners(listOf(ChatLogger()))
                .build()
            val response = chatModel.chat(prompt.text())
            log.debug("Generate answer with LM Studio and LangChain result size: ${response.length}")
            Result.success(response)
        } catch (e: CancellationException) {
            log.error("Generate answer with LM Studio and LangChain Cancelled")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("Generate answer with LM Studio and LangChain exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun generateAnswerByLangChainWithGradeLmStudio(
        baseUrl: String,
        model: String,
        prompt: Prompt,
    ): Result<AnswerWithEmotion> {
        log.debug("Generate answer with LM Studio and Emotion and LangChain\nmodel: $model\ntext: ${prompt.text()}")
        val url = "$baseUrl/v1"
        return try {
            val chatModel = OpenAiChatModel.builder()
                .httpClientBuilder(jdkClientBuilder)
                .baseUrl(url)
                .apiKey("lm-studio")
                .modelName(model)
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(90))
                .listeners(listOf(ChatLogger()))
                .build()
            val rawResponse = chatModel.chat(prompt.text()).trim()
            val json = rawResponse
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val response = Json.decodeFromString<AnswerWithEmotion>(json)
            log.debug("Generate answer with LM Studio and Emotion and LangChain result size: ${response.answer.length}")
            Result.success(response)
        } catch (e: CancellationException) {
            log.error("Generate answer with LM Studio and Emotion and LangChain Cancelled")
            e.printStackTrace()
            return Result.success(AnswerWithEmotion("", ""))
        } catch (e: Exception) {
            log.error("Generate answer with LM Studio and Emotion and LangChain exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun generateAnswerByLangChainOllama(
        baseUrl: String,
        model: String,
        prompt: Prompt,
    ): Result<String> {
        log.debug("Generate answer with Ollama and LangChain\nmodel: $model\ntext: ${prompt.text()}")
        return try {
            val chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(Duration.ofSeconds(90))
                .logRequests(true)
                .logResponses(true)
                .build()
            val response = chatModel.chat(prompt.text())
            log.debug("Generate answer with Ollama and LangChain result size: ${response.length}")
            Result.success(response)
        } catch (e: CancellationException) {
            log.error("Generate answer with Ollama and LangChain Cancelled")
            e.printStackTrace()
            return Result.success("...")
        } catch (e: Exception) {
            log.error("Generate answer with Ollama and LangChain exception: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

}