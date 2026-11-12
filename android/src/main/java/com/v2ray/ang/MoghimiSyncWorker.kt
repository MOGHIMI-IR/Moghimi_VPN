package com.v2ray.ang

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException

/**
 * Periodically refreshes the dedicated Moghimi cloud group when Android has network access.
 */
class MoghimiSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        return try {
            MoghimiCloudSync.sync()
            Result.success()
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure()
        }
    }
}
