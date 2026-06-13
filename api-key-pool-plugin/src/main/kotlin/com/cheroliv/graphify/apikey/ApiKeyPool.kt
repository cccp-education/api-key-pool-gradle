package com.cheroliv.graphify.apikey

import graphify.apikey.ApiKeyEntry
import graphify.apikey.AuditLogEntry

class ApiKeyPool(
    private val entries: List<ApiKeyEntry>,
    private val rotationStrategy: RotationStrategy = RotationStrategy.ROUND_ROBIN,
    private val fallbackEnabled: Boolean = true,
    autoResetEnabled: Boolean = true,
    auditEnabled: Boolean = true
) {
    private var currentIndex = 0
    private val tracker: QuotaTracker = QuotaTracker()
    private val resetManager: QuotaResetManager = QuotaResetManager(tracker, autoResetEnabled)
    private val auditLogger: QuotaAuditLogger = QuotaAuditLogger(auditEnabled)

    init {
        entries.forEach { entry ->
            tracker.getUsage(entry.id)
        }
    }

    fun getNextKey(): ApiKeyEntry {
        if (entries.isEmpty()) {
            throw IllegalStateException("API Key Pool is empty")
        }

        val selectedEntry = when (rotationStrategy) {
            RotationStrategy.ROUND_ROBIN -> getNextRoundRobin()
            RotationStrategy.LEAST_USED -> getNextLeastUsed()
            RotationStrategy.WEIGHTED -> getNextWeighted()
            RotationStrategy.SMART -> getNextSmart()
        }

        tracker.trackUsage(selectedEntry.id)
        val usageCount = tracker.getUsage(selectedEntry.id)
        auditLogger.logUsage(selectedEntry, usageCount)

        if (tracker.isQuotaExceeded(selectedEntry)) {
            auditLogger.logQuotaExceeded(selectedEntry, usageCount)
            if (resetManager.checkAndReset(selectedEntry)) {
                auditLogger.logReset(selectedEntry.id, resetManager.getResetCount(selectedEntry.id), false)
            }
        }

        return selectedEntry
    }

    private fun getNextRoundRobin(): ApiKeyEntry {
        val entry = entries[currentIndex % entries.size]
        currentIndex = (currentIndex + 1) % entries.size
        return entry
    }

    private fun getNextLeastUsed(): ApiKeyEntry {
        return entries.minByOrNull { entry ->
            tracker.getUsage(entry.id)
        } ?: entries.first()
    }

    private fun getNextWeighted(): ApiKeyEntry {
        val totalWeight = entries.sumOf { it.metadata["weight"]?.toDoubleOrNull() ?: 1.0 }
        if (totalWeight <= 0.0) return entries.first()

        val pointer = Math.random() * totalWeight
        var cumulative = 0.0
        for (entry in entries) {
            cumulative += entry.metadata["weight"]?.toDoubleOrNull() ?: 1.0
            if (pointer <= cumulative) return entry
        }
        return entries.last()
    }

    private fun getNextSmart(): ApiKeyEntry {
        val available = entries.filter { !tracker.isQuotaExceeded(it) }
        val candidates = available.ifEmpty { entries }

        val maxRemaining = candidates.maxOfOrNull { entry ->
            entry.quota.limitValue - tracker.getUsage(entry.id)
        } ?: return candidates.first()

        val topCandidates = candidates.filter { entry ->
            entry.quota.limitValue - tracker.getUsage(entry.id) == maxRemaining
        }

        if (topCandidates.size == 1) return topCandidates.first()

        val totalWeight = topCandidates.sumOf { it.metadata["weight"]?.toDoubleOrNull() ?: 1.0 }
        if (totalWeight <= 0.0) return topCandidates.first()

        val pointer = Math.random() * totalWeight
        var cumulative = 0.0
        for (entry in topCandidates) {
            cumulative += entry.metadata["weight"]?.toDoubleOrNull() ?: 1.0
            if (pointer <= cumulative) return entry
        }
        return topCandidates.last()
    }

    fun isQuotaExceeded(entry: ApiKeyEntry): Boolean {
        return tracker.isQuotaExceeded(entry)
    }

    fun getAllKeys(): List<ApiKeyEntry> = entries

    fun size(): Int = entries.size

    fun isFallbackEnabled(): Boolean = fallbackEnabled

    fun resetUsageCounts() {
        tracker.resetAll()
    }

    fun getUsageCount(entryId: String): Long {
        return tracker.getUsage(entryId)
    }

    fun getTracker(): QuotaTracker = tracker

    fun getResetManager(): QuotaResetManager = resetManager

    fun getAuditLogger(): QuotaAuditLogger = auditLogger

    fun getUsagePercentage(entry: ApiKeyEntry): Double {
        return tracker.getUsagePercentage(entry)
    }

    fun manualReset(entryId: String) {
        resetManager.manualReset(entryId)
        auditLogger.logReset(entryId, resetManager.getResetCount(entryId), true)
    }

    fun getAuditLogs(): List<AuditLogEntry> = auditLogger.getLogs()
}
