package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Publishes CarPlay turn-by-turn data to Hongqi's stock Gaode stack using the same
 * [AUTONAVI_STANDARD_BROADCAST_SEND] vocabulary many Chinese head units use for HUD and cluster.
 *
 * This path is experimental: firmware may require different extras or a vendor-only receiver.
 */
internal object HongqiNavigationBridge {
    private const val TAG = "DiPlay-Hongqi-Navi"
    private const val AMAP_ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND"
    private const val KEY_GUIDANCE = 10001
    private const val KEY_STATE = 10019
    private const val STATE_ENDED = 9
    private const val KEEPALIVE_TICKS = 5
    private const val FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000

    private val lock = Any()
    private val route = BydHudRouteState()
    private var context: Context? = null
    private var adapter: HongqiAmapAdapter? = null
    private var amapTargetPackage: String? = null
    private var available = false
    private var senderStarted = false
    private var lastSent: BydClusterFrame? = null
    private var ticksSinceSend = 0
    private var guidanceLogged = false

    fun initialize(appContext: Context) = synchronized(lock) {
        if (context != null) return@synchronized
        context = appContext.applicationContext
        val packageName = HongqiAmapAdapter.resolvedPackage(appContext)
        adapter = packageName?.let { pkg ->
            HongqiAmapAdapter.entries.firstOrNull { it.packageName == pkg } ?: HongqiAmapAdapter.AMAP_AUTO
        }
        val outputPackages = HongqiAmapAdapter.navigationOutputPackages(appContext)
        amapTargetPackage = packageName ?: outputPackages.firstOrNull()
        available = !HongqiHeadUnit.isBydHeadUnit(appContext) && outputPackages.isNotEmpty()
        Log.i(TAG, "hongqi nav available=$available target=$amapTargetPackage outputs=$outputPackages qisi=${HongqiAmapAdapter.isQisiStack(appContext)}")
        if (available && !senderStarted) {
            senderStarted = true
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "diplay-hongqi-navi").apply { isDaemon = true }
            }.scheduleAtFixedRate(::tick, 1, 1, TimeUnit.SECONDS)
        }
    }

    fun onFrame(frame: Iap2Frame) = synchronized(lock) {
        if (!available) return@synchronized
        when (route.accept(frame.messageId, frame.payload)) {
            BydHudRouteChange.GUIDANCE -> sendCurrentLocked(force = false)
            BydHudRouteChange.CLEAR -> if (lastSent != null) sendEndLocked()
            BydHudRouteChange.NONE -> Unit
        }
    }

    fun clear() = synchronized(lock) {
        route.clear()
        if (available && lastSent != null) sendEndLocked()
    }

    private fun tick() = synchronized(lock) {
        if (lastSent != null && route.currentApple() == null) sendEndLocked()
        else if (++ticksSinceSend >= KEEPALIVE_TICKS) sendCurrentLocked(force = true)
    }

    private fun sendCurrentLocked(force: Boolean) {
        if (context?.let(BydOutputSettings::enabled) != true) {
            if (lastSent != null) sendEndLocked()
            return
        }
        val frame = route.currentApple()?.let(BydClusterFrame::from)
        if (frame == null) {
            if (lastSent != null) sendEndLocked()
            return
        }
        if (!force && frame == lastSent) return
        val intent = baseIntent(KEY_GUIDANCE).apply {
            putExtra("TYPE", 0)
            putExtra("EXTRA_STATE", 0)
            putExtra("EXTRA_IS_FOREGROUND", 1)
            putExtra("NEW_ICON", frame.icon)
            putExtra("ROUNG_ABOUT_NUM", frame.roundaboutExit)
            putExtra("SEG_REMAIN_DIS", frame.distanceMeters)
            putExtra("NEXT_ROAD_NAME", frame.road)
            putExtra("ROUTE_REMAIN_DIS", frame.routeRemainingMeters)
            putExtra("ROUTE_REMAIN_TIME", frame.routeRemainingSeconds)
            putExtra("CUR_ROAD_NAME", frame.road)
        }
        if (broadcastLocked(intent)) {
            lastSent = frame
            ticksSinceSend = 0
            if (!guidanceLogged) {
                guidanceLogged = true
                Log.i(TAG, "hongqi guidance sent $frame")
            }
        }
    }

    private fun sendEndLocked() {
        val intent = baseIntent(KEY_STATE).apply {
            putExtra("EXTRA_STATE", STATE_ENDED)
            putExtra("EXTRA_IS_FOREGROUND", 1)
            putExtra("NEW_ICON", -1)
            putExtra("SEG_REMAIN_DIS", -1)
            putExtra("NEXT_ROAD_NAME", "")
            putExtra("ROUTE_REMAIN_DIS", -1)
            putExtra("ROUTE_REMAIN_TIME", -1)
        }
        if (!broadcastLocked(intent)) return
        lastSent = null
        guidanceLogged = false
        Log.i(TAG, "hongqi guidance ended")
    }

    private fun baseIntent(keyType: Int): Intent {
        val pkg = amapTargetPackage ?: adapter?.packageName ?: HongqiAmapAdapter.AMAP_AUTO.packageName
        return Intent(AMAP_ACTION).apply {
            setPackage(pkg)
            addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND)
            putExtra("KEY_TYPE", keyType)
        }
    }

    private fun broadcastLocked(intent: Intent): Boolean {
        val appContext = context ?: return false
        val targets = HongqiAmapAdapter.navigationOutputPackages(appContext).ifEmpty {
            listOfNotNull(amapTargetPackage, adapter?.packageName)
        }
        for (pkg in targets) {
            if (sendLocked(intent, pkg)) return true
        }
        return sendLocked(intent, null)
    }

    private fun sendLocked(intent: Intent, packageName: String?): Boolean {
        val appContext = context ?: return false
        val copy = Intent(intent)
        if (packageName != null) copy.setPackage(packageName) else copy.setPackage(null)
        return try {
            appContext.sendBroadcast(copy)
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "hongqi broadcast failed pkg=$packageName", error)
            false
        }
    }

    private fun installed(appContext: Context, packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
