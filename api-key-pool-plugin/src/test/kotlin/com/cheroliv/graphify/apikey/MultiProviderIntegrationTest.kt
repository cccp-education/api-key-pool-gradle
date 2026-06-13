package com.cheroliv.graphify.apikey

import graphify.apikey.ApiKeyEntry
import graphify.apikey.QuotaConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiProviderIntegrationTest {

    private fun createEntry(
        id: String,
        provider: Provider,
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry {
        return ApiKeyEntry(
            id = id,
            email = "$id@test.com",
            name = "Key $id",
            keyRef = "${provider.name}_KEY_$id",
            provider = provider,
            services = listOf(ServiceType.CHAT_COMPLETION),
            quota = quota
        )
    }

    @Test
    fun `pool with Ollama OpenAI and DeepSeek should rotate across providers`() {
        val entries = listOf(
            createEntry("ollama-1", Provider.OLLAMA),
            createEntry("openai-1", Provider.OPENAI),
            createEntry("deepseek-1", Provider.DEEPSEEK)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.ROUND_ROBIN)

        val first = pool.getNextKey()
        val second = pool.getNextKey()
        val third = pool.getNextKey()
        val fourth = pool.getNextKey()

        assertEquals(Provider.OLLAMA, first.provider)
        assertEquals(Provider.OPENAI, second.provider)
        assertEquals(Provider.DEEPSEEK, third.provider)
        assertEquals(Provider.OLLAMA, fourth.provider)
    }

    @Test
    fun `pool with mixed providers should track usage per provider`() {
        val entries = listOf(
            createEntry("ollama-1", Provider.OLLAMA),
            createEntry("openai-1", Provider.OPENAI),
            createEntry("deepseek-1", Provider.DEEPSEEK)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.ROUND_ROBIN)

        repeat(6) { pool.getNextKey() }

        assertEquals(2, pool.getUsageCount("ollama-1"))
        assertEquals(2, pool.getUsageCount("openai-1"))
        assertEquals(2, pool.getUsageCount("deepseek-1"))
    }

    @Test
    fun `least used strategy should work across providers`() {
        val entries = listOf(
            createEntry("ollama-1", Provider.OLLAMA),
            createEntry("openai-1", Provider.OPENAI),
            createEntry("deepseek-1", Provider.DEEPSEEK)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.LEAST_USED)

        pool.getNextKey()
        pool.getNextKey()
        pool.getNextKey()

        assertEquals(1, pool.getUsageCount("ollama-1"))
        assertEquals(1, pool.getUsageCount("openai-1"))
        assertEquals(1, pool.getUsageCount("deepseek-1"))
    }

    @Test
    fun `quota exceeded should be detected per provider independently`() {
        val lowQuota = QuotaConfig(limitValue = 10, thresholdPercent = 80)
        val highQuota = QuotaConfig(limitValue = 1000, thresholdPercent = 80)

        val entries = listOf(
            createEntry("ollama-1", Provider.OLLAMA, lowQuota),
            createEntry("openai-1", Provider.OPENAI, highQuota),
            createEntry("deepseek-1", Provider.DEEPSEEK, highQuota)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.ROUND_ROBIN, autoResetEnabled = false)

        repeat(24) { pool.getNextKey() }

        assertTrue(pool.isQuotaExceeded(entries[0]))
        assertEquals(8, pool.getUsageCount("ollama-1"))
        assertEquals(8, pool.getUsageCount("openai-1"))
        assertEquals(8, pool.getUsageCount("deepseek-1"))
    }

    @Test
    fun `audit logs should record provider info`() {
        val entries = listOf(
            createEntry("ollama-1", Provider.OLLAMA),
            createEntry("openai-1", Provider.OPENAI),
            createEntry("deepseek-1", Provider.DEEPSEEK)
        )
        val pool = ApiKeyPool(entries, auditEnabled = true)

        pool.getNextKey()
        pool.getNextKey()
        pool.getNextKey()

        val logs = pool.getAuditLogs()
        assertEquals(3, logs.size)
        assertEquals(Provider.OLLAMA, logs[0].provider)
        assertEquals(Provider.OPENAI, logs[1].provider)
        assertEquals(Provider.DEEPSEEK, logs[2].provider)
    }

    @Test
    fun `pool with single provider should still work`() {
        val entries = listOf(
            createEntry("ollama-1", Provider.OLLAMA),
            createEntry("ollama-2", Provider.OLLAMA)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.ROUND_ROBIN)

        val first = pool.getNextKey()
        val second = pool.getNextKey()

        assertEquals(Provider.OLLAMA, first.provider)
        assertEquals(Provider.OLLAMA, second.provider)
        assertEquals("ollama-1", first.id)
        assertEquals("ollama-2", second.id)
    }
}
