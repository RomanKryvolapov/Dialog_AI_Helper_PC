package repository

import kotlinx.coroutines.flow.Flow
import models.domain.ModelParameters
import models.domain.ModelPrompt

/**
 * Streams model answers in real time. Each emission is the current cumulative answer text (reasoning stripped
 * where possible); the flow completes when the model finishes, or throws on error / cancellation.
 * [parameters] are sent as they are: the caller narrows them to what the model accepts (effectiveParameters).
 */
interface CloudRepository {

    fun streamGoogleAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters): Flow<String>

    /** Like [streamGoogleAI], with one recorded phrase ([wav], 16 kHz mono WAV) placed where the prompt marks it. */
    fun streamGoogleAIWithAudio(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters, wav: ByteArray): Flow<String>

    fun streamOpenAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters): Flow<String>

    /** Like [streamOpenAI], with one recorded phrase ([wav], 16 kHz mono WAV) placed where the prompt marks it. */
    fun streamOpenAIWithAudio(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters, wav: ByteArray): Flow<String>

    fun streamClaude(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters): Flow<String>

}
