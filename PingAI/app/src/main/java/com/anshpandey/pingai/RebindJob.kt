package com.anshpandey.pingai

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationManagerCompat

class RebindJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val ctx = applicationContext
        val store = Store(ctx)
        if (store.enabled && !Live.connected &&
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(packageName)
        ) {
            Listener.restart(ctx)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 77

        fun schedule(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
                val info = JobInfo.Builder(JOB_ID, ComponentName(context, RebindJob::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(info)
            }
        }

        fun cancel(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                scheduler.cancel(JOB_ID)
            }
        }
    }
}
