package com.shilapi.xcertplay

import android.content.Context
import android.view.KeyEvent

/** Which QiSi factory hard keys DiPlay may temporarily register for CarPlay. */
internal object Hs5SteeringKeyPolicy {
    /** Only the verified SystemService BACK code is claimed while the fly screen shows. */
    val FLY_SCREEN_BACK_CODES = intArrayOf(QisiSteeringServiceClient.KEYCODE_BACK)

    /** The phone key is taken only during a CarPlay call or while CarPlay plays telephony audio. */
    fun factoryKeyCodes(context: Context, carPlayCall: Boolean, flyScreen: Boolean = false): IntArray {
        val codes = LinkedHashSet<Int>()
        val wheelKeys = AirPlayPersistence.loadCarPlayWheelSiri(context) ||
            (!AirPlayPersistence.loadCarBluetoothAudio(context) && AirPlayPersistence.loadCarPlayWheelMedia(context))
        val bluetoothAudio = AirPlayPersistence.loadCarBluetoothAudio(context)
        if (wheelKeys && !bluetoothAudio && AirPlayPersistence.loadCarPlayWheelMedia(context)) {
            codes += KeyEvent.KEYCODE_MEDIA_PREVIOUS
            codes += KeyEvent.KEYCODE_MEDIA_NEXT
        }
        if (AirPlayPersistence.loadClusterMapEnabled(context)) codes += KeyEvent.KEYCODE_MEDIA_PREVIOUS
        if (wheelKeys && AirPlayPersistence.loadCarPlayWheelSiri(context)) {
            codes += QisiSteeringServiceClient.KEYCODE_VOICE_ASSIST
            codes += QisiSteeringServiceClient.KEYCODE_SDS_VOICE_ASSIST
        }
        if (wheelKeys && !bluetoothAudio && carPlayCall) codes += KeyEvent.KEYCODE_CALL
        if (wheelKeys) codes += 110
        if (flyScreen) codes += FLY_SCREEN_BACK_CODES.toList()
        return codes.toIntArray()
    }

    /** Standalone mode claims only long-press PREVIOUS. OEM fly-screen exit arrives via Qisi callback. */
    fun standaloneKeyCodes(): IntArray = intArrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun shouldRegisterFactoryKeys(context: Context, flyScreen: Boolean = false): Boolean =
        flyScreen || AirPlayPersistence.loadClusterMapEnabled(context) || AirPlayPersistence.loadCarPlayWheelSiri(context) ||
            (!AirPlayPersistence.loadCarBluetoothAudio(context) && AirPlayPersistence.loadCarPlayWheelMedia(context))

    fun closesFlyScreen(keyCode: Int, action: Int, flyScreen: Boolean): Boolean =
        flyScreen && keyCode == QisiSteeringServiceClient.KEYCODE_BACK &&
            action == QisiSteeringServiceClient.HARD_KEY_SHORT_CLICK
}
