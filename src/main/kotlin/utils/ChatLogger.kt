package utils

import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import org.slf4j.LoggerFactory

class ChatLogger : ChatModelListener {

    private val log = LoggerFactory.getLogger("ChatLogger")

    override fun onRequest(request: ChatModelRequestContext) {
        val messages = request.chatRequest().messages().joinToString("\n---\n") { msg ->
            runCatching { msg.toString() }.getOrDefault(msg.javaClass.simpleName)
        }
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

}
