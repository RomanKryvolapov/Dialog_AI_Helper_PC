package mappers

import models.network.ContentRequest
import models.network.PartRequest
import models.network.TranslateWithGoogleAiRequest
import utils.BaseMapper

object TranslateWithGoogleAiRequestMapper : BaseMapper<String, TranslateWithGoogleAiRequest>() {

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