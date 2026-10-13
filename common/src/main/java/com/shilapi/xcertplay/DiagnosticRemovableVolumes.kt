package com.shilapi.xcertplay

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

data class RemovableStorageVolume(
    val label: String,
    val rootPath: String,
)

/** Lists removable volumes (typically USB sticks) with writable root paths. */
internal object DiagnosticRemovableVolumes {
    fun list(context: Context): List<RemovableStorageVolume> {
        return runCatching { listUnsafe(context) }.getOrDefault(emptyList())
    }

    private fun listUnsafe(context: Context): List<RemovableStorageVolume> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptyList()
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return manager.storageVolumes.mapNotNull { volume ->
            runCatching {
                if (!volume.isRemovable) return@runCatching null
                val directory = volume.directory ?: return@runCatching null
                if (!directory.isDirectory && !directory.mkdirs()) return@runCatching null
                val label = volume.getDescription(context)?.toString()?.takeIf { it.isNotBlank() }
                    ?: directory.name
                RemovableStorageVolume(label = label, rootPath = directory.absolutePath)
            }.getOrNull()
        }.distinctBy { it.rootPath }
    }

    fun firstUsbRoot(context: Context): File? =
        list(context).firstOrNull()?.rootPath?.let(::File)
}
