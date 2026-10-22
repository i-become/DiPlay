package com.shilapi.xcertplay

/** Retain the beginning AND the end if an unexpectedly noisy transport exhausts the budget. */
internal class StartupTimeline(
    private val headLimit: Int = 512,
    private val tailLimit: Int = 1536,
) {
    private val head = ArrayList<String>()
    private val tail = ArrayDeque<String>()
    private var sequence = 0L
    private var omitted = 0L

    init { require(headLimit > 0 && tailLimit > 0) }

    @Synchronized fun add(metadata: String, message: String): String {
        val line = "seq=${++sequence} $metadata ${message.take(600)}"
        if (head.size < headLimit) head.add(line) else {
            if (tail.size == tailLimit) { tail.removeFirst(); omitted++ }
            tail.addLast(line)
        }
        return line
    }

    @Synchronized fun report(persisted: String = ""): String = buildString {
        if (persisted.isNotEmpty()) {
            val merged = sortedMapOf<Long, String>()
            fun include(line: String) {
                val id = line.substringBefore(' ').removePrefix("seq=").toLongOrNull() ?: return
                if (id in 1..sequence) merged[id] = line
            }
            persisted.lineSequence().forEach(::include)
            head.forEach(::include)
            tail.forEach(::include)
            appendLine("timeline events=$sequence retained=${merged.size} omitted=${sequence - merged.size} source=disk+memory")
            var previous = 0L
            merged.forEach { (id, line) ->
                if (id > previous + 1) appendLine("--- omitted ${id - previous - 1} events ---")
                appendLine(line)
                previous = id
            }
            if (previous < sequence) appendLine("--- omitted ${sequence - previous} events ---")
            return@buildString
        }
        appendLine("timeline events=$sequence retained=${head.size + tail.size} omitted=$omitted")
        head.forEach { appendLine(it) }
        if (omitted > 0) appendLine("--- omitted $omitted middle events; startup head and latest events retained ---")
        tail.forEach { appendLine(it) }
    }

    @Synchronized fun clear() { head.clear(); tail.clear(); sequence = 0; omitted = 0 }
}
