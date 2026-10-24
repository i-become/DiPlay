package com.shilapi.xcertplay.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Build

/** Watches actual association changes, without using video silence as a disconnect signal. */
internal class WifiP2pPeerMonitor(
    private val context: Context,
    private val groupName: String,
    private val handler: Handler,
    private val requestGroup: ((WifiP2pGroup?) -> Unit) -> Unit,
    private val onPeerLeft: () -> Unit,
    private val diagnostic: (String) -> Unit,
) : AutoCloseable {
    private val presence = P2pPeerPresence()
    @Volatile private var closed = false
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (closed || intent.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
            @Suppress("DEPRECATION")
            val group = intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
            handler.post {
                if (group != null) observe(group) else query(::observe)
            }
        }
    }

    fun start() {
        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
        handler.post { query(::observe) }
    }

    private fun query(consume: (WifiP2pGroup?) -> Unit) {
        if (closed) return
        runCatching { requestGroup { group -> handler.post { if (!closed) consume(group) } } }
    }

    private fun observe(group: WifiP2pGroup?) {
        if (closed) return
        // Legacy firmware can leave clientList empty throughout a working session. Require
        // a previously observed client, and ignore groups belonging to other apps.
        if (group != null && (!group.isGroupOwner || group.networkName != groupName)) return
        val ticket = presence.observe(group?.clientList?.isNotEmpty() == true) ?: return
        handler.postDelayed({
            if (closed || !presence.isPending(ticket)) return@postDelayed
            query { confirmed ->
                if (confirmed != null && (!confirmed.isGroupOwner || confirmed.networkName != groupName)) return@query
                if (presence.confirm(ticket, confirmed?.clientList?.isNotEmpty() == true)) {
                    diagnostic("Wi-Fi P2P peer left confirmed; ending the current CarPlay session")
                    onPeerLeft()
                }
            }
        }, 1_000)
    }

    override fun close() {
        closed = true
        if (registered) {
            registered = false
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

/** A missing initial client list is unknown, not evidence that the phone disconnected. */
internal class P2pPeerPresence {
    private var seenClient = false
    private var absent = false
    private var revision = 0L

    fun observe(present: Boolean): Long? {
        if (present) {
            seenClient = true
            absent = false
            revision++
            return null
        }
        if (!seenClient || absent) return null
        absent = true
        return ++revision
    }

    fun isPending(ticket: Long): Boolean = seenClient && absent && revision == ticket

    fun confirm(ticket: Long, present: Boolean): Boolean {
        if (!isPending(ticket)) return false
        if (present) { observe(true); return false }
        seenClient = false
        absent = false
        revision++
        return true
    }
}
