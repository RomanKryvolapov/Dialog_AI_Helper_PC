package utils

import dev.langchain4j.data.message.AudioContent
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import org.slf4j.LoggerFactory

class ChatLogger : ChatModelListener {

    private val log = LoggerFactory.getLogger("ChatLogger")

    override fun onRequest(request: ChatModelRequestContext) {
        val messages = request.chatRequest().messages().joinToString("\n---\n") { describe(it) }
        log.debug("LLM REQUEST:\n$messages")
    }

    override fun onResponse(response: ChatModelResponseContext) {
        val text = runCatching { response.chatResponse().aiMessage().text() }
            .getOrNull()
            .orEmpty()
        log.debug("LLM RESPONSE ({} chars):\n{}", text.length, text)
    }

    override fun onError(context: ChatModelErrorContext) {
        log.error("LLM ERROR: ${context.error().message}", context.error())
    }

    companion object {
        /**
         * A message as the log shows it: text as is, any other part only by kind and size — a voice
         * phrase's audio is hundreds of KB of base64 that would flood the log and the Console tab.
         */
        fun describe(msg: ChatMessage): String {
            if (msg !is UserMessage) return runCatching { msg.toString() }.getOrDefault(msg.javaClass.simpleName)
            return msg.contents().joinToString("\n") { content ->
                when (content) {
                    is TextContent -> content.text()
                    is AudioContent -> "[audio, ${content.audio().base64Data()?.length ?: 0} base64 chars]"
                    else -> "[${content.type()}]"
                }
            }
        }
    }
}
