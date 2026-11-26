package com.shilapi.xcertplay.update

import org.json.JSONArray

/** The published release that a newer build can be downloaded from. */
internal data class UpdateRelease(
    val tagName: String,
    val apkName: String,
    val apkUrl: String,
    val checksumsUrl: String,
    val title: String = "",
    val description: String = "",
)

internal object UpdateCatalog {
    internal const val CHECKSUMS_FILE = "SHA256SUMS.txt"

    /** GitCode and GitHub both expose named assets with browser_download_url. */
    internal fun parse(json: String): UpdateRelease? {
        val releases = JSONArray(json)
        var newest: UpdateRelease? = null
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val tagName = release.optString("tag_name")
            if (!UpdateVersion.isStable(tagName)) continue
            val assets = release.optJSONArray("assets") ?: continue
            val apks = mutableListOf<Pair<String, String>>()
            var checksumsUrl = ""
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.optJSONObject(assetIndex) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (url.isBlank()) continue
                if (name.contains('/') || name.contains('\\')) continue
                if (name.endsWith(".apk", ignoreCase = true) && matchesApkName(name, tagName)) apks += name to url
                if (name.equals(CHECKSUMS_FILE, ignoreCase = true)) checksumsUrl = url
            }
            // Never let API asset ordering choose between debug, old or alternate builds.
            val apk = apks.singleOrNull() ?: continue
            if (checksumsUrl.isEmpty()) continue
            if (newest == null || UpdateVersion.isNewer(tagName, newest.tagName)) {
                newest = UpdateRelease(
                    tagName = tagName,
                    apkName = apk.first,
                    apkUrl = apk.second,
                    checksumsUrl = checksumsUrl,
                    title = release.optString("name").trim().take(160),
                    description = release.optString("body").trim().take(12_000),
                )
            }
        }
        return newest
    }

    internal fun matchesApkName(name: String, tag: String): Boolean {
        val version = tag.trim().removePrefix("v").removePrefix("V")
        return name.equals("DiPlay-$version.apk", ignoreCase = true) ||
            name.equals("DiPlay-v$version.apk", ignoreCase = true)
    }
}
