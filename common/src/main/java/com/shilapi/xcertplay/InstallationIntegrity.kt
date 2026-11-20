package com.shilapi.xcertplay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.lang.ref.WeakReference
import com.shilapi.xcertplay.security.NativeIntegrity

/** UI only: removing this observer does not bypass the protected native operations. */
internal object InstallationIntegrity {
    private var installed = false

    fun install(application: Application) {
        if (installed) return
        installed = true
        // Establish the native process deadline at bootstrap, not at the first connection.
        remainingMillis()
        application.registerActivityLifecycleCallbacks(DeferredIntegrityWarning(
            remainingMillis = ::remainingMillis,
            readWarning = { activity ->
                try {
                    NativeIntegrity.warning(activity.applicationContext)
                } catch (_: LinkageError) {
                    if (com.shilapi.xcertplay.host.BuildConfig.DEBUG) null else
                        "本软件为免费软件，若遇收费必为骗子，谨防上当！抖音关注“卖捣蛋的小男孩（66857839104）”"
                }
            },
            showWarning = { activity, warning ->
                activity.appDialogBuilder()
                    .setTitle("DiPlay")
                    .setMessage(warning)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok) { _, _ -> activity.finish() }
                    .show()
            },
        ))
    }

    private fun remainingMillis(): Long =
        try {
            NativeIntegrity.warningDelayMillis()
        } catch (_: LinkageError) {
            (30 * 60 * 1000L - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()))
                .coerceAtLeast(0L)
        }
}

/** A single process deadline; background/closed activities never receive a dialog. */
internal class DeferredIntegrityWarning(
    private val remainingMillis: () -> Long,
    private val readWarning: (Activity) -> String?,
    private val showWarning: (Activity, String) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : Application.ActivityLifecycleCallbacks {
    private var current = WeakReference<Activity>(null)
    private var completed = false
    private val tick = Runnable { evaluate() }

    override fun onActivityResumed(activity: Activity) {
        handler.removeCallbacks(tick)
        current = WeakReference(activity)
        evaluate()
    }

    private fun evaluate() {
        if (completed) return
        val activity = current.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val delay = remainingMillis()
        if (delay > 0L) {
            handler.postDelayed(tick, delay)
            return
        }
        val warning = readWarning(activity)
        completed = true
        if (warning != null) showWarning(activity, warning)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current.get() !== activity) return
        handler.removeCallbacks(tick)
        current.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) { onActivityPaused(activity) }
}
