package di

import mappers.domain.ApplicationInfoMapper
import mappers.network.OllamaModelsResponseMapper
import mappers.network.OpenAIGenerationResponseMapper
import mappers.network.OpenAIModelsResponseMapper
import mappers.network.TranslateWithGoogleAiRequestMapper
import mappers.network.TranslateWithGoogleAiResponseMapper
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mappersModule = module {

    singleOf(::ApplicationInfoMapper)
    singleOf(::OllamaModelsResponseMapper)
    singleOf(::OpenAIGenerationResponseMapper)
    singleOf(::OpenAIModelsResponseMapper)
    singleOf(::TranslateWithGoogleAiRequestMapper)
    singleOf(::TranslateWithGoogleAiResponseMapper)

}