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

    const val TX_VIEW_FOCUS_STATUS = 5
    const val TX_VIEW_FOCUS_RELEASE = 6
    const val TX_VIEW_FOCUS_REQUEST = 10
    const val TX_REGISTER_HARD_KEYS = 12
    const val TX_UNREGISTER_HARD_KEYS = 13

    const val VIEW_FOCUS_CARPLAY = 38

    val serviceIntent: Intent
        get() = Intent().setComponent(ComponentName(PACKAGE, SERVICE))

    /** Maps QiSi factory hard-key codes to CarPlay steering actions (0 previous … 3 Siri). */
    fun carPlayActionForFactoryKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 0
        KeyEvent.KEYCODE_MEDIA_NEXT -> 1
        KeyEvent.KEYCODE_CALL -> 2
        259 -> 3
        else -> null
    }

    private const val HARD_KEY_BATCH_SIZE = 64

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

    fun requestCarPlayViewFocus(binder: IBinder, listener: IBinder): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            data.writeInt(VIEW_FOCUS_CARPLAY)
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

    fun releaseCarPlayViewFocus(binder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOKEN)
            if (!binder.transact(TX_VIEW_FOCUS_RELEASE, data, reply, 0)) {
                throw RemoteException("view focus release rejected")
            }
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
