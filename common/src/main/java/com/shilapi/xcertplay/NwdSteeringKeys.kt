package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController

/** NWD bus values have their own namespace; they are not Android KeyEvent codes. */
internal object NwdSteeringKeyMappings {
    const val ACTION = "com.nwd.action.ACTION_KEY_VALUE"
    const val EXTRA = "extra_key_value"
    private const val PREFERENCES = "diplay_nwd_keys"

    enum class Command { CLOSE_PROJECTION, PREVIOUS, NEXT, SIRI, IGNORE }

    fun save(context: Context, code: Int, command: Command, longPress: Boolean = false) {
        require(code in 0..127)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(key(code, longPress), command.name).apply()
    }

    fun clear(context: Context, code: Int, longPress: Boolean = false) {
        require(code in 0..127)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .remove(key(code, longPress)).apply()
    }

    fun resolve(context: Context, code: Int, longPress: Boolean = false): Command? {
        if (code !in 0..127) return null
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = key(code, longPress)
        if (preferences.contains(key)) {
            // A damaged or newer mapping must not silently fall back to a different action.
            return Command.entries.firstOrNull { it.name == preferences.getString(key, null) }
        }
        if (longPress) return null
        return when (code) {
            5 -> Command.PREVIOUS
            6 -> Command.NEXT
            else -> null
        }
    }

    private fun key(code: Int, longPress: Boolean): String =
        "swc_$ACTION$code" + if (longPress) "_L" else ""
}

/** Parses the bus only. All session checks and UI work happen on the internal main-thread queue. */
class NwdSteeringKeyReceiver internal constructor(private val dynamic: Boolean) : BroadcastReceiver() {
    constructor() : this(false)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NwdSteeringKeyMappings.ACTION) return
        // Package broadcasts can reach both registrations. Component-targeted broadcasts reach
        // only the manifest receiver, whereas implicit broadcasts need the live registration on O+.
        if (!dynamic && SteeringWheelEvents.dynamicReceiverRegistered && intent.component == null) return
        @Suppress("DEPRECATION")
        val raw = intent.extras?.get(NwdSteeringKeyMappings.EXTRA)
        if (raw !is Byte) return
        val code = intent.getByteExtra(NwdSteeringKeyMappings.EXTRA, (-1).toByte()).toInt()
        if (code < 0) return
        SteeringWheelEvents.postNwd(code)
    }
}

/** Session-scoped ingress for OEM broadcasts. Never owns or stops a CarPlay connection. */
internal object SteeringWheelEvents {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var controller: CarPlayController? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var generation = 0L
    private var receiver: NwdSteeringKeyReceiver? = null
    val dynamicReceiverRegistered: Boolean get() = receiver != null

    fun attach(context: Context, next: CarPlayController) {
        if (controller !== next) generation++
        appContext = context.applicationContext
        controller = next
        if (receiver == null) {
            val ingress = NwdSteeringKeyReceiver(true)
            val filter = IntentFilter(NwdSteeringKeyMappings.ACTION).apply { priority = Int.MAX_VALUE }
            runCatching {
                ContextCompat.registerReceiver(appContext!!, ingress, filter, ContextCompat.RECEIVER_EXPORTED)
            }.onSuccess { receiver = ingress }
                .onFailure { failure -> Hs5SteeringKeys.recordEvent { "NWD registration failed: ${failure.javaClass.simpleName}" } }
        }
    }

    fun detach(expected: CarPlayController) {
        if (controller !== expected) return
        generation++
        receiver?.let { ingress -> runCatching { appContext?.unregisterReceiver(ingress) } }
        receiver = null
        controller = null
        appContext = null
    }

    fun postNwd(code: Int) {
        val context = appContext ?: return
        val expected = controller ?: return
        val revision = generation
        val session = expected.activeAirPlaySessionToken() ?: return
        val target = CarPlayHostSettingsBridge.wheelBackTarget()
        main.post {
            if (generation != revision || controller !== expected || expected.isClosed() ||
                expected.activeAirPlaySessionToken() !== session) return@post
            val command = NwdSteeringKeyMappings.resolve(context, code)
            Hs5SteeringKeys.recordEvent { "NWD key=$code command=$command" }
            when (command) {
                NwdSteeringKeyMappings.Command.CLOSE_PROJECTION -> {
                    if (target != null) CarPlayHostSettingsBridge.closeFromWheel(target, "NWD key $code")
                }
                NwdSteeringKeyMappings.Command.PREVIOUS -> media(context, CarPlayMediaButton.PREVIOUS)
                NwdSteeringKeyMappings.Command.NEXT -> media(context, CarPlayMediaButton.NEXT)
                NwdSteeringKeyMappings.Command.SIRI -> {
                    if (AirPlayPersistence.loadCarPlayWheelSiri(context)) expected.requestSiri()
                }
                NwdSteeringKeyMappings.Command.IGNORE, null -> Unit
            }
        }
    }

    private fun media(context: Context, button: Int) {
        if (AirPlayPersistence.loadCarPlayWheelMedia(context) &&
            !AirPlayPersistence.loadCarBluetoothAudio(context)) {
            CarPlayMediaKeys.onExternalMediaKey(button, "nwd-wheel")
        }
    }
}
