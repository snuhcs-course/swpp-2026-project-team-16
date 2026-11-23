package com.example.runtime

import android.app.Application
import com.example.runtime.data.push.Push
import com.example.runtime.data.remote.NetworkStatus
import com.example.runtime.data.telemetry.Telemetry

class RunTimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkStatus.initialize(this)
        Telemetry.initialize(this)
        Push.initialize(this)
    }
}
