package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.KeyEvent
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class FawSteeringServiceClientTest {
    private val packages = mock(PackageManager::class.java)
    private val messages = mutableListOf<Message>()
    private val events = mutableListOf<KeyEvent>()
    private val connections = mutableListOf<ServiceConnection>()
    private val unbound = mutableListOf<ServiceConnection>()
    private val main = Handler(Looper.getMainLooper())
    private var available = true
    private var accepted = true
    private var deliverConnection = true
    private val server = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) { messages += Message.obtain(msg) }
    })
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getPackageManager(): PackageManager = packages
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            assertEquals(ComponentName("faw.system.viewservice", "faw.system.viewservice.HVCtrlService"), intent.component)
            assertEquals(Context.BIND_AUTO_CREATE, flags)
            connections += conn
            if (accepted && deliverConnection) main.post { conn.onServiceConnected(intent.component, server.binder) }
            return accepted
        }
        override fun unbindService(conn: ServiceConnection) { unbound += conn }
    }
    private val client = FawSteeringServiceClient(context) { events += it }

    init {
        `when`(packages.resolveService(any(Intent::class.java), anyInt())).thenAnswer {
            if (!available) null else ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    exported = true
                    enabled = true
                    applicationInfo = ApplicationInfo().apply { enabled = true; flags = ApplicationInfo.FLAG_SYSTEM }
                }
            }
        }
    }

    @After fun tearDown() { client.close(); idle() }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun start(): Message { client.start(); idle(); return messages.single() }
    private fun send(inbox: Messenger, event: android.os.Parcelable?, what: Int = 18) {
        inbox.send(Message.obtain().apply {
            this.what = what
            data = Bundle().apply { putParcelable("faw.car.manager.HARD_KEY_EVENT", event) }
        })
    }

    @Test fun registersAndUnregistersWithTheSameIdentityAndReplyMessenger() {
        val registration = start()
        assertEquals(16, registration.what)
        assertEquals(CarPlayHostActivity::class.java.name, registration.data.getString("faw.system.view.ACTIVITY_ID"))
        assertNotNull(registration.replyTo)
        client.close(); idle()
        val removal = messages.last()
        assertEquals(17, removal.what)
        assertEquals(registration.data.getString("faw.system.view.ACTIVITY_ID"), removal.data.getString("faw.system.view.ACTIVITY_ID"))
        assertEquals(registration.replyTo, removal.replyTo)
        assertEquals(connections, unbound)
    }

    @Test fun keepsAndroidActionAndKeyCodeInsteadOfTreatingDownAsQisiShortClick() {
        val inbox = start().replyTo
        send(inbox, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        send(inbox, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        idle()
        assertEquals(listOf(0, 1), events.map { it.action })
        assertEquals(listOf(4, 4), events.map { it.keyCode })
    }

    @Test fun missingWrongParcelableAndWrongMessageAreIgnored() {
        val inbox = start().replyTo
        send(inbox, null)
        send(inbox, Bundle())
        send(inbox, KeyEvent(1, 4), what = 16)
        idle()
        assertTrue(events.isEmpty())
    }

    @Test fun queuedMessagesCannotOutliveTheirSubscription() {
        val inbox = start().replyTo
        send(inbox, KeyEvent(1, 4))
        client.close(); idle()
        assertTrue(events.isEmpty())
    }

    @Test fun serviceRestartRejectsItsOldMessengerAndSubscribesAgain() {
        val oldInbox = start().replyTo
        connections.single().onServiceDisconnected(FawSteeringServiceClient.COMPONENT)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        val newInbox = messages.last { it.what == 16 }.replyTo
        assertNotEquals(oldInbox, newInbox)
        send(oldInbox, KeyEvent(1, 4))
        send(newInbox, KeyEvent(1, 4)); idle()
        assertEquals(1, events.size)
        assertEquals(2, connections.size)
    }

    @Test fun bindRefusalRetriesOnlyThreeTimesAndNeverUnbindsAnUnacceptedConnection() {
        accepted = false
        client.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(3, connections.size)
        assertTrue(unbound.isEmpty())
    }

    @Test fun acceptedBindingWithoutCallbackTimesOutAndCleansUpBeforeRetry() {
        deliverConnection = false
        client.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(3, connections.size)
        assertEquals(connections, unbound)
        connections.first().onServiceConnected(FawSteeringServiceClient.COMPONENT, server.binder)
        idle()
        assertTrue(messages.isEmpty())
    }

    @Test fun absentOptionalServiceDoesNotPollOrBind() {
        available = false
        client.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertTrue(connections.isEmpty())
    }

    @Test fun closeCancelsPendingRetry() {
        accepted = false
        client.start(); client.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(1, connections.size)
    }
}
