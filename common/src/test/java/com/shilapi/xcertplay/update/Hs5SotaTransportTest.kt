package com.shilapi.xcertplay.update

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.system.Os
import android.system.StructStat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION")
class Hs5SotaTransportTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val transport get() = Hs5SotaTransport(app)
    private val source get() = File(app.cacheDir, "checked-update.apk")
    private val digest get() = UpdateChecksums.sha256Hex(source)

    @Before fun prepare() {
        source.writeText("a signed APK is simulated by the test PackageManager")
        val installed = app.packageManager.getPackageInfo(app.packageName, 0).apply {
            versionCode = 10
            signatures = arrayOf(Signature("010203"))
        }
        shadowOf(app.packageManager).installPackage(installed)
        archive()
    }

    @Test fun intentContainsOnlyTheExplicitApkInstallContract() {
        val intent = Hs5SotaTransport.requestIntent(File(app.cacheDir, "sota"))
        assertEquals("ltd.qisi.sotasupportapp.ACTION_SOTASUPPORT", intent.action)
        assertEquals("ltd.qisi.sotasupportapp.SotaSupportReceiver", intent.component?.className)
        assertEquals("ltd.qisi.sotasupportapp", intent.component?.packageName)
        assertEquals(File(app.cacheDir, "sota").absolutePath, intent.getStringExtra("sota_support_folder"))
        assertEquals("update.apk", intent.getStringExtra("sota_support_apk"))
        assertArrayEquals(emptyArray<String>(), intent.getStringArrayExtra("sota_support_resources"))
        assertFalse(intent.getBooleanExtra("usb_sota", true))
        assertEquals(4, intent.extras!!.size())
    }

    @Test fun knownVersionsMustMatchBothNameAndCode() {
        assertTrue(Hs5SotaTransport.knownBuild(2731, "20240905-114725"))
        assertTrue(Hs5SotaTransport.knownBuild(3337, "20250210-144658"))
        assertFalse(Hs5SotaTransport.knownBuild(2731, "20250210-144658"))
        assertFalse(Hs5SotaTransport.knownBuild(3338, "20250210-144658"))
        assertFalse(Hs5SotaTransport.knownBuild(2731, null))
    }

    @Test fun packageVersionSdkAndSignerAreCheckedBeforeStaging() {
        assertEquals(11L, transport.verifyArchive(source, digest).version)
        archive(packageName = "another.app")
        rejected("APK_WRONG_PACKAGE") { transport.verifyArchive(source, digest) }
        archive(version = 9)
        rejected("APK_DOWNGRADE") { transport.verifyArchive(source, digest) }
        archive(minSdk = 29)
        rejected("APK_INCOMPATIBLE") { transport.verifyArchive(source, digest) }
        archive(signer = Signature("040506"))
        rejected("APK_SIGNATURE_MISMATCH") { transport.verifyArchive(source, digest) }
    }

    @Test fun sameVersionReinstallIsAllowedButStillRequiresMatchingSigner() {
        archive(version = 10)
        assertEquals(10L, transport.verifyArchive(source, digest).version)
    }

    @Test fun missingOrChangedDownloadedChecksumCannotBeRecomputedIntoTrust() {
        rejected("APK_CHECKSUM_MISMATCH") { transport.verifyArchive(source, "") }
        val verified = digest
        source.appendText("tampered")
        rejected("APK_CHECKSUM_MISMATCH") { transport.verifyArchive(source, verified) }
    }

    @Test fun copiedApkIsRehashedBeforeItCanBeSentToSota() {
        val token = SotaStaging(java.util.UUID.randomUUID().toString(), transport.storageMode())
        try {
            rejected("APK_CHECKSUM_MISMATCH") { transport.stage(token, source, "0".repeat(64)) }
        } finally { transport.cleanup(token) }
        val root = app.createDeviceProtectedStorageContext().dataDir
        assertFalse(File(root, token.directoryName).exists())
    }

    @Test
    @Config(shadows = [StagingPermissions::class])
    fun stagingIsReadOnlyAndCleanupPreservesUnrelatedPrivateFiles() {
        StagingPermissions.modes.clear()
        val root = app.createDeviceProtectedStorageContext().dataDir
        val previousMode = transport.storageMode()
        val marker = File(root, "unrelated-private-file").apply { writeText("preserve") }
        val token = SotaStaging(java.util.UUID.randomUUID().toString(), previousMode)
        try {
            transport.stage(token, source, digest)
            val directory = File(root, token.directoryName)
            val apk = File(directory, "update.apk")
            assertEquals(digest, UpdateChecksums.sha256Hex(apk))
            assertEquals(292, Os.stat(apk.absolutePath).st_mode and 511)
            assertEquals(457, Os.stat(directory.absolutePath).st_mode and 511)
            assertEquals(previousMode or 9, transport.storageMode())
        } finally { transport.cleanup(token) }
        assertFalse(File(root, token.directoryName).exists())
        assertEquals(previousMode, transport.storageMode())
        assertEquals("preserve", marker.readText())
    }

    @Test fun cleanupNeverAcceptsAPathFromOutsideTheOwnedStagingDirectory() {
        val marker = File(app.createDeviceProtectedStorageContext().dataDir, "keep-me").apply { writeText("keep") }
        transport.cleanup(SotaStaging("../keep-me", 448))
        assertEquals("keep", marker.readText())
        rejected("SOTA_INVALID_STAGING") { transport.stage(SotaStaging("../escape", 448), source, digest) }
    }

    @Test fun missingOrSpoofedOemAppIsNeverUsed() {
        ReflectionHelpers.setStaticField(Build::class.java, "DEVICE", "t1c_acrn")
        rejected("SOTA_NOT_FOUND") { transport.checkEndpoint() }
        val info = PackageInfo().apply {
            packageName = Hs5SotaTransport.OEM_PACKAGE
            versionCode = 2731
            versionName = "20240905-114725"
            signatures = arrayOf(Signature("010203"))
            applicationInfo = ApplicationInfo().apply {
                packageName = Hs5SotaTransport.OEM_PACKAGE
                enabled = true
            }
        }
        shadowOf(app.packageManager).installPackage(info)
        rejected("SOTA_NOT_SYSTEM_APP") { transport.checkEndpoint() }
        info.applicationInfo!!.flags = ApplicationInfo.FLAG_SYSTEM
        shadowOf(app.packageManager).installPackage(info)
        rejected("SOTA_UNTRUSTED_CERTIFICATE") { transport.checkEndpoint() }
        app.packageManager.setApplicationEnabledSetting(info.packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
        rejected("SOTA_DISABLED") { transport.checkEndpoint() }
    }

    private fun archive(
        packageName: String = app.packageName, version: Int = 11,
        signer: Signature = Signature("010203"), minSdk: Int = 25,
    ) {
        shadowOf(app.packageManager).setPackageArchiveInfo(source.absolutePath, PackageInfo().apply {
            this.packageName = packageName
            versionCode = version
            signatures = arrayOf(signer)
            applicationInfo = ApplicationInfo().apply { minSdkVersion = minSdk }
        })
    }

    private fun rejected(code: String, action: () -> Any?) {
        try { action(); fail("Expected $code") }
        catch (failure: SotaFailure) { assertEquals(code, failure.code) }
    }

    /** Windows does not implement POSIX modes; verify the Android syscalls instead. */
    @Implements(Os::class)
    class StagingPermissions {
        companion object {
            val modes = mutableMapOf<String, Int>()

            @Implementation @JvmStatic
            fun chmod(path: String, mode: Int) { modes[path] = mode }

            @Implementation @JvmStatic
            fun stat(path: String): StructStat = StructStat(
                0, 0, modes[path] ?: 448, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            )
        }
    }
}
