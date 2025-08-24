package mappers.network

import mappers.base.BaseMapper
import models.network.googleai.ContentRequest
import models.network.googleai.PartRequest
import models.network.googleai.TranslateWithGoogleAiRequest

class TranslateWithGoogleAiRequestMapper : BaseMapper<String, TranslateWithGoogleAiRequest>() {

    override fun map(model: String): TranslateWithGoogleAiRequest {
        return TranslateWithGoogleAiRequest(
            contents = listOf(
                ContentRequest(
                    role = "user",
                    parts = listOf(
                        PartRequest(
                            text = model
                        )
                    )
                )
            )
        )
    }

}