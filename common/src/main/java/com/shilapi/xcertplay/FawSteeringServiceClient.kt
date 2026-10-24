package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.KeyEvent

/** Passive FAW hard-key subscription. Its KeyEvents are not Qisi short/long/up integers. */
internal class FawSteeringServiceClient(
    private val context: Context,
    private val onKey: (KeyEvent) -> Unit,
) {
    companion object {
        val COMPONENT = ComponentName("faw.system.viewservice", "faw.system.viewservice.HVCtrlService")
        const val CLIENT_ID = "faw.system.view.ACTIVITY_ID"
        const val KEY_EVENT = "faw.car.manager.HARD_KEY_EVENT"
        private const val REGISTER = 16
        private const val UNREGISTER = 17
        private const val EVENT = 18
        private const val RETRY_MS = 5_000L
        private const val MAX_ATTEMPTS = 3
    }

    private val main = Handler(Looper.getMainLooper())
    private val clientId = CarPlayHostActivity::class.java.name
    @Volatile private var closed = false
    private var attempts = 0
    private var connection: Connection? = null
    private val retry = Runnable { bind() }
    private val timeout = Runnable {
        connection?.takeIf { it.server == null }?.let {
            Hs5SteeringKeys.recordEvent { "FAW bind timeout" }
            retry(it)
        }
    }

    // Media ownership can move on a worker. Marshal binding state to Android's callback looper.
    fun start() {
        if (closed) return
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { start() }; return }
        @Suppress("DEPRECATION")
        val available = runCatching {
            context.packageManager.resolveService(Intent().setComponent(COMPONENT), PackageManager.MATCH_SYSTEM_ONLY)
                ?.serviceInfo?.let { it.enabled && it.exported && it.applicationInfo.enabled } == true
        }.getOrDefault(false)
        if (!available) {
            Hs5SteeringKeys.recordEvent { "FAW HVCtrlService unavailable" }
            return
        }
        bind()
    }

    fun close() {
        closed = true
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { close() }; return }
        main.removeCallbacks(retry)
        main.removeCallbacks(timeout)
        connection?.let { release(it) }
    }

    fun status(): String = "FAW steering: binding=${connection != null && connection?.server == null} " +
        "subscriptionSent=${connection?.server != null} attempts=$attempts closed=$closed"

    private fun bind() {
        if (closed || connection != null || attempts >= MAX_ATTEMPTS) return
        attempts++
        val next = Connection()
        connection = next
        next.bound = runCatching {
            context.bindService(Intent().setComponent(COMPONENT), next, Context.BIND_AUTO_CREATE)
        }.onFailure { error ->
            Hs5SteeringKeys.recordEvent { "FAW bind failed: ${error.javaClass.simpleName}" }
        }.getOrDefault(false)
        Hs5SteeringKeys.recordEvent { "FAW bind accepted=${next.bound} attempt=$attempts" }
        if (!next.bound) retry(next)
        else if (next.server == null) main.postDelayed(timeout, RETRY_MS)
    }

    private fun retry(previous: Connection) {
        if (connection !== previous) return
        release(previous)
        if (!closed && attempts < MAX_ATTEMPTS) main.postDelayed(retry, RETRY_MS)
    }

    private fun release(previous: Connection) {
        if (connection !== previous) return
        connection = null // Reject messages already queued by this registration before unbinding.
        main.removeCallbacks(timeout)
        previous.server?.let { server ->
            runCatching { server.send(subscription(UNREGISTER, previous.inbox)) }
                .onFailure { error -> Hs5SteeringKeys.recordEvent { "FAW unregister failed: ${error.javaClass.simpleName}" } }
        }
        previous.server = null
        if (previous.bound) runCatching { context.unbindService(previous) }
        previous.bound = false
    }

    private fun subscription(what: Int, inbox: Messenger?): Message = Message.obtain().apply {
        this.what = what
        replyTo = inbox
        data = Bundle().apply { putString(CLIENT_ID, clientId) }
    }

    private inner class Connection : ServiceConnection {
        var bound = false
        var server: Messenger? = null
        var inbox: Messenger? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (closed || connection !== this) return
            if (service == null) { retry(this); return }
            main.removeCallbacks(timeout)
            val source = this
            inbox = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (closed || connection !== source || source.server == null || msg.what != EVENT) return
                    // A malformed external Bundle must not crash the main looper.
                    @Suppress("DEPRECATION")
                    val event = runCatching {
                        msg.data.classLoader = KeyEvent::class.java.classLoader
                        msg.data.getParcelable<android.os.Parcelable>(KEY_EVENT) as? KeyEvent
                    }.getOrNull() ?: return
                    onKey(event)
                }
            })
            server = Messenger(service)
            runCatching { server!!.send(subscription(REGISTER, inbox)) }
                .onSuccess { Hs5SteeringKeys.recordEvent { "FAW subscription sent what=16 client=$clientId" } }
                .onFailure { error ->
                    Hs5SteeringKeys.recordEvent { "FAW subscribe failed: ${error.javaClass.simpleName}" }
                    retry(this)
                }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Hs5SteeringKeys.recordEvent { "FAW service disconnected current=${connection === this}" }
            retry(this)
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
    }
}
