package com.stemaker.ampachealbumplayer

import android.app.Application
import android.content.Context

class AmpacheAlbumPlayerApp(): Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = getApplicationContext()
    }

    companion object {
        lateinit var appContext: Context
    }

}