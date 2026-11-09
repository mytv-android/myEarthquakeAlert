package com.github.mytv.myearthquakealert.ui.about

import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import com.github.mytv.myearthquakealert.ui.about.AboutViewModel.UpdateState
import com.github.mytv.myearthquakealert.update.CheckState
import com.github.mytv.myearthquakealert.update.DownloadPhase
import com.github.mytv.myearthquakealert.update.DownloadSnapshot
import com.github.mytv.myearthquakealert.update.PendingDownload
import com.github.mytv.myearthquakealert.update.ReadyUpdate
import com.github.mytv.myearthquakealert.update.SessionInstaller
import com.github.mytv.myearthquakealert.update.UpdateFailure
import com.github.mytv.myearthquakealert.update.WaitReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStateTest {

    private fun info(code: Long) = UpdateInfo(code, "1.0.$code", "v1.0.$code", "", 1000, "u", "s", emptyList())

    private val v5 = info(5)
    private val v6 = info(6)

    private fun inputs(
        check: CheckState = CheckState.Idle,
        download: DownloadSnapshot? = null,
        install: SessionInstaller.Status = SessionInstaller.Status.Idle,
        ready: ReadyUpdate? = null,
        pending: PendingDownload? = null,
        dismissed: String? = null,
        deferred: Boolean = false,
    ) = UpdateInputs(check, download, install, ready, pending, dismissed, deferred)

    private fun resolve(i: UpdateInputs, files: Set<String> = setOf("/apk5", "/apk6"), part: Long = 400) =
        resolveUpdateState(i, installedCode = 4, partLength = { part }, fileExists = { it in files })

    @Test
    fun `check results map to the quiet states`() {
        assertEquals(UpdateState.Idle, resolve(inputs()))
        assertEquals(UpdateState.Checking, resolve(inputs(check = CheckState.Checking(true))))
        assertEquals(UpdateState.UpToDate, resolve(inputs(check = CheckState.UpToDate)))
        assertEquals(UpdateState.Available(v5), resolve(inputs(check = CheckState.Available(v5))))
        val error = resolve(inputs(check = CheckState.Failed(UpdateFailure.RATE_LIMITED, null))) as UpdateState.Error
        assertEquals(UpdateState.Stage.CHECK, error.stage)
    }

    @Test
    fun `an installed version is no longer available`() {
        val r = resolveUpdateState(inputs(check = CheckState.Available(v5)), installedCode = 5, partLength = { 0 }, fileExists = { true })
        assertEquals(UpdateState.UpToDate, r)
    }

    @Test
    fun `the running download wins over the check`() {
        val running = DownloadSnapshot("w", DownloadPhase.RUNNING, 5, downloaded = 300, total = 1000, speed = 50)
        val state = resolve(inputs(check = CheckState.Available(v5), download = running, pending = PendingDownload(v5, false)))
        assertEquals(UpdateState.Downloading(v5, 300, 1000, 50), state)
        assertEquals(30, (state as UpdateState.Downloading).progress)
    }

    @Test
    fun `waiting, verifying and paused`() {
        val pending = PendingDownload(v5, false)
        val waiting = DownloadSnapshot("w", DownloadPhase.WAITING, 5, waitReason = WaitReason.UNMETERED)
        assertEquals(UpdateState.Waiting(v5, WaitReason.UNMETERED, 400, 1000), resolve(inputs(download = waiting, pending = pending)))
        val verifying = DownloadSnapshot("w", DownloadPhase.VERIFYING, 5)
        assertEquals(UpdateState.Verifying(v5), resolve(inputs(download = verifying, pending = pending)))
        val cancelled = DownloadSnapshot("w", DownloadPhase.CANCELLED, 5)
        assertEquals(UpdateState.Paused(v5, 400, 1000), resolve(inputs(download = cancelled, pending = pending)))
        // Cancelled without a pending download (取消下载) is nothing.
        assertEquals(UpdateState.Idle, resolve(inputs(download = cancelled)))
    }

    @Test
    fun `failed downloads show until dismissed`() {
        val failed = DownloadSnapshot("w1", DownloadPhase.FAILED, 5, failure = UpdateFailure.SIGNATURE, verifyFailure = true)
        val pending = PendingDownload(v5, false)
        val error = resolve(inputs(download = failed, pending = pending)) as UpdateState.Error
        assertEquals(UpdateState.Stage.VERIFY, error.stage)
        assertEquals(UpdateFailure.SIGNATURE, error.failure)
        assertEquals(v5, error.retryInfo)
        assertEquals(UpdateState.Available(v5), resolve(inputs(check = CheckState.Available(v5), download = failed, pending = pending, dismissed = "w1")))
    }

    @Test
    fun `a verified package is ready unless a newer release was found or it is gone`() {
        val ready = ReadyUpdate(v5, "/apk5", "s")
        assertEquals(UpdateState.ReadyToInstall(java.io.File("/apk5"), v5, false), resolve(inputs(ready = ready)))
        assertEquals(UpdateState.ReadyToInstall(java.io.File("/apk5"), v5, true), resolve(inputs(ready = ready, deferred = true)))
        assertEquals(UpdateState.Available(v6), resolve(inputs(check = CheckState.Available(v6), ready = ready)))
        assertEquals(UpdateState.Idle, resolve(inputs(ready = ready), files = emptySet()))
    }

    @Test
    fun `install states come first`() {
        val ready = ReadyUpdate(v5, "/apk5", "s")
        assertEquals(UpdateState.Installing(v5, false), resolve(inputs(ready = ready, install = SessionInstaller.Status.Installing(v5))))
        val permission = resolve(inputs(ready = ready, install = SessionInstaller.Status.NeedsPermission(v5)))
        assertTrue(permission is UpdateState.NeedsInstallPermission)
        val failed = resolve(inputs(ready = ready, install = SessionInstaller.Status.Failed(v5, UpdateFailure.INSTALL_ABORTED, null))) as UpdateState.Error
        assertEquals(UpdateState.Stage.INSTALL, failed.stage)
        assertEquals(UpdateFailure.INSTALL_ABORTED, failed.failure)
    }
}
