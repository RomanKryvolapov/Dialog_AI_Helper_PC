package di

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import models.domain.ApplicationInfoNullable
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.AudioChunker
import utils.AudioChunkerImpl
import utils.DialogLogger
import utils.DialogLoggerImpl
import utils.WhisperUtil
import utils.WhisperUtilImpl
import java.time.Duration
import java.util.prefs.Preferences

val utilsModule = module {

    singleOf(::AudioChunkerImpl) bind AudioChunker::class
    singleOf(::WhisperUtilImpl) bind WhisperUtil::class
    singleOf(::DialogLoggerImpl) bind DialogLogger::class

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
            .readTimeout(Duration.ofSeconds(90))
    }

    single<Moshi> {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    single<JsonAdapter<ApplicationInfoNullable>> {
        get<Moshi>().adapter(ApplicationInfoNullable::class.java)
    }

}
