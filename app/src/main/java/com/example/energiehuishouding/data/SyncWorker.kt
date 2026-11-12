package com.example.energiehuishouding.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.energiehuishouding.SolarApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SolarApplication
        val repo = app.repository
        val settings = app.settingsRepository
        
        try {
            val brand = settings.inverterBrand.first() ?: "Solplanet"
            val seApiKey = settings.solarEdgeApiKey.first() ?: ""
            val seSiteId = settings.solarEdgeSiteId.first() ?: ""
            
            val ak = settings.appKey.first() ?: ""
            val sec = settings.appSecret.first() ?: ""
            val pid = settings.plantId.first() ?: ""
            val apiK = settings.apiKey.first() ?: ""
            val token = settings.solplanetToken.first() ?: ""
            
            val invIp = settings.inverterIp.first() ?: ""
            val p1Ip = settings.p1Ip.first() ?: ""
            val batIp = settings.batteryIp.first() ?: ""

            // 1. Refresh Local Sensors (for Daily Summary accumulation)
            repo.refreshLocalOnlyData(invIp, p1Ip, batIp, updateLiveFlows = false)

            // 2. Sync Cloud History (Monthly view for context)
            repo.syncCloudHistory(
                range = com.example.energiehuishouding.ui.TimeRange.MONTH,
                date = System.currentTimeMillis(),
                brand = brand,
                ak = ak, sec = sec, pid = pid, apiKey = apiK, token = token,
                seApiKey = seApiKey, seSiteId = seSiteId
            )
            // Force an extra sync for "Yesterday" context if it's late at night to catch finalized data
            val cal = java.util.Calendar.getInstance()
            if (cal.get(java.util.Calendar.HOUR_OF_DAY) >= 21) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                repo.syncCloudHistory(
                    range = com.example.energiehuishouding.ui.TimeRange.MONTH,
                    date = cal.timeInMillis,
                    brand = brand,
                    ak = ak, sec = sec, pid = pid, apiKey = apiK, token = token,
                    seApiKey = seApiKey, seSiteId = seSiteId
                )
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "SolarSyncWork"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
