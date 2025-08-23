import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val kotlinxJsonConfig: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

val client: HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(kotlinxJsonConfig)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 60_000
    }
}
