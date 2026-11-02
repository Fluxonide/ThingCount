package com.ranit.thingcount

import android.app.Application
import com.ranit.thingcount.data.drive.GoogleDriveSyncManager
import com.ranit.thingcount.data.local.AppDatabase
import com.ranit.thingcount.data.repository.CounterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    }

    companion object {
        lateinit var instance: ThingCountApp
            private set
    }
}
