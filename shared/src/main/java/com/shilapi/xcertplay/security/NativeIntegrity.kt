package com.shilapi.xcertplay.security

import android.content.Context
import java.security.PrivateKey

/** Release enforcement lives in the native operations, independently of the warning UI. */
object NativeIntegrity {
    init { System.loadLibrary("diplay_integrity") }
    external fun warningDelayMillis(): Long
    external fun warning(context: Context): String?
    external fun sign(context: Context, key: PrivateKey, digest: ByteArray): ByteArray
    // Any avoids introducing a runtime dependency on the separately injected Carlink SDK.
    external fun initializeCarlink(context: Context, adapter: Any, config: Any, callback: Any): Boolean
}
