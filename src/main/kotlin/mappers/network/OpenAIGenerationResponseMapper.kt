package mappers.network

import mappers.base.BaseMapper
import models.network.OpenAIGenerationResponse

object OpenAIGenerationResponseMapper : BaseMapper<OpenAIGenerationResponse, String>() {

    override fun map(model: OpenAIGenerationResponse): String {
        return model.choices.firstOrNull()?.message?.content.orEmpty()
    }

}