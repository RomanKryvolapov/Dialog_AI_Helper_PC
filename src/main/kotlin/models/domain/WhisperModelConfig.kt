package models.domain

data class WhisperModelConfig(
    val silenceThresholdPercents: Int,
    val maxSilenceMilliseconds: Long,
    val maxChunkDurationMilliseconds: Long,
    val maxConcurrentProcesses: Int = 5,
    val streamingStepMilliseconds: Long = 700,
    val streamingMinChars: Int = 20,
)
