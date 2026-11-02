package com.ranit.thingcount.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ranit.thingcount.MainActivity
import com.ranit.thingcount.R
import com.ranit.thingcount.ThingCountApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CounterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_INCREMENT) {
            val counterId = intent.getStringExtra(EXTRA_COUNTER_ID)
            if (counterId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val app = context.applicationContext as? ThingCountApp
                    app?.repository?.increment(counterId)

                    // Refresh widget views across all instances
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, CounterWidgetProvider::class.java)
                    val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                    for (widgetId in allWidgetIds) {
                        updateAppWidget(context, appWidgetManager, widgetId)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_INCREMENT = "com.ranit.thingcount.ACTION_WIDGET_INCREMENT"
        const val EXTRA_COUNTER_ID = "com.ranit.thingcount.EXTRA_COUNTER_ID"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_counter)

            // Intent to open app
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)

            CoroutineScope(Dispatchers.IO).launch {
                val app = context.applicationContext as? ThingCountApp
                val firstCounter = app?.database?.counterDao()?.getAllCountersRaw()?.firstOrNull { !it.isDeleted }

                if (firstCounter != null) {
                    views.setTextViewText(R.id.widget_title, "${firstCounter.emoji} ${firstCounter.title}")
                    views.setTextViewText(R.id.widget_count, firstCounter.currentValue.toString())
                    views.setTextViewText(R.id.widget_btn_increment, "+ ${firstCounter.step}")

                    val incrementIntent = Intent(context, CounterWidgetProvider::class.java).apply {
                        action = ACTION_INCREMENT
                        putExtra(EXTRA_COUNTER_ID, firstCounter.id)
                    }
                    val incrementPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        incrementIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Allow tapping both the large count display and the + button to increment
                    views.setOnClickPendingIntent(R.id.widget_count, incrementPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_btn_increment, incrementPendingIntent)
                } else {
                    views.setTextViewText(R.id.widget_title, "ThingCount")
                    views.setTextViewText(R.id.widget_count, "0")
                    views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
