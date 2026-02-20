package com.example.expirytracker.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.expirytracker.worker.ExpiryNotifyWorker
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.ZoneId


object ExpiryScheduler {

    fun scheduleTwoAlerts(context: Context, productId: Long, expiresAtMillis: Long) {
        val zone = ZoneId.systemDefault()
        val expiresDate = Instant
            .ofEpochMilli(expiresAtMillis)
            .atZone(zone)
            .toLocalDate()

        // 18:00 on expiration day
        val t18 = expiresDate
            .atTime(18, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        // 00:00 next day (your "24:00")
        val t24 = expiresDate
            .plusDays(1)
            .atTime(0, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        enqueue(context, productId, t18, "EXP_${productId}_18")
        enqueue(context, productId, t24, "EXP_${productId}_24")
    }

    private fun enqueue(context: Context, productId: Long, triggerAtMillis: Long, uniqueName: String) {
        val delay = triggerAtMillis - System.currentTimeMillis()
        if (delay <= 0) return

        val req = OneTimeWorkRequestBuilder<ExpiryNotifyWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("productId" to productId))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAlerts(context: Context, productId: Long) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("EXP_${productId}_18")
        wm.cancelUniqueWork("EXP_${productId}_24")
    }
}
