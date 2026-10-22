package com.shilapi.xcertplay.update

import org.json.JSONArray

/** The published release that a newer build can be downloaded from. */
internal data class UpdateRelease(
    val tagName: String,
    val apkName: String,
    val apkUrl: String,
    val checksumsUrl: String,
)

internal object UpdateCatalog {
    internal const val CHECKSUMS_FILE = "SHA256SUMS.txt"

    /** GitCode and GitHub both expose named assets with browser_download_url. */
    internal fun parse(json: String): UpdateRelease? {
        val releases = JSONArray(json)
        var newest: UpdateRelease? = null
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft")) continue
            val tagName = release.optString("tag_name")
            if (UpdateVersion.parse(tagName) == null) continue
            val assets = release.optJSONArray("assets") ?: continue
            var apkName = ""
            var apkUrl = ""
            var checksumsUrl = ""
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.optJSONObject(assetIndex) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (url.isBlank()) continue
                if (name.contains('/') || name.contains('\\')) continue
                if (name.endsWith(".apk")) {
                    apkName = name
                    apkUrl = url
                }
                if (name.equals(CHECKSUMS_FILE, ignoreCase = true)) checksumsUrl = url
            }
            if (apkUrl.isEmpty() || checksumsUrl.isEmpty()) continue
            if (newest == null || UpdateVersion.isNewer(tagName, newest.tagName)) {
                newest = UpdateRelease(tagName, apkName, apkUrl, checksumsUrl)
            }
        }
        return newest
    }
}
