package com.ranit.thingcount

import android.app.Application
import com.ranit.thingcount.data.drive.GoogleDriveSyncManager
import com.ranit.thingcount.data.local.AppDatabase
import com.ranit.thingcount.data.repository.CounterRepository
import com.ranit.thingcount.widget.CounterWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ThingCountApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: CounterRepository
        private set

    lateinit var driveSyncManager: GoogleDriveSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        repository = CounterRepository(database)
        driveSyncManager = GoogleDriveSyncManager(this, database)

        CoroutineScope(Dispatchers.IO).launch {
            repository.checkAutoResets()
        }

        // Keep home-screen widgets in sync with every change made inside the app.
        // collectLatest + a short delay coalesces rapid updates (e.g. quick taps).
        CoroutineScope(Dispatchers.IO).launch {
            repository.activeCounters.collectLatest {
                delay(150)
                CounterWidgetProvider.refreshAllWidgets(this@ThingCountApp)
            }
        }
    }

    companion object {
        lateinit var instance: ThingCountApp
            private set
    }
}
