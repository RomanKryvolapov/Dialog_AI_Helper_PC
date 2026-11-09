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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import models.domain.TranslationMethod
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What went wrong when a translation method handled a phrase. Every kind sends the phrase on to the
 * next method in the chain; the kind decides how long the failed method then rests (see [MethodHealth]).
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

/**
 * Which translation methods are resting after a failure, and which one translated the latest phrase.
 *
 * A method that failed rests before it is tried again, so the phrases meanwhile go straight to the next
 * method in the chain instead of each first failing on it; when the rest is over, the next phrase tries it
 * again, and a success puts it back in front. How long it rests depends on the failure:
 * - a per-minute limit — as long as the provider asked, or a minute;
 * - a used-up daily quota — as long as the provider asked (OpenAI), or until the daily reset at midnight
 *   Pacific (Google);
 * - no credit left on the account — 15 minutes;
 * - a rejected key or a missing model — until the user changes the keys or the chain ([reset]);
 * - a server, network or request error, or no answer in time — 30 seconds, doubling with every further
 *   failure in a row, up to 5 minutes. Such a rest is soft: when no other method can take the phrase,
 *   the method is tried anyway rather than leaving the phrase waiting.
 * - an unusable answer, a filtered answer, a failed transcription — no rest: that phrase moves on, the
 *   method stays in front for the next one.
 */
class MethodHealth(private val clock: () -> Long = System::currentTimeMillis) {

    data class Rest(val untilMillis: Long, val reason: String, val soft: Boolean) {
        val untilSettingsChange: Boolean get() = untilMillis == Long.MAX_VALUE

        fun describe(): String = when {
            untilSettingsChange -> "$reason — until the settings change"
            else -> "$reason — until ${CLOCK.format(Instant.ofEpochMilli(untilMillis).atZone(ZoneId.systemDefault()))}"
        }
    }

    private val lock = Any()
    private val failuresInRow = HashMap<String, Int>()

    private val restsState = MutableStateFlow<Map<String, Rest>>(emptyMap())

    /** Methods resting now, by [TranslationMethod.key]. */
    val rests: StateFlow<Map<String, Rest>> = restsState

    private val lastUsedState = MutableStateFlow<String?>(null)

    /** The key of the method that translated the latest phrase. */
    val lastUsed: StateFlow<String?> = lastUsedState

    /** The rest [key] is on now, or null if it may be used. */
    fun restOf(key: String): Rest? = synchronized(lock) {
        pruneExpiredLocked()
        restsState.value[key]
    }

    /** Ends the rests that are over (the UI calls this to repaint; the scheduler's queries do it too). */
    fun pruneExpired() = synchronized(lock) { pruneExpiredLocked() }

    fun recordSuccess(key: String) = synchronized(lock) {
        failuresInRow.remove(key)
        restsState.value = restsState.value - key
        lastUsedState.value = key
    }

    /** Puts [key] to rest for as long as [failure] calls for; null when only this phrase moves on. */
    fun recordFailure(key: String, failure: Failure): Rest? = synchronized(lock) {
        val now = clock()
        val rest = when (failure.kind) {
            FailureKind.RATE_LIMIT ->
                Rest(now + (failure.retryAfterMillis ?: RATE_LIMIT_REST_MILLIS), failure.kind.reason, soft = false)
            FailureKind.DAILY_QUOTA -> Rest(
                failure.retryAfterMillis?.let { now + it } ?: TranslationRateLimiter.nextPacificMidnight(now),
                failure.kind.reason,
                soft = false,
            )
            FailureKind.BILLING -> Rest(now + BILLING_REST_MILLIS, failure.kind.reason, soft = false)
            FailureKind.AUTH, FailureKind.NOT_FOUND -> Rest(Long.MAX_VALUE, failure.kind.reason, soft = false)
            FailureKind.SERVER, FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.BAD_REQUEST, FailureKind.UNKNOWN -> {
                val inRow = (failuresInRow[key] ?: 0) + 1
                failuresInRow[key] = inRow
                val millis = (FIRST_REST_MILLIS shl (inRow - 1).coerceAtMost(10)).coerceAtMost(MAX_REST_MILLIS)
                Rest(now + millis, failure.kind.reason, soft = true)
            }
            FailureKind.BAD_ANSWER, FailureKind.CONTENT_FILTERED, FailureKind.TRANSCRIPTION -> null
        }
        if (rest != null) restsState.value = restsState.value + (key to rest)
        rest
    }

    /** The keys or the chain changed: every method gets a fresh chance. */
    fun reset() = synchronized(lock) {
        failuresInRow.clear()
        restsState.value = emptyMap()
    }

    private fun pruneExpiredLocked() {
        val now = clock()
        val current = restsState.value
        val live = current.filterValues { it.untilMillis > now }
        if (live.size != current.size) restsState.value = live
    }

    companion object {
        const val FIRST_REST_MILLIS = 30_000L
        const val MAX_REST_MILLIS = 300_000L
        const val RATE_LIMIT_REST_MILLIS = 60_000L
        const val BILLING_REST_MILLIS = 15 * 60_000L
        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

sealed interface MethodChoice {
    /** Send the phrase to [method]; its rate limiter has already counted the request. */
    data class Use(val method: TranslationMethod) : MethodChoice

    /** No method can take the phrase now; the soonest one can is in [millis]. */
    data class Wait(val millis: Long, val reasons: List<String>) : MethodChoice

    /** No method left in the chain can take this phrase at all. */
    data class NoneLeft(val reasons: List<String>) : MethodChoice
}

/** A wait this short for a method's per-minute limit beats handing the phrase to the next method. */
const val SHORT_WAIT_MILLIS = 3_000L

/**
 * Picks the method for a phrase: the first in [chain] (primary, then the reserves in order) that is not in
 * [skip] (failed this phrase already), can work for it ([unusableReason] is null — a key is set, the
 * phrase has the input the method needs), is not resting ([rest]) and whose rate limit lets the request
 * through now ([reserve] returns 0, counting it, or the milliseconds until it could).
 *
 * A method whose limit frees within [SHORT_WAIT_MILLIS] is waited for rather than passed over. When
 * nothing healthy can take the phrase, a method on a soft rest (after a passing error) is used rather than
 * leaving the phrase to wait out the rest.
 */
fun chooseMethod(
    chain: List<TranslationMethod>,
    skip: Set<String>,
    now: Long,
    unusableReason: (TranslationMethod) -> String?,
    rest: (TranslationMethod) -> MethodHealth.Rest?,
    reserve: (TranslationMethod) -> Long,
): MethodChoice {
    val reasons = ArrayList<String>()
    val candidates = ArrayList<TranslationMethod>()
    for (method in chain) {
        if (method.key in skip) continue
        val unusable = unusableReason(method)
        if (unusable != null) reasons += "${method.shortTitle}: $unusable" else candidates += method
    }
    if (candidates.isEmpty()) return MethodChoice.NoneLeft(reasons)

    var soonest = Long.MAX_VALUE
    val softResting = ArrayList<TranslationMethod>()
    for (method in candidates) {
        val resting = rest(method)
        if (resting != null && resting.untilMillis > now) {
            reasons += "${method.shortTitle}: ${resting.describe()}"
            if (resting.soft) softResting += method else soonest = minOf(soonest, resting.untilMillis - now)
            continue
        }
        val wait = reserve(method)
        if (wait == 0L) return MethodChoice.Use(method)
        if (wait <= SHORT_WAIT_MILLIS) return MethodChoice.Wait(wait, reasons)
        reasons += "${method.shortTitle}: rate limit, free in ${(wait + 999) / 1000} s"
        soonest = minOf(soonest, wait)
    }
    for (method in softResting) {
        val wait = reserve(method)
        if (wait == 0L) return MethodChoice.Use(method)
        soonest = minOf(soonest, wait)
    }
    return MethodChoice.Wait(soonest, reasons)
}
