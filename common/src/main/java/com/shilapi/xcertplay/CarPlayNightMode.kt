package com.shilapi.xcertplay

import java.util.Calendar

/** Stable preference values; never persist enum ordinals. */
enum class CarPlayNightMode(val key: String) {
    SYSTEM("system"),
    AMBIENT("ambient"),
    DAY("day"),
    NIGHT("night"),
    SCHEDULE("schedule");

    companion object {
        fun fromKey(key: String?): CarPlayNightMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class CarPlayNightSchedule(val startMinute: Int = 18 * 60, val endMinute: Int = 6 * 60) {
    init {
        require(startMinute in 0 until 24 * 60 && endMinute in 0 until 24 * 60)
    }

    fun isNight(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

internal data class NightTimeSnapshot(val minuteOfDay: Int, val millisUntilNextMinute: Long)

internal interface NightTimeSource {
    fun snapshot(): NightTimeSnapshot
}

internal object SystemNightTimeSource : NightTimeSource {
    override fun snapshot(): NightTimeSnapshot = Calendar.getInstance().let {
        // Appearance and the next tick must use the same instant across a minute boundary.
        NightTimeSnapshot(
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE),
            60_000L - it.get(Calendar.SECOND) * 1_000L - it.get(Calendar.MILLISECOND),
        )
    }
}

/** Main-thread controller. Only its output is sent to CarPlay; Android theme is never changed. */
internal class CarPlayNightModeController(
    private val light: LightSource,
    private val scheduler: Scheduler,
    initialNight: Boolean,
    private val onNightChanged: (Boolean) -> Unit,
    private val timeSource: NightTimeSource = SystemNightTimeSource,
    private val vehicleLightEnabled: Boolean = false,
    initialVehicleNight: Boolean? = null,
) {
    interface LightSource {
        val available: Boolean
        fun start(onLux: (Float) -> Unit): Boolean
        fun stop()
    }

    interface Scheduler {
        fun postDelayed(task: Runnable, delayMillis: Long)
        fun remove(task: Runnable)
    }

    var night: Boolean = initialNight
        private set
    private var mode = CarPlayNightMode.SYSTEM
    private var systemNight = initialNight
    private var vehicleNight = initialVehicleNight
    private val automatic: Boolean
        get() = mode == CarPlayNightMode.SYSTEM || mode == CarPlayNightMode.AMBIENT
    private val ambientFallback: Boolean
        get() = mode == CarPlayNightMode.AMBIENT || (mode == CarPlayNightMode.SYSTEM && vehicleLightEnabled)
    private var threshold = AmbientLightThreshold()
    private var delaySeconds = 2
    private var schedule = CarPlayNightSchedule()
    private var resumed = false
    private var listening = false
    private var ambientKnown = false
    private var pending: Boolean? = null
    private val transition = Runnable {
        val target = pending
        pending = null
        if (resumed && listening && ambientFallback && vehicleNight == null && target != null) {
            applyNight(target)
        }
    }
    private val scheduleTick = object : Runnable {
        override fun run() {
            if (!resumed || mode != CarPlayNightMode.SCHEDULE) return
            applySchedule()
        }
    }

    fun configure(
        mode: CarPlayNightMode,
        systemNight: Boolean,
        threshold: AmbientLightThreshold = AmbientLightThreshold(),
        delaySeconds: Int = 2,
        schedule: CarPlayNightSchedule = CarPlayNightSchedule(),
    ) {
        stopListening()
        this.mode = mode
        this.systemNight = systemNight
        this.threshold = threshold
        this.delaySeconds = delaySeconds.coerceIn(0, 60)
        this.schedule = schedule
        applyMode()
    }

    fun resume(systemNight: Boolean) {
        this.systemNight = systemNight
        resumed = true
        applyMode()
    }

    fun pause() {
        resumed = false
        stopListening()
    }

    fun systemChanged(night: Boolean) {
        systemNight = night
        if (vehicleLightEnabled && automatic && vehicleNight != null) return
        if ((mode == CarPlayNightMode.SYSTEM && !vehicleLightEnabled) ||
            (ambientFallback && (!light.available || (resumed && !listening) ||
                (vehicleLightEnabled && !ambientKnown)))) applyNight(night)
    }

    /** A valid vehicle signal is authoritative, including while the projection UI is paused. */
    fun vehicleChanged(night: Boolean?) {
        if (!vehicleLightEnabled || vehicleNight == night) return
        vehicleNight = night
        if (!automatic) return
        stopListening()
        applyMode()
    }

    private fun applyMode() {
        if (vehicleLightEnabled && automatic) {
            vehicleNight?.let {
                stopListening()
                applyNight(it)
                return
            }
        }
        when (mode) {
            CarPlayNightMode.SYSTEM -> if (vehicleLightEnabled) applyAmbientFallback() else applyNight(systemNight)
            CarPlayNightMode.DAY -> applyNight(false)
            CarPlayNightMode.NIGHT -> applyNight(true)
            CarPlayNightMode.SCHEDULE -> applySchedule()
            CarPlayNightMode.AMBIENT -> applyAmbientFallback()
        }
    }

    private fun applyAmbientFallback() {
        if (!light.available || (vehicleLightEnabled && !listening)) applyNight(systemNight)
        if (light.available && resumed && !listening) {
            listening = true
            if (!light.start(::onLux)) {
                listening = false
                light.stop()
                applyNight(systemNight)
            }
        }
    }

    private fun onLux(lux: Float) {
        if (!resumed || !listening || !ambientFallback || vehicleNight != null) return
        ambientKnown = lux.isFinite() && lux >= 0f
        if (vehicleLightEnabled && !ambientKnown) {
            cancelPending()
            applyNight(systemNight)
            return
        }
        val target = when {
            !lux.isFinite() || lux < 0f -> null
            !night && lux < threshold.lux.toFloat() -> true
            night && lux >= threshold.lux.toFloat() -> false
            else -> null
        }
        if (target == pending) return
        cancelPending()
        pending = target
        // TYPE_LIGHT is commonly on-change: a stable reading need not emit again.
        if (target != null) {
            if (delaySeconds == 0) transition.run()
            else scheduler.postDelayed(transition, delaySeconds * 1_000L)
        }
    }

    private fun stopListening() {
        if (listening) light.stop()
        listening = false
        ambientKnown = false
        cancelPending()
        scheduler.remove(scheduleTick)
    }

    private fun applySchedule() {
        val time = timeSource.snapshot()
        applyNight(schedule.isNight(time.minuteOfDay))
        if (!resumed || mode != CarPlayNightMode.SCHEDULE) return
        scheduler.remove(scheduleTick)
        scheduler.postDelayed(scheduleTick, time.millisUntilNextMinute.coerceIn(1, 60_000))
    }

    private fun cancelPending() {
        scheduler.remove(transition)
        pending = null
    }

    private fun applyNight(value: Boolean) {
        if (value == night) return
        night = value
        onNightChanged(value)
    }

    /** Re-sends the current mode to CarPlay after a settings change that did not flip [night]. */
    fun pushCurrentNight() {
        onNightChanged(night)
    }
}
