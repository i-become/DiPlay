package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Known stock navigation packages on Hongqi head units. */
internal enum class Hs5NavPackages(val packageName: String) {
    /** QiSi IVI navigation (common on HS5; no standalone Gaode APK). */
    QISI_NAVI("ltd.qisi.navi"),
    VIEW_SERVICE("faw.system.viewservice"),
    AMAP_AUTO("com.autonavi.amapauto"),
    AMAP_AUTO_CUSTOM("com.autonavi.amapauto.custom"),
    AMAP_AUTO_HQ("com.autonavi.amapauto.hongqi"),
    STOCK_NAVI("com.faw.navi"),
    STOCK_MAP("com.faw.map"),
    STOCK_AUTONAVI("com.faw.autonavi"),
    NEUSOFT_NAVI("com.neusoft.navigation"),
    NEUSOFT_HQ_NAVI("com.neusoft.hongqi.navigation"),
    NEUSOFT_IVI_NAVI("com.neusoft.ivi.navigation"),
    NEUSOFT_AMAP("com.neusoft.amapauto"),
    NEUSOFT_CARLINK("com.neusoft.carlink"),
    NEUSOFT_SSP_AMAP("com.neusoft.ssp.ces.hongqi.amap"),
    HQ_NAVI("com.hongqi.navigation"),
    ;

    companion object {
        private const val AMAP_BROADCAST = "AUTONAVI_STANDARD_BROADCAST_SEND"

        fun find(installed: (String) -> Boolean): Hs5NavPackages? =
            entries.firstOrNull { installed(it.packageName) }

        fun isQisiStack(context: Context): Boolean =
            runCatching {
                context.applicationContext.packageManager.getPackageInfo(QISI_NAVI.packageName, 0)
            }.isSuccess

        /** Primary navigation/HUD target package on this head unit. */
        fun resolvedPackage(context: Context): String? {
            val app = context.applicationContext
            val installed = { pkg: String ->
                runCatching { app.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            }
            find(installed)?.packageName?.let { return it }
            packageFromAutonaviBroadcast(app)?.let { return it }
            packageFromMapCategory(app)?.let { return it }
            return scanInstalledPackages(app)
        }

        fun navigationOutputPackages(context: Context): List<String> {
            val primary = resolvedPackage(context)
            val extras = listOf(VIEW_SERVICE.packageName, QISI_NAVI.packageName)
            return buildList {
                primary?.let { add(it) }
                addAll(packagesFromAutonaviBroadcast(context))
                addAll(extras.filter { pkg ->
                    pkg != primary && runCatching {
                        context.applicationContext.packageManager.getPackageInfo(pkg, 0)
                    }.isSuccess
                })
                addAll(scanCandidatePackages(context).take(6))
            }.distinct()
        }

        fun scanCandidatePackages(context: Context): List<String> {
            val app = context.applicationContext
            val fromScan = runCatching {
                @Suppress("DEPRECATION")
                app.packageManager.getInstalledPackages(0).map { it.packageName }
            }.getOrDefault(emptyList())
            return fromScan.filter { isMapCandidate(it, app.packageName) }.sorted()
        }

        fun packagesFromAutonaviBroadcast(context: Context): List<String> {
            val app = context.applicationContext
            val intent = Intent(AMAP_BROADCAST)
            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_RESOLVED_FILTER
            return app.packageManager.queryBroadcastReceivers(intent, flags)
                .mapNotNull { resolve ->
                    resolve.activityInfo?.packageName?.takeIf { it.isNotBlank() && it != app.packageName }
                }
                .distinct()
        }

        private fun packageFromAutonaviBroadcast(context: Context): String? =
            packagesFromAutonaviBroadcast(context).firstOrNull()

        private fun packageFromMapCategory(context: Context): String? {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
            @Suppress("DEPRECATION")
            val matches = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return matches.mapNotNull { it.activityInfo?.packageName?.takeIf { pkg -> isMapCandidate(pkg, context.packageName) } }
                .distinct()
                .firstOrNull()
        }

        private fun scanInstalledPackages(context: Context): String? =
            scanCandidatePackages(context).firstOrNull()

        private fun isMapCandidate(pkg: String, selfPackage: String): Boolean {
            if (pkg == selfPackage || pkg.isBlank()) return false
            val lower = pkg.lowercase()
            if (lower == "ltd.qisi.navi" || (lower.startsWith("ltd.qisi.") && lower.endsWith(".navi"))) return true
            if (lower.contains("qisi") && lower.contains("navi")) return true
            if (lower.contains("autonavi") || lower.contains("amapauto") || lower.contains("gaode")) return true
            if (lower.contains("amap") && !lower.contains("example")) return true
            if (lower.contains("neusoft") &&
                (lower.contains("navi") || lower.contains("map") || lower.contains("amap") || lower.contains("carlink"))
            ) return true
            if (lower.contains("hongqi") && (lower.contains("navi") || lower.contains("map"))) return true
            if (lower.contains("faw") && (lower.contains("navi") || lower.contains("map") || lower.contains("view"))) {
                return true
            }
            return false
        }
    }
}

