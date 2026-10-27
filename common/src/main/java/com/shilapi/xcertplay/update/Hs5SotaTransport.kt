package com.shilapi.xcertplay.update

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class SotaFailure(val code: String) : IOException(code)
internal data class SotaStaging(val requestId: String, val originalMode: Int) {
    val directoryName: String get() = "diplay-sota-$requestId"
}
internal data class SotaUpdate(val version: Long, val previousUpdate: Long)

internal interface SotaTransport {
    fun checkEndpoint()
    fun verifyArchive(source: File, digest: String): SotaUpdate
    fun storageMode(): Int
    fun stage(staging: SotaStaging, source: File, digest: String)
    fun send(staging: SotaStaging)
    fun cleanup(staging: SotaStaging)
}

/** HS5 SOTA wire protocol; independently implemented, with no firmware/resource upgrade payload. */
internal class Hs5SotaTransport(private val context: Context) : SotaTransport {
    private val pm get() = context.packageManager
    private val root: File get() = context.createDeviceProtectedStorageContext().dataDir.canonicalFile

    override fun checkEndpoint() {
        if (Build.VERSION.SDK_INT != 28 || Build.DEVICE != "t1c_acrn") throw SotaFailure("SOTA_UNSUPPORTED_DEVICE")
        val info = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(OEM_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (_: PackageManager.NameNotFoundException) { throw SotaFailure("SOTA_NOT_FOUND") }
        val application = info.applicationInfo ?: throw SotaFailure("SOTA_APP_INFO_MISSING")
        requireSota(info.packageName == OEM_PACKAGE && application.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            "SOTA_NOT_SYSTEM_APP")
        requireSota(isEnabled(pm.getApplicationEnabledSetting(OEM_PACKAGE), application.enabled), "SOTA_DISABLED")
        requireSota(signatures(info).singleOrNull() == OEM_CERTIFICATE, "SOTA_UNTRUSTED_CERTIFICATE")
        requireSota(knownBuild(info.longVersionCode, info.versionName), "SOTA_UNVERIFIED_VERSION")
        val receiver = info.receivers?.singleOrNull { it.name == "$OEM_PACKAGE.SotaSupportReceiver" }
            ?: throw SotaFailure("SOTA_RECEIVER_MISSING")
        val service = info.services?.singleOrNull { it.name == "$OEM_PACKAGE.SotaSupportService" }
            ?: throw SotaFailure("SOTA_SERVICE_MISSING")
        for (component in listOf(receiver, service)) {
            requireSota(isEnabled(pm.getComponentEnabledSetting(ComponentName(OEM_PACKAGE, component.name)), component.enabled),
                "SOTA_COMPONENT_DISABLED")
            requireSota(component.exported && componentPermissionGranted(component), "SOTA_COMPONENT_INACCESSIBLE")
        }
        requireSota(pm.checkPermission(Manifest.permission.INSTALL_PACKAGES, OEM_PACKAGE) == PackageManager.PERMISSION_GRANTED,
            "SOTA_INSTALL_PERMISSION_MISSING")
    }

    private fun componentPermissionGranted(component: ComponentInfo): Boolean {
        val permission = when (component) {
            is android.content.pm.ActivityInfo -> component.permission
            is android.content.pm.ServiceInfo -> component.permission
            else -> return false
        }
        return permission.isNullOrEmpty() || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun verifyArchive(source: File, digest: String): SotaUpdate {
        verifyDigest(source, digest)
        @Suppress("DEPRECATION")
        val flags = (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0) or PackageManager.GET_SIGNATURES
        val incoming = pm.getPackageArchiveInfo(source.absolutePath, flags) ?: throw SotaFailure("APK_UNREADABLE")
        val installed = pm.getPackageInfo(context.packageName, flags)
        requireSota(incoming.packageName == context.packageName, "APK_WRONG_PACKAGE")
        requireSota(versionCode(incoming) >= versionCode(installed), "APK_DOWNGRADE")
        requireSota((incoming.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT, "APK_INCOMPATIBLE")
        val existingSigners = signatures(installed)
        val newSigners = signatures(incoming)
        // Refuse unknown or rotated signers rather than asking a privileged OEM service to bypass Android's checks.
        requireSota(existingSigners.isNotEmpty() && existingSigners == newSigners, "APK_SIGNATURE_MISMATCH")
        return SotaUpdate(versionCode(incoming), installed.lastUpdateTime)
    }

    override fun storageMode(): Int = Os.stat(root.absolutePath).st_mode and PERMISSIONS

    override fun stage(staging: SotaStaging, source: File, digest: String) {
        val directory = directory(staging)
        requireSota(directory.mkdir(), "SOTA_STAGING_FAILED")
        Os.chmod(directory.absolutePath, OWNER_ONLY)
        val apk = File(directory, APK_NAME)
        FileOutputStream(apk).use { output ->
            source.inputStream().use { it.copyTo(output) }
            output.fd.sync()
        }
        verifyDigest(apk, digest)
        Os.chmod(apk.absolutePath, READ_ONLY)
        Os.chmod(directory.absolutePath, TRAVERSE)
        // Only device-protected staging is traversable. Credential-protected app data stays private.
        Os.chmod(root.absolutePath, staging.originalMode or TRAVERSE_BITS)
    }

    override fun send(staging: SotaStaging) {
        checkEndpoint()
        context.sendBroadcast(requestIntent(directory(staging)))
    }

    override fun cleanup(staging: SotaStaging) {
        runCatching {
            val directory = directory(staging)
            File(directory, APK_NAME).delete()
            if (directory.exists() && !directory.delete()) return
            val mode = storageMode()
            if (mode == (staging.originalMode or TRAVERSE_BITS)) Os.chmod(root.absolutePath, staging.originalMode)
        }
    }

    private fun directory(staging: SotaStaging): File {
        requireSota(REQUEST_ID.matches(staging.requestId) && staging.originalMode in 0..PERMISSIONS, "SOTA_INVALID_STAGING")
        val candidate = File(root, staging.directoryName)
        requireSota(candidate.canonicalFile == candidate && candidate.parentFile == root, "SOTA_INVALID_STAGING")
        return candidate
    }

    private fun verifyDigest(file: File, expected: String) {
        requireSota(file.isFile && file.length() > 0 && DIGEST.matches(expected) &&
            UpdateChecksums.sha256Hex(file).equals(expected, ignoreCase = true), "APK_CHECKSUM_MISMATCH")
    }

    companion object {
        @Suppress("DEPRECATION")
        internal fun versionCode(info: PackageInfo): Long =
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

        const val OEM_PACKAGE = "ltd.qisi.sotasupportapp"
        const val OEM_CERTIFICATE = "0cf420a100ee59f645d4085968765f29731faa0301cb07c14008a4f33d95f5bd"
        private const val APK_NAME = "update.apk"
        private const val PERMISSIONS = 511
        private const val OWNER_ONLY = 448
        private const val TRAVERSE = 457
        private const val TRAVERSE_BITS = 9
        private const val READ_ONLY = 292
        private val REQUEST_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val DIGEST = Regex("[a-fA-F0-9]{64}")

        internal fun knownBuild(version: Long, name: String?): Boolean = when (version) {
            2731L -> name == "20240905-114725"
            3337L -> name == "20250210-144658"
            else -> false
        }

        internal fun requestIntent(directory: File): Intent = Intent("$OEM_PACKAGE.ACTION_SOTASUPPORT")
            .setComponent(ComponentName(OEM_PACKAGE, "$OEM_PACKAGE.SotaSupportReceiver"))
            .putExtra("sota_support_folder", directory.absolutePath)
            .putExtra("sota_support_apk", APK_NAME)
            .putExtra("sota_support_resources", emptyArray<String>())
            .putExtra("usb_sota", false)

        @Suppress("DEPRECATION")
        private fun signatures(info: PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) info.signingInfo!!.apkContentsSigners else info.signatures)
                .orEmpty().map { UpdateChecksums.sha256Hex(ByteArrayInputStream(it.toByteArray())) }.toSet()

        private fun isEnabled(override: Int, declared: Boolean): Boolean = when (override) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> declared
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }

        private fun requireSota(condition: Boolean, reason: String) { if (!condition) throw SotaFailure(reason) }
    }
}
