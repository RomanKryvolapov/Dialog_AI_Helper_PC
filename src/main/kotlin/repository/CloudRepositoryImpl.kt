package repository

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.googleai.GeminiThinkingConfig
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import models.domain.ClaudeModelsEnum
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.slf4j.LoggerFactory
import utils.ChatLogger
import java.time.Duration

class CloudRepositoryImpl(
    private val jdkClientBuilder: JdkHttpClientBuilder,
) : CloudRepository {

    private val log = LoggerFactory.getLogger("CloudRepository")

    companion object {
        private const val GOOGLE_AI_BASE_URL_LANG_CHAIN = "https://generativelanguage.googleapis.com/v1beta"
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(45)

        private val THINKING_BLOCK_PATTERNS = listOf(
            // Generic xml-ish tags used by many providers
            Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL),
            Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL),
            Regex("<thought>.*?</thought>", RegexOption.DOT_MATCHES_ALL),
            Regex("\\[THINKING].*?\\[/THINKING]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            // Gemma 4 special tokens: <|think|> ... <channel|>
            //                         <|channel>thought ... <channel|>
            Regex("<\\|think\\|>.*?<channel\\|>", RegexOption.DOT_MATCHES_ALL),
            Regex("<\\|channel>thought.*?<channel\\|>", RegexOption.DOT_MATCHES_ALL),
        )

        private val OPEN_THINKING_TAIL = listOf(
            Regex("<think[^>]*>.*", RegexOption.DOT_MATCHES_ALL),
            Regex("<thinking[^>]*>.*", RegexOption.DOT_MATCHES_ALL),
            Regex("<thought[^>]*>.*", RegexOption.DOT_MATCHES_ALL),
            Regex("\\[THINKING].*", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("<\\|think\\|>.*", RegexOption.DOT_MATCHES_ALL),
            Regex("<\\|channel>thought.*", RegexOption.DOT_MATCHES_ALL),
        )

        /** Trailing stray Gemma 4 closing tokens that may survive when only the head was emitted. */
        private val STRAY_CHANNEL_TOKEN = Regex("<channel\\|>")

        fun stripReasoning(text: String): String {
            var out = text
            THINKING_BLOCK_PATTERNS.forEach { out = it.replace(out, "") }
            out = STRAY_CHANNEL_TOKEN.replace(out, "")
            return out.trim()
        }

        /** During streaming, also hide dangling (unclosed) reasoning blocks. */
        fun stripStreamingReasoning(text: String): String {
            var out = stripReasoning(text)
            OPEN_THINKING_TAIL.forEach { out = it.replace(out, "") }
            return out.trim()
        }

        /**
         * Gemini 2.5+ / 3+ accept `thinkingBudget: 0` to disable thinking.
         */
        private fun supportsThinkingBudget(model: String): Boolean {
            val m = model.lowercase()
            return m.startsWith("gemini-2.5") ||
                m.startsWith("gemini-3") ||
                m.startsWith("gemini-flash-latest") ||
                m.startsWith("gemini-flash-lite-latest") ||
                m.startsWith("gemini-pro-latest")
        }

        /**
         * Gemma 4 rejects `thinkingBudget` but accepts `thinkingLevel`.
         * Per docs Gemma 4 supports only MINIMAL and HIGH levels.
         * MINIMAL is the closest you get to "don't think".
         */
        private fun supportsThinkingLevel(model: String): Boolean =
            model.lowercase().startsWith("gemma-4-")
    }

    private fun streamingFlow(
        label: String,
        starter: (StreamingChatResponseHandler) -> Unit,
    ): Flow<String> = callbackFlow {
        val buffer = StringBuilder()
        val handler = object : StreamingChatResponseHandler {
            override fun onPartialResponse(partialResponse: String) {
                buffer.append(partialResponse)
                trySend(stripStreamingReasoning(buffer.toString()))
            }

            override fun onCompleteResponse(completeResponse: ChatResponse) {
                val clean = stripReasoning(buffer.toString())
                log.debug("{} complete ({} chars)", label, clean.length)
                trySend(clean)
                close()
            }

            override fun onError(error: Throwable) {
                log.error("$label error: ${error.message}", error)
                close(error)
            }
        }
        log.debug("$label start")
        starter(handler)
        awaitClose { /* no-op; langchain4j handles HTTP cleanup on its side */ }
    }

    override fun streamGoogleAI(model: String, apiKey: String, prompt: Prompt): Flow<String> {
        val builder = GoogleAiGeminiStreamingChatModel.builder()
            .httpClientBuilder(jdkClientBuilder)
            .baseUrl(GOOGLE_AI_BASE_URL_LANG_CHAIN)
            .apiKey(apiKey)
            .modelName(model)
            .temperature(0.8)
            .logRequestsAndResponses(false)
            .timeout(REQUEST_TIMEOUT)
            .listeners(listOf(ChatLogger()))
        when {
            supportsThinkingBudget(model) -> {
                builder.thinkingConfig(
                    GeminiThinkingConfig.builder()
                        .thinkingBudget(0)
                        .includeThoughts(false)
                        .build()
                ).returnThinking(false)
            }
            supportsThinkingLevel(model) -> {
                builder.thinkingConfig(
                    GeminiThinkingConfig.builder()
                        .thinkingLevel(GeminiThinkingConfig.GeminiThinkingLevel.MINIMAL)
                        .includeThoughts(false)
                        .build()
                ).returnThinking(false)
            }
        }
        val chatModel = builder.build()
        return streamingFlow("GoogleAI[$model]") { handler ->
            chatModel.chat(prompt.text(), handler)
        }
    }

    override fun streamOpenAI(model: String, apiKey: String, prompt: Prompt): Flow<String> {
        val chatModel = OpenAiStreamingChatModel.builder()
            .httpClientBuilder(jdkClientBuilder)
            .apiKey(apiKey)
            .modelName(model)
            .temperature(0.8)
            .logRequests(false)
            .logResponses(false)
            .timeout(REQUEST_TIMEOUT)
            .listeners(listOf(ChatLogger()))
            .build()
        return streamingFlow("OpenAI[$model]") { handler ->
            chatModel.chat(prompt.text(), handler)
        }
    }

    override fun streamClaude(model: String, apiKey: String, prompt: Prompt): Flow<String> {
        val builder = AnthropicStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(model)
            .logRequests(false)
            .logResponses(false)
            .timeout(REQUEST_TIMEOUT)
            .listeners(listOf(ChatLogger()))
        // Claude 5 and later removed the sampling parameters and reject a request that carries one.
        if (ClaudeModelsEnum.byType(model)?.supportsTemperature != false) {
            builder.temperature(0.8)
        }
        val chatModel = builder.build()
        return streamingFlow("Claude[$model]") { handler ->
            chatModel.chat(prompt.text(), handler)
        }
    }

}
