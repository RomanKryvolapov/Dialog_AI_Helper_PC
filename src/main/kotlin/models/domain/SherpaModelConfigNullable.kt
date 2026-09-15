package models.domain

data class SherpaModelConfigNullable(
    val numThreads: Int? = null,
    val decodingMethod: String? = null,
    val maxActivePaths: Int? = null,
    val splitMaxWords: Int? = null,
    val maxHangMillis: Long? = null,
)
