package com.radiant.daw

import android.app.Application
import com.rmsl.juce.Java

class RadiantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Java.initialiseJUCE(this)
        NativeBridge.initialiseJuceGui()
    }
}
