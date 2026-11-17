package com.yatharth.yool.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yatharth.yool.appContainer
import java.util.concurrent.TimeUnit

/** Nightly background sync. No-ops if the background-read permission isn't granted. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val manager = container.healthConnectManager
        val granted = try { manager.grantedPermissions() } catch (e: Exception) { return Result.success() }
        if (manager.backgroundPermission !in granted) return Result.success()
        return when (container.syncEngine.syncAuto()) {
            is SyncStatus.Error -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "health_connect_nightly_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
