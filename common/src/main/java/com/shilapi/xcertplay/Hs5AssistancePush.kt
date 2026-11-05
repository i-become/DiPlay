package com.shilapi.xcertplay

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/** Read-only optional HUD/IFC subscriptions. Callback values remain raw display/mode enums. */
internal class Hs5AssistancePush(
    val module: Module,
    private val receive: (Int, Int) -> Unit,
) : Binder() {
    enum class Module(val id: Int, val register: Int, val unregister: Int, val listener: String) {
        HUD(23, 31, 32, "IVehicleCarHUDChangedListener"),
        IFC(46, 24, 25, "IVehicleCarIFCChangedListener");
        val descriptor get() = "ltd.qisi.adapterproxy.ICar${name}Manager"
        val listenerDescriptor get() = "ltd.qisi.adapterproxy.listener.$listener"
    }

    init { attachInterface(null, module.listenerDescriptor) }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == IBinder.INTERFACE_TRANSACTION) {
            reply?.writeString(module.listenerDescriptor)
            return true
        }
        if (code !in 1..14) return super.onTransact(code, data, reply, flags)
        try {
            data.enforceInterface(module.listenerDescriptor)
            if (data.dataAvail() != 4) return false
            receive(code, data.readInt())
            reply?.writeNoException()
            return true
        } catch (_: RuntimeException) {
            return false
        }
    }
}
