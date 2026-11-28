package com.shilapi.xcertplay.newui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.CarPlayHostSettingsBridge
import com.shilapi.xcertplay.ConnectionProgressUiState
import com.shilapi.xcertplay.DiPlayWindowFullscreen
import com.shilapi.xcertplay.Hs5HongqiStartupWarmup
import com.shilapi.xcertplay.Hs5BootSession
import com.shilapi.xcertplay.Hs5SteeringKeys
import com.shilapi.xcertplay.Hs5ClusterActivityOutput
import com.shilapi.xcertplay.ClusterScreenCaptureService
import com.shilapi.xcertplay.ClusterScreenFrames
import com.shilapi.xcertplay.ClusterStyle
import com.shilapi.xcertplay.newui.display.NewUiClusterCopyRegionScreen
import com.shilapi.xcertplay.ClusterMapPresentation
import androidx.core.content.FileProvider
import com.shilapi.xcertplay.AppLocale
import com.shilapi.xcertplay.AppAppearanceRuntime
import com.shilapi.xcertplay.HongqiPositionLights
import com.shilapi.xcertplay.resolveAppNightNow
import com.shilapi.xcertplay.DiPlayLauncher
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.PhoneBluetoothReceiver
import com.shilapi.xcertplay.WirelessRecovery
import com.shilapi.xcertplay.appDialogBuilder
import com.shilapi.xcertplay.hud.Hs5NavigationOutputs
import com.shilapi.xcertplay.telecom.PhoneAudioRoute
import com.shilapi.xcertplay.AudioChannelPreview
import com.shilapi.xcertplay.DiagnosticExportStore
import com.shilapi.xcertplay.DiagnosticRemovableVolumes
import com.shilapi.xcertplay.RemovableStorageVolume
import com.shilapi.xcertplay.CarPlayStartupDiagnostics
import com.shilapi.xcertplay.ImageCropActivity
import com.shilapi.xcertplay.InterfaceSize
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.BackgroundUpdateScheduler
import com.shilapi.xcertplay.update.UpdateAvailability
import com.shilapi.xcertplay.newui.connection.NewUiConnectionActions
import com.shilapi.xcertplay.newui.display.NewUiOriginalClusterConfigScreen
import com.shilapi.xcertplay.newui.connection.NewUiConnectionMethodActions
import com.shilapi.xcertplay.newui.connection.NewUiConnectionMethodDraft
import com.shilapi.xcertplay.newui.connection.NewUiConnectPhoneScreen
import com.shilapi.xcertplay.newui.connection.NewUiConnectionMethodScreen
import com.shilapi.xcertplay.newui.connection.NewUiConnectionSubpage
import com.shilapi.xcertplay.newui.connection.NewUiPhoneSelectionReader
import com.shilapi.xcertplay.newui.connection.NewUiPhoneSelectionState
import com.shilapi.xcertplay.newui.connection.NewUiSwitchPhoneScreen
import com.shilapi.xcertplay.newui.connection.NewUiBondedPhone
import com.shilapi.xcertplay.newui.connection.NewUiConnectionScreen
import com.shilapi.xcertplay.newui.connection.NewUiConnectionSettingsIntents
import com.shilapi.xcertplay.newui.connection.NewUiConnectionSnapshot
import com.shilapi.xcertplay.newui.connection.NewUiConnectionSnapshotReader
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticFile
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticReport
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticsRepository
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticReportScreen
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticsScreen
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticsStorageScreen
import com.shilapi.xcertplay.newui.diagnostics.DiagnosticReportShareSession
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticQrScreen
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticsSubpage
import com.shilapi.xcertplay.newui.display.NewUiDisplayActions
import com.shilapi.xcertplay.newui.display.NewUiDisplayFpsScreen
import com.shilapi.xcertplay.newui.display.NewUiDisplayIconSizeScreen
import com.shilapi.xcertplay.newui.display.NewUiClusterManagerScreen
import com.shilapi.xcertplay.newui.display.NewUiDisplayNavMarkerScreen
import com.shilapi.xcertplay.newui.display.NewUiDisplayScreen
import com.shilapi.xcertplay.newui.display.NewUiDisplayState
import com.shilapi.xcertplay.newui.display.NewUiDisplayStateReader
import com.shilapi.xcertplay.newui.display.NewUiDisplaySubpage
import com.shilapi.xcertplay.newui.settings.NewUiAudioChannelKind
import com.shilapi.xcertplay.newui.settings.NewUiSettingsAboutScreen
import com.shilapi.xcertplay.newui.settings.NewUiSettingsAudioChannelScreen
import com.shilapi.xcertplay.newui.settings.NewUiSettingsActions
import com.shilapi.xcertplay.newui.settings.NewUiSettingsLanguageScreen
import com.shilapi.xcertplay.newui.settings.NewUiSettingsPermissionsScreen
import com.shilapi.xcertplay.newui.settings.NewUiSettingsScreen
import com.shilapi.xcertplay.newui.settings.NewUiSettingsState
import com.shilapi.xcertplay.newui.settings.NewUiSettingsStateReader
import com.shilapi.xcertplay.newui.settings.NewUiSettingsSubpage
import com.shilapi.xcertplay.newui.settings.NewUiSettingsUpdateScreen
import com.shilapi.xcertplay.newui.settings.NewUiSettingsVehicleIconScreen
import com.shilapi.xcertplay.newui.settings.NewUiSettingsVehicleNameScreen
import com.shilapi.xcertplay.newui.settings.NewUiUpdateController
import com.shilapi.xcertplay.newui.settings.NewUiUpdateStage
import com.shilapi.xcertplay.newui.settings.NewUiUpdateUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Primary Hongqi head-unit shell for connection, display, settings and diagnostics. */
open class NewUiActivity : ComponentActivity() {
    private var interfaceOverride: Configuration? = null
    private val handler = Handler(Looper.getMainLooper())
    private var unsubscribeAppearance: (() -> Unit)? = null
    private lateinit var connectionActions: NewUiConnectionActions
    private var snapshot by mutableStateOf(NewUiConnectionSnapshot.placeholder)
    private var displayState by mutableStateOf(NewUiDisplayState.placeholder)
    private var copyRegionStyle by mutableStateOf(ClusterStyle.ORIGINAL)
    @Volatile private var clusterOnlyDismissed = false
    private var screenCaptureConsentPending = false
    private var screenCaptureConsentAttempted = false
    private var screenCaptureConsentRetries = 0
    private var screenCaptureRecoveryRetried = false
    private var clusterOnlyBootLaunch = false
    private var returnedHomeForClusterOnlyBoot = false
    private var observedCaptureStopGeneration = 0L
    private var captureStopRecoveryAttempted = false
    private var clusterOnlyOutputFailure: String? = null
    private var clusterOutputWaitTask: Runnable? = null
    private val standaloneCluster = object : CarPlayHostSettingsBridge.StandaloneCluster {
        override fun enabled() = AirPlayPersistence.loadClusterOnlyMode(this@NewUiActivity)
        override fun shown() = enabled() && Hs5ClusterActivityOutput.isOpen()
        override fun show(): Boolean {
            if (!enabled() || !AirPlayPersistence.loadClusterMapEnabled(this@NewUiActivity)) return false
            clusterOnlyDismissed = false
            startClusterOnlyOutput()
            return shown()
        }
        override fun close(reason: String): Boolean {
            if (!shown()) return false
            clusterOnlyDismissed = true
            Hs5ClusterActivityOutput.close(reason, clusterOnlyHost)
            com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setClusterMapShown(false)
            Hs5SteeringKeys.applySettings(this@NewUiActivity)
            return true
        }
        override fun settingsChanged() {
            clusterOnlyDismissed = false
            syncClusterOnlyOutput()
        }
    }
    private val clusterOnlyHost = object : Hs5ClusterActivityOutput.Host {
        override fun onClusterWindowSurface(surface: android.view.Surface?) {
            CarPlayStartupDiagnostics.capture("cluster-only window surface=${surface?.isValid == true}")
        }
        override fun onClusterWindowClosed(reason: String) {
            clusterOnlyDismissed = true
            Hs5SteeringKeys.applySettings(this@NewUiActivity)
            CarPlayStartupDiagnostics.capture("cluster-only window closed reason=$reason")
            if (AirPlayPersistence.loadClusterOnlyMode(this@NewUiActivity)) {
                com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setClusterMapShown(false)
            }
        }
    }
    private val screenCapturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        screenCaptureConsentPending = false
        screenCaptureConsentAttempted = true
        CarPlayStartupDiagnostics.capture("cluster-only screen-capture permission-result resultCode=${result.resultCode} dataPresent=${result.data != null} mode=${AirPlayPersistence.loadClusterOnlyMode(this)}")
        com.shilapi.xcertplay.diagnostics.DiagnosticLogging.logcat {
            android.util.Log.i("DiPlay-ScreenCopy", "permission result=${result.resultCode} data=${result.data != null} enabled=${AirPlayPersistence.loadClusterOnlyMode(this)}")
        }
        if (result.resultCode == RESULT_OK && result.data != null && AirPlayPersistence.loadClusterOnlyMode(this)) {
            runCatching {
                ClusterScreenCaptureService.start(this, result.data!!)
                waitForScreenCopyBeforeOpeningInstrument()
            }.onFailure {
                screenCaptureConsentAttempted = false
                CarPlayStartupDiagnostics.capture("cluster-only capture service request failed type=${it.javaClass.simpleName} message=${it.message}")
                com.shilapi.xcertplay.diagnostics.DiagnosticLogging.logcat {
                    android.util.Log.e("DiPlay-ScreenCopy", "service start failed ${it.javaClass.simpleName}")
                }
                Toast.makeText(this, R.string.cluster_copy_error, Toast.LENGTH_LONG).show()
                startClusterOnlyOutput()
            }
        } else {
            CarPlayStartupDiagnostics.capture("cluster-only screen-capture permission denied-or-obsolete resultCode=${result.resultCode} dataPresent=${result.data != null}")
            Toast.makeText(this, R.string.cluster_copy_denied, Toast.LENGTH_LONG).show()
            startClusterOnlyOutput()
        }
    }

    private var clusterOnlyMode by mutableStateOf(false)
    private var settingsState by mutableStateOf(NewUiSettingsState.placeholder)
    private var diagnosticReports by mutableStateOf(emptyList<NewUiDiagnosticFile>())
    private var diagnosticUsbRoots by mutableStateOf(emptyList<RemovableStorageVolume>())
    private var diagnosticStoragePath by mutableStateOf("")
    private var diagnosticSaving by mutableStateOf(false)
    private var sessionLogRecordingEnabled by mutableStateOf(false)
    private var carPlayWasActive = false
    private var destination by mutableStateOf(NewUiDestination.CONNECTION)
    private var connectionSubpage by mutableStateOf(NewUiConnectionSubpage.MAIN)
    private var settingsSubpage by mutableStateOf(NewUiSettingsSubpage.MAIN)
    private var displaySubpage by mutableStateOf(NewUiDisplaySubpage.MAIN)
    private var clusterNavMarkerX by mutableStateOf(50)
    private var clusterNavMarkerY by mutableStateOf(45)
    private var clusterNavigationCrop by mutableStateOf(0)
    private var clusterNavigationZoom by mutableStateOf(100)
    private var diagnosticsSubpage by mutableStateOf(NewUiDiagnosticsSubpage.MAIN)
    private var vehicleIconRevision by mutableStateOf(0)
    private var diagnosticReportTitle by mutableStateOf("")
    private var diagnosticReportBody by mutableStateOf("")
    private var updateUiState by mutableStateOf(NewUiUpdateUiState())
    private lateinit var updateController: NewUiUpdateController
    private lateinit var subpageBackCallback: OnBackPressedCallback
    private var originalClusterExitGuard: ((() -> Unit) -> Unit)? = null
    private var connectPhoneAfterSave by mutableStateOf(false)
    private var phoneSelectionState by mutableStateOf(NewUiPhoneSelectionState.placeholder)
    private var connectionMethodDraft by mutableStateOf(
        NewUiConnectionMethodDraft(
            mode = WirelessHotspotMode.WIFI_P2P,
            manualSsid = "",
            manualPassword = "",
            existingSsid = "",
            existingPassword = "",
        ),
    )
    private var manualHotspotOffHint by mutableStateOf(false)
    private var topBarStatus by mutableStateOf(NewUiTopBarStatus.placeholder)
    private var pendingWirelessRecovery = false
    private var audioChannelPreview: AudioChannelPreview? = null
    private var diagnosticShareSession: DiagnosticReportShareSession? = null
    private var diagnosticShareUrl by mutableStateOf<String?>(null)

    private val tick = object : Runnable {
        override fun run() {
            refreshAll()
            handler.postDelayed(this, 1000L)
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        connectionActions.onNotificationPermissionResult()
        refreshAll()
    }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connectionActions.onBluetoothPermissionGranted()
        refreshAll()
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (connectionActions.hasPreciseLocation()) {
            connectionActions.applyLocationReporting(true, ::refreshAll)
        } else {
            refreshAll()
        }
    }
    private val iconPicker = registerForActivityResult(com.shilapi.xcertplay.LocalMediaPicker(), ::cropIcon)
    private val iconCrop = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        vehicleIconRevision++
        refreshAll()
        Toast.makeText(this, R.string.car_button_saved_next_connection, Toast.LENGTH_LONG).show()
    }

    override fun attachBaseContext(newBase: Context) {
        val base = AppLocale.wrap(newBase)
        super.attachBaseContext(base)
        interfaceOverride = InterfaceSize.attach(this, base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getBooleanExtra(EXTRA_CLUSTER_ONLY_BOOT, false) ||
            AirPlayPersistence.loadClusterOnlyMode(this)) {
            setTheme(R.style.Theme_Xcertplay_ConnectionHub)
        }
        super.onCreate(savedInstanceState)
        screenCaptureConsentPending = savedInstanceState?.getBoolean("screen_copy_consent_pending") ?: false
        clusterOnlyBootLaunch = savedInstanceState?.getBoolean(EXTRA_CLUSTER_ONLY_BOOT)
            ?: intent.getBooleanExtra(EXTRA_CLUSTER_ONLY_BOOT, false)
        observedCaptureStopGeneration = ClusterScreenCaptureService.stoppedGeneration
        applyClusterOnlyBootWindow(clusterOnlyBootLaunch)
        if (savedInstanceState == null && DiPlayLauncher.isLauncherIntent(intent) &&
            !AirPlayPersistence.loadClusterOnlyMode(this) && CarPlayBackgroundSession.active) {
            DiPlayLauncher.openCarPlayProjection(this)
            finish()
            return
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        AppLocale.enforce(this)
        interfaceOverride?.let { InterfaceSize.enforce(resources, it) }
        DiPlayWindowFullscreen.apply(this)
        Hs5HongqiStartupWarmup.schedule(applicationContext)
        runCatching { BackgroundUpdateScheduler.schedule(applicationContext) }
        Hs5NavigationOutputs.onAppOpened(applicationContext)
        PhoneAudioRoute.attach(applicationContext)
        connectionActions = NewUiConnectionActions(
            activity = this,
            notificationPermission = notificationPermission,
            bluetoothPermission = bluetoothPermission,
            locationPermission = locationPermission,
            onOpenConnectionMethod = { openConnectionMethodPage() },
            onOpenConnectPhone = { connectAfter -> openConnectPhonePage(connectAfter) },
            onOpenSwitchPhone = { openSwitchPhonePage() },
        )
        updateController = NewUiUpdateController(this) {
            updateUiState = updateController.uiState
        }
        subpageBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                popActiveSubpage()
            }
        }
        onBackPressedDispatcher.addCallback(this, subpageBackCallback)
        if (savedInstanceState != null) {
            restoreNavigation(savedInstanceState)
        } else {
            applyIntentNavigation(intent)
        }
        refreshAll()
        if (AirPlayPersistence.loadClusterOnlyMode(this) && !clusterOnlyBootLaunch) handler.post { requestScreenCopy() }
        if (consumeBluetoothAutoConnectIntent(intent)) {
            handler.post { connectionActions.connect(wirelessForAutoConnect()) }
        } else if (savedInstanceState == null &&
            shouldAutoConnectOnColdStart(intent)
        ) {
            startColdStartConnectionWhenBootPreparationSettles()
        }
        setContent {
            val activity = this@NewUiActivity
            NewUiTheme {
                if (destination == NewUiDestination.DISPLAY && displaySubpage == NewUiDisplaySubpage.COPY_REGION) {
                    NewUiClusterCopyRegionScreen(copyRegionStyle,
                        onBack = { displaySubpage = NewUiDisplaySubpage.CLUSTER_MANAGER; syncSubpageBackCallback() },
                        onStartCapture = { requestScreenCopy() })
                } else NewUiShell(
                    destination = destination,
                    showHs5Branding = true,
                    topBarStatus = topBarStatus,
                    onDestinationSelected = { selected ->
                        leaveOriginalClusterConfig {
                            if (selected == destination && hasSubpage(selected)) {
                                if (displaySubpage == NewUiDisplaySubpage.ORIGINAL_CLUSTER_CONFIG)
                                    closeOriginalClusterConfig() else popSubpageFor(selected)
                            } else {
                                popAllSubpages()
                                destination = selected
                            }
                        }
                    },
                    onOpenHeadUnitHome = { NewUiHeadUnitIntents.openHome(activity) },
                ) {
                    val reduceMotion = rememberNewUiReduceMotion()
                    val pageFadeMs = if (reduceMotion) 0 else NewUiMotion.PageCrossfadeMs
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            fadeIn(tween(pageFadeMs, easing = NewUiMotion.EaseOut)) togetherWith
                                fadeOut(tween(pageFadeMs, easing = NewUiMotion.EaseOut))
                        },
                        label = "newUiMainNav",
                    ) { tab ->
                        when (tab) {
                        NewUiDestination.CONNECTION -> when (connectionSubpage) {
                            NewUiConnectionSubpage.CONNECTION_METHOD -> NewUiConnectionMethodScreen(
                                initial = connectionMethodDraft,
                                manualHotspotOff = manualHotspotOffHint,
                                onBack = { popConnectionSubpage() },
                                onSave = { saveConnectionMethod(it) },
                                onOpenCarHotspotSettings = {
                                    NewUiConnectionSettingsIntents.openCarHotspotSettings(activity)
                                },
                                onOpenCarWifiSettings = {
                                    NewUiConnectionSettingsIntents.openCarWifiSettings(activity)
                                },
                            )
                            NewUiConnectionSubpage.CONNECT_PHONE -> NewUiConnectPhoneScreen(
                                state = phoneSelectionState,
                                connectAfterSave = connectPhoneAfterSave,
                                onBack = { popConnectionSubpage() },
                                onConfirm = { confirmConnectPhone(it) },
                                onRequestBluetoothPermission = {
                                    connectionActions.requestBluetoothPermissionForPhone()
                                },
                                onOpenBluetoothSettings = {
                                    connectionActions.openBluetoothSettings()
                                },
                            )
                            NewUiConnectionSubpage.SWITCH_PHONE -> NewUiSwitchPhoneScreen(
                                state = phoneSelectionState,
                                onBack = { popConnectionSubpage() },
                                onSelectPhone = { switchPhone(it) },
                                onAddPhone = { connectionActions.openBluetoothSettings() },
                                onRequestBluetoothPermission = {
                                    connectionActions.requestBluetoothPermissionForPhone()
                                },
                            )
                            NewUiConnectionSubpage.MAIN -> NewUiConnectionScreen(
                                snapshot = snapshot,
                                onPhoneAction = { connectionActions.openPhonePicker() },
                                onConnectionMethod = { openConnectionMethodPage() },
                                onDisconnect = {
                                    connectionActions.disconnect(::refreshAll)
                                },
                                onEnterCarPlay = { connectionActions.openCarPlay() },
                                onLocationReportingChanged = { enabled ->
                                    connectionActions.onLocationReportingChanged(enabled, ::refreshAll)
                                },
                            )
                        }
                        NewUiDestination.DISPLAY -> when (displaySubpage) {
                            NewUiDisplaySubpage.COPY_REGION -> Unit
                            NewUiDisplaySubpage.ICON_SIZE -> NewUiDisplayIconSizeScreen(
                                selectedSize = displayState.carPlaySize,
                                reconnectNeeded = displayState.iconSizeReconnectNeeded,
                                onBack = { popDisplaySubpage() },
                                onSelectSize = { size ->
                                    NewUiDisplayActions.saveCarPlaySize(activity, size)
                                    markReconnectNeeded()
                                },
                                onReconnect = { connectionActions.reconnectAfterDisplayChange() },
                            )
                            NewUiDisplaySubpage.FPS -> NewUiDisplayFpsScreen(
                                selectedFps = displayState.fps,
                                onBack = { popDisplaySubpage() },
                                onSelectFps = { fps ->
                                    NewUiDisplayActions.saveFps(activity, fps)
                                    refreshAll()
                                },
                            )
                            NewUiDisplaySubpage.NAV_MARKER -> NewUiDisplayNavMarkerScreen(
                                xPercent = clusterNavMarkerX,
                                yPercent = clusterNavMarkerY,
                                navigationCrop = clusterNavigationCrop,
                                navigationZoom = clusterNavigationZoom,
                                onNavigationZoom = { value ->
                                    com.shilapi.xcertplay.ClusterMapCropSettings.saveZoom(activity, value)
                                    clusterNavigationZoom = value
                                },
                                onNavigationCrop = { value ->
                                    com.shilapi.xcertplay.ClusterMapCropSettings.save(activity, value)
                                    clusterNavigationCrop = value
                                },
                                onBack = { popDisplaySubpage() },
                                onSave = { x, y ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.new_ui_display_nav_marker),
                                        message = getString(R.string.new_ui_display_nav_marker_reconnect),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiDisplayActions.saveClusterNavMarker(activity, x, y)
                                    }
                                },
                                onResetDefault = {
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.new_ui_display_nav_marker_reset),
                                        message = getString(R.string.new_ui_display_nav_marker_reconnect),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiDisplayActions.resetClusterNavMarker(activity)
                                    }
                                },
                            )
                            NewUiDisplaySubpage.CLUSTER_MANAGER -> NewUiClusterManagerScreen(
                                selectedStyle = displayState.clusterStyle,
                                onConfigureCopyRegion = { style ->
                                    copyRegionStyle = style
                                    displaySubpage = NewUiDisplaySubpage.COPY_REGION
                                    syncSubpageBackCallback()
                                },
                                onBack = { popDisplaySubpage() },
                                onConfigureOriginal = {
                                    displaySubpage = NewUiDisplaySubpage.ORIGINAL_CLUSTER_CONFIG
                                    syncSubpageBackCallback()
                                },
                                onApply = { style ->
                                    if (clusterOnlyMode && style != displayState.clusterStyle) {
                                        Hs5ClusterActivityOutput.close("cluster-only style changed")
                                        NewUiDisplayActions.saveClusterStyle(activity, style)
                                        clusterOnlyDismissed = false
                                        refreshAll()
                                        waitForScreenCopyBeforeOpeningInstrument()
                                    } else if (style != displayState.clusterStyle) {
                                        applyWithReconnectConfirm(
                                            title = getString(R.string.settings_cluster_manager),
                                            message = getString(R.string.settings_cluster_style_reconnect),
                                            onApplied = {
                                                refreshAll()
                                                connectionActions.reconnectAfterClusterStyleChange()
                                            },
                                        ) {
                                            NewUiDisplayActions.saveClusterStyle(activity, style)
                                        }
                                    }
                                },
                            )
                            NewUiDisplaySubpage.ORIGINAL_CLUSTER_CONFIG -> NewUiOriginalClusterConfigScreen(
                                onBack = { closeOriginalClusterConfig() },
                                registerExitGuard = { originalClusterExitGuard = it },
                            )
                            NewUiDisplaySubpage.MAIN -> NewUiDisplayScreen(
                                state = displayState,
                                clusterOnlyMode = clusterOnlyMode,
                                onClusterOnlyDataSourceChanged = { source ->
                                    NewUiDisplayActions.saveClusterOnlyDataSource(activity, source)
                                    refreshAll()
                                },
                                onClusterOnlyModeChanged = { enabled ->
                                    AirPlayPersistence.saveClusterOnlyMode(activity, enabled)
                                    clusterOnlyMode = enabled
                                    clusterOnlyDismissed = false
                                    screenCaptureConsentAttempted = false
                                    screenCaptureConsentRetries = 0
                                    screenCaptureRecoveryRetried = false
                                    CarPlayStartupDiagnostics.capture("cluster-only mode changed enabled=$enabled carPlaySession=${CarPlayBackgroundSession.hasSession()}")
                                    if (enabled) {
                                        com.shilapi.xcertplay.Hs5BootSession.cancelPending()
                                        com.shilapi.xcertplay.orchestration.BluetoothBootPreconnect.cancel()
                                        if (CarPlayBackgroundSession.hasSession()) {
                                            CarPlayBackgroundSession.stop {
                                                activity.runOnUiThread {
                                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                                        refreshAll()
                                                        requestScreenCopy()
                                                    }
                                                }
                                            }
                                        } else {
                                            refreshAll()
                                            requestScreenCopy()
                                        }
                                    } else {
                                        clusterOutputWaitTask?.let(handler::removeCallbacks)
                                        clusterOutputWaitTask = null
                                        ClusterScreenCaptureService.stop(activity)
                                        Hs5ClusterActivityOutput.close("cluster-only disabled")
                                        com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setClusterMapShown(false)
                                        refreshAll()
                                    }
                                },
                                navMarkerSummary = getString(
                                    R.string.new_ui_display_nav_marker_summary,
                                    clusterNavMarkerX,
                                    clusterNavMarkerY,
                                ),
                                onPickFps = { openDisplayFpsPage() },
                                onPickIconSize = {
                                    displaySubpage = NewUiDisplaySubpage.ICON_SIZE
                                    syncSubpageBackCallback()
                                },
                                onReconnect = { connectionActions.reconnectAfterDisplayChange() },
                                onPickNavMarker = { openDisplayNavMarkerPage() },
                                onOpenClusterManager = {
                                    displaySubpage = NewUiDisplaySubpage.CLUSTER_MANAGER
                                    syncSubpageBackCallback()
                                },
                                onSmoothVideoChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.smooth_video),
                                        message = getString(R.string.smooth_video_description),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiDisplayActions.saveSmoothVideo(activity, enabled)
                                    }
                                },
                                onDirectVideoOutputChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.settings_direct_video_output),
                                        message = getString(R.string.settings_direct_video_output_description),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiDisplayActions.saveDirectVideoOutput(activity, enabled)
                                    }
                                },
                                onLowLatencyDecoderChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.settings_low_latency_decoder),
                                        message = getString(R.string.settings_low_latency_decoder_description),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiDisplayActions.saveLowLatencyDecoder(activity, enabled)
                                    }
                                },
                                onMtkDecoderTuningChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.settings_mtk_decoder_tuning),
                                        message = displayState.mtkDecoderTuningDescription,
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiDisplayActions.saveMtkDecoderTuning(activity, enabled)
                                    }
                                },
                                onHudNavigationChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.new_ui_display_hud_nav),
                                        message = getString(R.string.new_ui_display_hud_nav_sub),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiDisplayActions.saveHudNavigation(activity, enabled)
                                    }
                                },
                                onClusterMapChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.new_ui_display_cluster_map),
                                        message = getString(R.string.new_ui_display_cluster_map_sub),
                                        onApplied = {
                                            refreshAll()
                                            connectionActions.openCarPlay()
                                        },
                                    ) {
                                        NewUiDisplayActions.saveClusterMap(activity, enabled)
                                    }
                                },
                                onClusterFlyScreenDefaultOnConnectChanged = { enabled ->
                                    NewUiDisplayActions.saveClusterFlyScreenDefaultOnConnect(activity, enabled)
                                    refreshAll()
                                },
                                onClusterGestureChanged = { enabled ->
                                    NewUiDisplayActions.saveClusterGesture(activity, enabled)
                                    refreshAll()
                                },
                                onNightModeSelected = { index ->
                                    NewUiDisplayActions.saveCarPlayNightMode(activity, index)
                                    refreshAll()
                                    Toast.makeText(
                                        activity,
                                        R.string.new_ui_display_theme_applied,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        }
                        NewUiDestination.SETTINGS -> when (settingsSubpage) {
                            NewUiSettingsSubpage.VEHICLE_ICON -> NewUiSettingsVehicleIconScreen(
                                iconRevision = vehicleIconRevision,
                                onBack = { popSettingsSubpage() },
                                onChooseImage = { chooseVehicleIcon() },
                                onResetDefault = {
                                    AirPlayPersistence.clearCustomAirPlayIcon(activity)
                                    vehicleIconRevision++
                                    refreshAll()
                                    Toast.makeText(activity, R.string.car_button_saved_next_connection, Toast.LENGTH_LONG).show()
                                },
                            )
                            NewUiSettingsSubpage.VEHICLE_NAME -> NewUiSettingsVehicleNameScreen(
                                initialName = settingsState.vehicleName,
                                onBack = { popSettingsSubpage() },
                                onSave = { name ->
                                    AirPlayPersistence.saveOemLabel(activity, name)
                                    popSettingsSubpage()
                                    refreshAll()
                                    Toast.makeText(activity, R.string.car_button_saved_next_connection, Toast.LENGTH_LONG).show()
                                },
                            )
                            NewUiSettingsSubpage.PERMISSIONS -> NewUiSettingsPermissionsScreen(
                                onBack = { popSettingsSubpage() },
                                onOpenAppPermissions = { openAppPermissions() },
                                onOpenBluetoothSettings = {
                                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                            )
                            NewUiSettingsSubpage.LANGUAGE -> NewUiSettingsLanguageScreen(
                                currentLanguage = AppLocale.preference(activity),
                                onBack = { popSettingsSubpage() },
                                onApply = { language ->
                                    if (language != AppLocale.preference(activity)) {
                                        AppLocale.save(activity, language)
                                        if (Build.VERSION.SDK_INT < 33) activity.recreate()
                                    } else {
                                        popSettingsSubpage()
                                    }
                                },
                            )
                            NewUiSettingsSubpage.UPDATE -> NewUiSettingsUpdateScreen(
                                versionName = settingsState.versionName,
                                backgroundChecksEnabled = UpdateAvailability.backgroundChecksEnabled(activity),
                                state = updateUiState,
                                onBack = { popSettingsSubpage() },
                                onBackgroundChecksChanged = { enabled ->
                                    UpdateAvailability.saveBackgroundChecksEnabled(activity, enabled)
                                    runCatching { BackgroundUpdateScheduler.schedule(applicationContext) }
                                    refreshAll()
                                },
                                onCheck = { updateController.checkForUpdates() },
                                onDownload = { updateController.downloadUpdate() },
                                onInstall = { updateController.installUpdate() },
                            )
                            NewUiSettingsSubpage.MEDIA_AUDIO_CHANNEL -> NewUiSettingsAudioChannelScreen(
                                kind = NewUiAudioChannelKind.MEDIA,
                                selectedChannel = settingsState.mediaAudioChannel,
                                onBack = { popSettingsSubpage() },
                                onSelect = { channel ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.contrib_audio_home_media_channel_label),
                                        message = getString(R.string.contrib_audio_home_nav_channel_note),
                                        onApplied = {
                                            popSettingsSubpage()
                                            refreshAll()
                                        },
                                    ) {
                                        NewUiSettingsActions.saveMediaAudioChannel(activity, channel)
                                    }
                                },
                                onPreview = { previewAudioChannel(it, navigation = false) },
                            )
                            NewUiSettingsSubpage.NAVIGATION_AUDIO_CHANNEL -> NewUiSettingsAudioChannelScreen(
                                kind = NewUiAudioChannelKind.NAVIGATION,
                                selectedChannel = settingsState.navigationAudioChannel,
                                onBack = { popSettingsSubpage() },
                                onSelect = { channel ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.contrib_audio_home_nav_channel_label),
                                        message = getString(R.string.contrib_audio_home_nav_channel_note),
                                        onApplied = {
                                            popSettingsSubpage()
                                            refreshAll()
                                        },
                                    ) {
                                        NewUiSettingsActions.saveNavigationAudioChannel(activity, channel)
                                    }
                                },
                                onPreview = { previewAudioChannel(it, navigation = true) },
                            )
                            NewUiSettingsSubpage.MAIN -> NewUiSettingsScreen(
                                state = settingsState,
                                onAutoStartChanged = { enabled ->
                                    NewUiSettingsActions.saveAutoStart(activity, enabled)
                                    refreshAll()
                                },
                                onCarBluetoothAudioChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.settings_car_bluetooth_audio),
                                        message = getString(R.string.settings_car_bluetooth_audio_description),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiSettingsActions.saveCarBluetoothAudio(activity, enabled)
                                    }
                                },
                                onCallsInterruptMusicChanged = { enabled ->
                                    com.shilapi.xcertplay.media.CallInterruptionSettings.save(activity, enabled)
                                    com.shilapi.xcertplay.telecom.PhoneAudioRoute.settingsChanged(activity)
                                    CarPlayBackgroundSession.snapshot()?.sink?.onCallInterruptionSettingChanged()
                                    refreshAll()
                                },
                                onAudioFocusChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.contrib_audio_home_toggle_audio_focus),
                                        message = getString(R.string.contrib_audio_home_toggle_audio_focus_desc),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiSettingsActions.saveAudioFocusEnabled(activity, enabled)
                                    }
                                },
                                onAdvancedAudioMappingChanged = { enabled ->
                                    applyWithReconnectConfirm(
                                        title = getString(R.string.advanced_audio_channel_mapping),
                                        message = getString(R.string.use_usage_content_type_routing_instead_of_stream_type),
                                        onApplied = ::refreshAll,
                                    ) {
                                        NewUiSettingsActions.saveAdvancedAudioChannelMapping(activity, enabled)
                                    }
                                },
                                onPickMediaChannel = { pushSettingsSubpage(NewUiSettingsSubpage.MEDIA_AUDIO_CHANNEL) },
                                onPickNavigationChannel = {
                                    pushSettingsSubpage(NewUiSettingsSubpage.NAVIGATION_AUDIO_CHANNEL)
                                },
                                onVehicleIcon = { pushSettingsSubpage(NewUiSettingsSubpage.VEHICLE_ICON) },
                                onVehicleName = { pushSettingsSubpage(NewUiSettingsSubpage.VEHICLE_NAME) },
                                onPermissions = { pushSettingsSubpage(NewUiSettingsSubpage.PERMISSIONS) },
                                onLanguage = { pushSettingsSubpage(NewUiSettingsSubpage.LANGUAGE) },
                                onCheckUpdate = { openSettingsUpdatePage() },
                                onCarPlayWheelMediaChanged = { enabled ->
                                    NewUiSettingsActions.saveCarPlayWheelMedia(activity, enabled)
                                    refreshAll()
                                },
                                onCarPlayWheelSiriChanged = { enabled ->
                                    NewUiSettingsActions.saveCarPlayWheelSiri(activity, enabled)
                                    refreshAll()
                                },
                            )
                        }
                        NewUiDestination.DIAGNOSTICS -> when (diagnosticsSubpage) {
                            NewUiDiagnosticsSubpage.VIEW_REPORT -> NewUiDiagnosticReportScreen(
                                title = diagnosticReportTitle,
                                body = diagnosticReportBody,
                                onBack = { popDiagnosticsSubpage() },
                            )
                            NewUiDiagnosticsSubpage.QR_DOWNLOAD -> NewUiDiagnosticQrScreen(
                                shareUrl = diagnosticShareUrl,
                                onBack = { popDiagnosticsSubpage() },
                            )
                            NewUiDiagnosticsSubpage.STORAGE -> NewUiDiagnosticsStorageScreen(
                                initialPath = AirPlayPersistence.loadDiagnosticReportDirectory(activity).orEmpty(),
                                defaultPath = activity.defaultDiagnosticDirectory(),
                                onBack = { popDiagnosticsSubpage() },
                                onSave = { path ->
                                    AirPlayPersistence.saveDiagnosticReportDirectory(activity, path.ifBlank { null })
                                    popDiagnosticsSubpage()
                                    refreshAll()
                                    Toast.makeText(activity, R.string.new_ui_diag_storage_saved, Toast.LENGTH_LONG).show()
                                },
                                onSaveToUsbRoot = { root ->
                                    AirPlayPersistence.saveDiagnosticReportDirectory(activity, root)
                                    popDiagnosticsSubpage()
                                    saveDiagnosticReport(root)
                                },
                            )
                            NewUiDiagnosticsSubpage.MAIN -> NewUiDiagnosticsScreen(
                                sessionLogRecordingEnabled = sessionLogRecordingEnabled,
                                storagePath = diagnosticStoragePath,
                                reports = diagnosticReports,
                                saving = diagnosticSaving,
                                usbSaveRoots = diagnosticUsbRoots,
                                onSessionLogRecordingChanged = { enabled ->
                                    AirPlayPersistence.saveSessionLogRecordingEnabled(activity, enabled)
                                    sessionLogRecordingEnabled = enabled
                                    CarPlayHostSettingsBridge.notifySessionLogRecordingChanged()
                                },
                                onClearSessionLogs = { clearRecordedSessionLogs() },
                                onSaveReport = { saveDiagnosticReport() },
                                onSaveReportToUsb = { saveDiagnosticReportToUsb(it) },
                                onViewReport = { openDiagnosticReport(it) },
                                onShareReport = { shareDiagnosticReport(it) },
                                onDeleteReport = { deleteDiagnosticReport(it) },
                                onEditStorage = { openDiagnosticStoragePage() },
                                onScanDownload = { openDiagnosticQrSharePage() },
                            )
                        }
                        NewUiDestination.ABOUT -> NewUiSettingsAboutScreen()
                        }
                    }
                }
            }
        }
        com.shilapi.xcertplay.CarPlayStartupDiagnostics.mark(
            com.shilapi.xcertplay.CarPlayStartupDiagnostics.Phase.HOME_UI_ATTACHED,
        )
        if (pendingWirelessRecovery) {
            pendingWirelessRecovery = false
            WirelessRecovery.showResetConfirm(this) {
                connectionActions.connect(AirPlayPersistence.loadWirelessEnabled(this))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_CLUSTER_ONLY_BOOT, false)) {
            clusterOnlyBootLaunch = true
            returnedHomeForClusterOnlyBoot = false
            applyClusterOnlyBootWindow(true)
        } else if (DiPlayLauncher.isLauncherIntent(intent)) {
            clusterOnlyBootLaunch = false
            applyClusterOnlyBootWindow(false)
        }
        if (DiPlayLauncher.isLauncherIntent(intent)) {
            popAllSubpages()
            destination = NewUiDestination.CONNECTION
            if (CarPlayBackgroundSession.active) {
                DiPlayLauncher.openCarPlayProjection(this)
                return
            }
        }
        applyIntentNavigation(intent)
        refreshAll()
        if (!AirPlayPersistence.loadClusterOnlyMode(this) && consumeBluetoothAutoConnectIntent(intent)) {
            handler.post { connectionActions.connect(wirelessForAutoConnect()) }
        } else if (!AirPlayPersistence.loadClusterOnlyMode(this) &&
            shouldAutoConnectOnColdStart(intent)
        ) {
            startColdStartConnectionWhenBootPreparationSettles()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("screen_copy_consent_pending", screenCaptureConsentPending)
        outState.putBoolean(EXTRA_CLUSTER_ONLY_BOOT, clusterOnlyBootLaunch)
        outState.putString(DiPlayLauncher.EXTRA_PAGE, navigationPageExtra())
        outState.putBoolean("original_cluster_editor", destination == NewUiDestination.DISPLAY &&
            displaySubpage == NewUiDisplaySubpage.ORIGINAL_CLUSTER_CONFIG)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        HongqiPositionLights.initialize(this)
        unsubscribeAppearance = AppAppearanceRuntime.observeHost {
            handler.post { if (!isFinishing && !isDestroyed) refreshAppearance() }
        }
        refreshAll()
        handler.post(tick)
    }

    override fun onStop() {
        unsubscribeAppearance?.invoke()
        unsubscribeAppearance = null
        handler.removeCallbacks(tick)
        super.onStop()
    }

    override fun onPause() {
        Hs5SteeringKeys.hostPaused(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Hs5SteeringKeys.hostResumed(this)
        enforceInterfaceSize()
        DiPlayWindowFullscreen.apply(this)
        refreshAll()
        if (AirPlayPersistence.loadClusterOnlyMode(this) && !ClusterScreenCaptureService.isRunning &&
            !screenCaptureConsentPending && !screenCaptureConsentAttempted) {
            CarPlayStartupDiagnostics.capture("cluster-only resume recovery requesting screen-capture permission")
            handler.post { if (!isFinishing && !isDestroyed) requestScreenCopy() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enforceInterfaceSize()
        DiPlayWindowFullscreen.apply(this)
        refreshAppearance()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enforceInterfaceSize()
            DiPlayWindowFullscreen.apply(this)
        }
    }

    private fun enforceInterfaceSize() {
        interfaceOverride?.let { InterfaceSize.enforce(resources, it) }
    }

    private fun refreshAppearance() {
        NewUiColors.night = resolveAppNightNow()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !NewUiColors.night
            isAppearanceLightNavigationBars = !NewUiColors.night
        }
    }

    private fun refreshAll() {
        refreshAppearance()
        updateController.refreshInstallResult()
        connectionActions.refreshSetupError()
        if (!CarPlayBackgroundSession.hasSession() && !ConnectionProgressUiState.failed() &&
            !ConnectionProgressUiState.disconnected()) {
            ConnectionProgressUiState.reset()
        }
        val carPlayNowActive = CarPlayBackgroundSession.active
        syncClusterOnlyOutput()
        recoverStoppedClusterCapture()
        snapshot = NewUiConnectionSnapshotReader.read(this, connectionActions.setupError)
        if (!carPlayWasActive && carPlayNowActive) {
            val bootSessionOwnsConnection = CarPlayBackgroundSession.isOwner(Hs5BootSession)
            CarPlayStartupDiagnostics.capture(
                "home-ui observed CarPlay active bootSession=$bootSessionOwnsConnection " +
                    "destination=$destination subpage=$connectionSubpage",
            )
            // A cold-boot connection is intentionally established before its UI. Once that
            // session becomes active, always hand the display to CarPlay; a restored settings
            // page or connection subpage must not consume the one-shot transition and leave the
            // phone connected behind the vehicle home screen.
            if (bootSessionOwnsConnection) {
                CarPlayStartupDiagnostics.capture("home-ui launching projection for active boot session")
                DiPlayLauncher.openCarPlayProjection(this)
            }
        }
        carPlayWasActive = carPlayNowActive
        topBarStatus = NewUiTopBarStatusReader.read(this)
        displayState = NewUiDisplayStateReader.read(this)
        clusterOnlyMode = AirPlayPersistence.loadClusterOnlyMode(this)
        clusterNavMarkerX = AirPlayPersistence.loadClusterMarkerXPercent(this)
        clusterNavMarkerY = AirPlayPersistence.loadClusterMarkerYPercent(this)
        clusterNavigationCrop = com.shilapi.xcertplay.ClusterMapCropSettings.load(this)
        clusterNavigationZoom = com.shilapi.xcertplay.ClusterMapCropSettings.loadZoom(this)
        settingsState = NewUiSettingsStateReader.read(this)
        diagnosticStoragePath = NewUiDiagnosticsRepository.storageHint(this)
        diagnosticReports = NewUiDiagnosticsRepository.listReports(this)
        diagnosticUsbRoots = DiagnosticRemovableVolumes.list(this)
        sessionLogRecordingEnabled = AirPlayPersistence.loadSessionLogRecordingEnabled(this)
        phoneSelectionState = NewUiPhoneSelectionReader.read(this)
        if (connectionSubpage == NewUiConnectionSubpage.CONNECTION_METHOD) {
            manualHotspotOffHint = NewUiConnectionMethodActions.manualHotspotOff(this)
        }
    }

    private fun syncClusterOnlyOutput() {
        if (AirPlayPersistence.loadClusterOnlyMode(this)) {
            CarPlayHostSettingsBridge.registerStandalone(this, standaloneCluster)
            if (AirPlayPersistence.loadClusterMapEnabled(this)) {
                com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setClusterMapShown(Hs5ClusterActivityOutput.isOpen())
                if (!clusterOnlyDismissed) startClusterOnlyOutput()
            } else {
                com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setClusterMapShown(false)
                Hs5ClusterActivityOutput.close("cluster map disabled", clusterOnlyHost)
            }
        } else {
            // In normal mode CarPlayHostActivity owns instrument visibility. Do not let
            // NewUi refreshes send a competing global hide while CarPlay is active.
            CarPlayHostSettingsBridge.unregisterStandalone(this, standaloneCluster)
        }
    }

    private fun startClusterOnlyOutput() {
        val enabled = AirPlayPersistence.loadClusterOnlyMode(this)
        if (!enabled || !AirPlayPersistence.loadClusterMapEnabled(this) || CarPlayBackgroundSession.hasSession() || clusterOnlyDismissed || com.shilapi.xcertplay.HongqiScreenStatus.screenOff || Hs5ClusterActivityOutput.isOpen()) return
        val display = ClusterMapPresentation.findDisplay(this)
        if (display == null) {
            if (clusterOnlyOutputFailure != "no-display") {
                clusterOnlyOutputFailure = "no-display"
                CarPlayStartupDiagnostics.capture("cluster-only output failed: no presentation display")
            }
            return
        }
        val opened = Hs5ClusterActivityOutput.open(this, display, clusterOnlyHost)
        clusterOnlyOutputFailure = null
        com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setClusterMapShown(Hs5ClusterActivityOutput.isOpen())
        Hs5SteeringKeys.applySettings(this)
        CarPlayStartupDiagnostics.capture("cluster-only output open requested displayId=${display.displayId} result=$opened")
    }

    private fun returnToVehicleHomeAfterBootFlyScreen() {
        if (!clusterOnlyBootLaunch || returnedHomeForClusterOnlyBoot || !ClusterScreenFrames.hasFrame() ||
            !Hs5ClusterActivityOutput.isOpen() || !AirPlayPersistence.loadClusterOnlyMode(this)) return
        returnedHomeForClusterOnlyBoot = true
        clusterOnlyBootLaunch = false
        CarPlayStartupDiagnostics.capture("cluster-only boot first frame visible; centre host remains transparent behind instrument")
        applyClusterOnlyBootWindow(true)
    }

    private fun applyClusterOnlyBootWindow(hidden: Boolean) {
        if (hidden) {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            window.attributes = window.attributes.apply { alpha = 0f }
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            window.attributes = window.attributes.apply { alpha = 1f }
        }
    }

    private fun recoverStoppedClusterCapture() {
        val stoppedAt = ClusterScreenCaptureService.stoppedGeneration
        if (stoppedAt == observedCaptureStopGeneration) return
        observedCaptureStopGeneration = stoppedAt
        if (!AirPlayPersistence.loadClusterOnlyMode(this) || captureStopRecoveryAttempted ||
            isFinishing || isDestroyed) return
        captureStopRecoveryAttempted = true
        screenCaptureConsentAttempted = false
        screenCaptureConsentPending = false
        CarPlayStartupDiagnostics.capture("cluster-only capture stopped after start; scheduling one recovery authorization generation=$stoppedAt")
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && AirPlayPersistence.loadClusterOnlyMode(this) &&
                !ClusterScreenCaptureService.isRunning && !screenCaptureConsentPending) requestScreenCopy()
        }, 500L)
    }

    /** Observe capture recovery independently of the instrument window. */
    private fun waitForScreenCopyBeforeOpeningInstrument() {
        startClusterOnlyOutput()
        clusterOutputWaitTask?.let(handler::removeCallbacks)
        val startedAt = android.os.SystemClock.uptimeMillis()
        val task = object : Runnable {
            override fun run() {
                clusterOutputWaitTask = null
                if (isFinishing || isDestroyed || !AirPlayPersistence.loadClusterOnlyMode(this@NewUiActivity)) return
                val hasFrame = ClusterScreenFrames.hasFrame()
                val serviceRunning = ClusterScreenCaptureService.isRunning
                val elapsed = android.os.SystemClock.uptimeMillis() - startedAt
                if (hasFrame) {
                    screenCaptureConsentRetries = 0
                    screenCaptureRecoveryRetried = false
                                    captureStopRecoveryAttempted = false
                                    observedCaptureStopGeneration = ClusterScreenCaptureService.stoppedGeneration
                    CarPlayStartupDiagnostics.capture("cluster-only output gate released frame=$hasFrame ${ClusterScreenFrames.status()} elapsedMs=$elapsed")
                    startClusterOnlyOutput()
                    returnToVehicleHomeAfterBootFlyScreen()
                } else if (elapsed >= 3_000L && !screenCaptureRecoveryRetried) {
                    screenCaptureRecoveryRetried = true
                    screenCaptureConsentRetries++
                    CarPlayStartupDiagnostics.capture("cluster-only first-frame timeout; restarting capture with fresh consent retry=$screenCaptureConsentRetries serviceRunning=$serviceRunning ${ClusterScreenFrames.status()}")
                    if (serviceRunning) ClusterScreenCaptureService.stop(this@NewUiActivity)
                    screenCaptureConsentAttempted = false
                    handler.postDelayed({
                        if (!isFinishing && !isDestroyed && AirPlayPersistence.loadClusterOnlyMode(this@NewUiActivity)) {
                            CarPlayStartupDiagnostics.capture("cluster-only requesting one fresh screen-capture authorization")
                            requestScreenCopy()
                        }
                    }, 700L)
                } else if (!serviceRunning || elapsed >= 6_000L) {
                    CarPlayStartupDiagnostics.capture("cluster-only output gate timed out frame=false ${ClusterScreenFrames.status()} elapsedMs=$elapsed")
                    startClusterOnlyOutput()
                } else {
                    clusterOutputWaitTask = this
                    handler.postDelayed(this, 100L)
                }
            }
        }
        clusterOutputWaitTask = task
        handler.post(task)
    }

    private fun requestScreenCopy() {
        if (!AirPlayPersistence.loadClusterOnlyMode(this)) {
            Toast.makeText(this, R.string.cluster_copy_description, Toast.LENGTH_LONG).show()
            return
        }
        // Switching styles only changes the crop; keep the same full-screen capture session.
        if (screenCaptureConsentPending || ClusterScreenCaptureService.isRunning || screenCaptureConsentAttempted) return
        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        val consent = if (Build.VERSION.SDK_INT >= 34)
            manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            else manager.createScreenCaptureIntent()
        screenCaptureConsentPending = true
        screenCaptureConsentAttempted = true
        CarPlayStartupDiagnostics.capture("cluster-only screen-capture permission requested api=${Build.VERSION.SDK_INT}")
        com.shilapi.xcertplay.diagnostics.DiagnosticLogging.logcat {
            android.util.Log.i("DiPlay-ScreenCopy", "request launched api=${Build.VERSION.SDK_INT}")
        }
        try { screenCapturePermission.launch(consent) }
        catch (error: Exception) {
            screenCaptureConsentPending = false
            screenCaptureConsentAttempted = false
            CarPlayStartupDiagnostics.capture("cluster-only screen-capture permission launch failed type=${error.javaClass.simpleName} message=${error.message}")
            com.shilapi.xcertplay.diagnostics.DiagnosticLogging.logcat {
                android.util.Log.e("DiPlay-ScreenCopy", "permission launch failed ${error.javaClass.simpleName}")
            }
            Toast.makeText(this, R.string.cluster_copy_error, Toast.LENGTH_LONG).show()
            startClusterOnlyOutput()
        }
    }

    /** Saving a display preference never interrupts a running session. */
    private fun markReconnectNeeded() {
        displayState = NewUiDisplayStateReader.read(this)
    }

    private fun saveDiagnosticReport(customDirectory: String? = null) {
        if (!com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return
        if (diagnosticSaving) return
        diagnosticSaving = true
        val version = settingsState.versionName
        val app = applicationContext
        val recordingGeneration = com.shilapi.xcertplay.diagnostics.DiagnosticLogging.generation
        Thread({
            val fileName = "DiPlay-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
            val report = NewUiDiagnosticReport.build(app, version)
            val saved = runCatching {
                var written = false
                com.shilapi.xcertplay.diagnostics.DiagnosticLogging.write(recordingGeneration) {
                    if (customDirectory.isNullOrBlank()) {
                        DiagnosticExportStore.saveWithoutPicker(app, fileName, report)
                    } else {
                        DiagnosticExportStore.saveToDirectory(app, customDirectory, fileName, report)
                    }
                    written = true
                }
                check(written)
            }
            runOnUiThread {
                diagnosticSaving = false
                if (saved.isSuccess) {
                    Toast.makeText(this, R.string.diagnostic_report_saved, Toast.LENGTH_LONG).show()
                    refreshAll()
                } else {
                    Toast.makeText(this, R.string.could_not_save_the_report, Toast.LENGTH_LONG).show()
                }
            }
        }, "new-ui-diag-save").start()
    }

    private fun saveDiagnosticReportToUsb(rootPath: String) {
        AirPlayPersistence.saveDiagnosticReportDirectory(this, rootPath)
        saveDiagnosticReport(rootPath)
    }

    private fun clearRecordedSessionLogs() {
        CarPlayHostSettingsBridge.clearSessionLogs(this)
        Toast.makeText(this, R.string.new_ui_diag_log_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun openDiagnosticReport(file: NewUiDiagnosticFile) {
        diagnosticReportTitle = file.name
        diagnosticReportBody = runCatching { File(file.path).readText().take(12_000) }.getOrElse {
            getString(R.string.could_not_save_the_report)
        }
        diagnosticsSubpage = NewUiDiagnosticsSubpage.VIEW_REPORT
        syncSubpageBackCallback()
    }

    private fun shareDiagnosticReport(file: NewUiDiagnosticFile) {
        val reportFile = File(file.path)
        val uri = runCatching {
            FileProvider.getUriForFile(this, "${packageName}.diagnostic-reports", reportFile)
        }.getOrNull() ?: return
        startActivity(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun deleteDiagnosticReport(file: NewUiDiagnosticFile) {
        runCatching { File(file.path).delete() }
        refreshAll()
    }

    private fun popConnectionSubpage() {
        connectionSubpage = NewUiConnectionSubpage.MAIN
        connectPhoneAfterSave = false
        syncSubpageBackCallback()
    }

    private fun pushConnectionSubpage(subpage: NewUiConnectionSubpage) {
        connectionSubpage = subpage
        phoneSelectionState = NewUiPhoneSelectionReader.read(this)
        syncSubpageBackCallback()
    }

    private fun popSettingsSubpage() {
        settingsSubpage = NewUiSettingsSubpage.MAIN
        syncSubpageBackCallback()
    }

    private fun pushSettingsSubpage(subpage: NewUiSettingsSubpage) {
        settingsSubpage = subpage
        syncSubpageBackCallback()
    }

    private fun popDisplaySubpage() {
        if (displaySubpage == NewUiDisplaySubpage.COPY_REGION) {
            displaySubpage = NewUiDisplaySubpage.CLUSTER_MANAGER
            syncSubpageBackCallback()
            return
        }
        if (displaySubpage == NewUiDisplaySubpage.ORIGINAL_CLUSTER_CONFIG) {
            leaveOriginalClusterConfig { closeOriginalClusterConfig() }
            return
        }
        displaySubpage = NewUiDisplaySubpage.MAIN
        syncSubpageBackCallback()
    }

    private fun closeOriginalClusterConfig() {
        originalClusterExitGuard = null
        displaySubpage = NewUiDisplaySubpage.CLUSTER_MANAGER
        syncSubpageBackCallback()
    }

    private fun leaveOriginalClusterConfig(exit: () -> Unit) {
        val guard = originalClusterExitGuard.takeIf {
            destination == NewUiDestination.DISPLAY && displaySubpage == NewUiDisplaySubpage.ORIGINAL_CLUSTER_CONFIG
        }
        if (guard != null) guard(exit) else exit()
    }

    private fun openDisplayFpsPage() {
        displaySubpage = NewUiDisplaySubpage.FPS
        syncSubpageBackCallback()
    }

    private fun openDisplayNavMarkerPage() {
        displaySubpage = NewUiDisplaySubpage.NAV_MARKER
        syncSubpageBackCallback()
    }

    private fun popDiagnosticsSubpage() {
        if (diagnosticsSubpage == NewUiDiagnosticsSubpage.QR_DOWNLOAD) {
            stopDiagnosticShareSession()
        }
        diagnosticsSubpage = NewUiDiagnosticsSubpage.MAIN
        syncSubpageBackCallback()
    }

    private fun openDiagnosticStoragePage() {
        diagnosticsSubpage = NewUiDiagnosticsSubpage.STORAGE
        syncSubpageBackCallback()
    }

    private fun openDiagnosticQrSharePage() {
        stopDiagnosticShareSession()
        val session = DiagnosticReportShareSession(this)
        diagnosticShareSession = session
        if (!session.start(settingsState.versionName)) {
            diagnosticShareSession = null
            Toast.makeText(this, R.string.new_ui_diag_qr_no_network, Toast.LENGTH_LONG).show()
            return
        }
        diagnosticShareUrl = session.shareUrl
        diagnosticsSubpage = NewUiDiagnosticsSubpage.QR_DOWNLOAD
        syncSubpageBackCallback()
    }

    private fun stopDiagnosticShareSession() {
        diagnosticShareSession?.stop()
        diagnosticShareSession = null
        diagnosticShareUrl = null
    }

    private fun applyIntentNavigation(intent: Intent) {
        when (intent.getStringExtra(DiPlayLauncher.EXTRA_PAGE)) {
            "settings" -> destination = NewUiDestination.SETTINGS
            "home" -> {
                popAllSubpages()
                destination = NewUiDestination.CONNECTION
            }
            "wireless-recovery" -> {
                destination = NewUiDestination.CONNECTION
                pendingWirelessRecovery = true
            }
            else -> Unit
        }
        intent.getStringExtra(DiPlayActivity.EXTRA_SETTINGS_CATEGORY)?.let {
            destination = NewUiDestination.SETTINGS
        }
    }

    private fun restoreNavigation(state: Bundle) {
        if (state.getBoolean("original_cluster_editor")) {
            destination = NewUiDestination.DISPLAY
            displaySubpage = NewUiDisplaySubpage.ORIGINAL_CLUSTER_CONFIG
            syncSubpageBackCallback()
            return
        }
        when (state.getString(DiPlayLauncher.EXTRA_PAGE)) {
            "settings" -> destination = NewUiDestination.SETTINGS
            else -> destination = NewUiDestination.CONNECTION
        }
    }

    private fun navigationPageExtra(): String = when (destination) {
        NewUiDestination.SETTINGS -> "settings"
        else -> "home"
    }

    private fun consumeBluetoothAutoConnectIntent(intent: Intent): Boolean {
        if (AirPlayPersistence.loadClusterOnlyMode(this)) return false
        val requested = intent.getBooleanExtra(PhoneBluetoothReceiver.EXTRA_AUTO_CONNECT, false)
        intent.removeExtra(PhoneBluetoothReceiver.EXTRA_AUTO_CONNECT)
        if (!requested || !DiPlayPreferences.connectOnPhoneBluetooth(this)) return false
        if (DiPlayPreferences.phoneAddress(this) == null) return false
        if (connectionActions.setupError != null) return false
        if (CarPlayBackgroundSession.hasSession()) return false
        return true
    }

    private fun shouldAutoConnectOnColdStart(intent: Intent): Boolean =
        !AirPlayPersistence.loadClusterOnlyMode(this) &&
        NewUiAutoConnectPolicy.shouldConnectOnColdStart(
            intent = intent,
            phoneAddress = DiPlayPreferences.phoneAddress(this),
            setupError = connectionActions.setupError,
            sessionRunning = CarPlayBackgroundSession.hasSession(),
            autoConnectEnabled = DiPlayPreferences.autoConnect(this),
        )

    private fun startColdStartConnectionWhenBootPreparationSettles() {
        if (isFinishing || isDestroyed || AirPlayPersistence.loadClusterOnlyMode(this)) return
        if (Hs5BootSession.isPreparing()) {
            // Let the headless boot session win if its cached profile is ready. If preparation
            // falls back, begin the ordinary UI-owned connection without blocking the Home page.
            handler.postDelayed({ startColdStartConnectionWhenBootPreparationSettles() }, 150L)
            return
        }
        if (shouldAutoConnectOnColdStart(intent)) {
            handler.post { if (!isFinishing && !isDestroyed) connectionActions.connect(wirelessForAutoConnect()) }
        }
    }

    private fun wirelessForAutoConnect(): Boolean = DiPlayPreferences.autoConnectWireless(this)

    private fun defaultDiagnosticDirectory(): String {
        val external = getExternalFilesDir(null)?.let { File(it, "diagnostic-reports") }
        return external?.absolutePath ?: File(filesDir, "diagnostic-reports").absolutePath
    }

    private fun openSettingsUpdatePage() {
        val cached = UpdateAvailability.available(this, settingsState.versionName)
        updateController.bindCachedRelease(cached)
        updateUiState = updateController.uiState
        pushSettingsSubpage(NewUiSettingsSubpage.UPDATE)
    }

    private fun popActiveSubpage() {
        when {
            destination == NewUiDestination.CONNECTION && connectionSubpage != NewUiConnectionSubpage.MAIN ->
                popConnectionSubpage()
            destination == NewUiDestination.SETTINGS && settingsSubpage != NewUiSettingsSubpage.MAIN ->
                popSettingsSubpage()
            destination == NewUiDestination.DISPLAY && displaySubpage != NewUiDisplaySubpage.MAIN ->
                popDisplaySubpage()
            destination == NewUiDestination.DIAGNOSTICS && diagnosticsSubpage != NewUiDiagnosticsSubpage.MAIN ->
                popDiagnosticsSubpage()
        }
    }

    private fun popSubpageFor(tab: NewUiDestination) {
        when (tab) {
            NewUiDestination.CONNECTION -> popConnectionSubpage()
            NewUiDestination.SETTINGS -> popSettingsSubpage()
            NewUiDestination.DISPLAY -> popDisplaySubpage()
            NewUiDestination.DIAGNOSTICS -> popDiagnosticsSubpage()
            NewUiDestination.ABOUT -> Unit
        }
    }

    private fun popAllSubpages() {
        connectionSubpage = NewUiConnectionSubpage.MAIN
        connectPhoneAfterSave = false
        settingsSubpage = NewUiSettingsSubpage.MAIN
        displaySubpage = NewUiDisplaySubpage.MAIN
        diagnosticsSubpage = NewUiDiagnosticsSubpage.MAIN
        syncSubpageBackCallback()
    }

    private fun hasSubpage(tab: NewUiDestination): Boolean = when (tab) {
        NewUiDestination.CONNECTION -> connectionSubpage != NewUiConnectionSubpage.MAIN
        NewUiDestination.SETTINGS -> settingsSubpage != NewUiSettingsSubpage.MAIN
        NewUiDestination.DISPLAY -> displaySubpage != NewUiDisplaySubpage.MAIN
        NewUiDestination.DIAGNOSTICS -> diagnosticsSubpage != NewUiDiagnosticsSubpage.MAIN
        NewUiDestination.ABOUT -> false
    }

    private fun syncSubpageBackCallback() {
        subpageBackCallback.isEnabled = hasSubpage(destination)
    }

    private fun cropIcon(uri: Uri?) {
        if (uri != null) {
            iconCrop.launch(
                Intent(this, ImageCropActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    private fun chooseVehicleIcon() {
        iconPicker.launch("image/*")
    }

    private fun openConnectionMethodPage() {
        connectionMethodDraft = NewUiConnectionMethodDraft.read(this)
        manualHotspotOffHint = NewUiConnectionMethodActions.manualHotspotOff(this)
        pushConnectionSubpage(NewUiConnectionSubpage.CONNECTION_METHOD)
    }

    private fun openConnectPhonePage(connectAfterSave: Boolean) {
        connectPhoneAfterSave = connectAfterSave
        pushConnectionSubpage(NewUiConnectionSubpage.CONNECT_PHONE)
    }

    private fun openSwitchPhonePage() {
        connectPhoneAfterSave = false
        pushConnectionSubpage(NewUiConnectionSubpage.SWITCH_PHONE)
    }

    private fun confirmConnectPhone(phone: NewUiBondedPhone) {
        val shouldConnect = connectPhoneAfterSave
        connectionActions.selectBondedPhone(phone.address, phone.displayName)
        popConnectionSubpage()
        Toast.makeText(this, R.string.saved_for_your_next_connection, Toast.LENGTH_LONG).show()
        refreshAll()
        if (shouldConnect) connectionActions.openCarPlay()
    }

    private fun switchPhone(phone: NewUiBondedPhone) {
        if (phone.address == phoneSelectionState.selectedAddress) {
            popConnectionSubpage()
            return
        }
        val title = getString(R.string.new_ui_switch_phone_title)
        val message = getString(R.string.new_ui_switch_phone_reconnect_hint)
        val commit = {
            connectionActions.saveBondedPhone(phone.address, phone.displayName)
            popConnectionSubpage()
            Toast.makeText(this, R.string.saved_for_your_next_connection, Toast.LENGTH_LONG).show()
            refreshAll()
            if (CarPlayBackgroundSession.hasSession()) {
                connectionActions.connect(AirPlayPersistence.loadWirelessEnabled(this))
            }
        }
        if (CarPlayBackgroundSession.hasSession()) {
            applyWithReconnectConfirm(title, message, onApplied = {}, save = commit)
        } else {
            commit()
        }
    }

    private fun saveConnectionMethod(draft: NewUiConnectionMethodDraft) {
        val title = getString(R.string.new_ui_connection_method_sheet_title)
        val message = getString(R.string.new_ui_connection_method_reconnect_hint)
        val commit = {
            when (val result = NewUiConnectionMethodActions.save(this, draft)) {
                is NewUiConnectionMethodActions.SaveResult.Success -> {
                    connectionActions.onConnectionMethodSaved()
                    popConnectionSubpage()
                    Toast.makeText(this, R.string.saved_for_your_next_connection, Toast.LENGTH_LONG).show()
                    refreshAll()
                    if (CarPlayBackgroundSession.hasSession()) {
                        connectionActions.openCarPlay()
                    }
                }
                is NewUiConnectionMethodActions.SaveResult.ValidationError -> {
                    Toast.makeText(this, result.messageRes, Toast.LENGTH_LONG).show()
                }
            }
        }
        if (CarPlayBackgroundSession.hasSession()) {
            applyWithReconnectConfirm(title, message, onApplied = {}, save = commit)
        } else {
            commit()
        }
    }

    private fun openAppPermissions() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    private fun previewAudioChannel(channel: Int, navigation: Boolean) {
        if (audioChannelPreview == null) {
            audioChannelPreview = AudioChannelPreview { unavailable ->
                Toast.makeText(
                    this,
                    getString(R.string.contrib_audio_home_channel_preview_unavailable, unavailable),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        audioChannelPreview?.play(channel, navigation)
    }

    override fun onDestroy() {
        clusterOutputWaitTask?.let(handler::removeCallbacks)
        CarPlayHostSettingsBridge.unregisterStandalone(this, standaloneCluster)
        Hs5ClusterActivityOutput.close("cluster-only host destroyed", clusterOnlyHost)
        if (AirPlayPersistence.loadClusterOnlyMode(this)) {
            com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setClusterMapShown(false)
        }
        stopDiagnosticShareSession()
        audioChannelPreview?.close()
        audioChannelPreview = null
        super.onDestroy()
    }

    internal val currentDestinationForTests: NewUiDestination
        get() = destination

    companion object {
        const val EXTRA_CLUSTER_ONLY_BOOT = "com.shihab.diplay.CLUSTER_ONLY_BOOT"

        fun open(context: Context) {
            context.startActivity(Intent(context, NewUiActivity::class.java))
        }

        internal fun isLauncherIntent(intent: Intent): Boolean = DiPlayLauncher.isLauncherIntent(intent)
    }
}
