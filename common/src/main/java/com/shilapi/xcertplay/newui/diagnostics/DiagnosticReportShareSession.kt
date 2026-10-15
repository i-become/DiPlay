package com.shilapi.xcertplay.newui.diagnostics

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps a short-lived HTTP server so a phone can scan a QR code and download the latest report. */
class DiagnosticReportShareSession(private val context: Context) {
    private var server: DiagnosticReportShareServer? = null
    private var endpoint: DiagnosticReportShareEndpoint? = null

    val shareUrl: String?
        get() {
            val ep = endpoint ?: return null
            val address = DiagnosticReportShareServer.addressCandidates().firstOrNull() ?: return null
            return ep.url(address)
        }

    fun start(versionName: String): Boolean {
        stop()
        val app = context.applicationContext
        val fileName = "DiPlay-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        val reportText = NewUiDiagnosticReport.build(app, versionName)
        val page = runCatching {
            app.assets.open("diagnostic-report-share.html").use { it.readBytes() }
        }.getOrElse { "<html><body><a href=\"download\">Download</a></body></html>".toByteArray() }
        val started = DiagnosticReportShareServer(
            page = page,
            report = { reportText.toByteArray(Charsets.UTF_8) },
            fileName = fileName,
        ).also { server = it }.start() ?: return false
        endpoint = started
        return shareUrl != null
    }

    fun stop() {
        server?.stop()
        server = null
        endpoint = null
    }
}
