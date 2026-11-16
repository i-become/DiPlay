package com.shilapi.xcertplay.carlink

import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.newui.connection.NewUiConnectionSnapshotReader
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CarlinkHomeSnapshotTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val address = "02:00:00:00:00:02"
    private lateinit var state: MutableStateFlow<CarlinkBridge.Snapshot>

    @Before fun setup() {
        app.getSharedPreferences("diplay", 0).edit().clear().commit()
        DiPlayPreferences.savePhone(app, address, "Android phone")
        DiPlayPreferences.savePhoneLinkMode(app, address, DiPlayPreferences.PhoneLinkMode.CARLINK)
        state = ReflectionHelpers.getField(CarlinkBridge.get(app), "_snapshot")
    }

    @After fun cleanup() { state.value = CarlinkBridge.Snapshot() }

    @Test fun sdkPairingCodeIsShownForBothNewAndPreviouslyPairedPhones() {
        state.value = CarlinkBridge.Snapshot(state = CarlinkLinkState.PAIRING, pin = "123456", progressStep = 3)
        val first = NewUiConnectionSnapshotReader.read(app, null)
        assertTrue(first.linkSummary.orEmpty().contains("123456"))
        assertEquals(6, first.progressStepCount)
        assertEquals(3, first.progressLitCount)
        DiPlayPreferences.markCarlinkPhonePaired(app, address)
        val paired = NewUiConnectionSnapshotReader.read(app, null)
        assertEquals(first.linkSummary, paired.linkSummary)
        assertEquals(first.progressStepSubtitle, paired.progressStepSubtitle)
    }

    @Test fun noCodeIsInventedBeforeSdkReturnsOne() {
        DiPlayPreferences.markCarlinkPhonePaired(app, address)
        state.value = CarlinkBridge.Snapshot(state = CarlinkLinkState.ADVERTISING, progressStep = 2)
        val waiting = NewUiConnectionSnapshotReader.read(app, null)
        assertFalse(waiting.isConnected)
        assertEquals(2, waiting.progressLitCount)
        assertFalse(waiting.linkSummary.orEmpty().contains("123456"))
    }
}
