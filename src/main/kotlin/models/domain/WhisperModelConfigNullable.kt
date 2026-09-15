package models.domain

data class WhisperModelConfigNullable(
    val silenceThresholdPercents: Int? = null,
    val maxSilenceMilliseconds: Long? = null,
    val maxChunkDurationMilliseconds: Long? = null,
    val maxConcurrentProcesses: Int? = null,
    val streamingStepMilliseconds: Long? = null,
    val streamingMinChars: Int? = null,
)
