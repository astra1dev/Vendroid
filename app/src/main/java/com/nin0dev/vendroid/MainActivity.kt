package com.nin0dev.vendroid

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.KeyEvent
import android.webkit.ValueCallback
import android.webkit.WebView
import com.nin0dev.vendroid.HttpClient.fetchVencord
import com.nin0dev.vendroid.Logger.e
import java.io.IOException

class MainActivity : Activity() {
    private var wvInitialized = false
    private var wv: WebView? = null
    @JvmField
    var filePathCallback: ValueCallback<Array<Uri?>?>? = null

    @SuppressLint("SetJavaScriptEnabled") // mad? watch this swag
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this@MainActivity, WelcomeActivity::class.java))
        // https://developer.chrome.com/docs/devtools/remote-debugging/webviews/
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContentView(R.layout.activity_main)
        wv = findViewById(R.id.webview)!!
        explodeAndroid()
        wv!!.setWebViewClient(VWebviewClient())
        wv!!.setWebChromeClient(VChromeClient(this))
        val s = wv?.getSettings()!!
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        wv?.addJavascriptInterface(VencordNative(this, wv!!), "VencordMobileNative")
        try {
            fetchVencord(this)
        } catch (ex: IOException) {

        }
        val intent = intent
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null) handleUrl(intent.data)
        } else {
            wv!!.loadUrl("https://discord.com/app")
        }
        wvInitialized = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && wv != null) {
            runOnUiThread { wv!!.evaluateJavascript("VencordMobile.onBackPress()") { r: String -> if ("false" == r) onBackPressed() } }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        if (requestCode != FILECHOOSER_RESULTCODE || filePathCallback == null) return
        if (resultCode != RESULT_OK || intent == null) {
            filePathCallback!!.onReceiveValue(null)
        } else {
            var uris: Array<Uri?>?
            try {
                val clipData = intent.clipData
                if (clipData != null) { // multiple items
                    uris = arrayOfNulls(clipData.itemCount)
                    for (i in 0 until clipData.itemCount) {
                        uris[i] = clipData.getItemAt(i).uri
                    }
                } else { // single item
                    uris = arrayOf(intent.data)
                }
            } catch (ex: Exception) {
                e("Error during file upload", ex)
                uris = null
            }
            filePathCallback!!.onReceiveValue(uris)
        }
        filePathCallback = null
    }

    private fun explodeAndroid() {
        StrictMode.setThreadPolicy(
                ThreadPolicy.Builder() // trolley
                        .permitNetwork()
                        .build()
        )
    }

    fun handleUrl(url: Uri?) {
        if (url != null) {
            if (url.authority != "discord.com") return
            if (!wvInitialized) {
                wv!!.loadUrl(url.toString())
            } else {
                wv!!.evaluateJavascript("Vencord.Webpack.Common.NavigationRouter.transitionTo(\"" + url.path + "\")", null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        data?.let { handleUrl(it) }
    }

    companion object {
        const val FILECHOOSER_RESULTCODE = 8485
    }
}