package com.shilapi.xcertplay.hud



import android.content.Context

import android.content.res.Configuration

import android.os.Build



/** Detects Hongqi-family head units for OEM navigation output. */

object Hs5HeadUnit {

    /**
     * Build-time Hongqi / QiSi detection for code paths that cannot take a [Context] (for example
     * [com.shilapi.xcertplay.media.MicrophoneUplink]). On these firmwares the voice-recognition
     * capture source often records silence; voice-communication and [android.media.AudioManager] call
     * mode match what Siri and WeChat VoIP need.
     */
    val detected: Boolean = run {
        val tags = listOf(
            Build.BRAND,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.FINGERPRINT,
            Build.HARDWARE,
            Build.BOARD,
        ).joinToString(" ").lowercase()
        tags.contains("hongqi") ||
            tags.contains("hs5") ||
            tags.contains("qisi") ||
            tags.contains("neusoft") ||
            tags.contains("cchq") ||
            tags.contains("faw")
    }

    private val oemPackageAllowlist = listOf(

        "com.faw.hq",

        "com.faw.hongqi",

        "com.hongqi.car",

        "com.hongqi.launcher",

        "com.fawcar.launcher",

        "com.fawcar.systemui",

        "com.neusoft.hongqi",

        "com.neusoft.ssp.ces.hongqi",

        "com.neusoft.factorymode",

        "com.neusoft.ivi",

        "com.cchq.car",

        "com.juyou.app",
        "ltd.qisi.navi",
        "ltd.qisi.settings",
        "faw.system.viewservice",
        "com.neusoft.carlink",
    )



    fun isHeadUnit(context: Context): Boolean {

        val app = context.applicationContext

        if (oemPackageAllowlist.any { installed(app, it) }) return true

        if (installedPackages(app).any { pkg ->

                pkg.contains("hongqi", ignoreCase = true) ||

                    pkg.contains("fawcar", ignoreCase = true) ||

                    pkg.contains("faw", ignoreCase = true) ||
                    pkg.contains("qisi", ignoreCase = true)

            }

        ) return true

        return headUnitTags(context).let { tags ->

            tags.contains("hongqi", ignoreCase = true) ||

                tags.contains("红旗", ignoreCase = false) ||

                tags.contains("faw", ignoreCase = true) ||

                tags.contains("hs5", ignoreCase = true) ||

                tags.contains("cchq", ignoreCase = true) ||

                tags.contains("neusoft", ignoreCase = true) ||

                tags.contains("FAW", ignoreCase = true)

        }

    }

    /**
     * True when background code must not open local adbd (127.0.0.1:5555).
     * QiSi / HS5 builds usually keep wireless debugging off; probing adbd would block startup.
     */
    fun skipsBackgroundAdb(context: Context): Boolean = isHeadUnit(context)

    /** True when a Hongqi-style cluster presentation display is likely available. */
    fun hasClusterPresentation(context: Context): Boolean {
        val manager = context.getSystemService(android.hardware.display.DisplayManager::class.java) ?: return false
        return manager.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .any { Hs5ClusterDisplay.isInstrumentCluster(it.name) }
    }

    fun clusterDisplaySummary(context: Context): String? {
        val manager = context.getSystemService(android.hardware.display.DisplayManager::class.java) ?: return null
        val display = manager.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { Hs5ClusterDisplay.isInstrumentCluster(it.name) } ?: return null
        return "${display.name}#${display.displayId}"
    }

    /** True when settings should use Hongqi copy for HUD / cluster navigation. */

    fun showsHs5NavigationBranding(context: Context): Boolean = isHeadUnit(context)



    internal fun headUnitTags(context: Context): String {

        val parts = mutableListOf<String>()

        parts += listOf(

            Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.PRODUCT,

            Build.FINGERPRINT, Build.HARDWARE, Build.BOARD,

        )

        runCatching {

            val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)

            for (key in listOf(

                "ro.product.brand",

                "ro.product.manufacturer",

                "ro.product.model",

                "ro.product.device",

                "ro.product.name",

                "ro.build.product",

                "ro.car.product",

            )) {

                (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }?.let { parts += it }

            }

        }

        if (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==

            Configuration.UI_MODE_TYPE_CAR

        ) {

            parts += "uiModeCar"

        }

        return parts.joinToString(" ") { it.orEmpty() }

    }



    private fun installedPackages(context: Context): List<String> = runCatching {

        @Suppress("DEPRECATION")

        context.packageManager.getInstalledPackages(0).map { it.packageName }

    }.getOrDefault(emptyList())



    private fun installed(context: Context, packageName: String): Boolean = runCatching {

        context.packageManager.getPackageInfo(packageName, 0)

        true

    }.getOrDefault(false)

}


