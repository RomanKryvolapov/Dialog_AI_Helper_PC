package models.domain

data class SherpaModelConfig(
    val numThreads: Int = 2,
    val decodingMethod: String = "greedy_search",
    val maxActivePaths: Int = 4,
    val splitMaxWords: Int = 12,
    val maxHangMillis: Long = 8000,
) {
    companion object {
        val DECODING_METHODS = listOf("greedy_search", "modified_beam_search")
    }
}
