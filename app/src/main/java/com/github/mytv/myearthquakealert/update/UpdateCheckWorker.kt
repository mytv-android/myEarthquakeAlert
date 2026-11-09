package com.github.mytv.myearthquakealert.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 检查 (design §8): the daily automatic check, scheduled while 自动检查更新 is on, on any
 * network and on the selected channel. A newer release posts 发现新版本 (never while an
 * alert is active; it is then announced by the next check).
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ok = try {
            UpdateController.get(applicationContext).backgroundCheck()
        } catch (e: Exception) {
            Log.w(TAG, "background check failed", e)
            false
        }
        return if (ok || runAttemptCount >= MAX_RETRIES) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val NAME = "app_update_check"
        private const val MAX_RETRIES = 2

        /**
         * Keeps the periodic check in step with 自动检查更新. Called once the user is unlocked
         * (WorkManager's database lives in credential-encrypted storage).
         */
        fun follow(app: MyEarthQuakeAlertApp) {
            app.appScope.launch {
                app.settingsRepository.settings
                    .map { it.updateAutoCheck }
                    .distinctUntilChanged()
                    .collect { enabled -> if (enabled) schedule(app) else cancel(app) }
            }
        }

        private fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                Log.w(TAG, "cannot schedule", e)
            }
        }

        private fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(NAME)
            } catch (e: Exception) {
                Log.w(TAG, "cannot cancel", e)
            }
        }
    }
}
