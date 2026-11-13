package utils

import dev.langchain4j.exception.AuthenticationException
import dev.langchain4j.exception.ContentFilteredException
import dev.langchain4j.exception.HttpException
import dev.langchain4j.exception.InternalServerException
import dev.langchain4j.exception.InvalidRequestException
import dev.langchain4j.exception.ModelNotFoundException
import dev.langchain4j.exception.RateLimitException
import dev.langchain4j.exception.UnresolvedModelServerException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException

/**
 * What went wrong when a translation method handled a phrase. Every kind makes the method in use hand the
 * dialog over to the next one in the chain (see ActiveMethod); the kind is what the row and the status say.
 */
enum class FailureKind(val reason: String) {
    /** 429 for a per-minute limit: the provider asks to slow down. */
    RATE_LIMIT("rate limit"),
    /**
     * 429 for a quota counted per day. Google's resets at midnight Pacific (its "retry in" hint is only
     * the per-minute window); OpenAI's is a rolling window, and its "try again in" is when it frees up.
     */
    DAILY_QUOTA("daily quota used up"),
    /** OpenAI's 429 "insufficient_quota": the account has no credit left. */
    BILLING("no credit left on the account"),
    /** 401/403, or Google's 400 API_KEY_INVALID: the key is wrong or lacks access. */
    AUTH("API key rejected"),
    /** 404: the model is gone or the key cannot use it. */
    NOT_FOUND("model not found"),
    /** Any other 4xx: the provider refused the request itself. */
    BAD_REQUEST("request rejected"),
    /** 5xx, including "overloaded". */
    SERVER("server error"),
    /** Could not reach the provider: no connection, DNS, a reset socket. */
    NETWORK("network error"),
    /** No answer, or the answer stopped arriving, in time. */
    TIMEOUT("no answer in time"),
    /** The provider's safety filter blocked the answer. */
    CONTENT_FILTERED("answer blocked by the provider's filter"),
    /** The answer arrived but cannot be used: empty, or not in the agreed format. */
    BAD_ANSWER("unusable answer"),
    /** A recorded phrase falling back to a text method, and Whisper could not transcribe it. */
    TRANSCRIPTION("Whisper could not transcribe the phrase"),
    /** Anything else; treated like a server error. */
    UNKNOWN("unexpected error"),
}

data class Failure(
    val kind: FailureKind,
    /** The provider's own message, for the log. */
    val detail: String,
    /**
     * How long the provider asked to wait, when it said: for a per-minute limit, and for OpenAI's rolling
     * per-day one. Null for Google's per-day quota, which lasts until its midnight reset whatever it says.
     */
    val retryAfterMillis: Long? = null,
)

private val DAILY_QUOTA = Regex("(?i)per ?day")
private val RETRY_AFTER = Regex("(?i)(?:retry|try again) in ([0-9]+(?:\\.[0-9]+)?)\\s*(ms|s)\\b")
private val STATUS_IN_BODY = Regex("\"(?:code|status)\"\\s*:\\s*(\\d{3})\\b")

/**
 * Sorts a failed request into a [FailureKind]. langchain4j maps each provider's HTTP errors onto its own
 * exception types (checked on 2026-11-09 against Google, OpenAI and Anthropic with a bad key, an unknown
 * model, a rejected parameter and Google's free-tier per-minute 429); the status code and the provider's
 * error body inside the message settle what the type alone does not — a Google key error is a 400, and a
 * daily quota differs from a per-minute one only in the body.
 */
fun classifyFailure(error: Throwable): Failure {
    val causes = generateSequence(error) { it.cause }.take(10).toList()
    val message = causes.mapNotNull { it.message }.distinct().joinToString(" | ")
    val status = causes.firstNotNullOfOrNull { (it as? HttpException)?.statusCode() }
        ?: STATUS_IN_BODY.find(message)?.groupValues?.get(1)?.toIntOrNull()
    fun any(test: (Throwable) -> Boolean) = causes.any(test)
    val kind = when {
        any { it is RateLimitException } || status == 429 -> when {
            "insufficient_quota" in message -> FailureKind.BILLING
            DAILY_QUOTA.containsMatchIn(message) -> FailureKind.DAILY_QUOTA
            else -> FailureKind.RATE_LIMIT
        }
        any { it is AuthenticationException } || status == 401 || status == 403 || "API_KEY_INVALID" in message ->
            FailureKind.AUTH
        any { it is ModelNotFoundException } || status == 404 -> FailureKind.NOT_FOUND
        any { it is ContentFilteredException } -> FailureKind.CONTENT_FILTERED
        any { it is InternalServerException } || (status != null && status >= 500) -> FailureKind.SERVER
        any {
            it is dev.langchain4j.exception.TimeoutException || it is java.util.concurrent.TimeoutException ||
                it is HttpTimeoutException || it is SocketTimeoutException || it is TimeoutCancellationException
        } -> FailureKind.TIMEOUT
        any { it is InvalidRequestException } || (status != null && status in 400..499) -> FailureKind.BAD_REQUEST
        any { it is UnresolvedModelServerException || it is IOException } -> FailureKind.NETWORK
        else -> FailureKind.UNKNOWN
    }
    // Google's per-day body still says "Please retry in 11.6s" — the per-minute window — yet the quota is
    // spent until midnight Pacific; its bodies name googleapis.com (the quota metric, the error details).
    val googleDaily = kind == FailureKind.DAILY_QUOTA && "googleapis.com" in message
    val retryAfter = if (kind == FailureKind.RATE_LIMIT || (kind == FailureKind.DAILY_QUOTA && !googleDaily)) {
        RETRY_AFTER.find(message)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull() ?: return@let null
            (if (match.groupValues[2].lowercase() == "ms") amount else amount * 1000).toLong().coerceAtLeast(1)
        }
    } else null
    return Failure(kind, message.take(500), retryAfter)
}

/** A wait this short for the model's own rate limit is waited out; a longer one counts as a failure of the method. */
const val SHORT_WAIT_MILLIS = 3_000L
