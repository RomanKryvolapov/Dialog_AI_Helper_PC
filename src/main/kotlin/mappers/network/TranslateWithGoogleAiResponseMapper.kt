package mappers.network

import mappers.base.BaseMapper
import models.network.googleai.TranslateWithGoogleAiResponse

class TranslateWithGoogleAiResponseMapper : BaseMapper<TranslateWithGoogleAiResponse, String>() {

    override fun map(model: TranslateWithGoogleAiResponse): String {
        return model.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
    }

}