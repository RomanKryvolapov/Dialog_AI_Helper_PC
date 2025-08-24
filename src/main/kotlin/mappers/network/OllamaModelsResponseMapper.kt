package mappers.network

import mappers.base.BaseMapper
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.network.ollama.OllamaModelsResponse

class OllamaModelsResponseMapper : BaseMapper<OllamaModelsResponse, List<LlmModel>>() {

    override fun map(model: OllamaModelsResponse): List<LlmModel> {
        return model.models.map {
            LlmModel(
                id = it.name ?: "",
                description = "",
                engine = LlmModelEngine.OLLAMA,
                googleAiModel = null
            )
        }
    }

}