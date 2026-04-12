package com.tooltok.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

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
    private val interstitialAdPolicy =
        InterstitialAdPolicy(
            minMillisBetweenAds = BuildConfig.ADMOB_MIN_MILLIS_BETWEEN_ADS,
            routeChancePercent = BuildConfig.ADMOB_ROUTE_CHANCE_PERCENT,
            scrollChancePercent = BuildConfig.ADMOB_SCROLL_CHANCE_PERCENT,
        )
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var isInterstitialShowing = false
    private val adCheckHandler = Handler(Looper.getMainLooper())
    private val sessionAdCheckRunnable =
        object : Runnable {
            override fun run() {
                maybeShowInterstitialForSessionTick()
                scheduleSessionAdCheck()
            }
        }
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

        swipeRefreshLayout.setOnRefreshListener { webView.reload() }
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        retryButton.setOnClickListener {
            showError(false)
            webView.reload()
        }

        configureBackNavigation()
        configureWebView()
        initializeAds()

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
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            maybeShowInterstitialForScroll(scrollY)
        }

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
                maybeShowInterstitialForRoute(url)
                loadInterstitialIfNeeded()
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

    private fun initializeAds() {
        MobileAds.initialize(this)
        Log.d(TAG, "Mobile Ads initialized.")
        loadInterstitialIfNeeded()
        scheduleSessionAdCheck()
    }

    private fun loadInterstitialIfNeeded() {
        if (isInterstitialLoading || interstitialAd != null) return
        isInterstitialLoading = true
        Log.d(TAG, "Loading interstitial.")

        InterstitialAd.load(
            this,
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial loaded.")
                    interstitialAd = ad.apply {
                        fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                isInterstitialShowing = true
                                interstitialAdPolicy.onAdShown(SystemClock.elapsedRealtime())
                                interstitialAd = null
                                Log.d(TAG, "Interstitial shown.")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                isInterstitialShowing = false
                                Log.d(TAG, "Interstitial dismissed.")
                                loadInterstitialIfNeeded()
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                                isInterstitialShowing = false
                                interstitialAd = null
                                Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                                loadInterstitialIfNeeded()
                            }
                        }
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isInterstitialLoading = false
                    interstitialAd = null
                    Log.w(TAG, "Interstitial failed to load: ${adError.message}")
                }
            }
        )
    }

    private fun maybeShowInterstitialForRoute(url: String?) {
        if (isInterstitialShowing) return
        val nowMs = SystemClock.elapsedRealtime()
        if (interstitialAdPolicy.shouldShowForRoute(url, nowMs)) {
            showInterstitial()
        }
    }

    private fun maybeShowInterstitialForScroll(scrollY: Int) {
        if (isInterstitialShowing) return
        val nowMs = SystemClock.elapsedRealtime()
        if (interstitialAdPolicy.shouldShowForScroll(webView.url, scrollY, nowMs)) {
            showInterstitial()
        }
    }

    private fun maybeShowInterstitialForSessionTick() {
        if (isInterstitialShowing) return
        val nowMs = SystemClock.elapsedRealtime()
        if (interstitialAdPolicy.shouldShowForSessionTick(webView.url, nowMs)) {
            Log.d(TAG, "Session timer requested interstitial.")
            showInterstitial()
        }
    }

    private fun showInterstitial() {
        val ad = interstitialAd ?: return
        Log.d(TAG, "Showing interstitial.")
        ad.show(this)
    }

    private fun scheduleSessionAdCheck() {
        adCheckHandler.removeCallbacks(sessionAdCheckRunnable)
        adCheckHandler.postDelayed(
            sessionAdCheckRunnable,
            BuildConfig.ADMOB_SESSION_CHECK_INTERVAL_MILLIS
        )
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

    override fun onResume() {
        super.onResume()
        scheduleSessionAdCheck()
    }

    override fun onPause() {
        adCheckHandler.removeCallbacks(sessionAdCheckRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        adCheckHandler.removeCallbacks(sessionAdCheckRunnable)
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        webView.destroy()
        super.onDestroy()
    }
}
