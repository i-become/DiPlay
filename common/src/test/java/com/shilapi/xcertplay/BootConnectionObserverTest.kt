package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BootConnectionObserverTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }

    @Test fun disabledObserverNeverRegistersOrSamples() {
        com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false)
        val context = org.mockito.Mockito.mock(Context::class.java)
        BootConnectionObserver(context, { fail("disabled observer emitted a diagnostic") }).use { it.start() }
        org.mockito.Mockito.verifyNoInteractions(context)
    }

    @Test @Config(sdk = [33])
    fun modernAndroidRegistersForSystemRadioEventsWithExplicitFlagsAndCleansUp() {
        val app = RuntimeEnvironment.getApplication()
        val sampled = CountDownLatch(1)
        val unregistered = CountDownLatch(1)
        var registeredFlags = 0
        var registeredFilter: IntentFilter? = null
        var registeredScheduler: Handler? = null
        val context = object : ContextWrapper(app) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?, flags: Int): Intent? {
                registeredFlags = flags
                registeredFilter = filter
                registeredScheduler = scheduler
                return null
            }
            override fun unregisterReceiver(receiver: BroadcastReceiver?) { unregistered.countDown() }
        }
        val observer = BootConnectionObserver(context, {
            if (it.startsWith("radio-sample")) sampled.countDown()
        })
        try {
            observer.start()
            assertTrue(sampled.await(3, TimeUnit.SECONDS))
            assertEquals(Context.RECEIVER_EXPORTED, registeredFlags)
            assertTrue(registeredFilter!!.hasAction(BluetoothAdapter.ACTION_STATE_CHANGED))
            assertTrue(registeredFilter!!.hasAction(android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
            assertNotSame(android.os.Looper.getMainLooper(), registeredScheduler!!.looper)
        } finally {
            observer.close()
            assertTrue(unregistered.await(3, TimeUnit.SECONDS))
        }
    }

    @Test fun onBroadcastAndOffApiReadRemainSeparateEvidenceWithoutEnablingTheRadio() {
        val app = RuntimeEnvironment.getApplication()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setEnabled(false)
        val logs = CopyOnWriteArrayList<String>()
        val sampled = CountDownLatch(1)
        val unregistered = CountDownLatch(1)
        var registeredReceiver: BroadcastReceiver? = null
        val context = object : ContextWrapper(app) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?): Intent? {
                registeredReceiver = receiver
                return null
            }
            override fun unregisterReceiver(receiver: BroadcastReceiver?) { unregistered.countDown() }
        }
        val observer = BootConnectionObserver(context, {
            logs.add(it)
            if (it.startsWith("radio-sample")) sampled.countDown()
        })
        try {
            observer.start()
            assertTrue(sampled.await(3, TimeUnit.SECONDS))
            registeredReceiver!!.onReceive(app, Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
                .putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_TURNING_ON)
                .putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON))
            assertTrue(logs.any { it.contains("radio-broadcast") && it.contains("previous=11 state=12") })
            assertTrue(logs.any { it.contains("radio-sample") && it.contains("state=10 enabled=false") })
            assertFalse(adapter.isEnabled)
            assertFalse(adapter.isDiscovering)
        } finally {
            observer.close()
            assertTrue(unregistered.await(3, TimeUnit.SECONDS))
        }
    }

    @Test fun deniedBroadcastRegistrationStillSamplesApiStateAndReportsTheBlindSpot() {
        val app = RuntimeEnvironment.getApplication()
        val logs = CopyOnWriteArrayList<String>()
        val sampled = CountDownLatch(1)
        val context = object : ContextWrapper(app) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?): Intent? {
                throw SecurityException("private OEM message")
            }
        }
        val observer = BootConnectionObserver(context, {
            logs.add(it)
            if (it.startsWith("radio-sample")) sampled.countDown()
        })
        try {
            observer.start()
            assertTrue(sampled.await(3, TimeUnit.SECONDS))
            assertTrue(logs.any { it.contains("broadcasts=unavailable failureClass=SecurityException") })
            assertFalse(logs.any { it.contains("private OEM message") })
        } finally { observer.close() }
    }
}
