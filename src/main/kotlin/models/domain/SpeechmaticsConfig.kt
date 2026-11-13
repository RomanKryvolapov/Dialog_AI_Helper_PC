package models.domain

/** Settings → Speechmatics: where the realtime recogniser runs and how it listens. */
data class SpeechmaticsConfig(
    /** The realtime endpoint; the region keeps the audio there. */
    val endpoint: String = ENDPOINTS.first(),
    /** "standard", or "enhanced": more accurate, slower to settle. */
    val model: String = "enhanced",
    /** Label each phrase with who said it (S1, S2…), so the Questions mode knows who asked. */
    val speakerLabels: Boolean = true,
) {
    companion object {
        val ENDPOINTS = listOf(
            "wss://eu.rt.speechmatics.com/v2",
            "wss://us.rt.speechmatics.com/v2",
            "wss://global.rt.speechmatics.com/v2",
        )
        val MODELS = listOf("standard", "enhanced")
    }
}

data class SpeechmaticsConfigNullable(
    val endpoint: String? = null,
    val model: String? = null,
    val speakerLabels: Boolean? = null,
)
