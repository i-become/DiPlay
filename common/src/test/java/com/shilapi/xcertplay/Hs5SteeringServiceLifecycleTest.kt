package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.view.KeyEvent
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.json.JSONObject
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Hs5SteeringServiceLifecycleTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val handler = Handler(Looper.getMainLooper())
    private val component = ComponentName(QisiSteeringServiceClient.PACKAGE, QisiSteeringServiceClient.SERVICE)
    private val controller = mock(CarPlayController::class.java)
    private val host = mock(CarPlayHostActivity::class.java)
    private var shown = true
    private var bindAccepted = true
    private var deliverConnection = true
    private var binds = 0
    private var unbinds = 0
    private var closes = 0
    private var focusRequests = 0
    private var focusReleases = 0
    private val focusTypes = mutableListOf<Int>()
    private val focusResults = mutableMapOf<Int, Int>()
    private var rejectRegistration = false
    private var rejectSystemCallback = false
    private var rejectVoiceCommand = false
    private val registrations = mutableListOf<IntArray>()
    private val unregistrations = mutableListOf<IntArray>()
    private var listener: IBinder? = null
    private var focusListener: IBinder? = null
    private var systemCallback: IBinder? = null
    private val voiceCommands = mutableListOf<JSONObject>()
    private lateinit var connection: ServiceConnection
    private val fawMessages = mutableListOf<Message>()
    private val fawService = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) { fawMessages += Message.obtain(msg) }
    })
    private val context: Context = object : ContextWrapper(app) {
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            if (intent.component == FawSteeringServiceClient.COMPONENT) {
                handler.post { conn.onServiceConnected(intent.component, fawService.binder) }
                return true
            }
            assertEquals(component, intent.component)
            assertEquals(Context.BIND_AUTO_CREATE, flags)
            binds++
            connection = conn
            if (bindAccepted && deliverConnection) handler.post { conn.onServiceConnected(component, service) }
            return bindAccepted
        }
        override fun unbindService(conn: ServiceConnection) { unbinds++ }
    }
    private val service: IBinder = object : Binder() {
        init { attachInterface(null, QisiSteeringServiceClient.TOKEN) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(QisiSteeringServiceClient.TOKEN)
            when (code) {
                1 -> {
                    assertEquals(IBinder.FLAG_ONEWAY, flags)
                    if (rejectVoiceCommand) return false
                    val command = JSONObject(data.readString()!!)
                    assertEquals("GROUP_SDS", command.getString("TargetGroup"))
                    assertEquals("DOMAIN_SDS", command.getString("TargetDomain"))
                    assertEquals(0, command.getInt("TargetScreen"))
                    assertEquals(0, command.getInt("CommandType"))
                    assertEquals(context.packageName, command.getString("SenderGroup"))
                    assertEquals(context.packageName, command.getString("SenderDomain"))
                    assertTrue(command.getLong("SessionID") > 0)
                    val key = JSONObject(command.getString("Data"))
                    assertEquals("SPEECH", key.getString("KEY_CODE"))
                    assertEquals(0, key.getInt("KEY_ACTION"))
                    assertEquals(0, data.dataAvail())
                    voiceCommands += command
                    return true
                }
                3, 4 -> {
                    assertEquals(context.packageName, data.readString())
                    assertEquals(context.packageName, data.readString())
                    val callback = data.readStrongBinder()!!
                    assertEquals("ltd.qisi.systemmanager.ISystemServiceCallback", callback.interfaceDescriptor)
                    assertEquals(0, data.dataAvail())
                    if (code == 3) {
                        assertEquals(IBinder.FLAG_ONEWAY, flags)
                        if (rejectSystemCallback) return false
                        systemCallback = callback
                        return true
                    }
                    assertEquals(0, flags)
                    assertSame(systemCallback, callback)
                    systemCallback = null
                }
                12, 13 -> {
                    assertNotNull(systemCallback)
                    assertEquals(context.packageName, data.readString())
                    assertEquals(context.packageName, data.readString())
                    val keys: IntArray
                    if (code == 12) {
                        keys = data.createIntArray()!!
                        assertEquals(0, data.readInt())
                    } else {
                        assertEquals(0, data.readInt())
                        keys = data.createIntArray()!!
                    }
                    val callback = data.readStrongBinder()!!
                    assertEquals(0, data.dataAvail())
                    if (code == 12) {
                        registrations += keys
                        if (rejectRegistration) return false
                        listener = callback
                    } else {
                        unregistrations += keys
                        if (listener === callback) listener = null
                    }
                }
                // Literal transaction IDs/argument order verified from the OEM SDK, not our constants.
                5 -> {
                    val type = data.readInt()
                    assertTrue(type == 37 || type == 38)
                    focusTypes += type
                    focusListener = data.readStrongBinder()
                    assertNotNull(focusListener)
                    assertEquals(0, data.dataAvail())
                    focusRequests++
                    reply!!.writeNoException()
                    reply.writeInt(focusResults[type] ?: 1)
                    return true
                }
                6 -> {
                    assertSame(focusListener, data.readStrongBinder())
                    assertEquals(0, data.dataAvail())
                    focusReleases++
                    reply!!.writeNoException()
                    reply.writeInt(1)
                    return true
                }
                else -> fail("Unexpected transaction $code")
            }
            reply!!.writeNoException()
            return true
        }
    }

    @Before fun setUp() {
        DiagnosticLogging.setEnabled(false)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = QisiSteeringServiceClient.PACKAGE })
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        `when`(host.applicationContext).thenReturn(context)
        `when`(host.flyScreenShown()).thenAnswer { shown }
        `when`(host.closeFlyScreen(anyString())).thenAnswer {
            if (!shown) false else {
                closes++
                shown = false
                Hs5SteeringKeys.applySettings(context)
                true
            }
        }
        AirPlayPersistence.saveCarPlayWheelMedia(context, false)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
        CarPlayHostSettingsBridge.register(host)
    }

    @After fun tearDown() {
        Hs5SteeringKeys.detach(controller)
        CarPlayHostSettingsBridge.unregister(host)
        drain()
        DiagnosticLogging.setEnabled(false)
    }

    private fun start() {
        Hs5SteeringKeys.attach(context, controller)
        drain()
    }

    private fun drain() {
        val field = Hs5SteeringKeys::class.java.getDeclaredField("worker").apply { isAccessible = true }
        val worker = field.get(Hs5SteeringKeys) as ExecutorService
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idle()
            worker.submit {}.get(5, TimeUnit.SECONDS)
        }
    }

    private fun advanceRetry() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        drain()
    }

    private fun sendKey(callback: IBinder, key: Int = 4, action: Int = 0) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("ltd.qisi.systemmanager.IHardKeyListener")
            data.writeInt(key)
            data.writeInt(action)
            assertTrue(callback.transact(1, data, reply, 0))
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun enableFawKeys() {
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent().setComponent(FawSteeringServiceClient.COMPONENT),
            ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    packageName = "faw.system.viewservice"
                    name = "faw.system.viewservice.HVCtrlService"
                    enabled = true
                    exported = true
                    applicationInfo = ApplicationInfo().apply {
                        packageName = "faw.system.viewservice"
                        enabled = true
                        flags = ApplicationInfo.FLAG_SYSTEM
                    }
                }
            },
        )
    }

    private fun sendFaw(inbox: Messenger, action: Int, canceled: Boolean = false, key: Int = KeyEvent.KEYCODE_BACK) {
        inbox.send(Message.obtain().apply {
            what = 18
            data = Bundle().apply {
                putParcelable("faw.car.manager.HARD_KEY_EVENT", KeyEvent(
                    100L, 200L, action, key, 0, 0, 0, 0,
                    if (canceled) KeyEvent.FLAG_CANCELED else 0,
                ))
            }
        })
    }

    @Test fun fawBackClosesProjectionWithoutAnyQisiBackCallbackAndKeepsTheConnection() {
        enableFawKeys()
        rejectRegistration = true
        start()
        val inbox = fawMessages.single { it.what == 16 }.replyTo
        sendFaw(inbox, KeyEvent.ACTION_DOWN); drain()
        assertEquals(0, closes)
        sendFaw(inbox, KeyEvent.ACTION_UP); drain()
        assertEquals(1, closes)
        verify(controller, never()).close()
        verify(controller, never()).requestSiri()
    }

    @Test fun fawBackDoesNotCloseAReplacementSessionOrActOnCanceledAndUnpairedRelease() {
        enableFawKeys()
        start()
        val inbox = fawMessages.single { it.what == 16 }.replyTo
        sendFaw(inbox, KeyEvent.ACTION_UP); drain()
        sendFaw(inbox, KeyEvent.ACTION_DOWN); drain()
        sendFaw(inbox, KeyEvent.ACTION_UP, canceled = true); drain()
        sendFaw(inbox, KeyEvent.ACTION_DOWN); drain()
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        sendFaw(inbox, KeyEvent.ACTION_UP); drain()
        assertEquals(0, closes)
        sendFaw(inbox, KeyEvent.ACTION_DOWN); drain()
        sendFaw(inbox, KeyEvent.ACTION_UP); drain()
        assertEquals(1, closes)
    }

    @Test fun fawSubscriptionIsRemovedOnDetachAndLateKeysAreDiscarded() {
        enableFawKeys()
        start()
        val registration = fawMessages.single { it.what == 16 }
        sendFaw(registration.replyTo, KeyEvent.ACTION_DOWN); drain()
        Hs5SteeringKeys.detach(controller)
        sendFaw(registration.replyTo, KeyEvent.ACTION_UP); drain()
        assertEquals(0, closes)
        val removal = fawMessages.single { it.what == 17 }
        assertEquals(registration.replyTo, removal.replyTo)
    }

    @Test fun fawVoiceObservationDoesNotDuplicateTheQisiVoiceGesture() {
        enableFawKeys()
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        val inbox = fawMessages.single { it.what == 16 }.replyTo
        sendFaw(inbox, KeyEvent.ACTION_DOWN, key = 231)
        sendFaw(inbox, KeyEvent.ACTION_UP, key = 231)
        finishSingle()
        assertTrue(voiceCommands.isEmpty())
        verify(controller, never()).requestSiri()
        assertEquals(0, closes)
        voicePress()
        finishSingle()
        assertEquals(1, voiceCommands.size)
    }

    @Test fun shortBackClosesOnlyFlyScreenAndUnregistersWithBluetoothAudio() {
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        start()
        assertArrayEquals(intArrayOf(4), registrations.single())
        val callback = listener!!
        sendKey(callback, action = 1)
        sendKey(callback, action = 2)
        sendKey(callback, key = 9)
        drain()
        assertEquals(0, closes)
        sendKey(callback)
        drain()
        assertEquals(1, closes)
        assertArrayEquals(intArrayOf(4), unregistrations.single())
        assertNull(listener)
        verify(controller, never()).close()
    }

    @Test fun backIsRegisteredForFullScreenProjectionWithoutAClusterAndFinishesOnlyItsUi() {
        shown = false
        `when`(host.projectionShown()).thenReturn(true)
        `when`(host.exitProjection(anyString())).thenAnswer {
            `when`(host.projectionShown()).thenReturn(false)
            Hs5SteeringKeys.applySettings(context)
            true
        }
        start()
        assertArrayEquals(intArrayOf(4), registrations.single())
        val callback = listener!!
        sendKey(callback, action = 1)
        sendKey(callback, action = 2)
        drain()
        verify(host, never()).exitProjection(anyString())
        sendKey(callback)
        drain()
        verify(host).exitProjection("Qisi BACK short click")
        verify(host, never()).closeFlyScreen(anyString())
        verify(controller, never()).close()
        assertNull(listener)
    }

    @Test fun queuedBackCannotCloseAReplacementProjectionOnTheSameServiceRegistration() {
        start()
        sendKey(listener!!)
        val replacement = mock(CarPlayHostActivity::class.java)
        `when`(replacement.projectionShown()).thenReturn(true)
        CarPlayHostSettingsBridge.register(replacement)
        drain()
        verify(host, never()).exitProjection(anyString())
        verify(replacement, never()).exitProjection(anyString())
        assertEquals(0, closes)
        CarPlayHostSettingsBridge.unregister(replacement)
    }

    @Test fun serviceDisconnectRebindsAndOldCallbackCannotCloseNewFlyScreen() {
        start()
        val old = listener!!
        sendKey(old)
        connection.onServiceDisconnected(component)
        drain()
        assertEquals(0, closes)
        advanceRetry()
        assertEquals(2, binds)
        assertEquals(2, registrations.size)
        sendKey(old)
        drain()
        assertEquals(0, closes)
        sendKey(listener!!)
        drain()
        assertEquals(1, closes)
    }

    @Test fun rejectedBindAndMissingConnectionBothRetry() {
        bindAccepted = false
        start()
        assertEquals(1, binds)
        bindAccepted = true
        deliverConnection = false
        advanceRetry()
        assertEquals(2, binds)
        deliverConnection = true
        advanceRetry()
        assertEquals(3, binds)
        assertTrue(unbinds > 0)
        assertArrayEquals(intArrayOf(4), registrations.single())
    }

    @Test fun rejectedRegistrationRetriesWithoutAnotherActivityEvent() {
        rejectRegistration = true
        start()
        assertEquals(1, registrations.size)
        rejectRegistration = false
        advanceRetry()
        assertEquals(2, registrations.size)
        sendKey(listener!!)
        drain()
        assertEquals(1, closes)
    }

    @Test fun rejectedSystemCallbackRetriesBeforeRegisteringHardKeys() {
        rejectSystemCallback = true
        start()
        assertTrue(registrations.isEmpty())
        rejectSystemCallback = false
        advanceRetry()
        assertNotNull(systemCallback)
        assertArrayEquals(intArrayOf(4), registrations.single())
    }

    @Test fun detachCancelsRetryAndRejectsLateConnection() {
        deliverConnection = false
        start()
        val oldConnection = connection
        Hs5SteeringKeys.detach(controller)
        oldConnection.onServiceConnected(component, service)
        advanceRetry()
        assertEquals(1, binds)
        assertTrue(registrations.isEmpty())
    }

    @Test fun focusUsesVerifiedCarPlayIdAndIsReleasedWhenHostPauses() {
        shown = false
        start()
        Hs5SteeringKeys.hostResumed(host)
        drain()
        assertEquals(1, focusRequests)
        Hs5SteeringKeys.hostPaused(host)
        drain()
        assertEquals(1, focusReleases)
    }

    @Test fun independentFlyScreenKeepsFocusAfterMainActivityPausesAndBackStillWorks() {
        start()
        Hs5SteeringKeys.hostResumed(host)
        drain()
        Hs5SteeringKeys.hostPaused(host)
        drain()
        assertEquals(1, focusRequests)
        assertEquals(0, focusReleases)
        sendKey(listener!!)
        drain()
        assertEquals(1, closes)
        assertEquals(1, focusReleases)
    }

    @Test fun rejectedCarPlayFocusFallsBackToOemCarlinkFocusBeforeHandlingBack() {
        focusResults[37] = 0
        start()
        assertEquals(listOf(37, 38), focusTypes)
        sendKey(listener!!)
        drain()
        assertEquals(1, closes)
        assertEquals(1, focusReleases)
        verify(controller, never()).close()
    }

    @Test fun fallbackFocusIsRememberedAndItsLossIsRespected() {
        focusResults[37] = 0
        start()
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken("ltd.qisi.systemmanager.IViewFocusChangedListener")
            data.writeInt(38)
            data.writeInt(-1)
            assertTrue(focusListener!!.transact(1, data, null, IBinder.FLAG_ONEWAY))
        } finally {
            data.recycle()
        }
        drain()
        assertEquals(1, focusReleases)
        Hs5SteeringKeys.applySettings(context)
        drain()
        assertEquals(listOf(37, 38), focusTypes)
        Hs5SteeringKeys.hostResumed(host)
        drain()
        assertEquals(listOf(37, 38, 38), focusTypes)
    }

    @Test fun focusDeniedAtBootRetriesWithoutAnotherActivityEventAndStopsAfterDetach() {
        focusResults[37] = 0
        focusResults[38] = 0
        start()
        assertEquals(listOf(37, 38), focusTypes)
        focusResults[38] = 1
        advanceRetry()
        assertEquals(listOf(37, 38, 37, 38), focusTypes)
        Hs5SteeringKeys.detach(controller)
        drain()
        advanceRetry()
        assertEquals(4, focusRequests)
        assertEquals(1, focusReleases)
    }

    private fun configureVoice(single: VoiceAssistantKeyTarget) {
        AirPlayPersistence.saveCarPlayWheelSiri(context, true)
        AirPlayPersistence.saveVoiceAssistantKeySingle(context, single)
        AirPlayPersistence.saveVoiceAssistantKeyDouble(context, VoiceAssistantKeyTarget.entries.first { it != single })
        `when`(controller.requestSiri()).thenReturn(true)
    }

    private fun voicePress(key: Int = 231) {
        sendKey(listener!!, key)
        drain()
    }

    private fun finishSingle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(451))
        drain()
    }

    private fun doublePress(key: Int = 231) {
        voicePress(key)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        voicePress(key)
        finishSingle()
    }

    @Test fun hongqiSingleSiriDoubleRoutesRepeatedPressesWithoutStickyAssistantState() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        assertTrue(registrations.last().containsAllCodes(231, 259))
        repeat(2) {
            voicePress()
            finishSingle()
            doublePress()
        }
        assertEquals(2, voiceCommands.size)
        verify(controller, times(2)).requestSiri()
    }

    @Test fun siriSingleHongqiDoubleAlsoWorksForCarLinkVoiceCode() {
        configureVoice(VoiceAssistantKeyTarget.SIRI)
        start()
        repeat(2) {
            voicePress(259)
            finishSingle()
            doublePress(259)
        }
        assertEquals(2, voiceCommands.size)
        verify(controller, times(2)).requestSiri()
    }

    @Test fun aliasedCodesForSamePhysicalPressDoNotBecomeDoubleClick() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        voicePress(231)
        voicePress(259)
        finishSingle()
        assertEquals(1, voiceCommands.size)
        verify(controller, never()).requestSiri()
    }

    @Test fun customVoiceWorksWithBluetoothAudioButLeavesMediaAndPhoneAlone() {
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        configureVoice(VoiceAssistantKeyTarget.SIRI)
        start()
        assertArrayEquals(intArrayOf(231, 259, 110, 4), registrations.last())
        voicePress()
        finishSingle()
        verify(controller).requestSiri()
    }

    @Test fun disablingCustomVoiceCancelsPendingWakeAndReturnsBothCodesToOem() {
        configureVoice(VoiceAssistantKeyTarget.SIRI)
        start()
        val old = listener!!
        voicePress()
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        Hs5SteeringKeys.applySettings(context)
        drain()
        sendKey(old, 231)
        finishSingle()
        assertArrayEquals(intArrayOf(4), registrations.last())
        assertTrue(unregistrations.last().containsAllCodes(231, 259))
        verify(controller, never()).requestSiri()
        assertTrue(voiceCommands.isEmpty())
    }

    @Test fun disconnectCancelsPendingVoiceWake() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        voicePress()
        Hs5SteeringKeys.detach(controller)
        finishSingle()
        assertTrue(voiceCommands.isEmpty())
        assertNull(systemCallback)
    }

    @Test fun voiceKeyUpAndLongClickDoNotCountAsAnotherPress() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        voicePress()
        sendKey(listener!!, 231, 1)
        sendKey(listener!!, 231, 2)
        finishSingle()
        assertEquals(1, voiceCommands.size)
        verify(controller, never()).requestSiri()
    }

    @Test fun hongqiWakeReleasesFocusAndDoesNotTakeItBackOnSettingsRefresh() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        assertEquals(1, focusRequests)
        voicePress()
        finishSingle()
        assertEquals(1, focusReleases)
        Hs5SteeringKeys.applySettings(context)
        drain()
        assertEquals(1, focusRequests)
        doublePress()
        assertEquals(2, focusRequests)
    }

    @Test fun queuedHongqiThenSiriRestoresFocusBeforeSendingSiri() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        val worker = Hs5SteeringKeys::class.java.getDeclaredField("worker").apply { isAccessible = true }
            .get(Hs5SteeringKeys) as ExecutorService
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        worker.submit {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            Hs5SteeringKeys.launchVoiceAssistant(VoiceAssistantKeyTarget.HONGQI)
            Hs5SteeringKeys.launchVoiceAssistant(VoiceAssistantKeyTarget.SIRI)
            `when`(controller.requestSiri()).thenAnswer {
                assertEquals(1, focusReleases)
                assertEquals(listOf(37, 37), focusTypes)
                true
            }
        } finally { release.countDown() }
        drain()
        assertEquals(1, voiceCommands.size)
        assertEquals(2, focusRequests)
        verify(controller).requestSiri()
    }

    @Test fun focusLossDoesNotFightOemVoiceAndHostResumeCanRequestFocusAgain() {
        start()
        val callback = focusListener!!
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken("ltd.qisi.systemmanager.IViewFocusChangedListener")
            data.writeInt(37)
            data.writeInt(-1)
            assertTrue(callback.transact(1, data, null, IBinder.FLAG_ONEWAY))
        } finally {
            data.recycle()
        }
        drain()
        assertEquals(1, focusReleases)
        Hs5SteeringKeys.applySettings(context)
        drain()
        assertEquals(1, focusRequests)
        Hs5SteeringKeys.hostResumed(host)
        drain()
        assertEquals(2, focusRequests)
    }

    @Test fun changingVoiceMappingCancelsPendingActionAndUsesNewMapping() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        voicePress()
        AirPlayPersistence.saveVoiceAssistantKeySingle(context, VoiceAssistantKeyTarget.SIRI)
        Hs5SteeringKeys.applySettings(context)
        finishSingle()
        assertTrue(voiceCommands.isEmpty())
        voicePress()
        finishSingle()
        verify(controller).requestSiri()
    }

    @Test fun rejectedHongqiWakeDoesNotPreventSubsequentSiriOrHongqiRequests() {
        configureVoice(VoiceAssistantKeyTarget.HONGQI)
        start()
        rejectVoiceCommand = true
        voicePress()
        finishSingle()
        assertTrue(voiceCommands.isEmpty())
        doublePress()
        verify(controller).requestSiri()
        rejectVoiceCommand = false
        voicePress()
        finishSingle()
        assertEquals(1, voiceCommands.size)
    }

    @Test fun diagnosticEventsAreOptInAndAreClearedOnDisable() {
        start()
        assertEquals("no recorded Qisi key events", Hs5SteeringDiagnostics.report())
        DiagnosticLogging.setEnabled(true)
        sendKey(listener!!)
        drain()
        assertTrue(Hs5SteeringKeys.diagnosticReport(context).contains("BACK fly screen close=true"))
        DiagnosticLogging.setEnabled(false)
        assertEquals("no recorded Qisi key events", Hs5SteeringDiagnostics.report())
        DiagnosticLogging.setEnabled(true)
        assertFalse(Hs5SteeringKeys.diagnosticReport(context).contains("BACK fly screen close=true"))
    }

    @Test fun systemCommandEvidenceIsOptInAndExcludesSpeechData() {
        start()
        val command = JSONObject()
            .put("SenderGroup", "GROUP_SDS")
            .put("TargetGroup", context.packageName)
            .put("SessionID", 57)
            .put("CommandType", 0)
            .put("ErrorCode", 6)
            .put("Data", "private spoken words")
            .toString()
        fun deliver() {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken("ltd.qisi.systemmanager.ISystemServiceCallback")
                data.writeString(command)
                assertTrue(systemCallback!!.transact(1, data, null, IBinder.FLAG_ONEWAY))
            } finally { data.recycle() }
        }
        deliver()
        assertEquals("no recorded Qisi key events", Hs5SteeringDiagnostics.report())
        DiagnosticLogging.setEnabled(true)
        deliver()
        val report = Hs5SteeringDiagnostics.report()
        assertTrue(report.contains("system callback current=true"))
        assertTrue(report.contains("SessionID=57"))
        assertTrue(report.contains("ErrorCode=6"))
        assertFalse(report.contains("private spoken words"))
        DiagnosticLogging.setEnabled(false)
        assertEquals("no recorded Qisi key events", Hs5SteeringDiagnostics.report())
    }

    private fun IntArray.containsAllCodes(vararg codes: Int): Boolean = codes.all { contains(it) }

    @Test fun closingFlyScreenReleasesBackButRetainsMediaRegistration() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, true)
        start()
        assertTrue(registrations.single().contains(4))
        sendKey(listener!!)
        drain()
        assertEquals(1, closes)
        assertEquals(2, registrations.size)
        assertFalse(registrations.last().contains(4))
        assertTrue(registrations.last().contains(android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
        verify(controller, never()).close()
    }

    @Test fun flyScreenStartsAndStopsRegistrationWithoutAnActivityResume() {
        shown = false
        start()
        assertTrue(registrations.isEmpty())
        shown = true
        Hs5SteeringKeys.applySettings(context)
        drain()
        assertArrayEquals(intArrayOf(4), registrations.single())
        shown = false
        Hs5SteeringKeys.applySettings(context)
        drain()
        assertArrayEquals(intArrayOf(4), unregistrations.single())
        assertNull(listener)
    }
}
