package com.blocknize.app

import android.os.Bundle
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blocknize.app.AppViewModel
import com.blocknize.app.ui.BlockNizeApp
import com.blocknize.app.ui.theme.BlockNizeTheme
import com.blocknize.app.widget.CurrentTaskWidgetReceiver
import com.blocknize.app.widget.WidgetRefreshWorker

class MainActivity : ComponentActivity() {
    private lateinit var appViewModel: AppViewModel
    private val timeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            com.blocknize.app.domain.CivilTimeChanges.notifyChanged()
            appViewModel.refreshClock()
        }
    }
    override fun onStart() {
        super.onStart()
        androidx.core.content.ContextCompat.registerReceiver(this, timeReceiver,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_TIME_CHANGED)
                addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
                addAction(android.content.Intent.ACTION_DATE_CHANGED)
            }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(timeReceiver)
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[AppViewModel::class.java]
        appViewModel = model
        setContent {
            val accent by model.accent.collectAsStateWithLifecycle()
            BlockNizeTheme(accentColor = accent) {
                BlockNizeApp(
                    modifier = Modifier.fillMaxSize(),
                    model = model
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.blocknize.app.domain.CivilTimeChanges.notifyChanged()
        if (::appViewModel.isInitialized) appViewModel.onAppResumed()
        val widgetIds = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, CurrentTaskWidgetReceiver::class.java))
        if (widgetIds.isNotEmpty()) {
            WidgetRefreshWorker.ensurePeriodic(this)
            WidgetRefreshWorker.request(this)
        }
    }
}
