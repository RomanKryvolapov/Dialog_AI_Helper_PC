package di

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import models.domain.ApplicationInfoNullable
import models.domain.ClaudeModelsEnum
import models.domain.GoogleAiModelsEnum
import models.domain.OpenAiModelsEnum
import models.domain.VoiceRecognitionEngine
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ApplicationInfoEnvOverlay
import utils.AudioChunker
import utils.AudioChunkerImpl
import utils.DialogLogger
import utils.DialogLoggerImpl
import utils.EnvSettings
import utils.EnvSettingsImpl
import utils.MethodHealth
import utils.TokenUsageTracker
import utils.SherpaOnnxRecognizer
import utils.SherpaOnnxRecognizerImpl
import utils.WhisperStreamingUtil
import utils.WhisperStreamingUtilImpl
import utils.WhisperUtil
import utils.WhisperUtilImpl
import java.time.Duration
import java.util.prefs.Preferences

private fun <T : Enum<T>> lenientEnumAdapter(type: Class<T>): JsonAdapter<T> =
    EnumJsonAdapter.create(type).withUnknownFallback(null).nullSafe()

val utilsModule = module {

    singleOf(::EnvSettingsImpl) bind EnvSettings::class
    singleOf(::ApplicationInfoEnvOverlay)

    singleOf(::AudioChunkerImpl) bind AudioChunker::class
    singleOf(::WhisperUtilImpl) bind WhisperUtil::class
    singleOf(::WhisperStreamingUtilImpl) bind WhisperStreamingUtil::class
    singleOf(::SherpaOnnxRecognizerImpl) bind SherpaOnnxRecognizer::class
    singleOf(::DialogLoggerImpl) bind DialogLogger::class
    single { MethodHealth() }
    singleOf(::TokenUsageTracker)

    single<Preferences> {
        Preferences.userRoot().node("PREFERENCES_DATABASE")
    }

    single<java.net.http.HttpClient.Builder> {
        java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
    }

    single<JdkHttpClientBuilder> {
        JdkHttpClientBuilder()
            .httpClientBuilder(get<java.net.http.HttpClient.Builder>())
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(45))
    }

    single<Moshi> {
        Moshi.Builder()
            // A model later dropped from the catalog must not fail the whole saved settings document
            // (every setting, API keys included, would silently fall back to defaults): its unknown
            // name reads as null, and the repository resolves the saved id to its successor or,
            // without one, treats the selected model as unset.
            .add(GoogleAiModelsEnum::class.java, lenientEnumAdapter(GoogleAiModelsEnum::class.java))
            .add(OpenAiModelsEnum::class.java, lenientEnumAdapter(OpenAiModelsEnum::class.java))
            .add(ClaudeModelsEnum::class.java, lenientEnumAdapter(ClaudeModelsEnum::class.java))
            // Same for a recogniser the app no longer offers (the old "Gemini voice" engine is a
            // translation method now): it reads as unset and the default recogniser applies.
            .add(VoiceRecognitionEngine::class.java, lenientEnumAdapter(VoiceRecognitionEngine::class.java))
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    single<JsonAdapter<ApplicationInfoNullable>> {
        get<Moshi>().adapter(ApplicationInfoNullable::class.java)
    }

}
