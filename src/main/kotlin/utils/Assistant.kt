package utils

import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V

interface Assistant {

    @UserMessage("{{input}}")
    fun chat(@V("input") message: String): String

}