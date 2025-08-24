package utils

import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext

class ChatLogger : ChatModelListener {

    override fun onRequest(request: ChatModelRequestContext) {
        println("ON_LLM_START:")
        println(request)
    }

    override fun onResponse(response: ChatModelResponseContext) {
        println("ON_LLM_END:")
        println(response)
    }

    override fun onError(context: ChatModelErrorContext) {
        println("ON_LLM_ERROR:")
        println(context.error().message)
    }

}