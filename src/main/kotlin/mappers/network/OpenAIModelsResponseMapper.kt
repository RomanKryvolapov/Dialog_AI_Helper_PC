/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
package mappers.network

import mappers.base.BaseMapper
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.network.openai.OpenAIModelsResponse

class OpenAIModelsResponseMapper : BaseMapper<OpenAIModelsResponse, List<LlmModel>>() {

    override fun map(model: OpenAIModelsResponse): List<LlmModel> {
        return model.data.map {
            LlmModel(
                id = it.id ?: "",
                description = "",
                engine = LlmModelEngine.GOOGLE,
                googleAiModel = null
            )
        }
    }

}