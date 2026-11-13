package utils

import dev.langchain4j.exception.AuthenticationException
import dev.langchain4j.exception.ContentFilteredException
import dev.langchain4j.exception.HttpException
import dev.langchain4j.exception.InternalServerException
import dev.langchain4j.exception.InvalidRequestException
import dev.langchain4j.exception.ModelNotFoundException
import dev.langchain4j.exception.RateLimitException
import models.domain.TranslationMethod
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
