package com.shilapi.xcertplay.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.io.IOException

internal object UpdateChecksums {
    private val LINE = Regex("^([0-9a-fA-F]{64})\\s+([^\\s].*)$")
    private val BSD_LINE = Regex("^SHA256 \\((.+)\\) = ([0-9a-fA-F]{64})$", RegexOption.IGNORE_CASE)

    internal fun read(file: File): Map<String, String> {
        val bytes = file.readBytes()
        // Windows PowerShell commonly writes UTF-16 with a BOM; other tools use UTF-8.
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return parse(String(bytes, charset))
    }

    internal fun parse(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in text.removePrefix("\uFEFF").lineSequence()) {
            val entry = LINE.matchEntire(line.trim())?.let {
                it.groupValues[2].removePrefix("*").removePrefix("./") to it.groupValues[1].lowercase()
            } ?: BSD_LINE.matchEntire(line.trim())?.let {
                it.groupValues[1].removePrefix("./") to it.groupValues[2].lowercase()
            } ?: continue
            val previous = result.put(entry.first, entry.second)
            if (previous != null && previous != entry.second) throw IOException("校验文件包含冲突的 SHA-256：${entry.first}")
        }
        return result
    }

    internal fun sha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }

    internal fun sha256Hex(source: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun matches(expected: Map<String, String>, fileName: String, actualHex: String): Boolean {
        val wanted = expected[fileName] ?: return false
        return MessageDigest.isEqual(wanted.lowercase().toByteArray(), actualHex.lowercase().toByteArray())
    }
}
