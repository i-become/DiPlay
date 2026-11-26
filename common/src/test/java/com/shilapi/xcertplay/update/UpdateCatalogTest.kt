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
            "name": "连接稳定性优化",
            "body": "修复连接问题。\n\n新增仪表信息。",
            "draft": false,
            "prerelease": false,
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
        assertEquals("连接稳定性优化", release?.title)
        assertEquals("修复连接问题。\n\n新增仪表信息。", release?.description)
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

    @Test fun ignoresPrereleaseTagsEvenWithoutThePrereleaseFlag() {
        val releases = org.json.JSONArray()
            .put(assetRelease("2.0.0").put("prerelease", true))
            .put(assetRelease("1.9.0-beta.1"))
            .put(assetRelease("1.4.3"))
        assertEquals("v1.4.3", UpdateCatalog.parse(releases.toString())?.tagName)
    }

    @Test fun choosesMatchingProductionApkRegardlessOfAttachmentOrder() {
        val release = assetRelease("1.4.3")
        val assets = release.getJSONArray("assets")
        for (name in listOf("DiPlay-1.4.2.apk", "DiPlay-1.4.3-debug.apk", "other.apk")) {
            assets.put(org.json.JSONObject().put("name", name).put("browser_download_url", "https://example.com/$name"))
        }
        assertEquals("DiPlay-1.4.3.apk", UpdateCatalog.parse(org.json.JSONArray().put(release).toString())?.apkName)
    }

    @Test fun refusesAmbiguousProductionAttachments() {
        val release = assetRelease("1.4.3")
        release.getJSONArray("assets").put(org.json.JSONObject()
            .put("name", "DiPlay-v1.4.3.apk").put("browser_download_url", "https://example.com/alternate.apk"))
        assertNull(UpdateCatalog.parse(org.json.JSONArray().put(release).toString()))
    }

    @Test fun releaseNotesAreBoundedAndMissingNotesRemainEmpty() {
        val release = assetRelease("1.4.3")
        release.put("name", "  标题  ").put("body", "  说明  ")
        val parsed = UpdateCatalog.parse(org.json.JSONArray().put(release).toString())
        assertEquals("标题", parsed?.title)
        assertEquals("说明", parsed?.description)
        val withoutNotes = UpdateCatalog.parse(org.json.JSONArray().put(assetRelease("1.4.4")).toString())
        assertEquals("", withoutNotes?.title)
        assertEquals("", withoutNotes?.description)
    }

    private fun assetRelease(version: String) = org.json.JSONObject("""
        {"tag_name":"v$version","release_status":"latest","assets":[
          {"name":"DiPlay-$version.apk","browser_download_url":"https://gitcode.com/DiPlay-$version.apk"},
          {"name":"SHA256SUMS.txt","browser_download_url":"https://gitcode.com/SHA256SUMS.txt"}
        ]}
    """.trimIndent())
}
