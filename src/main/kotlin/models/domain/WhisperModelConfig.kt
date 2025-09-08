package models.domain

data class WhisperModelConfig(
    val silenceThresholdPercents: Int,
    val maxSilenceMilliseconds: Long,
    val maxChunkDurationMilliseconds: Long,
)
