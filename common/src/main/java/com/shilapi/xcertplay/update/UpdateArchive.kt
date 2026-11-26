package com.shilapi.xcertplay.update

import android.content.Context
import java.io.File
import java.io.IOException

/** Check the downloaded APK itself, rather than trusting a tag or attachment name. */
internal object UpdateArchive {
    fun verify(context: Context, file: File, release: UpdateRelease) {
        val incoming = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("下载内容不是有效的 APK，请重新下载或检查发布附件")
        if (incoming.packageName != context.packageName) throw IOException("更新包的应用标识与当前软件不一致")
        val actual = incoming.versionName
        if (actual == null || !UpdateVersion.isStable(actual) ||
            UpdateVersion.parse(actual) != UpdateVersion.parse(release.tagName)) {
            throw IOException("更新包实际版本与发布版本 ${release.tagName} 不一致")
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Hs5SotaTransport.versionCode(incoming) < Hs5SotaTransport.versionCode(installed)) {
            throw IOException("更新包的内部版本号低于当前版本，已阻止降级")
        }
    }
}
