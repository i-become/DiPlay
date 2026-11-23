package com.shilapi.xcertplay.carlink

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import com.shilapi.xcertplay.ClusterCopyRegion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The decoder always consumes frames, including when the centre window has been hidden.
 * EGL and readback belong exclusively to this thread; the UI never screenshots a TextureView.
 */
internal class CarlinkVideoRenderer(
    private val width: Int,
    private val height: Int,
    private val onReady: (Surface) -> Unit,
    private val onFrame: (Bitmap) -> Unit,
    private val log: (String) -> Unit,
) {
    private val thread = HandlerThread("CarlinkVideo").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val deliveryPending = AtomicBoolean(false)
    private val outputGeneration = AtomicInteger()
    private var boundGeneration = -1
    private var hasFrame = false
    @Volatile private var region: ClusterCopyRegion? = null
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var offscreen = EGL14.EGL_NO_SURFACE
    private var window = EGL14.EGL_NO_SURFACE
    private var windowWidth = width
    private var windowHeight = height
    private var texture: SurfaceTexture? = null
    private var decoder: Surface? = null
    private var textureId = 0
    private var program = 0
    private val transform = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }
    private var pixels: ByteBuffer? = null
    // Three frames prevent overwriting the bitmap still used by a UI render node.
    private val frames = arrayOfNulls<Bitmap>(3)
    private var frameIndex = 0
    private var lastCopy = 0L
    private var statsSince = SystemClock.elapsedRealtime()
    private var received = 0
    private var copied = 0
    private var readbackMs = 0L
    private var failureReported = false

    fun start() { worker.post { guarded { initialize() } } }

    /** Takes ownership of the wrapper Surface, not of its TextureView's SurfaceTexture. */
    fun attach(surface: Surface?, width: Int = this.width, height: Int = this.height) {
        // Invalidate the old output immediately, before queued frame callbacks can draw it.
        val generation = outputGeneration.incrementAndGet()
        worker.post {
            try {
                if (!closed.get() && generation == outputGeneration.get() && display != EGL14.EGL_NO_DISPLAY) guarded {
                    makeCurrent(offscreen)
                    if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
                    window = EGL14.EGL_NO_SURFACE
                    if (surface != null && surface.isValid) {
                        window = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
                        check(window != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface" }
                        windowWidth = width; windowHeight = height
                        boundGeneration = generation
                        // The phone may not emit a new frame while its screen is static.
                        if (hasFrame) {
                            makeCurrent(window); draw(windowWidth, windowHeight, false)
                            EGL14.eglSwapBuffers(display, window)
                        }
                    }
                }
            } finally { surface?.release() }
        }
    }

    fun setCopyRegion(value: ClusterCopyRegion?) { region = value?.clamped() }

    private fun initialize() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0)
        config = configs[0]
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT)
        offscreen = EGL14.eglCreatePbufferSurface(display, config,
            intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
        check(offscreen != EGL14.EGL_NO_SURFACE)
        makeCurrent(offscreen)
        program = GLES20.glCreateProgram()
        val vertex = shader(GLES20.GL_VERTEX_SHADER, """
            attribute vec4 position; attribute vec2 uv; uniform mat4 transform;
            uniform float flip; varying vec2 sampleUv;
            void main() { gl_Position=position;
              sampleUv=(transform*vec4(uv.x,mix(uv.y,1.0-uv.y,flip),0.0,1.0)).xy; }
        """.trimIndent())
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER, """
            #extension GL_OES_EGL_image_external : require
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform samplerExternalOES video; varying vec2 sampleUv;
            void main() { gl_FragColor=texture2D(video,sampleUv); }
        """.trimIndent())
        GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); textureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        texture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener({ if (!closed.get()) guarded { renderFrame() } }, worker)
        }
        decoder = Surface(texture)
        log("persistent decoder ready native=${width}x$height worker=CarlinkVideo")
        main.post { if (!closed.get()) onReady(checkNotNull(decoder)) }
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
        val compiled = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] != 0) { GLES20.glGetShaderInfoLog(id) }
        return id
    }

    private fun makeCurrent(target: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, target, target, context)) { "eglMakeCurrent" }
    }

    private fun draw(width: Int, height: Int, flip: Boolean) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "video"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "flip"), if (flip) 1f else 0f)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "transform"), 1, false, transform, 0)
        val position = GLES20.glGetAttribLocation(program, "position")
        val uv = GLES20.glGetAttribLocation(program, "uv")
        vertices.position(0); GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun renderFrame() {
        makeCurrent(offscreen)
        texture?.updateTexImage(); texture?.getTransformMatrix(transform)
        hasFrame = true
        received++
        if (window != EGL14.EGL_NO_SURFACE && boundGeneration == outputGeneration.get()) {
            makeCurrent(window); draw(windowWidth, windowHeight, false)
            if (!EGL14.eglSwapBuffers(display, window)) {
                makeCurrent(offscreen)
                EGL14.eglDestroySurface(display, window); window = EGL14.EGL_NO_SURFACE
                log("centre output lost; decoder and cluster retained")
            }
        }
        val now = SystemClock.elapsedRealtime()
        val selected = region
        if (selected != null && now - lastCopy >= 50 && !deliveryPending.get()) {
            lastCopy = now
            makeCurrent(offscreen); draw(width, height, true)
            val rect = CarlinkFrameCopy.pixelBounds(width, height, selected)
            val bytes = rect.width() * rect.height() * 4
            val buffer = pixels?.takeIf { it.capacity() == bytes }
                ?: ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).also { pixels = it }
            buffer.clear()
            GLES20.glReadPixels(rect.left, rect.top, rect.width(), rect.height(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "readback failed" }
            val index = frameIndex++ % frames.size
            val bitmap = frames[index]?.takeIf { it.width == rect.width() && it.height == rect.height() }
                ?: Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888).also { frames[index] = it }
            buffer.rewind(); bitmap.copyPixelsFromBuffer(buffer)
            readbackMs += SystemClock.elapsedRealtime() - now; copied++
            deliveryPending.set(true)
            main.post {
                try { if (!closed.get() && region != null) onFrame(bitmap) }
                finally { deliveryPending.set(false) }
            }
        }
        if (now - statsSince >= 10000) {
            log("video intervalMs=${now - statsSince} frames=$received copies=$copied readbackMs=$readbackMs centre=${window != EGL14.EGL_NO_SURFACE} native=${width}x$height region=$selected")
            received = 0; copied = 0; readbackMs = 0; statsSince = now
        }
    }

    private inline fun guarded(block: () -> Unit) {
        if (closed.get()) return
        try { block() } catch (error: Exception) {
            if (!failureReported) log("video error ${error.javaClass.simpleName}: ${error.message}")
            failureReported = true
        }
    }

    /** Called only after the SDK has detached from the decoder Surface. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.post {
            try {
                texture?.setOnFrameAvailableListener(null)
                decoder?.release(); texture?.release()
                if (display != EGL14.EGL_NO_DISPLAY) {
                    if (context != EGL14.EGL_NO_CONTEXT && offscreen != EGL14.EGL_NO_SURFACE) {
                        makeCurrent(offscreen)
                        GLES20.glDeleteProgram(program); GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                    }
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
                    if (offscreen != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, offscreen)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display); EGL14.eglReleaseThread()
                }
            } finally { thread.quitSafely() }
        }
    }
}
