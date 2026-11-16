package com.shilapi.xcertplay

/** SDKs may send either the current line or a complete LRC document. */
internal object ClusterLyrics {
    private val timestamp = Regex("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)

    data class Line(val text: String?, val remainingMillis: Long? = null)

    fun current(raw: String?, elapsedMillis: Long?): String? = line(raw, elapsedMillis).text

    fun line(raw: String?, elapsedMillis: Long?): Line {
        if (raw.isNullOrBlank()) return Line(null)
        val timed = raw.lineSequence().flatMap { line ->
            val matches = timestamp.findAll(line).toList()
            val text = timestamp.replace(line, "").trim()
            matches.asSequence().mapNotNull { match ->
                val minutes = match.groupValues[1].toLongOrNull()?.takeIf { it <= Long.MAX_VALUE / 60_000 - 1 }
                    ?: return@mapNotNull null
                val fraction = match.groupValues[3].padEnd(3, '0').toLongOrNull() ?: 0L
                (minutes * 60_000 + match.groupValues[2].toLong() * 1000 + fraction) to text
            }
        }.sortedBy { it.first }.toList()
        if (timed.isNotEmpty()) {
            val position = (elapsedMillis ?: 0L) + (offset.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L)
            val current = timed.lastOrNull { it.first <= position }
            val next = timed.firstOrNull { it.first > position }
            return Line(current?.second?.takeIf { it.isNotBlank() }, next?.first?.minus(position)?.coerceAtLeast(1))
        }
        return Line(raw.lineSequence().map(String::trim)
            .firstOrNull { it.isNotBlank() && !it.startsWith("[") })
    }
}
