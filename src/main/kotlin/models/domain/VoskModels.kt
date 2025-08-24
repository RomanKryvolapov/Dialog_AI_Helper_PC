package models.domain

import models.common.TypeEnum

enum class VoskModels(
    override val type: String,
    val path: String,
) : TypeEnum {
    VOSK_MODEL_EN_US_0_22(
        type = "English 0.22 size 1.8 GB",
        path = "src/main/resources/models/vosk-model-en-us-0.22",
    ),
    VOSK_MODEL_EN_US_0_22_LGRAPH(
        type = "English 0.22 lgraph size 128 MB",
        path = "src/main/resources/models/vosk-model-en-us-0.22-lgraph",
    ),
    VOSK_MODEL_EN_US_0_15_SMALL(
        type = "English 0.15 small size 40 MB",
        path = "src/main/resources/models/vosk-model-small-en-us-0.15",
    ),

}