package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Read-only PowerController subscription. Uses the firmware's own complete Binder ABI. */
internal object HongqiScreenStatus {
    private const val MANAGER = "alfusos.systemstatus.ISystemStatusManager"
    private const val CALLBACK = "alfusos.systemstatus.ISystemStatusCallback"
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "hs5-screen-status").apply { isDaemon = true } }
    private var app: Context? = null
    private var connection: Connection? = null
    private var attempt = 0
    @Volatile var screenOff = false
        private set
    private var raw: Int? = null
    private val retry = Runnable { bind() }

    fun initialize(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { initialize(context) }; return }
        if (app != null) return
        app = context.applicationContext
        bind()
    }

    private fun bind() {
        val context = app ?: return
        if (connection != null) return
        val next = Connection()
        connection = next
        next.bound = runCatching {
            context.bindService(Intent(MANAGER).setComponent(ComponentName("com.alfus.controller",
                "com.alfus.controller.power.PowerController")), next, Context.BIND_AUTO_CREATE)
        }.onFailure { record("bind failed ${it.javaClass.simpleName}") }.getOrDefault(false)
        if (!next.bound) lost(next)
        else main.postDelayed({ if (connection === next && !next.ready) lost(next) }, 15_000L)
    }

    private fun lost(source: Connection) {
        if (connection !== source) return
        connection = null
        raw = null // Unknown is not evidence of a screen-on transition.
        if (source.bound) runCatching { app?.unbindService(source) }
        worker.execute { source.detach() }
        main.removeCallbacks(retry)
        main.postDelayed(retry, (5_000L shl attempt.coerceAtMost(4)).coerceAtMost(60_000L))
        attempt++
    }

    private fun accept(source: Connection, status: Int) {
        if (connection !== source || raw == status) return
        raw = status
        record("status=$status")
        when (offState(status)) {
            false -> screenOff = false
            true -> {
                screenOff = true
                ConnectionProgressUiState.markDisconnected()
                CarPlayHostSettingsBridge.closeFlyScreen("head unit screen/power off status=$status")
                com.shilapi.xcertplay.carlink.CarlinkBridge.stopIfStarted()
                CarPlayBackgroundSession.stop()
            }
            null -> Unit
        }
    }

    /** Legacy PowerController ABI: SCREEN_OFF=2, POWER_OFF=3, ACC_OFF=4.
     * Unknown/boot/error states must not release a previously observed power-off gate. */
    internal fun offState(status: Int): Boolean? = when (status) {
        2, 3, 4 -> true
        1, 5, 6, 12 -> false
        else -> null
    }

    private class Connection : ServiceConnection {
        var bound = false
        var ready = false
        private var remote: Any? = null
        private var callback: Any? = null
        private var callbackClass: Class<*>? = null
        private var binder: IBinder? = null
        private val version = AtomicLong()
        private val death = IBinder.DeathRecipient { main.post { lost(this) } }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (connection !== this || service == null) return
            binder = service
            worker.execute {
                runCatching {
                    require(service.interfaceDescriptor == MANAGER)
                    // No fabricated AIDL or hard-coded transactions: resolve matching boot framework.
                    val managerStub = Class.forName("$MANAGER\$Stub")
                    val managerType = Class.forName(MANAGER)
                    val type = Class.forName(CALLBACK)
                    val stub = Class.forName("$CALLBACK\$Stub")
                    val codes = listOf("onSystemStatus", "onDtcData", "onRebootReason").associateWith { method ->
                        stub.getDeclaredField("TRANSACTION_$method").apply { isAccessible = true }.getInt(null)
                    }
                    require(codes.values.toSet().size == 3)
                    val target = managerStub.getMethod("asInterface", IBinder::class.java).invoke(null, service)
                    val listener = object : Binder() {
                        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                            if (code == INTERFACE_TRANSACTION) { reply?.writeString(CALLBACK); return true }
                            if (code !in codes.values) return super.onTransact(code, data, reply, flags)
                            data.enforceInterface(CALLBACK)
                            if (code == codes.getValue("onSystemStatus")) {
                                val status = data.readInt()
                                version.incrementAndGet()
                                main.post { accept(this@Connection, status) }
                            } else data.createIntArray() // Complete callback contract; no payload logging.
                            reply?.writeNoException()
                            return true
                        }
                    }
                    val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
                        when (method.name) {
                            "asBinder" -> listener
                            "equals" -> instance === args?.firstOrNull()
                            "hashCode" -> System.identityHashCode(instance)
                            "toString" -> "DiPlayScreenStatusCallback"
                            else -> null
                        }
                    }
                    remote = target; callback = proxy; callbackClass = type
                    service.linkToDeath(death, 0)
                    val observed = version.get()
                    managerType.getMethod("attachCallback", type).invoke(target, proxy)
                    val initial = managerType.getMethod("getSystemStatus").invoke(target) as Int
                    main.post {
                        if (connection === this && version.get() == observed) accept(this, initial)
                        if (connection === this) { attempt = 0; ready = true }
                        else worker.execute { detach() }
                    }
                }.onFailure {
                    record("interface/registration unavailable ${it.javaClass.simpleName}")
                    main.post { lost(this) }
                }
            }
        }

        fun detach() {
            runCatching {
                val target = remote ?: return@runCatching
                Class.forName(MANAGER).getMethod("detachCallback", callbackClass!!).invoke(target, callback)
            }
            runCatching { binder?.unlinkToDeath(death, 0) }
            remote = null; callback = null; callbackClass = null; binder = null
        }

        override fun onServiceDisconnected(name: ComponentName?) = lost(this)
        override fun onBindingDied(name: ComponentName?) = lost(this)
        override fun onNullBinding(name: ComponentName?) = lost(this)
    }

    private fun record(message: String) = DiagnosticLogging.record {
        CarPlayStartupDiagnostics.capture("screen-status $message")
    }
}
