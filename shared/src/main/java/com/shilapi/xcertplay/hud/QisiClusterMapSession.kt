package com.shilapi.xcertplay.hud

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.orchestration.ConnectionDiagnosticTrace

/** Owns the OEM full-screen map request and its corresponding exit notification. */
internal class QisiClusterMapSession(
    private val system: IBinder,
    private val instrument: IBinder,
    private val main: Handler,
    private val onQuit: () -> Unit,
) {
    @Volatile private var listener: IBinder? = null
    @Volatile private var active = false
    private var mapRequested = false
    private var navigationRequested = false

    fun open() {
        if (active) return
        val callback = object : Binder() {
            init { attachInterface(null, CALLBACK_TOKEN) }

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) {
                    reply?.writeString(CALLBACK_TOKEN)
                    return true
                }
                if (code !in 1..29) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(CALLBACK_TOKEN)
                // This shared OEM listener also carries unrelated vehicle notifications.
                if (code == FULL_SCREEN_NAVIGATION_REQUEST) {
                    val request = data.readString()
                    trace { "full-screen navigation request=$request" }
                    if (request == "HardKeyQuit" || request == "0") {
                        main.post {
                            if (active && listener === this) {
                                trace { "exit request delivered to current instrument window" }
                                onQuit()
                            } else trace { "stale instrument exit request ignored" }
                        }
                    }
                }
                return true // OEM callbacks are one-way.
            }
        }
        listener = callback
        try {
            changeListener(REGISTER_LISTENER, callback)
            trace { "exit listener registered tx=64" }
            mapRequested = true
            QisiVehicleBinder.setInstrumentMapMode(instrument, 4)
            navigationRequested = true
            QisiVehicleBinder.transactInts(system, QisiVehicleBinder.TOKEN_SYSTEM,
                QisiVehicleBinder.SYSTEM_INSTRUMENT_MAP, intArrayOf(1, 1))
            active = true
            trace { "full-screen navigation active map=4 navigation=1,true" }
        } catch (error: Exception) {
            trace { "full-screen navigation entry failed: ${error.javaClass.simpleName}: ${error.message}" }
            close()
            throw error
        }
    }

    fun close() {
        val callback = listener ?: return
        // Invalidate queued requests before touching the remote service or releasing its listener.
        listener = null
        active = false
        try {
            if (mapRequested) {
                QisiVehicleBinder.setInstrumentMapMode(instrument, 2)
            }
        } catch (error: Exception) {
            DiagnosticLogging.logcat { Log.w(TAG, "full-screen map mode release failed", error) }
            trace { "map mode release failed: ${error.javaClass.simpleName}: ${error.message}" }
        }
        try {
            if (navigationRequested) {
                QisiVehicleBinder.transactInts(system, QisiVehicleBinder.TOKEN_SYSTEM,
                    QisiVehicleBinder.SYSTEM_INSTRUMENT_MAP, intArrayOf(0, 0))
            }
        } catch (error: Exception) {
            DiagnosticLogging.logcat { Log.w(TAG, "full-screen navigation release failed", error) }
            trace { "navigation release failed: ${error.javaClass.simpleName}: ${error.message}" }
        }
        try {
            changeListener(UNREGISTER_LISTENER, callback)
            trace { "full-screen navigation released; exit listener removed tx=65" }
        } catch (error: Exception) {
            DiagnosticLogging.logcat { Log.w(TAG, "full-screen navigation listener release failed", error) }
            trace { "exit listener release failed: ${error.javaClass.simpleName}: ${error.message}" }
        }
        mapRequested = false
        navigationRequested = false
    }

    private inline fun trace(message: () -> String) {
        DiagnosticLogging.record {
            val text = message()
            Log.i(TAG, text)
            ConnectionDiagnosticTrace.record("QISI_CLUSTER $text")
        }
    }

    private fun changeListener(code: Int, callback: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(QisiVehicleBinder.TOKEN_SYSTEM)
            data.writeStrongBinder(callback)
            if (!system.transact(code, data, reply, 0)) {
                throw RemoteException("full-screen navigation listener rejected")
            }
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        private const val TAG = "Xcert-Hs5-Cluster"
        private const val CALLBACK_TOKEN = "ltd.qisi.adapterproxy.listener.IVehicleSystemControlListener"
        private const val REGISTER_LISTENER = 64
        private const val UNREGISTER_LISTENER = 65
        private const val FULL_SCREEN_NAVIGATION_REQUEST = 15
    }
}
