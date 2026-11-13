package models.domain

/**
 * One request as the model receives it: [system] is the instructions (sent as the system message, left
 * out when blank), [user] the message they apply to — for a voice request with the recording placed where
 * it carries VOICE_AUDIO_MARK.
 */
data class ModelPrompt(val system: String, val user: String)
