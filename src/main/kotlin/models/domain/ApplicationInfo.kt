package models.domain

import models.common.ApplicationLanguage
import models.common.GoogleAiModelsEnum

data class ApplicationInfo(
    val lastSelectedDevice: String,
    val selectedModel: LlmModel,
    val selectedFromLanguage: ApplicationLanguage,
    val selectedToLanguage: ApplicationLanguage,
    val googleCloudToken: String,
    val prompt: String,
    val lastOpenedTab: Int,
    val promptsMap: Map<String, String>,
    val lmStudioPort: String,
    val lmStudioModels: List<LlmModel>,
)