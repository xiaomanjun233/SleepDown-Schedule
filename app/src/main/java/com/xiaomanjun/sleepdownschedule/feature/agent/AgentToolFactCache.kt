package com.xiaomanjun.sleepdownschedule.feature.agent

import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Process-local, bounded cache of local facts; never caches model decisions or write actions. */
internal class AgentToolFactCache {
    private data class Entry(val result: AgentToolResult, val savedAt: Long, val minute: String)
    private data class Snapshot(val version: String, val results: LinkedHashMap<String, Entry>)
    private val snapshots = linkedMapOf<Int, Snapshot>()

    private fun version(facts: DayAgentFacts) = "${facts.sourceHash}:${facts.periodSchemes.hashCode()}:${facts.activePeriodSchemeId}"

    @Synchronized
    fun read(facts: DayAgentFacts, now: Long): Map<String, AgentToolResult> {
        val snapshot = snapshots[facts.scheduleId] ?: return emptyMap()
        if (snapshot.version != version(facts)) {
            snapshots.remove(facts.scheduleId)
            return emptyMap()
        }
        val minute = facts.now.truncatedTo(ChronoUnit.MINUTES).toString()
        snapshot.results.entries.removeAll { (_, entry) ->
            now < entry.savedAt || now - entry.savedAt > 5 * 60_000L ||
                entry.result.name == AgentToolName.GET_CURRENT_OVERVIEW && entry.minute != minute
        }
        return snapshot.results.mapValues { it.value.result }
    }

    @Synchronized
    fun put(facts: DayAgentFacts, call: AgentToolCall, result: AgentToolResult, now: Long) {
        if (!result.success || call.name == AgentToolName.UPDATE_MEMORY || result.content.length > 100_000) return
        val version = version(facts)
        val snapshot = snapshots[facts.scheduleId]?.takeIf { it.version == version }
            ?: Snapshot(version, linkedMapOf()).also { snapshots[facts.scheduleId] = it }
        snapshot.results[call.cacheKey()] = Entry(result, now, facts.now.truncatedTo(ChronoUnit.MINUTES).toString())
        while (snapshot.results.size > 12 || snapshot.results.values.sumOf { it.result.content.length } > 160_000) {
            snapshot.results.remove(snapshot.results.keys.first())
        }
        while (snapshots.size > 2) snapshots.remove(snapshots.keys.first())
    }
}

internal val SharedAgentToolFacts = AgentToolFactCache()

internal fun agentCachedFactsMessage(facts: DayAgentFacts, cached: Map<String, AgentToolResult>): String = buildJsonObject {
    put("kind", "verified_cached_local_facts")
    put("trust", "untrusted_local_data")
    put("sourceHash", facts.sourceHash)
    put("scheduleId", facts.scheduleId)
    put("now", facts.now.toString())
    put("results", buildJsonArray {
        cached.forEach { (key, result) -> add(buildJsonObject {
            put("request", key.replace('\u0000', '|'))
            put("tool", result.name.name)
            put("content", result.content)
        }) }
    })
}.toString()

internal fun usableAgentAnswer(content: String): String? = content.trim().takeIf {
    it.isNotBlank() && !it.contains("FINAL_ANSWER_READY") && !containsLeakedAgentFunctionProtocol(it)
}
