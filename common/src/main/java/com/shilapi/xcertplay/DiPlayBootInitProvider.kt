package com.shilapi.xcertplay

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.shilapi.xcertplay.hud.Hs5HeadUnit

/**
 * Runs as soon as the DiPlay process starts (before [BootReceiver]) so Hongqi boot prewarm
 * begins while the system is still bringing up the head unit.
 */
class DiPlayBootInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return true
        if (!Hs5HeadUnit.isHeadUnit(app)) return true
        if (!AirPlayPersistence.loadAutoStartOnBoot(app)) return true
        DiPlayBootStartup.runPrewarm(app, launchEnabled = true)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
