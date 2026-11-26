package com.shilapi.xcertplay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.PairingStore
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Owns the connection until a visible host adopts it; no Activity, window or measurement is needed. */
internal object Hs5BootSession {
    private val main = Handler(Looper.getMainLooper())
    private val scheduled = AtomicBoolean(false)
    private val generation = AtomicInteger()
    @Volatile private var preparing = false
    fun isPreparing(): Boolean = preparing

    private data class Prepared(
        val profile: Hs5BootProfileCache.Profile,
        val identity: AirPlayIdentity,
        val runtime: CarPlayRuntimeConfig,
        val pairings: PairingStore,
    )

    fun schedule(context: Context, phoneWake: Boolean = false) {
        val app = context.applicationContext
        if (!eligible(app, phoneWake) || CarPlayBackgroundSession.hasSession() || !scheduled.compareAndSet(false, true)) return
        val ticket = generation.get()
        preparing = true
        record("prepare source=${if (phoneWake) "selected_phone_acl" else "boot"}")
        Thread({
            val prepared = runCatching {
                val profile = Hs5BootProfileCache.load(app) ?: return@runCatching null
                DiPlayBootstrap.ensure(app, AirPlayPersistence.loadMfiTarget(app))
                val identity = AirPlayPersistence.loadIdentity(app)
                if (profile.config.deviceId != DiPlayBootstrap.deviceId(identity)) return@runCatching null
                Prepared(profile, identity, CarPlayRuntimeConfigFactory.create(app, identity),
                    AirPlayPersistence.loadPairings(app) { id, key -> AirPlayPersistence.savePairing(app, id, key) })
            }.onFailure { record("prepare failed type=${it.javaClass.simpleName}") }.getOrNull()
            main.post {
                preparing = false
                if (prepared == null) { if (generation.get() == ticket) scheduled.set(false); record("skip=display_profile_unavailable; first UI connection will cache it"); return@post }
                if (generation.get() != ticket || !eligible(app, phoneWake) || CarPlayBackgroundSession.hasSession()) return@post
                val (profile, identity, runtime, pairings) = prepared
                var renderer: AndroidMediaSink? = null
                var controller: CarPlayController? = null
                try {
                    ConnectionProgressUiState.reset()
                    val fps = profile.config.main.fps
                    val nextSink = AndroidMediaSink(
                        videoWidth = profile.config.main.widthPixels,
                        videoHeight = profile.config.main.heightPixels,
                        preferSoftwareHevcDecoder = AirPlayPersistence.loadHevcSoftwareDecoderEnabled(app),
                        advancedAudioChannelMapping =
                            app.resources.getBoolean(R.bool.config_advanced_audio_channel_mapping) &&
                                AirPlayPersistence.loadAdvancedAudioChannelMapping(app),
                        audioFocusEnabled = AirPlayPersistence.loadAudioFocusEnabled(app),
                        audioFocusAutoYield = AirPlayPersistence.loadAudioFocusAutoYield(app),
                        mediaChannel = AirPlayPersistence.loadMediaAudioChannel(app),
                        navigationChannel = AirPlayPersistence.loadNavigationAudioChannel(app),
                        context = app,
                        navigationStreamType = AirPlayPersistence.loadNavigationStreamType(app),
                        mediaBufferMillis = AirPlayPersistence.loadMediaBufferMillis(app),
                        onAudioDiagnostic = { record(it) },
                        onMediaAudioChanged = CarPlayMediaKeys::onMediaAudioChanged,
                        callEchoCancellation = AirPlayPersistence.loadCallEchoCancellation(app),
                        callVoiceFilter = AirPlayPersistence.loadCallVoiceFilter(app),
                        videoPacingDelayMillis = 0,
                        mainVideoFrameRate = fps,
                        vendorLowLatencyDecoder = AirPlayPersistence.loadLowLatencyDecoder(app),
                        mtkDecoderTuningEnabled = AirPlayPersistence.loadMtkDecoderTuning(app),
                    )
                    renderer = nextSink
                    var active: AirPlaySession? = null
                    val listener = object : AirPlaySessionListener {
                        override fun onSessionActive(session: AirPlaySession) {
                            main.post {
                                if (!owns(controller)) return@post
                                active = session
                                CarPlayBackgroundSession.active = true
                                CarPlayBackgroundSession.callPopupReady = true
                                ConnectionProgressUiState.advance(CarPlayConnectionProgress.STEP_COUNT)
                                CarPlayStartupDiagnostics.mark(CarPlayStartupDiagnostics.Phase.SESSION_ACTIVE)
                                record("session active before UI")
                            }
                        }
                        override fun onSessionEnded(session: AirPlaySession) {
                            main.post {
                                if (owns(controller) && active === session) {
                                    ConnectionProgressUiState.markDisconnected()
                                    CarPlayBackgroundSession.stop()
                                }
                            }
                        }
                        override fun onTransportError(message: String) {
                            main.post {
                                if (owns(controller) && active != null) {
                                    ConnectionProgressUiState.markDisconnected()
                                    CarPlayBackgroundSession.stop()
                                }
                            }
                        }
                        override fun onDebugLog(message: String) {
                            CarPlayConnectionProgress.stepIndexFromDebugLog(message)?.let(ConnectionProgressUiState::advance)
                            record(message)
                        }
                    }
                    val next = CarPlayController(
                        context = app, config = runtime, airPlayConfig = profile.config, identity = identity,
                        pairings = pairings,
                        listener = listener,
                        media = CarPlayMediaEngine(nextSink, microphoneEnabled = profile.config.microphone),
                        reportStatus = { status ->
                            main.post {
                                if (owns(controller)) {
                                    CarPlayConnectionProgress.stepIndexFromStatus(status)?.let(ConnectionProgressUiState::advance)
                                    if (status is CarPlayStatus.Failed) {
                                        ConnectionProgressUiState.markFailed(status.message)
                                        CarPlayBackgroundSession.stop()
                                    }
                                }
                            }
                        },
                        loadPairRecord = { AirPlayPersistence.loadLockdownRecord(app) },
                        savePairRecord = { AirPlayPersistence.saveLockdownRecord(app, it) },
                        clearPairRecord = { AirPlayPersistence.clearLockdownRecord(app) },
                        locationProvider = CarPlayRuntimeConfigFactory.locationProvider(app, runtime),
                        vehicleStatusProvider = CarPlayRuntimeConfigFactory.vehicleStatusProvider(app),
                    )
                    controller = next
                    CarPlayBackgroundSession.store(next, nextSink, profile.width, profile.height, this, profile.display) { completion ->
                        main.post { stop(app, next, nextSink, completion) }
                    }
                    ContextCompat.startForegroundService(app, Intent(app, DiPlaySessionService::class.java))
                    // Register safety policy before start; OEM media/steering setup is not a
                    // prerequisite for MFi discovery and can run while its worker is busy.
                    if (profile.config.videoInCar) CarPlayVideo.attach(app, next)
                    CarPlayStartupDiagnostics.mark(CarPlayStartupDiagnostics.Phase.CONTROLLER_STARTED)
                    record("controller started before UI display=${profile.display.width}x${profile.display.height}")
                    next.start()
                    CarPlayMediaKeys.attach(app, next)
                    main.postDelayed({
                        if (owns(next) && !CarPlayBackgroundSession.active) {
                            record("startup deadline reached before UI adoption")
                            CarPlayBackgroundSession.stop()
                        }
                    }, 120_000)
                } catch (failure: Exception) {
                    record("start failed type=${failure.javaClass.simpleName}")
                    if (owns(controller)) CarPlayBackgroundSession.stop()
                    else {
                        controller?.close()
                        renderer?.close()
                    }
                }
            }
        }, "hs5-boot-session").apply { isDaemon = true; start() }
    }

    /** Also cancels a prepared profile which has not reached the main thread yet. */
    fun cancelPending() {
        generation.incrementAndGet()
        scheduled.set(true)
    }

    private fun eligible(context: Context, phoneWake: Boolean): Boolean =
        !AirPlayPersistence.loadClusterOnlyMode(context) &&
        Hs5HeadUnit.isHeadUnit(context) &&
        !ConnectionProgressUiState.disconnected() && !HongqiScreenStatus.screenOff &&
        (if (phoneWake) DiPlayPreferences.connectOnPhoneBluetooth(context)
            else AirPlayPersistence.loadAutoStartOnBoot(context)) && DiPlayPreferences.autoConnect(context) &&
        DiPlayPreferences.autoConnectWireless(context) && !DiPlayPreferences.phoneAddress(context).isNullOrBlank() &&
        AirPlayPersistence.loadWirelessHotspotMode(context) != WirelessHotspotMode.MANUAL &&
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun owns(controller: CarPlayController?): Boolean = controller != null &&
        CarPlayBackgroundSession.isOwner(this) && CarPlayBackgroundSession.snapshot()?.controller === controller

    private fun stop(context: Context, controller: CarPlayController, renderer: AndroidMediaSink, completion: () -> Unit) {
        if (!owns(controller)) { completion(); return }
        CarPlayBackgroundSession.clear(controller, keepOwner = true)
        CarPlayMediaKeys.detach(controller)
        controller.close()
        Thread({
            runCatching {
                try { controller.awaitClosed(8_000) } finally { renderer.close() }
            }.onFailure { record("stop failed type=${it.javaClass.simpleName}") }
            main.post {
                if (CarPlayBackgroundSession.isOwner(this)) CarPlayBackgroundSession.clear()
                if (CarPlayBackgroundSession.snapshot() == null) context.stopService(Intent(context, DiPlaySessionService::class.java))
                completion()
            }
        }, "hs5-boot-session-stop").apply { isDaemon = true; start() }
    }

    private fun record(message: String) = DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("boot-session $message") }
}
