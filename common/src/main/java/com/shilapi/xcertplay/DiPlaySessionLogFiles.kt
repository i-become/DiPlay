package com.shilapi.xcertplay

import android.content.Context
import java.io.File

/** On-device session logs included in diagnostic reports. */
object DiPlaySessionLogFiles {
    fun directory(context: Context): File = File(context.applicationContext.filesDir, "logs")

    fun diplayLog(context: Context): File = File(directory(context), "diplay.log")

    fun clearAll(context: Context) {
        CarPlayStartupDiagnostics.clearTrace()
        val dir = directory(context)
        for (name in SessionLogFile.REPORT_NAMES) {
            runCatching { File(dir, name).delete() }
        }
    }
}
