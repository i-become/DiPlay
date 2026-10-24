package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.orchestration.CarPlayStatus

/** Replaces Activity callbacks before that Activity finishes, without recreating the connection. */
internal class ProjectionBackgroundOwner private constructor(
    private val context: Context,
    private val snapshot: CarPlayBackgroundSession.Snapshot,
    private var active: AirPlaySession?,
) {
    private val main = Handler(Looper.getMainLooper())

    private fun owns(): Boolean = CarPlayBackgroundSession.isOwner(this) &&
        CarPlayBackgroundSession.snapshot()?.controller === snapshot.controller

    private val listener = object : AirPlaySessionListener {
        override fun onSessionActive(session: AirPlaySession) {
            main.post {
                if (owns()) {
                    active = session
                    CarPlayBackgroundSession.active = true
                }
            }
        }

        override fun onSessionEnded(session: AirPlaySession) {
            main.post { if (owns() && active === session) disconnected() }
        }

        override fun onTransportError(message: String) {
            main.post { if (owns()) disconnected() }
        }

        override fun onDebugLog(message: String) {
            DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("projection-background $message") }
        }
    }

    private fun disconnected() {
        ConnectionProgressUiState.markDisconnected()
        CarPlayBackgroundSession.stop()
    }

    private fun stop(completion: () -> Unit) {
        if (!owns()) { completion(); return }
        CarPlayBackgroundSession.clear(snapshot.controller, keepOwner = true)
        CarPlayMediaKeys.detach(snapshot.controller)
        snapshot.controller.close()
        Thread({
            try {
                runCatching { snapshot.controller.awaitClosed(8_000) }
                runCatching { snapshot.sink.close() }
            } finally {
                main.post {
                    if (CarPlayBackgroundSession.isOwner(this)) CarPlayBackgroundSession.clear()
                    if (CarPlayBackgroundSession.snapshot() == null) {
                        context.stopService(Intent(context, DiPlaySessionService::class.java))
                    }
                    completion()
                }
            }
        }, "diplay-projection-stop").apply { isDaemon = true; start() }
    }

    companion object {
        fun retain(context: Context, host: CarPlayHostActivity, active: AirPlaySession?): Boolean {
            val snapshot = CarPlayBackgroundSession.snapshot() ?: return false
            if (!CarPlayBackgroundSession.isOwner(host) || snapshot.controller.isClosed()) return false
            val owner = ProjectionBackgroundOwner(context.applicationContext, snapshot, active)
            CarPlayBackgroundSession.store(snapshot.controller, snapshot.sink, snapshot.width, snapshot.height,
                owner, snapshot.display) { completion -> owner.main.post { owner.stop(completion) } }
            snapshot.sink.setScreenStreamActiveChangedListener(null)
            snapshot.controller.attachUi(owner.listener) { status ->
                owner.main.post {
                    // A control-window timeout alone does not prove the active media link ended.
                    if (owner.owns() && status is CarPlayStatus.Failed) {
                        owner.disconnected()
                    }
                }
            }
            return true
        }
    }
}
