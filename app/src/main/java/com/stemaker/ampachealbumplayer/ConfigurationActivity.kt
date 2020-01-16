package com.stemaker.ampachealbumplayer

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Exception

class ConfigurationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        findViewById<EditText>(R.id.config_ampache_server).setText(Configuration.serverUrl)
        findViewById<EditText>(R.id.config_ampache_user).setText(Configuration.user)

        if(android.os.Build.VERSION.SDK_INT < 19) {
            GlobalScope.launch(Dispatchers.Main) {
                val answer = showConfirmationDialog(getString(R.string.old_dev_no_enc), this@ConfigurationActivity)
                if (answer != AlertDialog.BUTTON_POSITIVE) {
                    this@ConfigurationActivity.finish()
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_HOME)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
        }
    }

    fun onClickSave(@Suppress("UNUSED_PARAMETER") saveButton: View) {
        Configuration.serverUrl = findViewById<EditText>(R.id.config_ampache_server).text.toString()
        Configuration.user = findViewById<EditText>(R.id.config_ampache_user).text.toString()
        try {
            Configuration.password = findViewById<EditText>(R.id.config_ampache_password).text.toString()
        } catch (e: Exception) {
            val toast = Toast.makeText(this@ConfigurationActivity, e.message, Toast.LENGTH_LONG)
            toast.show()
        }
        Configuration.save()
        val intent = Intent(this, MainActivity::class.java).apply {}
        startActivity(intent)
    }
}
