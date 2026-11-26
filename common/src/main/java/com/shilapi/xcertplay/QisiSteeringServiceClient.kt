package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.view.KeyEvent

/** Binder client for [ltd.qisi.systemservice.QisiSystemService] steering-wheel hard keys. */
internal object QisiSteeringServiceClient {
    const val PACKAGE = "ltd.qisi.systemservice"
    const val SERVICE = "ltd.qisi.systemservice.QisiSystemService"
    const val TOKEN = "ltd.qisi.systemmanager.ISystemServiceInterface"

    // Verified against the HS5 SystemService interface ABI.
    const val TX_SEND_COMMAND = 1
    const val TX_REGISTER_SYSTEM_CALLBACK = 3
    const val TX_UNREGISTER_SYSTEM_CALLBACK = 4
    const val TX_VIEW_FOCUS_REQUEST = 5
    const val TX_VIEW_FOCUS_RELEASE = 6
    const val TX_VIEW_FOCUS_STATUS = 10
    const val TX_REGISTER_HARD_KEYS = 12
    const val TX_UNREGISTER_HARD_KEYS = 13

    // SystemService hard-key namespace.
    const val KEYCODE_BACK = 4
    const val KEYCODE_VOICE_ASSIST = 231
    const val KEYCODE_SDS_VOICE_ASSIST = 259
    const val HARD_KEY_SHORT_CLICK = 0
    const val HARD_KEY_LONG_CLICK = 1
    const val HARD_KEY_UP = 2
    const val VIEW_FOCUS_CARPLAY = 37
    // The HS5 firmware expects this focus category for its active projection view.
    const val VIEW_FOCUS_HS5_PROJECTION = 38

    val serviceIntent: Intent
        get() = Intent().setComponent(ComponentName(PACKAGE, SERVICE))

    /** Maps QiSi factory hard-key codes to CarPlay steering actions (0 previous … 3 Siri). */
    fun carPlayActionForFactoryKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 0
        KeyEvent.KEYCODE_MEDIA_NEXT -> 1
        KeyEvent.KEYCODE_CALL -> 2
        KEYCODE_VOICE_ASSIST, KEYCODE_SDS_VOICE_ASSIST -> 3
        else -> null
    }

    private const val HARD_KEY_BATCH_SIZE = 64

    fun registerSystemCallback(binder: IBinder, packageName: String, listener: IBinder) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            data.writeString(packageName)
            data.writeString(packageName)
            data.writeStrongBinder(listener)
            if (!binder.transact(TX_REGISTER_SYSTEM_CALLBACK, data, null, IBinder.FLAG_ONEWAY)) {
                throw RemoteException("system callback registration rejected")
            }
        } finally {
            data.recycle()
        }
    }

    fun unregisterSystemCallback(binder: IBinder, packageName: String, listener: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            data.writeString(packageName)
            data.writeString(packageName)
            data.writeStrongBinder(listener)
            if (!binder.transact(TX_UNREGISTER_SYSTEM_CALLBACK, data, reply, 0)) {
                throw RemoteException("system callback removal rejected")
            }
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** sendCommand is one-way: success acknowledges submission, not an assistant opening. */
    fun sendCommand(binder: IBinder, command: String) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            data.writeString(command)
            if (!binder.transact(TX_SEND_COMMAND, data, null, IBinder.FLAG_ONEWAY)) {
                throw RemoteException("system command rejected")
            }
        } finally {
            data.recycle()
        }
    }

    fun registerHardKeys(binder: IBinder, packageName: String, keyCodes: IntArray, listener: IBinder) {
        if (keyCodes.size <= HARD_KEY_BATCH_SIZE) {
            transactKeyOperation(binder, TX_REGISTER_HARD_KEYS, packageName, keyCodes, listener)
            return
        }
        keyCodes.toList().chunked(HARD_KEY_BATCH_SIZE).forEach { chunk ->
            transactKeyOperation(binder, TX_REGISTER_HARD_KEYS, packageName, chunk.toIntArray(), listener)
        }
    }

    fun unregisterHardKeys(binder: IBinder, packageName: String, keyCodes: IntArray, listener: IBinder) {
        if (keyCodes.size <= HARD_KEY_BATCH_SIZE) {
            transactKeyOperation(binder, TX_UNREGISTER_HARD_KEYS, packageName, keyCodes, listener)
            return
        }
        keyCodes.toList().chunked(HARD_KEY_BATCH_SIZE).forEach { chunk ->
            transactKeyOperation(binder, TX_UNREGISTER_HARD_KEYS, packageName, chunk.toIntArray(), listener)
        }
    }

    private fun transactKeyOperation(
        binder: IBinder,
        code: Int,
        packageName: String,
        keyCodes: IntArray,
        listener: IBinder,
    ) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            data.writeString(packageName)
            data.writeString(packageName)
            if (code == TX_REGISTER_HARD_KEYS) {
                data.writeIntArray(keyCodes)
                data.writeInt(0)
            } else {
                data.writeInt(0)
                data.writeIntArray(keyCodes)
            }
            data.writeStrongBinder(listener)
            if (!binder.transact(code, data, reply, 0)) {
                throw RemoteException("hard key operation rejected")
            }
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun requestCarPlayViewFocus(binder: IBinder, listener: IBinder, type: Int = VIEW_FOCUS_CARPLAY): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            data.writeInt(type)
            data.writeStrongBinder(listener)
            if (!binder.transact(TX_VIEW_FOCUS_REQUEST, data, reply, 0)) {
                throw RemoteException("view focus request rejected")
            }
            reply.readException()
            return reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun releaseCarPlayViewFocus(binder: IBinder, listener: IBinder): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            data.writeStrongBinder(listener)
            if (!binder.transact(TX_VIEW_FOCUS_RELEASE, data, reply, 0)) {
                throw RemoteException("view focus release rejected")
            }
            reply.readException()
            return reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
