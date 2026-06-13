package com.cheroliv.graphify.apikey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RateLimitHeadersParserTest {

    @Test
    fun `should parse X-RateLimit-Remaining header`() {
        val headers = mapOf("X-RateLimit-Remaining" to "42")
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(42, result.remaining)
    }

    @Test
    fun `should parse X-RateLimit-Limit header`() {
        val headers = mapOf("X-RateLimit-Limit" to "100")
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(100, result.limit)
    }

    @Test
    fun `should parse X-RateLimit-Reset header`() {
        val headers = mapOf("X-RateLimit-Reset" to "1718300000")
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(1718300000, result.resetEpochSeconds)
    }

    @Test
    fun `should parse Retry-After header in seconds`() {
        val headers = mapOf("Retry-After" to "30")
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(30, result.retryAfterSeconds)
    }

    @Test
    fun `should parse Retry-After header as HTTP date`() {
        val futureDate = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .plusSeconds(3600)
            .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        val headers = mapOf("Retry-After" to futureDate)
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(true, result.retryAfterSeconds!! > 0)
        assertEquals(true, result.retryAfterSeconds!! <= 3600)
    }

    @Test
    fun `should parse all headers together`() {
        val headers = mapOf(
            "X-RateLimit-Limit" to "1000",
            "X-RateLimit-Remaining" to "350",
            "X-RateLimit-Reset" to "1718300000",
            "Retry-After" to "60"
        )
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(1000, result.limit)
        assertEquals(350, result.remaining)
        assertEquals(1718300000, result.resetEpochSeconds)
        assertEquals(60, result.retryAfterSeconds)
    }

    @Test
    fun `should return defaults when no headers present`() {
        val result = RateLimitHeadersParser.parse(emptyMap())

        assertEquals(-1, result.limit)
        assertEquals(-1, result.remaining)
        assertNull(result.resetEpochSeconds)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `should handle malformed numeric values gracefully`() {
        val headers = mapOf("X-RateLimit-Remaining" to "not-a-number")
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(-1, result.remaining)
    }

    @Test
    fun `should parse OpenAI-style rate limit headers`() {
        val headers = mapOf(
            "x-ratelimit-limit-requests" to "500",
            "x-ratelimit-remaining-requests" to "499",
            "x-ratelimit-reset-requests" to "1s"
        )
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(500, result.limit)
        assertEquals(499, result.remaining)
    }

    @Test
    fun `should parse Anthropic-style rate limit headers`() {
        val headers = mapOf(
            "anthropic-ratelimit-requests-limit" to "1000",
            "anthropic-ratelimit-requests-remaining" to "998",
            "anthropic-ratelimit-requests-reset" to "2025-06-13T12:00:00Z"
        )
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(1000, result.limit)
        assertEquals(998, result.remaining)
    }

    @Test
    fun `should parse DeepSeek-style rate limit headers`() {
        val headers = mapOf(
            "x-ds-ratelimit-limit" to "200",
            "x-ds-ratelimit-remaining" to "150",
            "x-ds-ratelimit-reset" to "3600"
        )
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(200, result.limit)
        assertEquals(150, result.remaining)
        assertEquals(3600, result.resetEpochSeconds)
    }

    @Test
    fun `should detect rate limit exceeded from remaining zero`() {
        val headers = mapOf("X-RateLimit-Remaining" to "0")
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(true, result.isExhausted)
    }

    @Test
    fun `should detect rate limit not exceeded when remaining positive`() {
        val headers = mapOf("X-RateLimit-Remaining" to "5")
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(false, result.isExhausted)
    }

    @Test
    fun `should calculate usage percentage`() {
        val headers = mapOf(
            "X-RateLimit-Limit" to "100",
            "X-RateLimit-Remaining" to "25"
        )
        val result = RateLimitHeadersParser.parse(headers)

        assertEquals(75.0, result.usagePercent)
    }

    @Test
    fun `should return zero usage percent when no limit info`() {
        val result = RateLimitHeadersParser.parse(emptyMap())

        assertEquals(0.0, result.usagePercent)
    }
}
