package com.shilapi.xcertplay.update

import android.content.pm.PackageInfo
import java.io.File
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class UpdateArchiveTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val file = File("/tmp/update-archive-test.apk")
    private val release = UpdateRelease("v1.4.4", "DiPlay-1.4.4.apk", "https://example.com/app.apk", "https://example.com/SHA256SUMS.txt")

    @Before fun installed() {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = context.packageName
            versionName = "1.4.3"
            longVersionCode = 1403
        })
    }

    private fun archive(name: String = context.packageName, version: String = "1.4.4", code: Long = 1404) {
        shadowOf(context.packageManager).setPackageArchiveInfo(file.absolutePath, PackageInfo().apply {
            packageName = name
            versionName = version
            longVersionCode = code
        })
    }

    @Test fun acceptsMatchingArchive() {
        archive()
        UpdateArchive.verify(context, file, release)
    }

    @Test fun refusesWrongPackageMislabelledVersionTestBuildAndDowngrade() {
        archive(name = "another.app")
        assertThrows(IOException::class.java) { UpdateArchive.verify(context, file, release) }
        archive(version = "1.4.2")
        assertThrows(IOException::class.java) { UpdateArchive.verify(context, file, release) }
        archive(version = "1.4.4-hud-test")
        assertThrows(IOException::class.java) { UpdateArchive.verify(context, file, release) }
        archive(code = 1402)
        assertThrows(IOException::class.java) { UpdateArchive.verify(context, file, release) }
    }

    @Test fun refusesNonApkDownload() {
        assertThrows(IOException::class.java) { UpdateArchive.verify(context, file, release) }
    }
}
