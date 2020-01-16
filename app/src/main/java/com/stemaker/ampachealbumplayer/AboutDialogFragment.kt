package com.stemaker.ampachealbumplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import android.webkit.WebView
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import java.util.*

class AboutDialogFragment : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val v = inflater.inflate(R.layout.fragment_about_dialog, container, false)
        val btnOk: Button = v.findViewById(R.id.ok_button)
        btnOk.setOnClickListener(object: View.OnClickListener {
            override fun onClick(btn: View) {
                dismiss()
            }
        })
        val btnLic: Button = v.findViewById(R.id.license_button)
        btnLic.setOnClickListener(object: View.OnClickListener {
            override fun onClick(btn: View) {
                val intent = Intent(activity, OssLicensesMenuActivity::class.java).apply {}
                startActivity(intent)
            }
        })
        val wv = v.findViewById<WebView>(R.id.about_webview)
        Log.d("bla blubber", "${Locale.getDefault().language}")
        if(Locale.getDefault().language == "de")
            wv.loadUrl("file:///android_asset/about_de.html")
        else
            wv.loadUrl("file:///android_asset/about_en.html")
        return v
    }
}
