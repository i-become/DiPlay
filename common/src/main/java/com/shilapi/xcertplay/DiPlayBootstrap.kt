package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.orchestration.MfiTarget
import java.io.File
import java.security.MessageDigest

/** Installs bundled local MFi authentication assets when [MfiTarget.LOCAL] is selected. */
internal object DiPlayBootstrap {
    @Volatile private var ready = false

    @Synchronized fun ensure(context: Context, mfiTarget: MfiTarget) {
        if (mfiTarget != MfiTarget.LOCAL) return
        if (ready) return
        val target = File(context.noBackupFilesDir, LocalMfiAuthenticationClient.DIRECTORY)
        if (!target.exists()) {
            val staging = File(context.noBackupFilesDir, "offline-mfi-staging")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Could not prepare local authentication" }
            staging.setReadable(false, false); staging.setReadable(true, true)
            staging.setExecutable(false, false); staging.setExecutable(true, true)
            try {
                for (name in listOf("identity.pk8", "certificate.p7b")) {
                    val file = File(staging, name)
                    context.assets.open("offline-mfi/$name").use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file.setReadable(false, false); file.setReadable(true, true)
                    file.setWritable(false, false); file.setWritable(true, true)
                }
                LocalMfiAuthenticationClient.load(staging)
                check(staging.renameTo(target)) { "Could not install local authentication" }
            } finally {
                staging.deleteRecursively()
            }
        }
        LocalMfiAuthenticationClient.load(target)
        AirPlayPersistence.saveDebugLogsEnabled(context, false)
        ready = true
    }

    fun deviceId(identity: AirPlayIdentity): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.publicKey).take(6).toByteArray()
        bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }
}

internal enum class DefaultConnectionMode(val key: String) {
    LAST_USED("last_used"), WIRELESS("wireless"), USB("usb");

    fun wireless(lastUsedWireless: Boolean): Boolean = when (this) {
        LAST_USED -> lastUsedWireless
        WIRELESS -> true
        USB -> false
    }

    companion object {
        fun fromKey(key: String?): DefaultConnectionMode = entries.firstOrNull { it.key == key } ?: LAST_USED
    }
}

internal object DiPlayPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
    fun phoneAddress(context: Context): String? = prefs(context).getString("phone_address", null)
    fun phoneName(context: Context): String = prefs(context).getString("phone_name", null) ?: "Your iPhone"
    fun savePhone(context: Context, address: String, name: String) {
        val preferences = prefs(context)
        preferences.edit().putString("phone_address", address).putString("phone_name", name)
            .also { if (!preferences.contains("phone_link_mode_$address")) it.putString("phone_link_mode_$address", inferredPhoneLinkMode(name).key) }
            .apply()
    }
    enum class PhoneLinkMode(val key: String, val label: String) {
        CARPLAY("carplay", "CarPlay"), CARLINK("carlink", "Carlink");
        companion object {
            fun fromKey(key: String?): PhoneLinkMode = entries.firstOrNull { it.key == key } ?: CARPLAY
        }
    }
    fun phoneLinkMode(context: Context, address: String? = phoneAddress(context)): PhoneLinkMode {
        if (address.isNullOrBlank()) return PhoneLinkMode.CARPLAY
        return PhoneLinkMode.fromKey(prefs(context).getString("phone_link_mode_$address", null))
    }
    fun inferredPhoneLinkMode(name: String): PhoneLinkMode {
        val normalized = name.lowercase()
        val androidBrands = listOf("android", "pixel", "samsung", "galaxy", "xiaomi", "redmi", "huawei", "honor", "oppo", "vivo", "oneplus", "realme", "motorola")
        return if (androidBrands.any(normalized::contains)) PhoneLinkMode.CARLINK else PhoneLinkMode.CARPLAY
    }
    fun phoneLinkMode(context: Context, address: String, deviceName: String): PhoneLinkMode =
        if (prefs(context).contains("phone_link_mode_$address")) phoneLinkMode(context, address) else inferredPhoneLinkMode(deviceName)
    fun savePhoneLinkMode(context: Context, address: String, mode: PhoneLinkMode) {
        prefs(context).edit().putString("phone_link_mode_$address", mode.key).apply()
    }
    fun carlinkPhonePaired(context: Context, address: String?): Boolean =
        !address.isNullOrBlank() && prefs(context).getBoolean("carlink_paired_$address", false)
    fun markCarlinkPhonePaired(context: Context, address: String?) {
        if (!address.isNullOrBlank()) prefs(context).edit().putBoolean("carlink_paired_$address", true).apply()
    }
    fun defaultConnectionMode(context: Context) =
        DefaultConnectionMode.fromKey(prefs(context).getString("default_connection_mode", null))
    fun saveDefaultConnectionMode(context: Context, mode: DefaultConnectionMode) {
        prefs(context).edit().putString("default_connection_mode", mode.key).apply()
    }
    fun autoConnectWireless(context: Context) =
        defaultConnectionMode(context).wireless(AirPlayPersistence.loadWirelessEnabled(context))
    fun autoConnect(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.contains("auto_connect") && Hs5HeadUnit.isHeadUnit(context)) return true
        return prefs.getBoolean("auto_connect", false)
    }
    fun saveAutoConnect(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("auto_connect", value).apply()
    }
    fun connectOnPhoneBluetooth(context: Context) = prefs(context).getBoolean("connect_on_phone_bluetooth", false)
    fun saveConnectOnPhoneBluetooth(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("connect_on_phone_bluetooth", value).apply()
    }

}
