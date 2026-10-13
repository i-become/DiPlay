package com.shilapi.xcertplay

import android.content.Context
import java.lang.ref.WeakReference

/** Lets [NewUiActivity] push display settings into a running [CarPlayHostActivity]. */
internal object CarPlayHostSettingsBridge {
    private var hostRef: WeakReference<CarPlayHostActivity>? = null

    fun register(host: CarPlayHostActivity) {
        hostRef = WeakReference(host)
    }

    fun unregister(host: CarPlayHostActivity) {
        if (hostRef?.get() === host) hostRef = null
    }

    fun notifyCarPlayNightModeChanged(context: Context) {
        val host = hostRef?.get()
        if (host != null) {
            host.reloadCarPlayNightModeFromSettings()
            return
        }
        val night = CarPlayNightModePolicy.effectiveNight(context)
        CarPlayBackgroundSession.snapshot()?.controller?.syncCarPlayNightMode(night)
    }

    fun notifyClusterMapSettingsChanged() {
        hostRef?.get()?.reloadClusterMapFromSettings()
    }
}
