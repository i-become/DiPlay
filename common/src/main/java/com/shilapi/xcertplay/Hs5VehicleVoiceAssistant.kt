package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Qisi CommandWrapper/HardKeyWrapper wire format from the OEM SDK. */
internal object Hs5VehicleVoiceAssistant {
    // Android defines this system action outside the public Intent SDK constants.
    private const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"
    private val requests = AtomicLong()

    fun wake(context: Context, binder: IBinder): String {
        // Resolve the installed factory assistant instead of assuming that SDS consumes a forwarded
        // HardKeyWrapper. A successful one-way Binder transaction does not mean SDS handled it.
        for (action in arrayOf(Intent.ACTION_VOICE_COMMAND, ACTION_VOICE_ASSIST, Intent.ACTION_ASSIST)) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val info = runCatching {
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY)
                    ?.activityInfo
            }.getOrNull() ?: continue
            if (!info.exported || !info.enabled || !info.applicationInfo.enabled ||
                info.packageName == context.packageName ||
                info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue
            // An implicit resolver is not an assistant. Select only an installed, exported handler.
            if (info.name == "com.android.internal.app.ResolverActivity" ||
                info.name == "com.android.internal.app.ChooserActivity") continue
            intent.component = ComponentName(info.packageName, info.name)
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                return "system assistant activity started action=$action component=${intent.component?.flattenToShortString()}"
            }
        }
        val request = requests.incrementAndGet()
        submitSdsCommand(context, binder, request)
        return "no exported system assistant activity; SPEECH submitted to SDS domain=0 sender=${context.packageName} request=$request; visibility unconfirmed"
    }

    private fun submitSdsCommand(context: Context, binder: IBinder, request: Long) {
        // Match our registered SystemService callback identity. COMMAND_DOMAIN (0) addresses SDS;
        // COMMAND_HARDKEY (1) enters hard-key arbitration, which can route SPEECH back to us.
        // Submission is one-way and must not be reported as confirmed assistant visibility.
        QisiSteeringServiceClient.sendCommand(binder, JSONObject()
            .put("SenderGroup", context.packageName)
            .put("SenderDomain", context.packageName)
            .put("SenderScreen", -1)
            .put("TargetGroup", "GROUP_SDS")
            .put("TargetDomain", "DOMAIN_SDS")
            .put("TargetScreen", 0)
            .put("Data", JSONObject()
                .put("KEY_CODE", "SPEECH")
                .put("KEY_ACTION", QisiSteeringServiceClient.HARD_KEY_SHORT_CLICK).toString())
            .put("SessionID", request)
            .put("CommandType", 0)
            .put("ErrorCode", 10000)
            .toString())
    }
}
