package com.stemaker.ampachealbumplayer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import com.google.gson.Gson
import java.io.*

object StorageHandler {
    var inited: Boolean = false
    var gson = Gson()

    fun initialize() {
        if(inited == false) {
            inited = true
            // Read the configuration
            Log.d("StorageHandler::initialize", "start")
            loadConfigurationFromFile()

            Log.d("StorageHandler::myInit", "done")
        }
    }

    fun loadConfigurationFromFile() {
        Log.d("StorageHandler.loadConfigurationFromFile", "")
        val c: Context = AmpacheAlbumPlayerApp.appContext
        try {
            val fIn = c.openFileInput("configuration.json")
            val isr = InputStreamReader(fIn)
            Configuration.store = gson.fromJson(isr, ConfigurationStore::class.java)
            Log.d("StorageHandler.loadConfigurationFromFile", "Server URL: ${Configuration.serverUrl}")
        }
        catch (e: FileNotFoundException){
            Log.d("StorageHandler.loadConfigurationFromFile", "No configuration file found, creating a new one")
            saveConfigurationToFile()
        }
    }

    fun saveConfigurationToFile() {
        val c: Context = AmpacheAlbumPlayerApp.appContext
        val fOut = c.openFileOutput("configuration.json", MODE_PRIVATE)
        Log.d("StorageHandler.saveConfigurationToFile", "called")
        val osw = OutputStreamWriter(fOut)
        gson.toJson(Configuration.store, osw)
        osw.close()
    }
}

