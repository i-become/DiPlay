package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Sets a stable Wi-Fi Direct device name before createGroup on QiSi head units. */
internal object WifiP2pBrandedDeviceName {
    private const val TAG = "xcertplay-wifi-p2p"

    fun prefix(packageName: String, scopedDeviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$packageName:$scopedDeviceId".toByteArray(Charsets.UTF_8))
        val token = digest.take(6).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "DIRECT-DiPlay-$token-"
    }

    fun applyBlocking(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        context: Context,
        scopedDeviceId: String,
        timeoutMillis: Long,
        log: (String) -> Unit,
    ) {
        val name = prefix(context.packageName, scopedDeviceId)
        val latch = CountDownLatch(1)
        val applied = AtomicBoolean(false)
        try {
            val method = WifiP2pManager::class.java.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java,
            )
            method.invoke(
                manager,
                channel,
                name,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        applied.set(true)
                        latch.countDown()
                    }

                    override fun onFailure(reason: Int) {
                        latch.countDown()
                    }
                },
            )
            latch.await(timeoutMillis.coerceIn(250L, 1_500L), TimeUnit.MILLISECONDS)
        } catch (error: ReflectiveOperationException) {
            log("Wi-Fi P2P branded name unavailable type=${error.javaClass.simpleName}")
            DiagnosticLogging.logcat { Log.i(TAG, "Wi-Fi P2P branded name unavailable", error) }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        log("Wi-Fi P2P branded name applied=${applied.get()}")
    }
}
