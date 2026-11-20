package com.shilapi.xcertplay

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class DeferredIntegrityWarningTest {
    private class Fixture(val warning: String? = "notice") {
        val deadline = SystemClock.elapsedRealtime() + Duration.ofMinutes(30).toMillis()
        var reads = 0
        val shown = mutableListOf<Activity>()
        val observer = DeferredIntegrityWarning(
            remainingMillis = { (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) },
            readWarning = { reads++; warning },
            showWarning = { activity, _ -> shown.add(activity) },
        )
        fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        fun advance(duration: Duration) { shadowOf(Looper.getMainLooper()).idleFor(duration) }
    }

    @Test fun warningAppearsAtThirtyMinutesWithoutReopeningTheScreen() {
        val f = Fixture()
        val activity = f.activity()
        f.observer.onActivityResumed(activity)
        // Activity creation can advance Robolectric's clock; use the original deadline.
        f.advance(Duration.ofMillis(f.deadline - SystemClock.elapsedRealtime() - 1))
        assertEquals(0, f.reads)
        assertTrue(f.shown.isEmpty())
        f.advance(Duration.ofMillis(1))
        assertEquals(listOf(activity), f.shown)
        assertEquals(1, f.reads)
    }

    @Test fun screenChangesDoNotRestartTheDeadlineAndOnlyOneNoticeIsShown() {
        val f = Fixture()
        val first = f.activity()
        f.observer.onActivityResumed(first)
        f.advance(Duration.ofMinutes(20))
        f.observer.onActivityPaused(first)
        val second = f.activity()
        f.observer.onActivityResumed(second)
        f.advance(Duration.ofMinutes(10))
        assertEquals(listOf(second), f.shown)
        f.observer.onActivityPaused(second)
        f.observer.onActivityResumed(first)
        f.advance(Duration.ofMinutes(30))
        assertEquals(1, f.shown.size)
    }

    @Test fun backgroundExpiryWaitsForTheNextForegroundActivity() {
        val f = Fixture()
        val activity = f.activity()
        f.observer.onActivityResumed(activity)
        f.advance(Duration.ofMinutes(10))
        f.observer.onActivityPaused(activity)
        f.advance(Duration.ofMinutes(25))
        assertEquals(0, f.reads)
        assertTrue(f.shown.isEmpty())
        f.observer.onActivityResumed(activity)
        assertEquals(listOf(activity), f.shown)
    }

    @Test fun destroyedActivityNeverReceivesTheNotice() {
        val f = Fixture()
        val old = f.activity()
        f.observer.onActivityResumed(old)
        f.observer.onActivityDestroyed(old)
        f.advance(Duration.ofMinutes(31))
        assertTrue(f.shown.isEmpty())
        val replacement = f.activity()
        f.observer.onActivityResumed(replacement)
        assertEquals(listOf(replacement), f.shown)
    }

    @Test fun officialInstallationNeverShowsTheNotice() {
        val f = Fixture(warning = null)
        val activity = f.activity()
        f.observer.onActivityResumed(activity)
        f.advance(Duration.ofMinutes(31))
        f.observer.onActivityResumed(activity)
        assertEquals(1, f.reads)
        assertTrue(f.shown.isEmpty())
    }
}
