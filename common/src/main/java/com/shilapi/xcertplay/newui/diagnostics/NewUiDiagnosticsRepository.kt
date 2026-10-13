package com.shilapi.xcertplay.newui.diagnostics

import android.content.Context
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.DiagnosticExportStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NewUiDiagnosticFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
) {
    val timeLabel: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(modifiedMillis))

    val sizeLabel: String
        get() = when {
            sizeBytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", sizeBytes / 1_048_576.0)
            sizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
}

internal object NewUiDiagnosticsRepository {
    fun reportDirectories(context: Context): List<File> {
        val custom = DiagnosticExportStore.preferredReportDirectory(context)
        val defaults = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "diagnostic-reports") },
            File(context.filesDir, "diagnostic-reports"),
        )
        return if (custom != null) listOf(custom) + defaults else defaults
    }

    fun listReports(context: Context): List<NewUiDiagnosticFile> {
        return reportDirectories(context).flatMap { dir ->
            runCatching {
                if (!dir.isDirectory) emptyList()
                else dir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") }.orEmpty()
            }.getOrDefault(emptyList())
        }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .take(20)
            .map { file ->
                NewUiDiagnosticFile(
                    name = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    modifiedMillis = file.lastModified(),
                )
            }
    }

    fun storageHint(context: Context): String {
        val custom = AirPlayPersistence.loadDiagnosticReportDirectory(context)
        if (!custom.isNullOrBlank()) return custom
        val external = context.getExternalFilesDir(null)?.let { File(it, "diagnostic-reports") }
        return external?.absolutePath ?: File(context.filesDir, "diagnostic-reports").absolutePath
    }
}
