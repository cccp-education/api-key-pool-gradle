package com.cheroliv.graphify.apikey

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class RateLimitInfo(
    val limit: Long = -1,
    val remaining: Long = -1,
    val resetEpochSeconds: Long? = null,
    val retryAfterSeconds: Long? = null
) {
    val isExhausted: Boolean get() = remaining == 0L

    val usagePercent: Double get() {
        if (limit <= 0) return 0.0
        val used = limit - remaining.coerceAtLeast(0)
        return (used.toDouble() / limit) * 100.0
    }
}

object RateLimitHeadersParser {

    private val standardLimitKeys = setOf(
        "x-ratelimit-limit",
        "x-ratelimit-limit-requests",
        "anthropic-ratelimit-requests-limit",
        "x-ds-ratelimit-limit"
    )

    private val standardRemainingKeys = setOf(
        "x-ratelimit-remaining",
        "x-ratelimit-remaining-requests",
        "anthropic-ratelimit-requests-remaining",
        "x-ds-ratelimit-remaining"
    )

    private val standardResetKeys = setOf(
        "x-ratelimit-reset",
        "x-ratelimit-reset-requests",
        "anthropic-ratelimit-requests-reset",
        "x-ds-ratelimit-reset"
    )

    fun parse(headers: Map<String, String>): RateLimitInfo {
        val lowerHeaders = headers.mapKeys { it.key.lowercase() }

        val limit = lowerHeaders.entries
            .firstOrNull { it.key in standardLimitKeys }
            ?.value?.toLongOrNull() ?: -1

        val remaining = lowerHeaders.entries
            .firstOrNull { it.key in standardRemainingKeys }
            ?.value?.toLongOrNull() ?: -1

        val resetEpoch = lowerHeaders.entries
            .firstOrNull { it.key in standardResetKeys }
            ?.let { parseResetValue(it.value) }

        val retryAfter = lowerHeaders["retry-after"]
            ?.let { parseRetryAfter(it) }

        return RateLimitInfo(
            limit = limit,
            remaining = remaining,
            resetEpochSeconds = resetEpoch,
            retryAfterSeconds = retryAfter
        )
    }

    private fun parseResetValue(value: String): Long? {
        value.toLongOrNull()?.let { return it }

        val durationMatch = Regex("^(\\d+)s$").find(value)
        durationMatch?.let { return it.groupValues[1].toLongOrNull() }

        try {
            val parsed = ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
            return parsed.toEpochSecond()
        } catch (_: Exception) {
        }

        return null
    }

    private fun parseRetryAfter(value: String): Long? {
        value.toLongOrNull()?.let { return it }

        try {
            val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
            val parsed = ZonedDateTime.parse(value, formatter)
            val now = ZonedDateTime.now(parsed.zone)
            val diff = parsed.toEpochSecond() - now.toEpochSecond()
            return diff.coerceAtLeast(0)
        } catch (_: Exception) {
        }

        return null
    }
}
