package com.tooltok.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

class MainActivity : AppCompatActivity() {
    private companion object {
        const val TAG = "ToolTokAds"
    }

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var errorTextView: TextView
    private lateinit var retryButton: Button
    private lateinit var bannerAdContainer: FrameLayout
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var bannerAdView: AdView? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val value = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileChooserCallback?.onReceiveValue(value)
            fileChooserCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        errorTextView = findViewById(R.id.errorTextView)
        retryButton = findViewById(R.id.retryButton)
        bannerAdContainer = findViewById(R.id.bannerAdContainer)

        swipeRefreshLayout.setOnRefreshListener { webView.reload() }
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        retryButton.setOnClickListener {
            showError(false)
            webView.reload()
        }

        configureBackNavigation()
        configureWebView()
        configureBannerAds()

        if (savedInstanceState == null) {
            webView.loadUrl(AppConfig.BASE_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString ToolTokAndroid/1.0"
        }

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (AppUrlPolicy.isInternal(url)) return false
                return openExternal(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                showError(false)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    swipeRefreshLayout.isRefreshing = false
                    progressBar.visibility = View.GONE
                    errorTextView.text = getString(R.string.error_loading_page)
                    showError(true)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val chooserIntent = fileChooserParams?.createIntent() ?: return false
                return try {
                    fileChooserLauncher.launch(chooserIntent)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener(
            DownloadListener { url, _, _, _, _ ->
                if (!url.isNullOrBlank()) {
                    openExternal(url)
                }
            }
        )
    }

    private fun configureBannerAds() {
        if (!AdRuntimeConfig.adsEnabled() || BuildConfig.ADMOB_BANNER_AD_UNIT_ID.isBlank()) {
            bannerAdContainer.visibility = View.GONE
            return
        }

        bannerAdContainer.post {
            loadBannerAd()
        }
    }

    private fun loadBannerAd() {
        if (!AdRuntimeConfig.adsEnabled() || BuildConfig.ADMOB_BANNER_AD_UNIT_ID.isBlank()) {
            bannerAdContainer.visibility = View.GONE
            destroyBannerAd()
            return
        }

        val adWidth = calculateBannerAdWidth()
        if (adWidth <= 0) return

        destroyBannerAd()

        bannerAdView = AdView(this).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, adWidth))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    bannerAdContainer.visibility = View.VISIBLE
                    Log.d(TAG, "Banner ad loaded.")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    bannerAdContainer.visibility = View.GONE
                    Log.w(TAG, "Banner ad failed to load: ${adError.message}")
                }
            }
        }

        bannerAdContainer.removeAllViews()
        bannerAdContainer.addView(bannerAdView)
        bannerAdContainer.visibility = View.GONE

        Log.d(TAG, "Loading banner ad.")
        bannerAdView?.loadAd(AdRequest.Builder().build())
    }

    private fun calculateBannerAdWidth(): Int {
        val displayMetrics = resources.displayMetrics
        val adWidthPixels =
            if (bannerAdContainer.width > 0) bannerAdContainer.width.toFloat() else displayMetrics.widthPixels.toFloat()
        return (adWidthPixels / displayMetrics.density).toInt()
    }

    private fun destroyBannerAd() {
        bannerAdContainer.removeAllViews()
        bannerAdView?.destroy()
        bannerAdView = null
    }

    private fun openExternal(url: String): Boolean {
        if (!AppUrlPolicy.shouldOpenExternally(url)) return false
        return try {
            startActivity(ExternalIntentFactory.create(url))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun showError(show: Boolean) {
        errorView.visibility = if (show) View.VISIBLE else View.GONE
        swipeRefreshLayout.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (AdRuntimeConfig.adsEnabled() && BuildConfig.ADMOB_BANNER_AD_UNIT_ID.isNotBlank()) {
            bannerAdContainer.post { loadBannerAd() }
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAdView?.resume()
    }

    override fun onPause() {
        bannerAdView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        destroyBannerAd()
        webView.destroy()
        super.onDestroy()
    }
}
