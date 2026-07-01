package io.base14.scout.android.instrumentation

import org.json.JSONArray
import org.json.JSONObject

internal object AnrTombstone {
    data class ThreadInfo(val name: String, val state: String, val priority: Int, val daemon: Boolean, val frames: List<String>)

    private val HEADER = Regex("^\"(.*)\"\\s+(daemon\\s+)?prio=(\\d+)\\s+tid=(\\d+)\\s+(.+)$")
    private const val MAX_FRAMES = 64

    fun parse(text: String): List<ThreadInfo> {
        val threads = ArrayList<ThreadInfo>()
        var name: String? = null
        var state = ""
        var prio = 0
        var daemon = false
        val frames = ArrayList<String>()

        fun flush() {
            name?.let { threads.add(ThreadInfo(it, state, prio, daemon, frames.toList())) }
            name = null
            frames.clear()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val m = HEADER.matchEntire(line)
            when {
                m != null -> {
                    flush()
                    name = m.groupValues[1]
                    daemon = m.groupValues[2].isNotBlank()
                    prio = m.groupValues[3].toIntOrNull() ?: 0
                    state = m.groupValues[5].trim()
                }
                name != null && line.startsWith("at ") -> if (frames.size < MAX_FRAMES) frames.add(line.removePrefix("at ").trim())
                name != null && line.isEmpty() && frames.isNotEmpty() -> flush()
            }
        }
        flush()
        return threads
    }

    fun toJson(threads: List<ThreadInfo>): String {
        val arr = JSONArray()
        for (t in threads) {
            arr.put(
                JSONObject()
                    .put("name", t.name)
                    .put("state", t.state)
                    .put("priority", t.priority)
                    .put("daemon", t.daemon)
                    .put("frames", JSONArray(t.frames)),
            )
        }
        return arr.toString()
    }

    fun mainStack(threads: List<ThreadInfo>): String? =
        threads.firstOrNull { it.name == "main" }?.frames?.takeIf { it.isNotEmpty() }?.joinToString("\n")
}
