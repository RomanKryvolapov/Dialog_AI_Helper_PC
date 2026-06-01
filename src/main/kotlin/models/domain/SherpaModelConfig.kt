package models.domain

data class SherpaModelConfig(
    val numThreads: Int = 2,
    val decodingMethod: String = "greedy_search",
    val maxActivePaths: Int = 4,
    val splitMaxWords: Int = 12,
)
