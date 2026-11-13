package utils

import models.domain.LlmModel

/**
 * One rate limiter per model id, shared by every request to that model — translations, summaries and questions —
 * because the provider counts them all against the same quota (Google per model). Each is set to the model's limits
 * with a safety margin.
 */
class RateLimiters {

    private companion object {
        const val SAFETY_MARGIN = 0.8
    }

    private val limiters = HashMap<String, TranslationRateLimiter>()

    fun forModel(model: LlmModel): TranslationRateLimiter {
        val limiter = synchronized(limiters) { limiters.getOrPut(model.id) { TranslationRateLimiter() } }
        limiter.configure(
            rpm = model.limitRequestsInMinute?.let { (it * SAFETY_MARGIN).toInt().coerceAtLeast(1) },
            tpm = model.limitTokensInMinute?.let { (it * SAFETY_MARGIN).toInt().coerceAtLeast(1) },
            rpd = model.limitRequestsInDay,
            tpd = model.limitTokensInDay,
        )
        return limiter
    }
}
