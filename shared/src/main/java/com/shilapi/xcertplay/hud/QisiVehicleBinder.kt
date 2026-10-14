package com.shilapi.xcertplay.hud

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/** Binder client for ltd.qisi.adapterservice vehicle modules (HUD, instrument, system). */
internal object QisiVehicleBinder {
    const val ADAPTER_PACKAGE = "ltd.qisi.adapterservice"
    const val ADAPTER_SERVICE = "ltd.qisi.adapterservice.QISIAdapterService"

    const val TOKEN_REMOTE = "ltd.qisi.adapterproxy.IVehicleRemoteClienter"
    const val TOKEN_HUD = "ltd.qisi.adapterproxy.ICarHUDManager"
    const val TOKEN_INSTRUMENT = "ltd.qisi.adapterproxy.ICarInstrumentRelatedManager"
    const val TOKEN_SYSTEM = "ltd.qisi.adapterproxy.ICarSystemControlManager"

    const val MODULE_HUD = 23
    const val MODULE_INSTRUMENT = 8
    const val MODULE_SYSTEM = 13

    const val TX_REMOTE_QUERY = 1

    const val HUD_SET_ICON = 5
    const val HUD_SET_ROUNDABOUT = 6
    const val HUD_SET_DISTANCE = 70

    const val INSTRUMENT_SET_ACTIVE = 5
    const val INSTRUMENT_SEND_GUIDANCE = 6
    const val INSTRUMENT_MAP_MODE = 10

    const val SYSTEM_INSTRUMENT_MAP = 69

    fun queryModule(remote: IBinder, moduleId: Int, expectedToken: String): IBinder {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN_REMOTE)
            data.writeInt(moduleId)
            if (!remote.transact(TX_REMOTE_QUERY, data, reply, 0)) {
                throw RemoteException("queryBinder rejected")
            }
            reply.readException()
            val binder = reply.readStrongBinder()
                ?: throw RemoteException("queryBinder returned null")
            val descriptor = binder.interfaceDescriptor
            if (descriptor != expectedToken) {
                throw SecurityException("module interface")
            }
            return binder
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun transactInts(binder: IBinder, token: String, code: Int, values: IntArray) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(token)
            for (value in values) data.writeInt(value)
            if (!binder.transact(code, data, reply, 0)) {
                throw RemoteException("navigation transaction rejected")
            }
            reply.readException()
            ensureSuccess(reply.readInt())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun sendInstrumentGuidance(binder: IBinder, guidance: QisiInstrumentFrame) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN_INSTRUMENT)
            data.writeInt(1)
            guidance.writeToParcel(data)
            if (!binder.transact(INSTRUMENT_SEND_GUIDANCE, data, reply, 0)) {
                throw RemoteException("instrument guidance rejected")
            }
            reply.readException()
            ensureSuccess(reply.readInt())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun setInstrumentMapMode(binder: IBinder, mode: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN_INSTRUMENT)
            data.writeInt(1)
            data.writeInt(0)
            data.writeInt(mode)
            if (!binder.transact(INSTRUMENT_MAP_MODE, data, reply, 0)) {
                throw RemoteException("instrument map mode rejected")
            }
            reply.readException()
            ensureSuccess(reply.readInt())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Segment distance as QiSi HUD expects: unit selector plus scaled value. */
    fun hudDistanceUnits(meters: Int): IntArray {
        val distance = meters.coerceAtLeast(0)
        return when {
            distance < 15 -> intArrayOf(0, 0)
            distance < 1_000 -> intArrayOf(1, distance)
            distance < 10_000 -> intArrayOf(2, distance / 100)
            else -> intArrayOf(3, distance / 1_000)
        }
    }

    private fun ensureSuccess(code: Int) {
        if (code >= 0) return
        throw RemoteException("navigation result $code")
    }
}

internal data class QisiInstrumentFrame(
    val distanceLabel: String,
    val icon: Int,
    val nextRoad: String,
    val routeDistanceLabel: String,
    val etaLabel: String,
    val distanceUnit: Int,
    val roundaboutExit: Int,
) {
    fun writeToParcel(parcel: Parcel) {
        parcel.writeString(distanceLabel)
        parcel.writeInt(icon)
        parcel.writeString(nextRoad)
        parcel.writeString(routeDistanceLabel)
        parcel.writeString(etaLabel)
        parcel.writeInt(distanceUnit)
        parcel.writeInt(roundaboutExit)
    }

    companion object {
        val cleared = QisiInstrumentFrame("", 0, "", "", "", 0, -1)
    }
}
