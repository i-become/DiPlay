package com.shilapi.xcertplay.carlink

import android.util.Log
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** Static ABI called by the injected SDK; do not rename or remove (invisible to R8). */
object CarlinkSdkLog {
    private inline fun emit(crossinline output: () -> Int): Int {
        var result = 0
        DiagnosticLogging.write { result = output() }
        return result
    }

    @JvmStatic fun v(tag: String?, message: String?) = emit { Log.v(tag, message.orEmpty()) }
    @JvmStatic fun v(tag: String?, message: String?, error: Throwable?) = emit { Log.v(tag, message, error) }
    @JvmStatic fun d(tag: String?, message: String?) = emit { Log.d(tag, message.orEmpty()) }
    @JvmStatic fun d(tag: String?, message: String?, error: Throwable?) = emit { Log.d(tag, message, error) }
    @JvmStatic fun i(tag: String?, message: String?) = emit { Log.i(tag, message.orEmpty()) }
    @JvmStatic fun i(tag: String?, message: String?, error: Throwable?) = emit { Log.i(tag, message, error) }
    @JvmStatic fun w(tag: String?, message: String?) = emit { Log.w(tag, message.orEmpty()) }
    @JvmStatic fun w(tag: String?, message: String?, error: Throwable?) = emit { Log.w(tag, message, error) }
    @JvmStatic fun e(tag: String?, message: String?) = emit { Log.e(tag, message.orEmpty()) }
    @JvmStatic fun e(tag: String?, message: String?, error: Throwable?) = emit { Log.e(tag, message, error) }
    @JvmStatic fun printStackTrace(error: Throwable?) {
        emit { Log.e("DiPlay-Carlink-SDK", "SDK exception", error) }
    }
    @JvmStatic fun println(stream: java.io.PrintStream, message: String?) {
        emit { stream.println(message); 0 }
    }
}
