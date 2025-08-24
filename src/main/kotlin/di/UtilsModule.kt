package di

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import models.domain.ApplicationInfoNullable
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.VoskRecognizer
import utils.VoskRecognizerImpl
import java.time.Duration
import java.util.prefs.Preferences

val utilsModule = module {

    singleOf(::VoskRecognizerImpl) bind VoskRecognizer::class

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

    single<Json> {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
    }

    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(get<Json>())
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
        }
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