package com.shilapi.xcertplay

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.util.concurrent.CopyOnWriteArraySet
import java.io.ByteArrayOutputStream
import java.io.File

/** Read-only firmware DataSync reader, implemented from the working DataSyncProbe sample. */
internal object Hs5SystemMedia {
    const val SERVICE_PACKAGE = "ltd.qisi.datasyncservice"
    const val SERVICE_CLASS = "ltd.qisi.datasyncservice.DataSyncService"
    val uri: Uri = Uri.parse("content://ltd.qisi.datasync.datasyncProvider/domainValue_table")
    const val PROVIDER_DESCRIPTOR = "ltd.qisi.datasyncmanager.IDataSyncProvider"
    const val LISTENER_DESCRIPTOR = "ltd.qisi.datasyncmanager.listener.IDataSyncListener"
    const val DISPLAY_ID = 0
    val keys = listOf("media_name", "media_artist", "media_ablum", "media_source", "media_playstate",
        "media_cover",
        "navigation_status", "navi_current_state", "navi_start", "navi_target", "navi_info_distance",
        "navi_info_time", "navi_arrive_time", "navi_arrive_time_day_num", "navi_tbt_info")
    @Volatile private var music = ClusterDashboardMusic()
    @Volatile private var guidance = com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot()
    @Volatile private var diagnostic = "not-started"
    private val observers = CopyOnWriteArraySet<() -> Unit>()
    private var reader: Reader? = null

    fun snapshot() = music
    fun guidanceSnapshot() = guidance
    fun diagnosticStatus() = diagnostic

    @Synchronized fun subscribe(context: Context, changed: () -> Unit): () -> Unit {
        val observer = { changed() } // unique ownership even when the same callback is subscribed twice
        observers.add(observer)
        if (reader == null && AirPlayPersistence.loadClusterOnlyMode(context)) {
            val next = Reader(context.applicationContext)
            reader = next
            next.start()
        }
        return { unsubscribe(observer) }
    }

    @Synchronized private fun unsubscribe(observer: () -> Unit) {
        observers.remove(observer)
        if (observers.isEmpty()) {
            reader?.stop(); reader = null
            music = ClusterDashboardMusic()
            guidance = com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot()
            diagnostic = "stopped"
        }
    }

    private class Reader(private val context: Context) {
        private val thread = HandlerThread("hs5-system-media").apply { start() }
        private val worker = Handler(thread.looper)
        private val cancel = CancellationSignal()
        @Volatile private var stopped = false
        private var contentObserverRegistered = false
        private var listenersRegistered = false
        private var serviceBound = false
        private var provider: DataSyncProvider? = null
        private val values = linkedMapOf<String, DataSyncValue>()
        private var lastCoverValue: String? = null
        private var coverBitmap: Bitmap? = null
        private val listener = object : Binder() {
            init { attachInterface(null, LISTENER_DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) { reply?.writeString(LISTENER_DESCRIPTOR); return true }
                if (code != 1) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(LISTENER_DESCRIPTOR)
                data.readInt() // display id
                val key = data.readString() ?: return true
                val value = if (data.readInt() != 0) DataSyncValue.read(data) else null
                if (value != null && value.resultCode == 0 && key in keys) worker.post {
                    if (!stopped) {
                        values[key] = value
                        publishDataSync("push")
                    }
                }
                reply?.writeNoException()
                return true
            }
        }
        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                worker.post {
                    if (stopped || binder == null) return@post
                    try {
                        val connectedProvider = DataSyncProvider(binder)
                        provider = connectedProvider
                        runCatching { connectedProvider.registerPackageLabel(context.packageName,
                            context.getString(com.shilapi.xcertplay.host.R.string.app_name)) }
                        val registeredCount = keys.count { key ->
                            runCatching { connectedProvider.register(DISPLAY_ID, key, listener) }.isSuccess
                        }
                        listenersRegistered = registeredCount > 0
                        status("datasync-bound;subscribed=$registeredCount/${keys.size}")
                        refresh.run()
                    } catch (error: Exception) {
                        provider = null
                        listenersRegistered = false
                        status("datasync-unavailable:${error.javaClass.simpleName}")
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                worker.post {
                    provider = null
                    listenersRegistered = false
                    values.clear()
                    status("datasync-disconnected")
                    publish(ClusterDashboardMusic(), com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot())
                }
            }
            override fun onBindingDied(name: ComponentName?) { lostBinding("datasync-binding-died") }
            override fun onNullBinding(name: ComponentName?) { lostBinding("datasync-null-binding") }
            private fun lostBinding(message: String) {
                worker.post {
                    provider = null
                    listenersRegistered = false
                    values.clear()
                    if (serviceBound) runCatching { context.unbindService(this) }
                    serviceBound = false
                    status(message)
                    publish(ClusterDashboardMusic(), com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot())
                    worker.removeCallbacks(refresh)
                    worker.post(refresh)
                }
            }
        }
        private var lastStatus: String? = null
        private val observer: ContentObserver = object : ContentObserver(worker) {
            override fun onChange(selfChange: Boolean) {
                worker.removeCallbacks(refresh)
                worker.postDelayed(refresh, 50)
            }
        }
        private val refresh: Runnable = object : Runnable {
            override fun run() {
                if (stopped) return
                if (!AirPlayPersistence.loadClusterOnlyMode(context)) {
                    publish(ClusterDashboardMusic(), Hs5SystemMediaColumns.Result().guidance); return
                }
                var retry = 2_000L
                try {
                    if (!serviceBound) bindDataSync()
                    val activeProvider = provider
                    if (activeProvider != null) {
                        keys.forEach { key ->
                            runCatching { activeProvider.query(DISPLAY_ID, key) }
                                .getOrNull()?.takeIf { it.resultCode == 0 }?.let { values[key] = it }
                        }
                        publishDataSync("query")
                    } else {
                        if (!contentObserverRegistered) {
                            // Provider fallback for firmware builds without the working DataSync Binder.
                            contentObserverRegistered = runCatching {
                            context.contentResolver.registerContentObserver(uri, true, observer); true
                            }.getOrDefault(false)
                        }
                        val result = context.contentResolver.query(uri, null, null, null, null, cancel)?.use {
                            Hs5SystemMediaColumns.read(it)
                        }
                        status(result?.status ?: "no-cursor")
                        publish(ClusterDashboardMusic(result?.metadata ?: CarPlayNowPlaying()),
                            result?.guidance ?: com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot())
                    }
                } catch (error: Exception) {
                    if (!stopped) {
                        status("unavailable:${error.javaClass.simpleName}")
                        publish(ClusterDashboardMusic(), com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot())
                    }
                    retry = 5_000L
                }
                if (!stopped) { worker.removeCallbacks(this); worker.postDelayed(this, retry) }
            }
        }
        private fun publish(value: ClusterDashboardMusic, nextGuidance: com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot) = synchronized(Hs5SystemMedia) {
            if (!stopped && reader === this && (music != value || guidance != nextGuidance)) {
                music = value
                guidance = nextGuidance
                observers.forEach { runCatching { it() } }
            }
        }
        private fun status(value: String) {
            if (value == lastStatus) return
            lastStatus = value
            diagnostic = value
            // Structure/availability only: never log song names, values, or provider exception messages.
            DiagnosticLogging.logcat { android.util.Log.i("DiPlay-SystemMedia", value) }
        }
        private fun publishDataSync(source: String) {
            val decoded = Hs5SystemMediaColumns.readDataSync(values.mapValues { it.value.value })
            val coverValue = values["media_cover"]?.value
            if (coverValue != lastCoverValue) {
                lastCoverValue = coverValue
                coverBitmap = coverValue?.let { decodeCover(context, it) }
                status("datasync-cover=${if (coverBitmap != null) "available" else "unavailable"}")
            }
            status("datasync-$source:${decoded.status}")
            publish(ClusterDashboardMusic(decoded.metadata, coverBitmap), decoded.guidance)
        }
        fun start() {
            worker.post {
                bindDataSync()
                refresh.run()
            }
        }
        private fun bindDataSync() {
            if (stopped || serviceBound || provider != null) return
            val intent = Intent().setComponent(ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
            serviceBound = runCatching { context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE) }
                .onFailure { status("datasync-bind-error:${it.javaClass.simpleName}") }
                .getOrDefault(false)
            if (!serviceBound) status("datasync-bind-rejected")
        }
        fun stop() {
            stopped = true
            cancel.cancel()
            worker.removeCallbacksAndMessages(null)
            worker.post {
                if (contentObserverRegistered) runCatching { context.contentResolver.unregisterContentObserver(observer) }
                provider?.let { p -> if (listenersRegistered) keys.forEach { key -> runCatching { p.unregister(DISPLAY_ID, key, listener) } } }
                provider = null
                if (serviceBound) runCatching { context.unbindService(serviceConnection) }
                serviceBound = false
                thread.quitSafely()
            }
        }
    }

    private data class DataSyncValue(val resultCode: Int, val value: String?, val type: Int) {
        companion object { fun read(parcel: Parcel) = DataSyncValue(parcel.readInt(), parcel.readString(), parcel.readInt()) }
    }

    private class DataSyncProvider(private val remote: IBinder) {
        fun query(displayId: Int, key: String): DataSyncValue? = transact(1) { data ->
            data.writeInt(displayId); data.writeString(key)
        }.let { reply -> try { if (reply.readInt() != 0) DataSyncValue.read(reply) else null } finally { reply.recycle() } }
        fun registerPackageLabel(packageName: String, label: String) { transact(18) { it.writeString(packageName); it.writeString(label) }.recycle() }
        fun register(displayId: Int, key: String, listener: IBinder): Int = transact(4) {
            it.writeInt(displayId); it.writeString(key); it.writeStrongBinder(listener)
        }.let { reply -> reply.readInt().also { reply.recycle() } }
        fun unregister(displayId: Int, key: String, listener: IBinder) {
            transact(5) { it.writeInt(displayId); it.writeString(key); it.writeStrongBinder(listener) }.recycle()
        }
        private inline fun transact(code: Int, write: (Parcel) -> Unit): Parcel {
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PROVIDER_DESCRIPTOR); write(data)
                if (!remote.transact(code, data, reply, 0)) throw RemoteException("DataSync transaction $code rejected")
                reply.readException()
                return reply
            } catch (error: Exception) {
                data.recycle(); reply.recycle(); throw error
            } finally { data.recycle() }
        }
    }
}

private fun decodeCover(context: Context, raw: String): Bitmap? {
    val value = runCatching {
        if (raw.trimStart().startsWith("{")) {
            val json = org.json.JSONObject(raw)
            listOf("uri", "path", "url", "data", "base64", "image", "cover").firstNotNullOfOrNull { key ->
                json.optString(key).takeIf { it.isNotBlank() && it != "null" }
            } ?: raw
        } else raw
    }.getOrDefault(raw).trim()
    val bytes = when {
        value.startsWith("data:image", ignoreCase = true) -> runCatching {
            android.util.Base64.decode(value.substringAfter("base64,"), android.util.Base64.DEFAULT)
        }.getOrNull()
        value.startsWith("content://", ignoreCase = true) || value.startsWith("file://", ignoreCase = true) ->
            runCatching { context.contentResolver.openInputStream(Uri.parse(value))?.use(::readCoverBytes) }.getOrNull()
        value.startsWith("/") -> runCatching { File(value).inputStream().use(::readCoverBytes) }.getOrNull()
        value.length > 128 -> runCatching { android.util.Base64.decode(value, android.util.Base64.DEFAULT) }.getOrNull()
        else -> null
    } ?: return null
    if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private fun readCoverBytes(input: java.io.InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() + count > MAX_COVER_BYTES) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MAX_COVER_BYTES = 8 * 1024 * 1024

/** Column names are inspected at runtime. Recognized layouts are candidates, not a claimed SDK ABI. */
internal object Hs5SystemMediaColumns {
    private val musicKeys = setOf("media_name", "media_artist", "media_ablum", "media_source", "media_playstate")
    private val navigationKeys = setOf("navigation_status", "navi_current_state", "navi_start", "navi_target",
        "navi_info_distance", "navi_info_time", "navi_arrive_time", "navi_arrive_time_day_num", "navi_tbt_info")
    private val keys = musicKeys + navigationKeys
    private val mediaSources = mapOf("0" to "FM", "1" to "AM", "2" to "USB1", "3" to "USB2",
        "4" to "蓝牙", "5" to "本地", "6" to "网络电台", "7" to "酷我", "8" to "资讯",
        "9" to "在线视频", "10" to "本地视频", "11" to "USB1视频", "12" to "USB2视频", "20" to "无音源")
    data class Result(
        val metadata: CarPlayNowPlaying = CarPlayNowPlaying(),
        val status: String = "empty",
        val guidance: com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot = com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot(),
    )
    fun read(cursor: Cursor): Result {
        val columns = cursor.columnNames.toList()
        val schema = columns.take(24).joinToString(",") { it.take(64).replace(Regex("[^a-zA-Z0-9_]"), "?") }
        // Qisi's optional Provider helper appends displayId (default hint: 0) to stored keys.
        // Accept both forms; the live SDK's Binder API, when available, uses bare keys.
        val columnKeys = keys.associateWith { key -> listOf(key, "${key}0").firstOrNull { it in columns } }
        val wide = columnKeys.values.filterNotNull()
        val pairs = listOf("key" to "value", "name" to "value", "domain" to "value",
            "domainKey" to "domainValue", "domain_name" to "domain_value")
            .filter { (key, value) -> key in columns && value in columns }
        if (wide.isEmpty() && pairs.size != 1) return Result(status = "unsupported-columns:$schema")
        val values = mutableMapOf<String, String?>()
        fun accept(key: String, value: String?): Boolean {
            if (values.containsKey(key) && values[key] != value) return false // no arbitrary source selection
            values[key] = value
            return true
        }
        var rows = 0
        while (cursor.moveToNext()) {
            if (++rows > 512) return Result(status = "row-limit")
            if (wide.isNotEmpty()) {
                for ((key, column) in columnKeys) if (column != null && !accept(key, cursor.getString(cursor.getColumnIndexOrThrow(column))?.take(2048)))
                    return Result(status = "ambiguous-media-rows")
            } else {
                val (keyColumn, valueColumn) = pairs.single()
                val key = cursor.getString(cursor.getColumnIndexOrThrow(keyColumn))
                val normalizedKey = keys.firstOrNull { key == it || key == "${it}0" }
                if (normalizedKey != null && !accept(normalizedKey, cursor.getString(cursor.getColumnIndexOrThrow(valueColumn))?.take(2048)))
                    return Result(status = "ambiguous-media-rows")
            }
        }
        // Numeric playback enums are unknown; do not invent a 0/1 mapping.
        val playing = values["media_playstate"]?.trim()?.lowercase(java.util.Locale.ROOT) in setOf("playing", "play", "true")
        val source = values["media_source"]?.let { mediaSources[it.trim()] ?: it }
        val metadata = CarPlayNowPlaying(title = values["media_name"], artist = values["media_artist"],
            album = values["media_ablum"], sourceApp = source, playing = playing)
        val destination = values["navi_target"]?.trim()?.let(::navigationName).orEmpty()
        val distance = values["navi_info_distance"]?.trim().orEmpty()
        val duration = values["navi_info_time"]?.trim().orEmpty()
        val arrival = values["navi_arrive_time"]?.trim().orEmpty()
        val dayCount = values["navi_arrive_time_day_num"]?.trim().orEmpty()
        val arrivalText = listOf(dayCount.takeIf { it.isNotEmpty() }, arrival.takeIf { it.isNotEmpty() })
            .filterNotNull().joinToString(" ")
        val summary = listOfNotNull(
            destination.takeIf { it.isNotEmpty() }?.let { "目的地 ${compactDestination(it)}" },
            distance.takeIf { it.isNotEmpty() }?.let { "距离 ${it.take(80)}" },
            duration.takeIf { it.isNotEmpty() }?.let { "用时 ${it.take(80)}" },
            arrivalText.takeIf { it.isNotEmpty() }?.let { "到达 ${it.take(80)}" },
        ).joinToString(" · ").take(320)
        val hasRoute = summary.isNotEmpty()
        val guidance = com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot(
            connected = hasRoute, navigating = hasRoute, routeSummary = summary)
        val recognizedKeys = values.keys.sorted().joinToString(",").ifBlank { "none" }
        return Result(metadata, "read:${values.size}-fields;keys=$recognizedKeys;columns:$schema", guidance)
    }

    fun readDataSync(raw: Map<String, String?>): Result {
        val values = raw.filterKeys { it in keys }.mapValues { it.value?.trim()?.take(2048) }
        val playing = when (values["media_playstate"]?.lowercase(java.util.Locale.ROOT)) {
            "playing", "play", "true" -> true
            else -> false
        }
        val source = values["media_source"]?.let { mediaSources[it] ?: it }
        val metadata = CarPlayNowPlaying(
            title = values["media_name"].blankToNull(), artist = values["media_artist"].blankToNull(),
            album = values["media_ablum"].blankToNull(), sourceApp = source.blankToNull(), playing = playing,
        )
        val target = values["navi_target"].blankToNull()?.let(::parseNavigationObject)
        val tbt = values["navi_tbt_info"].blankToNull()?.let(::parseNavigationObject)
        val destination = target?.name ?: values["navi_target"].blankToNull()?.let(::navigationName)
        val distance = values["navi_info_distance"].blankToNull() ?: target?.distance
        val duration = values["navi_info_time"].blankToNull() ?: target?.duration
        val arrival = listOfNotNull(values["navi_arrive_time_day_num"].blankToNull(),
            values["navi_arrive_time"].blankToNull()).joinToString(" ").ifBlank { null }
        val maneuver = tbt?.maneuver
        val road = tbt?.road.orEmpty()
        val state = values["navigation_status"]?.toIntOrNull()
        val currentState = values["navi_current_state"]?.toIntOrNull()
        val active = (state != null && state > 0) || (currentState != null && currentState > 0)
        val summary = listOfNotNull(
            destination?.let { "目的地 ${compactDestination(it)}" },
            distance?.let { "距离 ${it.take(80)}" },
            duration?.let { "用时 ${it.take(80)}" },
            arrival?.let { "到达 ${it.take(80)}" },
            road.takeIf { it.isNotBlank() }?.let { "道路 $it" },
        ).joinToString(" · ").take(320)
        val guidance = com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot(
            connected = active, navigating = active && summary.isNotEmpty(), routeSummary = summary,
            maneuverType = maneuver,
        )
        return Result(metadata, "read:${values.size}-fields;keys=${values.keys.sorted().joinToString(",")}", guidance)
    }

    private fun String?.blankToNull() = this?.takeIf { it.isNotBlank() && it.lowercase(java.util.Locale.ROOT) != "null" }

    private data class NavigationObject(val name: String? = null, val distance: String? = null,
        val duration: String? = null, val road: String? = null, val maneuver: Int? = null)

    private fun parseNavigationObject(value: String): NavigationObject {
        val nameKeys = setOf("name", "target", "destination", "destName")
        val distanceKeys = setOf("distance", "remainDistance", "remainingDistance")
        val durationKeys = setOf("time", "duration", "remainTime", "remainingTime")
        val roadKeys = setOf("road", "roadName", "currentRoad", "nextRoad")
        val maneuverKeys = setOf("maneuverType", "turnType", "action", "direction")
        var name: String? = null; var distance: String? = null; var duration: String? = null
        var road: String? = null; var maneuver: Int? = null
        fun scan(item: org.json.JSONObject, depth: Int) {
            if (depth > 5) return
            val keys = item.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val valueAtKey = item.opt(key)
                if (valueAtKey is org.json.JSONObject) scan(valueAtKey, depth + 1)
                else if (valueAtKey is org.json.JSONArray) {
                    for (i in 0 until minOf(valueAtKey.length(), 32))
                        valueAtKey.optJSONObject(i)?.let { scan(it, depth + 1) }
                } else {
                    val text = valueAtKey?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
                    when (key) {
                        in nameKeys -> if (name == null) name = text
                        in distanceKeys -> if (distance == null) distance = text
                        in durationKeys -> if (duration == null) duration = text
                        in roadKeys -> if (road == null) road = text
                        in maneuverKeys -> if (maneuver == null) maneuver = text.toIntOrNull()
                    }
                }
            }
        }
        runCatching { scan(org.json.JSONObject(value), 0) }
        return NavigationObject(name, distance, duration, road, maneuver)
    }

    private fun navigationName(value: String): String = parseNavigationObject(value).name ?: value

    private fun compactDestination(value: String, limit: Int = 10): String {
        val points = value.codePoints().toArray()
        if (points.size <= limit) return value
        return String(points, 0, limit) + "…"
    }
}

/** One source decision shared by all three instruments, independent of their visual style. */
internal object ClusterInstrumentData {
    fun music(context: Context) = if (AirPlayPersistence.loadClusterOnlyMode(context))
        Hs5SystemMedia.snapshot() else CarPlayMediaKeys.dashboardMusic()
    fun guidance(context: Context) = if (AirPlayPersistence.loadClusterOnlyMode(context))
        Hs5SystemMedia.guidanceSnapshot()
        else com.shilapi.xcertplay.glance.CarPlayGlance.snapshot()
    fun subscribe(context: Context, changed: () -> Unit): () -> Unit =
        if (AirPlayPersistence.loadClusterOnlyMode(context)) Hs5SystemMedia.subscribe(context, changed)
        else {
            val navigation = com.shilapi.xcertplay.glance.CarPlayGlance.subscribe { changed() }
            val music = CarPlayMediaKeys.subscribeDashboard(changed)
            val unsubscribe: () -> Unit = { navigation(); music() }
            unsubscribe
        }
}
