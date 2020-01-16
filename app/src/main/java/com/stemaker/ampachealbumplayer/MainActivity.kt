package com.stemaker.ampachealbumplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.stemaker.ampachealbumplayer.musicdb.MusicDb
import com.stemaker.ampachealbumplayer.player.AmpachePlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Exception
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val PERMISSION_CODE_REQUEST_INTERNET = 1
private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    var internetPermissionContinuation: Continuation<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PlayerNotificationHandler.initialize()
        Configuration.initialize()
        val progressBar = findViewById<ProgressBar>(R.id.ampache_init_progressbar)
        GlobalScope.launch(Dispatchers.Main) {
            progressBar.progress = 0
            checkAndObtainInternetPermission()
            progressBar.progress = 5
            if (!Configuration.loginDataAvailable()) {
                val toast =
                    Toast.makeText(this@MainActivity, R.string.no_config, Toast.LENGTH_LONG)
                toast.show()
                showSettingsActivity()
            } else {
                findViewById<TextView>(R.id.entryscreen_info_headline).setText("Trying to connect to ${Configuration.serverUrl}")
                var connected = false
                try {
                    MusicDb.connect()
                    progressBar.progress = 50
                    findViewById<TextView>(R.id.entryscreen_info_headline).setText("Connected to ${Configuration.serverUrl}")
                    findViewById<TextView>(R.id.entryscreen_info_detail).setText("${MusicDb.numAlbums} Albums")
                    connected = true
                } catch(e: Exception) {
                    Log.w(TAG, "Exception occured during trying to connect to Ampache server: ${e.message}")
                    findViewById<TextView>(R.id.entryscreen_info_headline).setText("Connection to ${Configuration.serverUrl} failed.")
                    findViewById<TextView>(R.id.entryscreen_info_detail).setText("Error: ${e.message}")
                }
                if(connected) {
                    try {
                        findViewById<TextView>(R.id.entryscreen_info_headline).setText("Retrieving Album information")
                        MusicDb.initialize()
                        progressBar.progress = 98
                        findViewById<TextView>(R.id.entryscreen_info_headline).setText("Connected to ${Configuration.serverUrl}, Initialization done")
                        findViewById<TextView>(R.id.entryscreen_info_detail).setText("${MusicDb.numAlbums} Albums")
                        startPlayerService()
                        showAlbumViewActivity()
                    } catch(e: Exception) {
                        findViewById<TextView>(R.id.entryscreen_info_headline).setText("Connection established, but failed to initialize")
                        findViewById<TextView>(R.id.entryscreen_info_detail).setText("Error: ${e.message}")
                        connected = false
                    }
                } else {
                    showSettingsActivity()
                }
            }
        }
    }

    suspend fun checkAndObtainInternetPermission() {
        var internetPermissionGranted = true
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.INTERNET
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("MainActivity", "No permission for Internet, requesting it")
            /* TODO Add a Dialog that explains why we need network access */
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.INTERNET),
                PERMISSION_CODE_REQUEST_INTERNET
            )
            internetPermissionGranted = suspendCoroutine<Boolean> {
                internetPermissionContinuation = it
            }
        }
        if (internetPermissionGranted)
            Log.d("MainActivity", "Permission for Internet is granted")
        else {
            val toast =
                Toast.makeText(this@MainActivity, R.string.no_internet_access_abort, Toast.LENGTH_LONG)
            toast.show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE_REQUEST_INTERNET) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                internetPermissionContinuation!!.resume(true)
            } else {
                internetPermissionContinuation!!.resume(false)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.settings -> {
                showSettingsActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun showSettingsActivity() {
        val intent = Intent(this, ConfigurationActivity::class.java).apply {}
        startActivity(intent)
        Log.d("MainActivity::showSettingsActivity", "called")
    }

    fun showAlbumViewActivity() {
        val intent = Intent(this, AlbumViewActivity::class.java).apply {}
        startActivity(intent)
        Log.d("MainActivity::showAlbumViewActivity", "called")
    }

    private fun startPlayerService() {
        val intent = Intent(this, AmpachePlayerService::class.java).apply {}
        startService(intent)
        Log.d(TAG, ": startPlayerService")
    }
}
