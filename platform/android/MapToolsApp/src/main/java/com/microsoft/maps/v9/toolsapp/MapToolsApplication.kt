package com.microsoft.maps.v9.toolsapp

import android.os.StrictMode
import androidx.multidex.MultiDexApplication
import org.maplibre.android.MapLibre
import org.maplibre.android.MapStrictMode
import timber.log.Timber
import timber.log.Timber.DebugTree
import org.maplibre.android.log.Logger

class MapToolsApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        initializeLogger()
        initializeStrictMode()
        initializeMapbox()
    }

    private fun initializeLogger() {
        Logger.setLoggerDefinition(TimberLogger())
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }

    private fun initializeStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
    }

    private fun initializeMapbox() {
        MapLibre.getInstance(applicationContext)
        MapStrictMode.setStrictModeEnabled(true)
    }
}
