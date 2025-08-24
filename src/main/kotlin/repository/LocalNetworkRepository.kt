package repository

import client
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mappers.network.OpenAIModelsResponseMapper
import models.domain.LlmModel
import models.network.OpenAIModelsResponse

object LocalNetworkRepository {

    suspend fun getLmStudioModels(
        port: String,
    ): List<LlmModel> {
        val url = "http://localhost:$port/v1/models"
        val response = client.get(url) {
            contentType(ContentType.Application.Json)
        }
        val body = response.body<OpenAIModelsResponse>()
        return  OpenAIModelsResponseMapper.map(body)
    }

}