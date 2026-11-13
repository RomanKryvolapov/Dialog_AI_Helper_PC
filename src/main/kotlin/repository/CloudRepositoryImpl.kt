package repository

import VOICE_AUDIO_MARK
import dev.langchain4j.data.message.AudioContent
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.Content
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.googleai.GeminiThinkingConfig
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import models.domain.ModelParameters
import models.domain.ModelPrompt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.slf4j.LoggerFactory
import utils.ChatLogger
import utils.TokenUsageTracker
import java.time.Duration
import java.util.Base64

class CloudRepositoryImpl(
    private val jdkClientBuilder: JdkHttpClientBuilder,
    private val tokenUsageTracker: TokenUsageTracker,
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

        /**
         * The parts of a voice request: the prompt text with [audio] placed where the prompt marks it
         * ([VOICE_AUDIO_MARK], inside its <audio> block), so the model sees the recording as a named
         * input of its own rather than something trailing the recent transcripts.
         */
        fun audioMessageParts(promptText: String, audio: Content): List<Content> {
            val at = promptText.indexOf(VOICE_AUDIO_MARK)
            if (at < 0) return listOf(TextContent.from(promptText), audio)
            val before = promptText.substring(0, at)
            val after = promptText.substring(at + VOICE_AUDIO_MARK.length)
            return listOfNotNull(
                before.takeIf { it.isNotBlank() }?.let(TextContent::from),
                audio,
                after.takeIf { it.isNotBlank() }?.let(TextContent::from),
            )
        }

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
    }

    /** Streams one answer of [model]; when it finishes, the tokens the provider reports are counted. */
    private fun streamingFlow(
        label: String,
        model: String,
        starter: (StreamingChatResponseHandler) -> Unit,
    ): Flow<String> = callbackFlow {
        val dialog = tokenUsageTracker.currentDialog()
        val buffer = StringBuilder()
        val handler = object : StreamingChatResponseHandler {
            override fun onPartialResponse(partialResponse: String) {
                buffer.append(partialResponse)
                trySend(stripStreamingReasoning(buffer.toString()))
            }

            override fun onCompleteResponse(completeResponse: ChatResponse) {
                tokenUsageTracker.record(model, completeResponse.tokenUsage(), dialog)
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

    override fun streamGoogleAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters): Flow<String> {
        val chatModel = googleModel(model, apiKey, parameters)
        return streamingFlow("GoogleAI[$model]", model) { handler ->
            chatModel.chat(messages(prompt, UserMessage.from(prompt.user)), handler)
        }
    }

    override fun streamGoogleAIWithAudio(
        model: String,
        apiKey: String,
        prompt: ModelPrompt,
        parameters: ModelParameters,
        wav: ByteArray,
    ): Flow<String> {
        val chatModel = googleModel(model, apiKey, parameters)
        val message = UserMessage.from(audioMessageParts(prompt.user, wavContent(wav)))
        return streamingFlow("GoogleAI-voice[$model]", model) { handler ->
            chatModel.chat(messages(prompt, message), handler)
        }
    }

    private fun googleModel(model: String, apiKey: String, parameters: ModelParameters): GoogleAiGeminiStreamingChatModel {
        val builder = GoogleAiGeminiStreamingChatModel.builder()
            .httpClientBuilder(jdkClientBuilder)
            .baseUrl(GOOGLE_AI_BASE_URL_LANG_CHAIN)
            .apiKey(apiKey)
            .modelName(model)
            .temperature(parameters.temperature)
            .topP(parameters.topP)
            .topK(parameters.topK)
            .maxOutputTokens(parameters.maxOutputTokens)
            .logRequestsAndResponses(false)
            .timeout(REQUEST_TIMEOUT)
            .listeners(listOf(ChatLogger()))
        parameters.thinking?.let { level ->
            builder.thinkingConfig(
                GeminiThinkingConfig.builder()
                    .thinkingLevel(GeminiThinkingConfig.GeminiThinkingLevel.valueOf(level.uppercase()))
                    .includeThoughts(false)
                    .build()
            ).returnThinking(false)
        }
        return builder.build()
    }

    override fun streamOpenAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters): Flow<String> {
        val chatModel = openAiModel(model, apiKey, parameters)
        return streamingFlow("OpenAI[$model]", model) { handler ->
            chatModel.chat(messages(prompt, UserMessage.from(prompt.user)), handler)
        }
    }

    override fun streamOpenAIWithAudio(
        model: String,
        apiKey: String,
        prompt: ModelPrompt,
        parameters: ModelParameters,
        wav: ByteArray,
    ): Flow<String> {
        val chatModel = openAiModel(model, apiKey, parameters)
        val message = UserMessage.from(audioMessageParts(prompt.user, wavContent(wav)))
        return streamingFlow("OpenAI-voice[$model]", model) { handler ->
            chatModel.chat(messages(prompt, message), handler)
        }
    }

    private fun openAiModel(model: String, apiKey: String, parameters: ModelParameters): OpenAiStreamingChatModel {
        val builder = OpenAiStreamingChatModel.builder()
            .httpClientBuilder(jdkClientBuilder)
            .apiKey(apiKey)
            .modelName(model)
            .temperature(parameters.temperature)
            .topP(parameters.topP)
            .maxCompletionTokens(parameters.maxOutputTokens)
            .logRequests(false)
            .logResponses(false)
            .timeout(REQUEST_TIMEOUT)
            .listeners(listOf(ChatLogger()))
        parameters.thinking?.let { builder.reasoningEffort(it) }
        parameters.verbosity?.let { builder.customParameters(mapOf("verbosity" to it)) }
        return builder.build()
    }

    override fun streamClaude(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters): Flow<String> {
        val builder = AnthropicStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(model)
            .maxTokens(parameters.maxOutputTokens)
            .logRequests(false)
            .logResponses(false)
            .timeout(REQUEST_TIMEOUT)
            .listeners(listOf(ChatLogger()))
        parameters.thinking?.let { builder.thinkingType(it) }
        val chatModel = builder.build()
        return streamingFlow("Claude[$model]", model) { handler ->
            chatModel.chat(messages(prompt, UserMessage.from(prompt.user)), handler)
        }
    }

    /** The instructions as the system message (left out when blank), then the message itself. */
    private fun messages(prompt: ModelPrompt, user: UserMessage): List<ChatMessage> =
        listOfNotNull(prompt.system.takeIf { it.isNotBlank() }?.let(SystemMessage::from), user)

    private fun wavContent(wav: ByteArray) = AudioContent(Base64.getEncoder().encodeToString(wav), "audio/wav")

}
