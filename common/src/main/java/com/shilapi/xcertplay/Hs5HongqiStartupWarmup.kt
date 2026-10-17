package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Prewarm wireless link and local MFi assets so the first CarPlay session starts sooner. */
internal object Hs5HongqiStartupWarmup {
    private val started = AtomicBoolean(false)

    fun schedule(context: Context) {
        val app = context.applicationContext
        if (!Hs5HeadUnit.isHeadUnit(app)) return
        if (!started.compareAndSet(false, true)) return
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        Hs5WirelessPrewarm.schedule(app)
        Thread({
            runCatching {
                DiPlayBootstrap.ensure(app, AirPlayPersistence.loadMfiTarget(app))
            }
        }, "diplay-hq-bootstrap").apply { isDaemon = true }.start()
    }
}
