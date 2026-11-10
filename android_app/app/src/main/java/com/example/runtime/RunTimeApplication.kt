package com.example.runtime

import android.app.Application
import com.example.runtime.data.push.Push

class RunTimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.initialize(this)
    }
}
