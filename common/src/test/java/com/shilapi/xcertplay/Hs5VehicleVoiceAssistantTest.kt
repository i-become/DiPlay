package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Hs5VehicleVoiceAssistantTest {
    private val context = mock(Context::class.java)
    private val packages = mock(PackageManager::class.java)
    private val handlers = mutableMapOf<String, ResolveInfo>()
    private val launches = mutableListOf<Intent>()
    private var sdsSubmissions = 0
    private var rejectVoiceCommand = false
    private val submissions = mutableListOf<JSONObject>()
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            assertEquals(1, code)
            assertEquals(IBinder.FLAG_ONEWAY, flags)
            data.enforceInterface(QisiSteeringServiceClient.TOKEN)
            submissions += JSONObject(data.readString()!!)
            sdsSubmissions++
            return true
        }
    }

    init {
        `when`(context.packageManager).thenReturn(packages)
        `when`(context.packageName).thenReturn("com.shihab.diplay")
        `when`(packages.resolveActivity(any(Intent::class.java), anyInt())).thenAnswer { call ->
            assertEquals(PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY, call.getArgument<Int>(1))
            handlers[call.getArgument<Intent>(0).action]
        }
        doAnswer { call ->
            val intent = call.getArgument<Intent>(0)
            if (rejectVoiceCommand && intent.action == Intent.ACTION_VOICE_COMMAND) throw SecurityException("OEM entry denied")
            launches += intent
            null
        }.`when`(context).startActivity(any(Intent::class.java))
    }

    private fun handler(action: String, system: Boolean = true, exported: Boolean = true, name: String = "ltd.qisi.sds.VoiceActivity") {
        handlers[action] = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "ltd.qisi.sds"
                this.name = name
                this.exported = exported
                enabled = true
                applicationInfo = ApplicationInfo().apply {
                    enabled = true
                    flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
                }
            }
        }
    }

    @Test fun factoryAssistantUsesResolvedExplicitEntryInsteadOfUnacknowledgedSdsCommand() {
        handler(Intent.ACTION_VOICE_COMMAND)
        val route = Hs5VehicleVoiceAssistant.wake(context, binder)
        val intent = launches.single()
        assertEquals("ltd.qisi.sds", intent.component!!.packageName)
        assertEquals("ltd.qisi.sds.VoiceActivity", intent.component!!.className)
        assertEquals(Intent.ACTION_VOICE_COMMAND, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(0, sdsSubmissions)
        assertTrue(route.contains("activity started"))
    }

    @Test fun deniedFactoryEntryTriesTheNextExportedVoiceEntry() {
        handler(Intent.ACTION_VOICE_COMMAND)
        handler("android.intent.action.VOICE_ASSIST")
        rejectVoiceCommand = true
        Hs5VehicleVoiceAssistant.wake(context, binder)
        assertEquals("android.intent.action.VOICE_ASSIST", launches.single().action)
        assertEquals(0, sdsSubmissions)
    }

    @Test fun nonSystemPrivateAndChooserActivitiesAreNeverOpenedAsFactoryVoice() {
        handler(Intent.ACTION_VOICE_COMMAND, system = false)
        handler("android.intent.action.VOICE_ASSIST", exported = false)
        handler(Intent.ACTION_ASSIST, name = "com.android.internal.app.ResolverActivity")
        val route = Hs5VehicleVoiceAssistant.wake(context, binder)
        assertTrue(launches.isEmpty())
        assertEquals(1, sdsSubmissions)
        assertTrue(route.contains("visibility unconfirmed"))
    }

    @Test fun firmwareWithoutExportedVoiceEntryKeepsTheSdsFallback() {
        val route = Hs5VehicleVoiceAssistant.wake(context, binder)
        assertTrue(launches.isEmpty())
        assertEquals(1, sdsSubmissions)
        assertTrue(route.contains("no exported system assistant activity"))
    }

    @Test fun sdsUsesRegisteredSenderAndDomainDeliveryInsteadOfReenteringHardKeyArbitration() {
        Hs5VehicleVoiceAssistant.wake(context, binder)
        Hs5VehicleVoiceAssistant.wake(context, binder)
        submissions.forEach { command ->
            assertEquals("com.shihab.diplay", command.getString("SenderGroup"))
            assertEquals("com.shihab.diplay", command.getString("SenderDomain"))
            assertEquals("GROUP_SDS", command.getString("TargetGroup"))
            assertEquals("DOMAIN_SDS", command.getString("TargetDomain"))
            assertEquals(0, command.getInt("CommandType"))
            assertEquals(-1, command.getInt("SenderScreen"))
            assertEquals(0, command.getInt("TargetScreen"))
            val key = JSONObject(command.getString("Data"))
            assertEquals("SPEECH", key.getString("KEY_CODE"))
            assertEquals(0, key.getInt("KEY_ACTION"))
        }
        assertNotEquals(submissions[0].getLong("SessionID"), submissions[1].getLong("SessionID"))
    }
}
