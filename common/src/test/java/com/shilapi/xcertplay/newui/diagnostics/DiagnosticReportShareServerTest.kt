package com.shilapi.xcertplay.newui.diagnostics

import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticReportShareServerTest {
    private lateinit var server: DiagnosticReportShareServer
    private lateinit var endpoint: DiagnosticReportShareEndpoint

    @Before
    fun startServer() {
        server = DiagnosticReportShareServer(
            page = "<html>diag</html>".toByteArray(),
            report = { "report-body".toByteArray() },
            fileName = "DiPlay-test.txt",
        )
        val started = server.start()
        assertNotNull(started)
        endpoint = started!!
    }

    @After
    fun stopServer() {
        server.stop()
    }

    @Test fun servesPageAndDownloadWithToken() {
        val page = get("/d/${endpoint.token}/")
        assertEquals(200, page.first)
        assertTrue(page.second.contains("diag"))

        val download = get("/d/${endpoint.token}/download")
        assertEquals(200, download.first)
        assertEquals("report-body", download.second)
    }

    @Test fun rejectsWrongToken() {
        assertEquals(404, get("/d/wrong-token/download").first)
    }

    private fun get(path: String): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:${endpoint.port}$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val status = connection.responseCode
        val stream = if (status < 400) connection.inputStream else connection.errorStream
        val text = stream?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
        connection.disconnect()
        return status to text
    }
}
