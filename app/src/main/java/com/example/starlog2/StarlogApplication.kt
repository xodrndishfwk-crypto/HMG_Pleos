package com.example.starlog2

import android.app.Application
import android.content.pm.ApplicationInfo
import timber.log.Timber
import com.example.starlog2.starlog.journey.JourneyController
import com.example.starlog2.starlog.storage.SqliteMemoryRepository

class StarlogApplication : Application() {
    lateinit var journeyController: JourneyController
        private set

    override fun onCreate() {
        super.onCreate()
        journeyController = JourneyController(SqliteMemoryRepository(applicationContext))
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
