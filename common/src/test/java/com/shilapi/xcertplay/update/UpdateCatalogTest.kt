package com.shilapi.xcertplay.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UpdateCatalogTest {
    private val releaseJson = """
        [
          {
            "tag_name": "v0.2.15",
            "draft": true,
            "assets": [
              {"name": "DiPlay-0.2.15.apk", "browser_download_url": "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.15/DiPlay-0.2.15.apk"},
              {"name": "SHA256SUMS.txt", "browser_download_url": "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.15/SHA256SUMS.txt"}
            ]
          },
          {
            "tag_name": "v0.2.14",
            "draft": false,
            "prerelease": true,
            "assets": [
              {"name": "DiPlay-0.2.14.apk", "browser_download_url": "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.14/DiPlay-0.2.14.apk"},
              {"name": "SHA256SUMS.txt", "browser_download_url": "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.14/SHA256SUMS.txt"}
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun skipsDraftsAndReturnsTheFirstPublishedRelease() {
        val release = UpdateCatalog.parse(releaseJson)
        assertEquals("v0.2.14", release?.tagName)
        assertEquals("DiPlay-0.2.14.apk", release?.apkName)
        assertEquals(
            "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.14/DiPlay-0.2.14.apk",
            release?.apkUrl,
        )
        assertEquals(
            "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.14/SHA256SUMS.txt",
            release?.checksumsUrl,
        )
    }

    @Test
    fun returnsNullWithoutAnApkAsset() {
        val json = """
            [
              {"tag_name": "v0.2.14", "draft": false, "assets": [
                {"name": "source.zip", "browser_download_url": "https://example.com/source.zip"}
              ]}
            ]
        """.trimIndent()
        assertNull(UpdateCatalog.parse(json))
    }

    @Test
    fun returnsNullWithoutChecksums() {
        val json = """
            [
              {"tag_name": "v0.2.14", "draft": false, "assets": [
                {"name": "DiPlay-0.2.14.apk", "browser_download_url": "https://example.com/DiPlay-0.2.14.apk"}
              ]}
            ]
        """.trimIndent()
        assertNull(UpdateCatalog.parse(json))
    }

    @Test
    fun returnsNullForAnEmptyReleaseList() {
        assertNull(UpdateCatalog.parse("[]"))
    }

    @Test fun choosesTheHighestVersionEvenWhenTheApiReturnsAscendingOrUploadOrder() {
        val releases = org.json.JSONArray()
        for (version in listOf("1.0.9", "1.0.52", "1.0.10")) releases.put(assetRelease(version))
        assertEquals("v1.0.52", UpdateCatalog.parse(releases.toString())?.tagName)
    }

    @Test fun incompleteReleasesAndSourceArchivesDoNotHideTheNewestUsableApk() {
        val releases = org.json.JSONArray()
            .put(org.json.JSONObject("""{"tag_name":"v1.0.55"}"""))
            .put(assetRelease("1.0.54").apply { getJSONArray("assets").remove(1) })
            .put(org.json.JSONObject("""{"tag_name":"v1.0.53","assets":[
                {"name":"source.zip","browser_download_url":"https://example.com/source.zip"}
            ]}"""))
            .put(assetRelease("1.0.52"))
        assertEquals("v1.0.52", UpdateCatalog.parse(releases.toString())?.tagName)
    }

    @Test fun ignoresBlankDownloadLinksUnusableTagsAndUnsafeFileNames() {
        val releases = org.json.JSONArray()
            .put(assetRelease("1.0.55").apply { getJSONArray("assets").getJSONObject(0).put("browser_download_url", " ") })
            .put(assetRelease("1.0.54").apply { getJSONArray("assets").getJSONObject(0).put("name", "../DiPlay.apk") })
            .put(assetRelease("1.0.53").apply { getJSONArray("assets").getJSONObject(0).put("name", "..\\DiPlay.apk") })
            .put(assetRelease("1.0.99").put("tag_name", "unversioned"))
            .put(assetRelease("1.0.52"))
        assertEquals("v1.0.52", UpdateCatalog.parse(releases.toString())?.tagName)
    }

    private fun assetRelease(version: String) = org.json.JSONObject("""
        {"tag_name":"v$version","release_status":"latest","assets":[
          {"name":"DiPlay-$version.apk","browser_download_url":"https://gitcode.com/DiPlay-$version.apk"},
          {"name":"SHA256SUMS.txt","browser_download_url":"https://gitcode.com/SHA256SUMS.txt"}
        ]}
    """.trimIndent())
}
