package com.shilapi.xcertplay.hud

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/** Binder client for ltd.qisi.adapterservice vehicle modules (HUD, instrument, system). */
internal object QisiVehicleBinder {
    /** An absent transaction is a firmware capability mismatch, not a dead service. */
    class UnsupportedTransaction(code: Int) : RemoteException("unsupported navigation transaction $code")
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
    const val HUD_SET_NEXT_CROSS_DISTANCE = 7
    const val HUD_SET_NEXT_CROSS_RANGE = 9
    const val HUD_SET_INTERSECTION = 24
    const val HUD_SET_LEFT_LANE_LINE = 33
    const val HUD_SET_RIGHT_LANE_LINE = 34
    const val HUD_SET_ROAD_TYPE = 35
    const val HUD_SET_NAV_STATUS = 36
    const val HUD_SET_DESTINATION_NAME = 37
    const val HUD_SET_DESTINATION_DISTANCE = 38
    const val HUD_SET_TARGET_EXIT = 44
    const val HUD_SET_CURRENT_EXIT = 45
    const val HUD_SET_REMAINING_TIME = 46
    const val HUD_SET_TOTAL_DISTANCE = 47
    const val HUD_SET_CAMERA_DISTANCE = 48
    const val HUD_SET_NEXT_ROAD = 49
    const val HUD_SET_LANE_TYPE = 57
    const val HUD_SET_NEXT_ROAD_INFO = 58
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
                throw UnsupportedTransaction(code)
            }
            reply.readException()
            ensureSuccess(reply.readInt())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun transactString(binder: IBinder, token: String, code: Int, value: String) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(token)
            data.writeString(value)
            if (!binder.transact(code, data, reply, 0)) {
                throw UnsupportedTransaction(code)
            }
            reply.readException()
            ensureSuccess(reply.readInt())
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun transactStringInt(binder: IBinder, token: String, code: Int, value: String, number: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(token)
            data.writeString(value)
            data.writeInt(number)
            if (!binder.transact(code, data, reply, 0)) {
                throw UnsupportedTransaction(code)
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
                throw UnsupportedTransaction(INSTRUMENT_SEND_GUIDANCE)
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
