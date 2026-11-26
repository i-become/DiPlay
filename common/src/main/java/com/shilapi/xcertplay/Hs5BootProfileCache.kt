package com.shilapi.xcertplay

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest
import java.util.concurrent.Executors

/** The exact UI-negotiated display offer, available before an Activity can measure a window. */
internal object Hs5BootProfileCache {
    private const val FILE = "hs5-boot-display-v1"
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "hs5-display-cache").apply { isDaemon = true } }
    private val lock = Any()

    internal data class Profile(
        val stamp: String,
        val config: AirPlayConfig,
        val display: CarPlaySessionDisplay,
        val width: Int,
        val height: Int,
    ) : Serializable

    fun saveAsync(context: Context, config: AirPlayConfig, display: CarPlaySessionDisplay, width: Int, height: Int) {
        val app = context.applicationContext
        if (!Hs5HeadUnit.isHeadUnit(app)) return
        val profile = Profile(stamp(app), config,
            display.copy(viewAreas = display.viewAreas?.startupCopy(config.main.initialViewArea)), width, height)
        writer.execute { save(app, profile) }
    }

    internal fun save(context: Context, profile: Profile): Boolean = synchronized(lock) {
        runCatching {
            val bytes = ByteArrayOutputStream().also { buffer ->
                ObjectOutputStream(buffer).use { it.writeObject(profile) }
            }.toByteArray()
            require(bytes.size <= MAX_BYTES)
            val target = AtomicFile(File(context.filesDir, FILE))
            val output = target.startWrite()
            try {
                output.write(bytes)
                target.finishWrite(output)
            } catch (failure: Exception) {
                target.failWrite(output)
                throw failure
            }
        }.isSuccess
    }

    fun load(context: Context): Profile? = synchronized(lock) {
        runCatching {
            val bytes = AtomicFile(File(context.filesDir, FILE)).openRead().use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    require(buffer.size() + count <= MAX_BYTES)
                    buffer.write(chunk, 0, count)
                }
                buffer.toByteArray()
            }
            val profile = ObjectInputStream(bytes.inputStream()).use { it.readObject() as Profile }
            profile.takeIf { it.stamp == stamp(context) && it.width > 0 && it.height > 0 &&
                (!Hs5HeadUnit.isHeadUnit(context) || profile.width == 1920 && profile.height == 720) &&
                it.config.main.widthPixels == it.display.width && it.config.main.heightPixels == it.display.height }
        }.getOrNull()
    }

    internal fun stamp(context: Context): String {
        // Pairing records, logs, runtime day/night state and diagnostic export destinations change
        // during normal use. None changes the display offer or invalidates this boot profile.
        val ignored = setOf("debug_logs_enabled", "session_log_recording_enabled", "diagnostic_report_directory",
            "carplay_night_mode", "carplay_night_start_minute", "carplay_night_end_minute", "app_appearance",
            "ambient_lux_threshold", "ambient_delay_seconds", "auto_start_on_boot",
            // Local overlay appearance does not change the negotiated CarPlay canvas.
            // Do not exclude cluster stream size, scale, markers or enabled state here.
            "cluster_turn_card_theme", "cluster_turn_card_opacity_percent",
            "cluster_small_window_card_theme", "cluster_small_window_card_opacity_percent",
            "cluster_turn_card_overlay_position", "cluster_turn_card_overlay_size",
            "cluster_turn_card_overlay_size_percent", "cluster_turn_card_overlay_x_percent",
            "cluster_turn_card_overlay_y_percent", "cluster_small_window_card_size",
            "cluster_small_window_card_x", "cluster_small_window_card_y")
        val values = buildString {
            append(Build.FINGERPRINT)
            append(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            for (name in listOf("xcertplay_airplay", "diplay_carplay_dock", "diplay_carplay_rotation",
                "diplay_side_panel", "diplay_split_screen")) {
                append('|').append(name)
                for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toSortedMap()) {
                    if (key in ignored || key.startsWith("identity_") || key.startsWith("pairing") ||
                        key.startsWith("lockdown_") || key.startsWith("startup_display_") ||
                        key.startsWith("display_max_detected_")) continue
                    append('|').append(key).append('=').append(value)
                }
            }
            val icon = File(context.filesDir, "airplay-icon.png")
            append('|').append(icon.length()).append(':').append(icon.lastModified())
        }
        return MessageDigest.getInstance("SHA-256").digest(values.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
