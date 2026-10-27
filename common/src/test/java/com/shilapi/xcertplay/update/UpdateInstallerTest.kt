package com.shilapi.xcertplay.update

import android.content.Context
import android.os.SystemClock
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateInstallerTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences(UpdateInstaller.PREFERENCES, Context.MODE_PRIVATE)
    private val transport = FakeSota()
    private val scheduled = mutableListOf<String>()

    @Before fun reset() {
        DiagnosticLogging.setEnabled(false)
        for (name in listOf(UpdateInstaller.PREFERENCES, "diplay_update_diagnostics", "diplay_update_install")) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun dispatchIsPendingNotSuccessAndDoesNotOpenAStandardInstaller() {
        val result = submit()
        assertEquals(UpdateInstaller.Status.WAITING, result.status)
        assertTrue(result.busy)
        assertEquals(listOf("inspect", "verify", "stage", "send"), transport.calls)
        assertEquals(listOf(result.requestId), scheduled)
        assertTrue(app.packageManager.packageInstaller.mySessions.isEmpty())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun duplicateSubmissionDoesNotCopyOrBroadcastAgain() {
        val first = submit()
        assertEquals(first, submit())
        assertEquals(1, transport.calls.count { it == "send" })
        assertEquals(1, scheduled.size)
    }

    @Test fun untrustedServiceFailsClosedWithoutStagingOrFallback() {
        transport.failAt = "inspect"
        val result = submit()
        assertEquals(UpdateInstaller.Status.FAILED, result.status)
        assertEquals("TEST_inspect", result.reason)
        assertEquals(listOf("inspect"), transport.calls)
        assertTrue(scheduled.isEmpty())
        assertTrue(app.packageManager.packageInstaller.mySessions.isEmpty())
    }

    @Test fun invalidUpdateIsNeverExposedToSota() {
        transport.failAt = "verify"
        assertEquals(UpdateInstaller.Status.FAILED, submit().status)
        assertEquals(listOf("inspect", "verify"), transport.calls)
        assertFalse(prefs.contains("rootMode"))
    }

    @Test fun stagingFailureCleansOnlyThisRequestAndDoesNotBroadcast() {
        transport.failAt = "stage"
        val result = submit()
        assertFalse(result.busy)
        assertEquals(listOf("inspect", "verify", "stage", "cleanup"), transport.calls)
        assertEquals(result.requestId, transport.cleaned.single().requestId)
    }

    @Test fun broadcastFailureIsVisibleAndCleansTheReadOnlyCopy() {
        transport.failAt = "send"
        val result = submit()
        assertEquals(UpdateInstaller.Status.FAILED, result.status)
        assertEquals("TEST_send", result.reason)
        assertEquals(1, transport.cleaned.size)
    }

    @Test fun schedulerFailurePreventsSendingAnUntrackedRequest() {
        val result = UpdateInstaller.submit(app, File(app.cacheDir, "input.apk"), "digest", transport) {
            throw IllegalStateException("scheduler unavailable")
        }
        assertEquals(UpdateInstaller.Status.FAILED, result.status)
        assertFalse(transport.calls.contains("send"))
    }

    @Test fun selfReplacementConfirmsVersionAndCleansStaging() {
        val request = submit()
        installed(1201, 400)
        UpdateInstaller.reconcile(app, transport = transport)
        val result = UpdateInstaller.result(app)!!
        assertEquals(UpdateInstaller.Status.SUCCEEDED, result.status)
        assertEquals(request.requestId, result.requestId)
        assertTrue(result.revision > request.revision)
        assertEquals(request.requestId, transport.cleaned.single().requestId)
    }

    @Test fun timestampAloneOrAnUnchangedTimestampCannotConfirmSuccess() {
        submit()
        installed(1200, 400)
        UpdateInstaller.reconcile(app, transport = transport)
        assertTrue(UpdateInstaller.result(app)!!.busy)
        installed(1201, 0)
        UpdateInstaller.reconcile(app, transport = transport)
        assertTrue(UpdateInstaller.result(app)!!.busy)
    }

    @Test fun timeoutSurvivesProcessUiLossAndUnlocksRetry() {
        val request = submit()
        UpdateInstaller.reconcile(app, requestId = request.requestId, expired = true, transport = transport)
        assertEquals(UpdateInstaller.Status.TIMED_OUT, UpdateInstaller.result(app)?.status)
        assertEquals(request.requestId, transport.cleaned.single().requestId)
        assertNotEquals(request.requestId, submit().requestId)
    }

    @Test fun lateSelfReplacementCanCorrectATimeout() {
        val request = submit()
        UpdateInstaller.reconcile(app, expired = true, transport = transport)
        installed(1201, 400)
        UpdateInstaller.reconcile(app, requestId = request.requestId, transport = transport)
        assertEquals(UpdateInstaller.Status.SUCCEEDED, UpdateInstaller.result(app)?.status)
    }

    @Test fun oldTimeoutCannotChangeOrCleanANewerAttempt() {
        val old = submit()
        UpdateInstaller.reconcile(app, expired = true, transport = transport)
        val fresh = submit()
        transport.cleaned.clear()
        UpdateInstaller.reconcile(app, requestId = old.requestId, expired = true, transport = transport)
        assertEquals(fresh, UpdateInstaller.result(app))
        assertTrue(transport.cleaned.isEmpty())
    }

    @Test fun elapsedDeadlineAndRebootCannotLeaveRequestBusyForever() {
        submit()
        prefs.edit().putLong("startedElapsed", SystemClock.elapsedRealtime() - UpdateInstaller.TIMEOUT_MS).commit()
        UpdateInstaller.reconcile(app, transport = transport)
        assertEquals(UpdateInstaller.Status.TIMED_OUT, UpdateInstaller.result(app)?.status)
        submit()
        prefs.edit().putLong("startedElapsed", SystemClock.elapsedRealtime() + 10_000).commit()
        UpdateInstaller.reconcile(app, transport = transport)
        assertFalse(UpdateInstaller.result(app)!!.busy)
    }

    @Test fun diagnosticOptOutDoesNotPersistAnInstallLog() {
        submit()
        assertFalse(app.getSharedPreferences("diplay_update_diagnostics", Context.MODE_PRIVATE).contains("events"))
        assertFalse(UpdateAvailability.report(app, System.currentTimeMillis()).contains("sota status="))
    }

    private fun submit() = UpdateInstaller.submit(app, File(app.cacheDir, "input.apk"), "digest", transport, scheduled::add)

    private fun installed(version: Int, updated: Long) {
        val info = app.packageManager.getPackageInfo(app.packageName, 0).apply {
            versionCode = version
            lastUpdateTime = updated
        }
        shadowOf(app.packageManager).installPackage(info)
    }

    private class FakeSota : SotaTransport {
        val calls = mutableListOf<String>()
        val cleaned = mutableListOf<SotaStaging>()
        var failAt: String? = null
        private fun step(name: String) { calls += name; if (name == failAt) throw SotaFailure("TEST_$name") }
        override fun checkEndpoint() = step("inspect")
        override fun verifyArchive(source: File, digest: String): SotaUpdate { step("verify"); return SotaUpdate(1201, 0) }
        override fun storageMode() = 448
        override fun stage(staging: SotaStaging, source: File, digest: String) = step("stage")
        override fun send(staging: SotaStaging) {
            assertEquals(staging.requestId, RuntimeEnvironment.getApplication().getSharedPreferences(
                UpdateInstaller.PREFERENCES, Context.MODE_PRIVATE).getString("request", null))
            step("send")
        }
        override fun cleanup(staging: SotaStaging) { calls += "cleanup"; cleaned += staging }
    }
}
