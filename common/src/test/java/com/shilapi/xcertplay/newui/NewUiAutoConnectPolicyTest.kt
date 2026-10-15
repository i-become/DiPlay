package com.shilapi.xcertplay.newui

import android.content.Intent
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.DiPlayLauncher
import com.shilapi.xcertplay.PhoneBluetoothReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NewUiAutoConnectPolicyTest {
    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    @Test fun connectsWhenPhoneSavedAndIntentIsPlain() {
        assertTrue(
            NewUiAutoConnectPolicy.shouldConnectOnColdStart(
                launcher(),
                phoneAddress = "AA:BB:CC:DD:EE:FF",
                setupError = null,
                sessionRunning = false,
                autoConnectEnabled = true,
            ),
        )
    }

    @Test fun skipsWhenAutoConnectDisabled() {
        assertFalse(
            NewUiAutoConnectPolicy.shouldConnectOnColdStart(
                launcher(),
                phoneAddress = "AA:BB:CC:DD:EE:FF",
                setupError = null,
                sessionRunning = false,
                autoConnectEnabled = false,
            ),
        )
    }

    @Test fun skipsWithoutPhone() {
        assertFalse(
            NewUiAutoConnectPolicy.shouldConnectOnColdStart(
                launcher(),
                phoneAddress = null,
                setupError = null,
                sessionRunning = false,
                autoConnectEnabled = true,
            ),
        )
    }

    @Test fun skipsWhenReturningFromCarPlayHome() {
        assertFalse(
            NewUiAutoConnectPolicy.shouldConnectOnColdStart(
                launcher().putExtra(DiPlayLauncher.EXTRA_PAGE, "home"),
                phoneAddress = "AA:BB:CC:DD:EE:FF",
                setupError = null,
                sessionRunning = false,
                autoConnectEnabled = true,
            ),
        )
    }

    @Test fun skipsSettingsDeepLink() {
        assertFalse(
            NewUiAutoConnectPolicy.shouldConnectOnColdStart(
                launcher().putExtra(DiPlayLauncher.EXTRA_PAGE, "settings"),
                phoneAddress = "AA:BB:CC:DD:EE:FF",
                setupError = null,
                sessionRunning = false,
                autoConnectEnabled = true,
            ),
        )
    }

    @Test fun skipsWhenBluetoothAutoConnectExtraPresent() {
        assertFalse(
            NewUiAutoConnectPolicy.shouldConnectOnColdStart(
                launcher().putExtra(PhoneBluetoothReceiver.EXTRA_AUTO_CONNECT, true),
                phoneAddress = "AA:BB:CC:DD:EE:FF",
                setupError = null,
                sessionRunning = false,
                autoConnectEnabled = true,
            ),
        )
    }

    @Test fun skipsLegacySettingsCategoryExtra() {
        assertFalse(
            NewUiAutoConnectPolicy.shouldConnectOnColdStart(
                Intent().putExtra(DiPlayActivity.EXTRA_SETTINGS_CATEGORY, "display"),
                phoneAddress = "AA:BB:CC:DD:EE:FF",
                setupError = null,
                sessionRunning = false,
                autoConnectEnabled = true,
            ),
        )
    }
}
