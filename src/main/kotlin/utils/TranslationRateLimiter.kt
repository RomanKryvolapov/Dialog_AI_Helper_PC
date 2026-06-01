package utils

import java.time.Instant
import java.time.ZoneId

/**
 * Client-side rate limiter for a single LLM model, tracking the Google AI Studio limit
 * dimensions: requests/min (RPM), tokens/min (TPM), requests/day (RPD), tokens/day (TPD).
 *
 * RPD/TPD windows reset at midnight Pacific time, matching Google's quota reset.
 * Limits are advisory (soft): a real 429 from the server takes precedence via [penalize].
 * A null limit means "no constraint on that dimension".
 *
 * [tryReserve] is non-suspending and returns the number of milliseconds the caller must wait
 * (0 = reserved successfully); a single scheduler coroutine is expected to delay and retry.
 * Thread-safe.
 */
class TranslationRateLimiter {

    private val lock = Any()
    private val zonePacific = ZoneId.of("America/Los_Angeles")

    private var rpm: Int? = null
    private var tpm: Int? = null
    private var rpd: Int? = null
    private var tpd: Int? = null

    private val requestTimes = ArrayDeque<Long>()           // ms timestamps within the last minute
    private val tokenEvents = ArrayDeque<Pair<Long, Int>>() // (ms timestamp, tokens) within the last minute
    private var dayRequests = 0
    private var dayTokens = 0L
    private var dayResetAt = 0L
    private var cooldownUntil = 0L

    fun configure(rpm: Int?, tpm: Int?, rpd: Int?, tpd: Int?) {
        synchronized(lock) {
            this.rpm = rpm?.takeIf { it > 0 }
            this.tpm = tpm?.takeIf { it > 0 }
            this.rpd = rpd?.takeIf { it > 0 }
            this.tpd = tpd?.takeIf { it > 0 }
        }
    }

    /** @return 0 if the request slot was reserved; otherwise the number of ms to wait (nothing reserved). */
    fun tryReserve(estimatedTokens: Int): Long {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            prune(now)

            var wait = 0L
            if (now < cooldownUntil) wait = maxOf(wait, cooldownUntil - now)
            rpm?.let { limit ->
                if (requestTimes.size >= limit) wait = maxOf(wait, requestTimes.first() + WINDOW_MS - now)
            }
            tpm?.let { limit ->
                val used = tokenEvents.sumOf { it.second }
                if (used + estimatedTokens > limit && tokenEvents.isNotEmpty()) {
                    wait = maxOf(wait, tokenEvents.first().first + WINDOW_MS - now)
                }
            }
            rpd?.let { limit -> if (dayRequests >= limit) wait = maxOf(wait, dayResetAt - now) }
            tpd?.let { limit -> if (dayTokens + estimatedTokens > limit) wait = maxOf(wait, dayResetAt - now) }

            if (wait > 0L) return wait

            requestTimes.addLast(now)
            tokenEvents.addLast(now to estimatedTokens)
            dayRequests += 1
            dayTokens += estimatedTokens
            return 0L
        }
    }

    /** Called when the server returns 429: forces a cooldown regardless of local counters. */
    fun penalize(retryAfterMillis: Long?) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            cooldownUntil = maxOf(cooldownUntil, now + (retryAfterMillis ?: DEFAULT_COOLDOWN_MS))
        }
    }

    private fun prune(now: Long) {
        val windowStart = now - WINDOW_MS
        while (requestTimes.isNotEmpty() && requestTimes.first() <= windowStart) requestTimes.removeFirst()
        while (tokenEvents.isNotEmpty() && tokenEvents.first().first <= windowStart) tokenEvents.removeFirst()
        if (now >= dayResetAt) {
            dayRequests = 0
            dayTokens = 0L
            dayResetAt = nextPacificMidnight(now)
        }
    }

    private fun nextPacificMidnight(now: Long): Long =
        Instant.ofEpochMilli(now).atZone(zonePacific)
            .toLocalDate().plusDays(1).atStartOfDay(zonePacific)
            .toInstant().toEpochMilli()

    companion object {
        private const val WINDOW_MS = 60_000L
        private const val DEFAULT_COOLDOWN_MS = 60_000L
    }
}
