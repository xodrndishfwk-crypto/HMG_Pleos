package com.example.starlog2

import android.app.Application
import android.content.pm.ApplicationInfo
import timber.log.Timber
import com.example.starlog2.starlog.demo.DemoRoute
import com.example.starlog2.starlog.journey.JourneyController
import com.example.starlog2.starlog.storage.SqliteMemoryRepository
import java.util.UUID

class StarlogApplication : Application() {
    lateinit var journeyController: JourneyController
        private set

    override fun onCreate() {
        super.onCreate()
        // New demo journeys replay the Daegu → Mungyeong Saejae storyboard route.
        val route = DemoRoute.MUNGYEONG_SAEJAE
        journeyController = JourneyController(
            repository = SqliteMemoryRepository(applicationContext),
            tripIdFactory = { now -> "${route.tripIdPrefix()}$now-${UUID.randomUUID()}" },
            route = route,
        )
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
