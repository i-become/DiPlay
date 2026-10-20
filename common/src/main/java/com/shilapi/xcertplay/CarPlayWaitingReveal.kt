package com.shilapi.xcertplay



import android.graphics.SurfaceTexture

import android.media.MediaPlayer

import android.net.Uri

import android.view.Surface

import android.view.TextureView

import android.view.View

import android.widget.FrameLayout

import androidx.activity.ComponentActivity

import com.shilapi.xcertplay.host.R

import com.shilapi.xcertplay.newui.NewUiShellBackgroundViews



/**

 * After the main CarPlay video stream starts, fades the DiPlay shell and a short Hongqi flag clip

 * together so CarPlay appears underneath instead of an abrupt cut.

 *

 * Uses [TextureView] rather than [android.widget.VideoView] so the clip composites above CarPlay's

 * [android.view.TextureView] on head units where SurfaceView z-order would hide the effect.

 */

internal class CarPlayWaitingReveal(

    private val activity: ComponentActivity,

    overlay: FrameLayout,

    private val connectionPanel: View,

    private val onFinished: () -> Unit,

    private val onStarting: () -> Unit = {},

) {

    private val overlay: FrameLayout = overlay

    private val flagVideo = TextureView(activity).apply {

        alpha = 0f

        isOpaque = false

    }

    private var mediaPlayer: MediaPlayer? = null

    private var running = false

    private var pendingStart = false



    init {

        NewUiShellBackgroundViews.installInto(activity, overlay)

        overlay.addView(

            flagVideo,

            FrameLayout.LayoutParams(

                FrameLayout.LayoutParams.MATCH_PARENT,

                FrameLayout.LayoutParams.MATCH_PARENT,

            ),

        )

        overlay.visibility = View.VISIBLE

        overlay.alpha = 1f

        flagVideo.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {

                if (!pendingStart) return

                startPlaybackOn(surfaceTexture)

            }



            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit



            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {

                stopPlayback()

                return true

            }



            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

        }

    }



    val isRunning: Boolean get() = running



    fun resetForNewSession() {

        running = false

        pendingStart = false

        overlay.animate().cancel()

        flagVideo.animate().cancel()

        stopPlayback()

        flagVideo.alpha = 0f

        overlay.alpha = 0f

        overlay.visibility = View.GONE

        connectionPanel.visibility = View.GONE

    }



    /** Hides the waiting shell immediately when the main CarPlay video stream starts. */

    fun onMainCarPlayStreamActive(): Boolean {

        if (running) return false

        running = false

        pendingStart = false

        overlay.animate().cancel()

        flagVideo.animate().cancel()

        stopPlayback()

        flagVideo.alpha = 0f

        overlay.alpha = 0f

        overlay.visibility = View.GONE

        connectionPanel.visibility = View.GONE

        onFinished()

        return false

    }



    private fun startPlaybackOn(surfaceTexture: SurfaceTexture) {

        if (!running) return

        pendingStart = false

        stopPlayback()

        val uri = Uri.parse(

            "android.resource://${activity.packageName}/${R.raw.carplay_waiting_reveal}",

        )

        val player = MediaPlayer()

        mediaPlayer = player

        player.setSurface(Surface(surfaceTexture))

        player.setOnPreparedListener { prepared ->

            if (!running) return@setOnPreparedListener

            prepared.isLooping = false

            flagVideo.alpha = FLAG_VIDEO_PEAK_ALPHA

            prepared.start()

            overlay.animate()

                .alpha(0f)

                .setStartDelay(FADE_START_DELAY_MS)

                .setDuration(FADE_DURATION_MS)

                .withEndAction { finish() }

                .start()

            flagVideo.animate()

                .alpha(0f)

                .setStartDelay(FADE_START_DELAY_MS)

                .setDuration(FADE_DURATION_MS)

                .start()

        }

        player.setOnErrorListener { _, _, _ ->

            startShellFadeOnly()

            true

        }

        player.setOnCompletionListener {

            if (running) finish()

        }

        runCatching {

            player.setDataSource(activity, uri)

            player.prepareAsync()

        }.onFailure {

            startShellFadeOnly()

        }

    }

    /** Keeps the wallpaper fade when the clip cannot decode on this head unit. */
    private fun startShellFadeOnly() {
        if (!running) return
        stopPlayback()
        flagVideo.alpha = 0f
        overlay.animate()
            .alpha(0f)
            .setStartDelay(FADE_START_DELAY_MS)
            .setDuration(FADE_DURATION_MS)
            .withEndAction { finish() }
            .start()
    }



    private fun stopPlayback() {

        runCatching {

            mediaPlayer?.stop()

            mediaPlayer?.release()

        }

        mediaPlayer = null

    }



    private fun finish() {

        if (!running) return

        running = false

        pendingStart = false

        overlay.animate().cancel()

        flagVideo.animate().cancel()

        stopPlayback()

        flagVideo.alpha = 0f

        overlay.alpha = 0f

        overlay.visibility = View.GONE

        onFinished()

    }



    fun release() {

        overlay.animate().cancel()

        flagVideo.animate().cancel()

        stopPlayback()

        running = false

        pendingStart = false

    }



    private companion object {

        /** Peak opacity of the flag clip before the shared fade (0.5 = 50% opaque). */

        const val FLAG_VIDEO_PEAK_ALPHA = 0.5f

        const val FADE_START_DELAY_MS = 350L

        const val FADE_DURATION_MS = 2_800L

    }

}


