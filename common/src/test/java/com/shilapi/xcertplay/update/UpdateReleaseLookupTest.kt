package com.shilapi.xcertplay.update

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class UpdateReleaseLookupTest {
    @Test fun gitCodeIsPrimaryAndSuppliesBothDownloadUrls() {
        val requests = mutableListOf<Reply>()
        val found = UpdateReleaseLookup.latest("1.0.51", "DiPlay/test") { url ->
            Reply(url, 200, release()).also(requests::add)
        }
        assertEquals(listOf(UpdateClient.RELEASES_URL), requests.map { it.url.toString() })
        assertTrue(requests.single().url.toString().startsWith("https://api.gitcode.com/api/v5/repos/liujing_1998/DiPlay/"))
        assertEquals("application/json", requests.single().getRequestProperty("Accept"))
        assertEquals("DiPlay/test", requests.single().getRequestProperty("User-Agent"))
        assertTrue(requests.single().disconnected)
        assertEquals("v1.0.52", found?.tagName)
        assertEquals("DiPlay-1.0.52.apk", found?.apkName)
        assertEquals("$DOWNLOAD/DiPlay-1.0.52.apk", found?.apkUrl)
        assertEquals("$DOWNLOAD/SHA256SUMS.txt", found?.checksumsUrl)
    }

    @Test fun anEmptyGitCodeRepositoryDoesNotQueryAnotherRepository() {
        val requests = mutableListOf<String>()
        val found = UpdateReleaseLookup.latest("1.0.51", "test") { url ->
            requests += url.toString()
            Reply(url, 200, "[]")
        }
        assertNull(found)
        assertEquals(listOf(UpdateClient.RELEASES_URL), requests)
    }

    @Test fun anUpToDateGitCodeReleaseDoesNotQueryTheFallback() {
        var requests = 0
        val found = UpdateReleaseLookup.latest("1.0.52", "test") { url ->
            requests++
            Reply(url, 200, release())
        }
        assertNull(found)
        assertEquals(1, requests)
    }

    @Test fun publishedLookupKeepsVersionEvenWhenNoUpgradeIsNeeded() {
        val found = UpdateReleaseLookup.latestPublished("test") { url -> Reply(url, 200, release()) }
        assertEquals("v1.0.52", found?.tagName)
    }

    @Test fun rateLimitingFallsBackThroughTheSameNetworkConnectionFactory() {
        val requests = mutableListOf<Reply>()
        val found = UpdateReleaseLookup.latest("1.0.51", "test") { url ->
            Reply(url, if (requests.isEmpty()) 429 else 200, release()).also(requests::add)
        }
        assertEquals(listOf(UpdateClient.RELEASES_URL, UpdateClient.FALLBACK_RELEASES_URL),
            requests.map { it.url.toString() })
        assertTrue(requests.all { it.disconnected })
        assertEquals("v1.0.52", found?.tagName)
    }

    @Test fun aLoginPageOrMalformedCatalogFallsBackInsteadOfReportingUpToDate() {
        var requests = 0
        val found = UpdateReleaseLookup.latest("1.0.51", "test") { url ->
            Reply(url, 200, if (requests++ == 0) "<html>Login</html>" else release())
        }
        assertEquals(2, requests)
        assertEquals("v1.0.52", found?.tagName)
    }

    @Test fun twoFailedSourcesRemainAnErrorAndPreserveBothCauses() {
        var requests = 0
        try {
            UpdateReleaseLookup.latest("1.0.51", "test") { url ->
                Reply(url, if (requests++ == 0) 429 else 403, "unavailable")
            }
            fail("A failed check must not be reported as up to date")
        } catch (failure: IOException) {
            assertTrue(failure.message!!.contains("429"))
            assertTrue(failure.suppressed.single().message!!.contains("403"))
        }
        assertEquals(2, requests)
    }

    private fun release() = """
        [{"tag_name":"v1.0.52","release_status":"latest","assets":[
          {"name":"source.zip","browser_download_url":"https://gitcode.com/source.zip"},
          {"name":"DiPlay-1.0.52.apk","browser_download_url":"$DOWNLOAD/DiPlay-1.0.52.apk","type":"other"},
          {"name":"SHA256SUMS.txt","browser_download_url":"$DOWNLOAD/SHA256SUMS.txt","type":"other"}
        ]}]
    """.trimIndent()

    private class Reply(url: URL, private val status: Int, body: String) : HttpURLConnection(url) {
        private val bytes = body.toByteArray()
        var disconnected = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getErrorStream() = ByteArrayInputStream(bytes)
    }

    private companion object {
        const val DOWNLOAD = "https://api.gitcode.com/api/v5/repos/liujing_1998/DiPlay/releases/v1.0.52/attach_files"
    }
}
