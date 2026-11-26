package com.shilapi.xcertplay.update

import java.net.URL
import java.net.URLConnection

internal object UpdateReleaseLookup {
    fun latest(
        installedVersion: String,
        userAgent: String,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): UpdateRelease? {
        return latestPublished(userAgent, openConnection)
            ?.takeIf { UpdateVersion.isNewer(it.tagName, installedVersion) }
    }

    fun latestPublished(
        userAgent: String,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): UpdateRelease? {
        return try {
            fetch(UpdateClient.RELEASES_URL, userAgent, openConnection)
        } catch (primaryFailure: Exception) {
            try {
                fetch(UpdateClient.FALLBACK_RELEASES_URL, userAgent, openConnection)
            } catch (fallbackFailure: Exception) {
                primaryFailure.addSuppressed(fallbackFailure)
                throw primaryFailure
            }
        }
    }

    private fun fetch(
        url: String,
        userAgent: String,
        openConnection: (URL) -> URLConnection,
    ): UpdateRelease? = UpdateCatalog.parse(
        UpdateClient.fetchText(url, "application/json", userAgent, openConnection),
    )
}
