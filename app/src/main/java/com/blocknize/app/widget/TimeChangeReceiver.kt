package com.blocknize.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** WorkManager persists reboot scheduling; these signals also invalidate civil-time transitions. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        com.blocknize.app.domain.CivilTimeChanges.notifyChanged()
        val ids = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(
            android.content.ComponentName(context, CurrentTaskWidgetReceiver::class.java))
        if (ids.isNotEmpty()) {
            WidgetRefreshWorker.ensurePeriodic(context)
            WidgetRefreshWorker.request(context)
        }
    }
}
