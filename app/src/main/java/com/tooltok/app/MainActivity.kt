package com.tooltok.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
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
    private var baseWebViewBottomPadding = 0
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var bannerAdView: AdView? = null
    private var gestureStartY = 0f
    private var gestureStartScrollY = 0
    private var gestureStartedNearTop = false
    private val topEdgeRefreshZonePx by lazy { (72 * resources.displayMetrics.density).toInt() }
    private val refreshTouchSlopPx by lazy { ViewConfiguration.get(this).scaledTouchSlop }
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
        baseWebViewBottomPadding = webView.paddingBottom

        swipeRefreshLayout.setOnRefreshListener { webView.reload() }
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ -> canWebViewScrollUp() }
        swipeRefreshLayout.isEnabled = false
        retryButton.setOnClickListener {
            showError(false)
            webView.reload()
        }
        bannerAdContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateWebViewBottomInset()
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
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartY = event.y
                    gestureStartScrollY = webView.scrollY
                    gestureStartedNearTop = event.y <= topEdgeRefreshZonePx
                    swipeRefreshLayout.isEnabled = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - gestureStartY
                    val pulledDownEnough = deltaY > refreshTouchSlopPx
                    // Refresh only on an intentional top-edge pull-down gesture when already at page top.
                    swipeRefreshLayout.isEnabled =
                        gestureStartedNearTop &&
                            gestureStartScrollY == 0 &&
                            !canWebViewScrollUp() &&
                            pulledDownEnough
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    swipeRefreshLayout.isEnabled = false
                }
            }
            false
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
                swipeRefreshLayout.isEnabled = false
                progressBar.visibility = View.GONE
                updateWebViewBottomInset()
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
            updateWebViewBottomInset()
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
            updateWebViewBottomInset()
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
                    updateWebViewBottomInset()
                    Log.d(TAG, "Banner ad loaded.")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    bannerAdContainer.visibility = View.GONE
                    updateWebViewBottomInset()
                    Log.w(TAG, "Banner ad failed to load: ${adError.message}")
                }
            }
        }

        bannerAdContainer.removeAllViews()
        bannerAdContainer.addView(bannerAdView)
        bannerAdContainer.visibility = View.GONE
        updateWebViewBottomInset()

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
        updateWebViewBottomInset()
    }

    internal fun canWebViewScrollUp(): Boolean = webView.canScrollVertically(-1)

    internal fun updateBannerInsetForTesting(heightPx: Int, visible: Boolean) {
        bannerAdContainer.layoutParams =
            bannerAdContainer.layoutParams.apply {
                height = if (visible) heightPx else ViewGroup.LayoutParams.WRAP_CONTENT
            }
        bannerAdContainer.visibility = if (visible) View.VISIBLE else View.GONE
        bannerAdContainer.measure(
            View.MeasureSpec.makeMeasureSpec(webView.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx.coerceAtLeast(0), View.MeasureSpec.EXACTLY)
        )
        bannerAdContainer.layout(
            bannerAdContainer.left,
            bannerAdContainer.top,
            bannerAdContainer.left + bannerAdContainer.measuredWidth,
            bannerAdContainer.top + bannerAdContainer.measuredHeight
        )
        updateWebViewBottomInset()
    }

    internal fun currentWebViewBottomInset(): Int = webView.paddingBottom

    private fun updateWebViewBottomInset() {
        val bannerInset = if (bannerAdContainer.visibility == View.VISIBLE) bannerAdContainer.height else 0
        val desiredBottomPadding = baseWebViewBottomPadding + bannerInset
        if (webView.paddingBottom != desiredBottomPadding) {
            webView.setPadding(
                webView.paddingLeft,
                webView.paddingTop,
                webView.paddingRight,
                desiredBottomPadding
            )
            webView.clipToPadding = false
        }
        injectBottomInsetIntoPage(bannerInset)
    }

    private fun injectBottomInsetIntoPage(insetPx: Int) {
        val script =
            """
            (function() {
              var inset = '${insetPx}px';
              var styleId = 'tooltok-native-bottom-inset';
              var style = document.getElementById(styleId);
              if (!style) {
                style = document.createElement('style');
                style.id = styleId;
                document.head.appendChild(style);
              }
              style.textContent =
                ':root {' +
                '--tooltok-native-bottom-inset:' + inset + ';' +
                'scroll-padding-bottom: calc(' + inset + ' + env(safe-area-inset-bottom, 0px));' +
                '}' +
                'body {' +
                'padding-bottom: calc(' + inset + ' + env(safe-area-inset-bottom, 0px)) !important;' +
                '}';
            })();
            """.trimIndent()
        webView.evaluateJavascript(script, null)
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
