package com.example.expirytracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expirytracker.data.ProductStore
import com.example.expirytracker.notify.NotificationHelper

class ExpiryNotifyWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val productId = inputData.getLong("productId", -1L)
        if (productId <= 0) return Result.failure()

        val store = ProductStore(applicationContext)
        val p = store.getById(productId) ?: return Result.success()

        // Check status/qty before notifying
        if (p.status != "ACTIVE") return Result.success()
        if (p.qtyCurrent <= 0) return Result.success()

        NotificationHelper.show(
            applicationContext,
            notifId = (productId % Int.MAX_VALUE).toInt(),
            title = "Expiration reminder",
            text = "${p.name} expires today. Remaining: ${p.qtyCurrent}"
        )

        return Result.success()
    }
}
