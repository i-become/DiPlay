package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.util.concurrent.CopyOnWriteArraySet

/** CarLife's documented, opt-in broadcast feed for standalone instrument rendering. */
internal object CarLifeInstrumentData {
    const val MEDIA_ACTION = "com.muyetuge.carlifevehicle.export.MEDIA"
    const val NAVIGATION_ACTION = "com.muyetuge.carlifevehicle.export.NAVIGATION"
    const val SOURCE_PACKAGE = "com.muyetuge.carlifevehicle"

    data class Snapshot(
        val music: ClusterDashboardMusic = ClusterDashboardMusic(),
        val guidance: CarPlayGlance.Snapshot = CarPlayGlance.Snapshot(),
    )

    @Volatile private var current = Snapshot()
    private val observers = CopyOnWriteArraySet<() -> Unit>()
    private var receiverContext: Context? = null

    fun snapshot(): Snapshot = current

    @Synchronized fun subscribe(context: Context, changed: () -> Unit): () -> Unit {
        observers.add(changed)
        if (receiverContext == null) register(context.applicationContext)
        return { unsubscribe(changed) }
    }

    @Synchronized private fun unsubscribe(changed: () -> Unit) {
        observers.remove(changed)
        if (observers.isEmpty()) {
            receiverContext?.let { runCatching { it.unregisterReceiver(receiver) } }
            receiverContext = null
            current = Snapshot()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("sourcePackage") != SOURCE_PACKAGE) return
            when (intent.action) {
                MEDIA_ACTION -> receiveMedia(intent)
                NAVIGATION_ACTION -> receiveNavigation(intent)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(MEDIA_ACTION)
            addAction(NAVIGATION_ACTION)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            receiverContext = context
            DiagnosticLogging.logcat { android.util.Log.i("DiPlay-CarLife", "broadcast receiver registered") }
        } catch (error: Exception) {
            DiagnosticLogging.logcat { android.util.Log.w("DiPlay-CarLife", "broadcast receiver registration failed", error) }
        }
    }

    @Synchronized private fun receiveMedia(intent: Intent) {
        when (intent.getStringExtra("event")) {
            "metadata" -> {
                val old = current.music.metadata
                val duration = intent.getIntExtra("durationMs", 0).takeIf { it > 0 }?.toLong()
                val metadata = old.copy(
                    title = intent.getStringExtra("title").nonBlank(),
                    artist = intent.getStringExtra("artist").nonBlank(),
                    album = intent.getStringExtra("album").nonBlank(),
                    sourceApp = intent.getStringExtra("source").nonBlank(),
                    durationMillis = duration,
                )
                publish(current.copy(music = ClusterDashboardMusic(metadata, current.music.artwork)))
            }
            "playback" -> {
                val old = current.music.metadata
                val metadata = old.copy(
                    playing = intent.getBooleanExtra("isPlaying", false),
                    elapsedMillis = intent.getLongExtra("positionMs", 0L).coerceAtLeast(0L),
                )
                publish(current.copy(music = ClusterDashboardMusic(metadata, current.music.artwork)))
            }
        }
    }

    @Synchronized private fun receiveNavigation(intent: Intent) {
        if (intent.getStringExtra("event") != "update") return
        val actionCode = intent.getIntExtra("actionCode", -1)
        val nextTurn = intent.getIntExtra("nextTurn", -1)
        val distance = intent.getIntExtra("remainDistanceMeters", 0).coerceAtLeast(0)
        val totalDistance = intent.getIntExtra("totalDistanceMeters", 0).coerceAtLeast(0)
        val road = intent.getStringExtra("roadName").nonBlank().orEmpty()
        val turnIcon = intent.getByteArrayExtra("turnIconBytes")?.takeIf { it.size in 1..262_144 }?.copyOf()
        // Keep the vendor enum values explicit in the summary. Their mapping is not part of
        // the broadcast contract, so do not reinterpret them as CarPlay maneuver values.
        val details = buildList {
            if (nextTurn >= 0) add("CarLife 转向 $nextTurn")
            if (road.isNotBlank()) add(road)
            if (distance > 0) add(if (distance >= 1_000) "${"%.1f".format(java.util.Locale.ROOT, distance / 1_000f)} km" else "$distance m")
            if (totalDistance > 0) add("总程 ${if (totalDistance >= 1_000) "${"%.1f".format(java.util.Locale.ROOT, totalDistance / 1_000f)} km" else "$totalDistance m"}")
        }
        val guidance = CarPlayGlance.Snapshot(
            connected = true,
            navigating = details.isNotEmpty(),
            distanceMeters = distance,
            road = road,
            directionIcon = turnIcon,
            routeSummary = details.joinToString(" · ").take(320),
        )
        DiagnosticLogging.logcat {
            android.util.Log.i("DiPlay-CarLife", "navigation update action=$actionCode turn=$nextTurn distance=$distance")
        }
        publish(current.copy(guidance = guidance))
    }

    private fun publish(next: Snapshot) {
        if (next == current) return
        current = next
        observers.forEach { runCatching(it) }
    }

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() && it != "null" }
}
