package repository

import dev.langchain4j.model.input.Prompt
import kotlinx.coroutines.flow.Flow

interface CloudRepository {

    /**
     * Streams the model's answer in real time.
     * Each emission is the current cumulative answer text (reasoning stripped where possible).
     * The flow completes when the model finishes, or throws on error / cancellation.
     */
    fun streamGoogleAI(model: String, apiKey: String, prompt: Prompt): Flow<String>

    fun streamOpenAI(model: String, apiKey: String, prompt: Prompt): Flow<String>

    fun streamClaude(model: String, apiKey: String, prompt: Prompt): Flow<String>

}
