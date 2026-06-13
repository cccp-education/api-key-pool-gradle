package com.cheroliv.graphify.apikey

import graphify.apikey.ApiKeyEntry
import graphify.apikey.QuotaConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvancedRotationStrategyTest {

    private fun createEntry(
        id: String,
        provider: Provider = Provider.OLLAMA,
        weight: Double = 1.0,
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry {
        return ApiKeyEntry(
            id = id,
            email = "$id@test.com",
            name = "Key $id",
            keyRef = "${provider.name}_KEY_$id",
            provider = provider,
            services = listOf(ServiceType.CHAT_COMPLETION),
            quota = quota,
            metadata = mapOf("weight" to weight.toString())
        )
    }

    @Test
    fun `WEIGHTED strategy should favor higher weight keys`() {
        val entries = listOf(
            createEntry("heavy", weight = 3.0),
            createEntry("light", weight = 1.0)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.WEIGHTED)

        val counts = mutableMapOf<String, Int>()
        repeat(40) {
            val key = pool.getNextKey()
            counts[key.id] = (counts[key.id] ?: 0) + 1
        }

        val heavyCount = counts["heavy"] ?: 0
        val lightCount = counts["light"] ?: 0

        assertTrue(heavyCount > lightCount, "heavy=$heavyCount should be > light=$lightCount")
        assertTrue(heavyCount >= 25, "heavy should get ~75% of selections, got $heavyCount")
    }

    @Test
    fun `WEIGHTED strategy with equal weights should distribute evenly`() {
        val entries = listOf(
            createEntry("a", weight = 1.0),
            createEntry("b", weight = 1.0),
            createEntry("c", weight = 1.0)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.WEIGHTED)

        val counts = mutableMapOf<String, Int>()
        repeat(300) {
            val key = pool.getNextKey()
            counts[key.id] = (counts[key.id] ?: 0) + 1
        }

        val aCount = counts["a"] ?: 0
        val bCount = counts["b"] ?: 0
        val cCount = counts["c"] ?: 0

        assertTrue(aCount in 80..120, "a should get ~100, got $aCount")
        assertTrue(bCount in 80..120, "b should get ~100, got $bCount")
        assertTrue(cCount in 80..120, "c should get ~100, got $cCount")
    }

    @Test
    fun `WEIGHTED strategy should default weight to 1 when metadata missing`() {
        val entries = listOf(
            ApiKeyEntry(
                id = "no-meta",
                email = "no@test.com",
                name = "No Meta",
                keyRef = "KEY_NM",
                provider = Provider.OLLAMA,
                services = listOf(ServiceType.CHAT_COMPLETION)
            ),
            createEntry("with-meta", weight = 2.0)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.WEIGHTED)

        val counts = mutableMapOf<String, Int>()
        repeat(30) {
            val key = pool.getNextKey()
            counts[key.id] = (counts[key.id] ?: 0) + 1
        }

        val withMetaCount = counts["with-meta"] ?: 0
        val noMetaCount = counts["no-meta"] ?: 0

        assertTrue(withMetaCount > noMetaCount, "weight=2.0 should get more than weight=1.0 default")
    }

    @Test
    fun `SMART strategy should prefer keys with more remaining quota`() {
        val lowQuota = QuotaConfig(limitValue = 10, thresholdPercent = 100)
        val highQuota = QuotaConfig(limitValue = 1000, thresholdPercent = 100)

        val entries = listOf(
            createEntry("low", quota = lowQuota),
            createEntry("high", quota = highQuota)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.SMART, autoResetEnabled = false)

        val counts = mutableMapOf<String, Int>()
        repeat(20) {
            val key = pool.getNextKey()
            counts[key.id] = (counts[key.id] ?: 0) + 1
        }

        val highCount = counts["high"] ?: 0
        val lowCount = counts["low"] ?: 0

        assertTrue(highCount > lowCount, "high quota key should be preferred, high=$highCount low=$lowCount")
    }

    @Test
    fun `SMART strategy should fall back to weighted when quotas equal`() {
        val quota = QuotaConfig(limitValue = 1000, thresholdPercent = 100)

        val entries = listOf(
            createEntry("heavy", weight = 3.0, quota = quota),
            createEntry("light", weight = 1.0, quota = quota)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.SMART, autoResetEnabled = false)

        val counts = mutableMapOf<String, Int>()
        repeat(100) {
            val key = pool.getNextKey()
            counts[key.id] = (counts[key.id] ?: 0) + 1
        }

        val heavyCount = counts["heavy"] ?: 0
        val lightCount = counts["light"] ?: 0

        assertTrue(heavyCount in 45..55, "SMART alternates to balance remaining quotas, heavy=$heavyCount")
        assertTrue(lightCount in 45..55, "SMART alternates to balance remaining quotas, light=$lightCount")
    }

    @Test
    fun `SMART strategy should avoid exhausted keys`() {
        val tinyQuota = QuotaConfig(limitValue = 3, thresholdPercent = 100)
        val normalQuota = QuotaConfig(limitValue = 100, thresholdPercent = 100)

        val entries = listOf(
            createEntry("tiny", quota = tinyQuota),
            createEntry("normal", quota = normalQuota)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.SMART, autoResetEnabled = false)

        repeat(3) { pool.getNextKey() }

        val remaining = mutableMapOf<String, Int>()
        repeat(10) {
            val key = pool.getNextKey()
            remaining[key.id] = (remaining[key.id] ?: 0) + 1
        }

        assertEquals(0, remaining["tiny"] ?: 0, "tiny key should be exhausted and avoided")
        assertEquals(10, remaining["normal"] ?: 0, "normal key should take all remaining calls")
    }

    @Test
    fun `SMART strategy with multi-provider should balance quota and weight`() {
        val ollamaQuota = QuotaConfig(limitValue = 50, thresholdPercent = 100)
        val openaiQuota = QuotaConfig(limitValue = 200, thresholdPercent = 100)
        val deepseekQuota = QuotaConfig(limitValue = 100, thresholdPercent = 100)

        val entries = listOf(
            createEntry("ollama", Provider.OLLAMA, weight = 1.0, quota = ollamaQuota),
            createEntry("openai", Provider.OPENAI, weight = 2.0, quota = openaiQuota),
            createEntry("deepseek", Provider.DEEPSEEK, weight = 1.0, quota = deepseekQuota)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.SMART, autoResetEnabled = false)

        val counts = mutableMapOf<String, Int>()
        repeat(30) {
            val key = pool.getNextKey()
            counts[key.id] = (counts[key.id] ?: 0) + 1
        }

        val openaiCount = counts["openai"] ?: 0
        val deepseekCount = counts["deepseek"] ?: 0
        val ollamaCount = counts["ollama"] ?: 0

        assertTrue(openaiCount > ollamaCount, "openai (high quota + weight=2) should dominate ollama (low quota + weight=1)")
        assertTrue(openaiCount >= deepseekCount, "openai (quota=200, weight=2) should >= deepseek (quota=100, weight=1)")
    }

    @Test
    fun `WEIGHTED strategy should track usage correctly`() {
        val entries = listOf(
            createEntry("a", weight = 2.0),
            createEntry("b", weight = 1.0)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.WEIGHTED)

        repeat(5) { pool.getNextKey() }

        val totalUsage = pool.getUsageCount("a") + pool.getUsageCount("b")
        assertEquals(5, totalUsage)
    }

    @Test
    fun `SMART strategy should track usage correctly`() {
        val entries = listOf(
            createEntry("a", quota = QuotaConfig(limitValue = 100)),
            createEntry("b", quota = QuotaConfig(limitValue = 100))
        )
        val pool = ApiKeyPool(entries, RotationStrategy.SMART, autoResetEnabled = false)

        repeat(5) { pool.getNextKey() }

        val totalUsage = pool.getUsageCount("a") + pool.getUsageCount("b")
        assertEquals(5, totalUsage)
    }
}
