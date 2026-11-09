package utils

import dev.langchain4j.exception.AuthenticationException
import dev.langchain4j.exception.ContentFilteredException
import dev.langchain4j.exception.HttpException
import dev.langchain4j.exception.InternalServerException
import dev.langchain4j.exception.InvalidRequestException
import dev.langchain4j.exception.ModelNotFoundException
import dev.langchain4j.exception.RateLimitException
import models.domain.TranslationMethod
import models.domain.resolveTranslationMethod
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationFailoverTest {

    /** langchain4j wraps each provider error twice in its typed exception, around an HttpException. */
    private fun wrapped(status: Int, body: String, type: (String, Throwable) -> Throwable): Throwable {
        val http = HttpException(status, body)
        return type(body, type(body, http))
    }

    // Bodies as the providers returned them on 2026-11-09 (shortened where long).
    private val googleBadKey = """{ "error": { "code": 400, "message": "API key not valid. Please pass a valid API key.", "status": "INVALID_ARGUMENT", "details": [ { "reason": "API_KEY_INVALID" } ] } }"""
    private val googlePerMinute = """{ "error": { "code": 429, "message": "You exceeded your current quota, please check your plan and billing details. * Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 5, model: gemini-3.6-flash, description: Generate content requests per minute limit exceeded on Free Tier. Please retry in 41.218s." } }"""
    private val googlePerDay = """{ "error": { "code": 429, "message": "You exceeded your current quota, please check your plan and billing details. * Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 500, model: gemini-3.5-flash-lite Please retry in 11.615098148s.", "status": "RESOURCE_EXHAUSTED", "details": [ { "@type": "type.googleapis.com/google.rpc.QuotaFailure", "violations": [ { "quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier" } ] } ] } }"""
    private val openAiBadKey = """{ "error": { "message": "Incorrect API key provided: sk-inval********-key.", "type": "invalid_request_error", "code": "invalid_api_key" }, "status": 401 }"""
    private val openAiNoCredit = """{ "error": { "message": "You exceeded your current quota, please check your plan and billing details.", "type": "insufficient_quota", "code": "insufficient_quota" } }"""
    private val openAiRpm = """{ "error": { "message": "Rate limit reached for gpt-audio-1.5 on requests per min (RPM): Limit 500, Used 500. Please try again in 120ms.", "type": "requests", "code": "rate_limit_exceeded" } }"""
    private val openAiPerDay = """{ "error": { "message": "Rate limit reached for gpt-6.1-terra on requests per day (RPD): Limit 10000, Used 10000. Please try again in 8.64s.", "type": "requests", "code": "rate_limit_exceeded" } }"""
    private val openAiAudioOnly = """{ "error": { "message": "This model requires that either input content or output modality contain audio.", "type": "invalid_request_error", "code": "invalid_value" } }"""

    @Test
    fun `provider errors are sorted by what they mean for the method`() {
        assertEquals(FailureKind.AUTH, classifyFailure(wrapped(400, googleBadKey, ::InvalidRequestException)).kind)
        assertEquals(FailureKind.AUTH, classifyFailure(wrapped(401, openAiBadKey, ::AuthenticationException)).kind)
        assertEquals(FailureKind.AUTH, classifyFailure(AuthenticationException("""{"type":"error","error":{"type":"authentication_error"}}""")).kind)
        assertEquals(FailureKind.NOT_FOUND, classifyFailure(wrapped(404, "models/x is not found", ::ModelNotFoundException)).kind)
        assertEquals(FailureKind.BAD_REQUEST, classifyFailure(wrapped(400, openAiAudioOnly, ::InvalidRequestException)).kind)
        assertEquals(FailureKind.SERVER, classifyFailure(wrapped(503, "The model is overloaded", ::InternalServerException)).kind)
        assertEquals(FailureKind.SERVER, classifyFailure(HttpException(529, """{"type":"overloaded_error"}""")).kind)
        assertEquals(FailureKind.CONTENT_FILTERED, classifyFailure(ContentFilteredException("blocked")).kind)
    }

    @Test
    fun `a daily quota, a per-minute limit and an empty account are told apart`() {
        val perMinute = classifyFailure(wrapped(429, googlePerMinute, ::RateLimitException))
        assertEquals(FailureKind.RATE_LIMIT, perMinute.kind)
        assertEquals(41_218L, perMinute.retryAfterMillis)
        val googleDay = classifyFailure(wrapped(429, googlePerDay, ::RateLimitException))
        assertEquals(FailureKind.DAILY_QUOTA, googleDay.kind)
        assertNull(googleDay.retryAfterMillis, "Google's retry hint is the minute window; its day lasts until midnight Pacific")
        assertEquals(FailureKind.BILLING, classifyFailure(wrapped(429, openAiNoCredit, ::RateLimitException)).kind)
        val openAiDay = classifyFailure(wrapped(429, openAiPerDay, ::RateLimitException))
        assertEquals(FailureKind.DAILY_QUOTA, openAiDay.kind)
        assertEquals(8_640L, openAiDay.retryAfterMillis, "OpenAI's day is a rolling window: it says when to try again")
        val openAi = classifyFailure(wrapped(429, openAiRpm, ::RateLimitException))
        assertEquals(FailureKind.RATE_LIMIT, openAi.kind)
        assertEquals(120L, openAi.retryAfterMillis)
    }

    @Test
    fun `network trouble and silence are failures too`() {
        assertEquals(FailureKind.NETWORK, classifyFailure(RuntimeException(ConnectException("Connection refused"))).kind)
        assertEquals(FailureKind.NETWORK, classifyFailure(RuntimeException(UnknownHostException("api.openai.com"))).kind)
        assertEquals(FailureKind.TIMEOUT, classifyFailure(RuntimeException(HttpTimeoutException("request timed out"))).kind)
        assertEquals(FailureKind.TIMEOUT, classifyFailure(dev.langchain4j.exception.TimeoutException("timeout")).kind)
        assertEquals(FailureKind.UNKNOWN, classifyFailure(IllegalStateException("odd")).kind)
    }

    @Test
    fun `a failed method rests as long as its failure calls for, and a success ends it`() {
        var now = 1_000_000L
        val health = MethodHealth { now }
        val key = "voice:gemini-3.5-flash-lite"

        assertNull(health.recordFailure(key, Failure(FailureKind.BAD_ANSWER, "")), "a bad answer moves only that phrase on")
        assertNull(health.restOf(key))

        val first = health.recordFailure(key, Failure(FailureKind.SERVER, ""))!!
        assertEquals(now + 30_000, first.untilMillis)
        assertTrue(first.soft)
        assertEquals(now + 60_000, health.recordFailure(key, Failure(FailureKind.TIMEOUT, ""))!!.untilMillis, "doubles in a row")
        repeat(6) { health.recordFailure(key, Failure(FailureKind.NETWORK, "")) }
        assertEquals(now + MethodHealth.MAX_REST_MILLIS, health.restOf(key)!!.untilMillis, "capped")

        health.recordSuccess(key)
        assertNull(health.restOf(key))
        assertEquals(key, health.lastUsed.value)
        assertEquals(now + 30_000, health.recordFailure(key, Failure(FailureKind.SERVER, ""))!!.untilMillis, "a success restarts the count")

        now += 30_001
        assertNull(health.restOf(key), "the rest is over")

        val limited = health.recordFailure(key, Failure(FailureKind.RATE_LIMIT, "", retryAfterMillis = 41_218))!!
        assertEquals(now + 41_218, limited.untilMillis)
        assertTrue(!limited.soft)
        val daily = health.recordFailure(key, Failure(FailureKind.DAILY_QUOTA, ""))!!
        assertEquals(TranslationRateLimiter.nextPacificMidnight(now), daily.untilMillis, "Google: until the daily reset")
        val rolling = health.recordFailure("voice:gpt-audio-1.5", Failure(FailureKind.DAILY_QUOTA, "", retryAfterMillis = 8_640))!!
        assertEquals(now + 8_640, rolling.untilMillis, "OpenAI: as long as it asked")

        val auth = health.recordFailure("whisper-batch:gpt-6.1-terra", Failure(FailureKind.AUTH, ""))!!
        assertTrue(auth.untilSettingsChange)
        now += 24 * 3_600_000L
        assertTrue(health.restOf("whisper-batch:gpt-6.1-terra") != null, "a rejected key rests until the settings change")
        health.reset()
        assertNull(health.restOf("whisper-batch:gpt-6.1-terra"))
    }

    private val geminiVoice = resolveTranslationMethod("voice:gemini-3.5-flash-lite")!!
    private val openAiVoice = resolveTranslationMethod("voice:gpt-audio-1.5")!!
    private val terra = resolveTranslationMethod("whisper-batch:gpt-6.1-terra")!!
    private val chain = listOf(geminiVoice, openAiVoice, terra)

    private fun choose(
        skip: Set<String> = emptySet(),
        unusable: Map<TranslationMethod, String> = emptyMap(),
        rests: Map<TranslationMethod, MethodHealth.Rest> = emptyMap(),
        waits: Map<TranslationMethod, Long> = emptyMap(),
        reserved: MutableList<TranslationMethod> = ArrayList(),
    ) = chooseMethod(
        chain = chain,
        skip = skip.toSet(),
        now = 0L,
        unusableReason = { unusable[it] },
        rest = { rests[it] },
        reserve = { method -> (waits[method] ?: 0L).also { if (it == 0L) reserved += method } },
    )

    @Test
    fun `the primary takes the phrase while it can`() {
        val reserved = ArrayList<TranslationMethod>()
        assertEquals(MethodChoice.Use(geminiVoice), choose(reserved = reserved))
        assertEquals(listOf(geminiVoice), reserved, "only the chosen method's limiter counts the request")
    }

    @Test
    fun `an error on the primary moves the phrase to reserve 1, and one there to reserve 2`() {
        assertEquals(MethodChoice.Use(openAiVoice), choose(skip = setOf(geminiVoice.key)))
        assertEquals(MethodChoice.Use(terra), choose(skip = setOf(geminiVoice.key, openAiVoice.key)))
        assertIs<MethodChoice.NoneLeft>(choose(skip = chain.map { it.key }.toSet()))
    }

    @Test
    fun `a resting method is passed over, and a soft rest is used when nothing else can take the phrase`() {
        val hard = MethodHealth.Rest(10_000, "daily quota used up", soft = false)
        val soft = MethodHealth.Rest(10_000, "server error", soft = true)
        assertEquals(MethodChoice.Use(openAiVoice), choose(rests = mapOf(geminiVoice to hard)))
        assertEquals(MethodChoice.Use(terra), choose(rests = mapOf(geminiVoice to soft, openAiVoice to hard)))
        assertEquals(
            MethodChoice.Use(geminiVoice),
            choose(rests = mapOf(geminiVoice to soft, openAiVoice to hard), unusable = mapOf(terra to "no key")),
            "with no healthy method left, the softly resting primary is tried rather than waited out",
        )
        val allHard = choose(rests = chain.associateWith { hard })
        assertIs<MethodChoice.Wait>(allHard)
        assertEquals(10_000, allHard.millis)
        assertEquals(3, allHard.reasons.size)
    }

    @Test
    fun `a moment's rate limit is waited for, a long one hands the phrase on`() {
        val short = choose(waits = mapOf(geminiVoice to 1_500L))
        assertIs<MethodChoice.Wait>(short)
        assertEquals(1_500, short.millis)
        assertEquals(MethodChoice.Use(openAiVoice), choose(waits = mapOf(geminiVoice to 40_000L)))
    }

    @Test
    fun `a method that cannot work is skipped and named`() {
        assertEquals(MethodChoice.Use(openAiVoice), choose(unusable = mapOf(geminiVoice to "no Google API key")))
        val none = choose(unusable = chain.associateWith { "no key" })
        assertIs<MethodChoice.NoneLeft>(none)
        assertEquals(3, none.reasons.size)
    }
}
