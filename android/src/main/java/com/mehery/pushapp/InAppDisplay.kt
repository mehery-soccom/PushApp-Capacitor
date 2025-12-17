package com.mehery.pushapp

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.pushapp.ionic.R
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt



import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.core.view.setMargins
import kotlin.math.roundToInt

class InAppDisplay(private val activity: Activity) {

    // Queue to show multiple in-app items sequentially
    private var queue: List<Map<String, Any>> = emptyList()
    private var currentIndex = 0

    fun showInApps(items: List<Map<String, Any>>) {
        if (items.isEmpty()) return
        queue = items
        currentIndex = 0
        showNext()
    }

    private fun showNext() {
        println("Show next called")
        if (currentIndex >= queue.size) return

        val data = queue[currentIndex]
        currentIndex++

        val code = data["code"] as? String
        val htmlContent = data["html"] as? String
        val horizontal_align = data["horizontal_align"] as? String
        val vertical_align = data["vertical_align"] as? String
        val draggable = data["draggable"] as? Boolean
        val placeholderId = data["placeholderId"] as? String
        val messageId = data["messageId"] as? String
        val filterId = data["filterId"] as? String
        var align = "bottom-right"
        if (horizontal_align != null && vertical_align != null) {
            align = getAlignment(vertical_align, horizontal_align)
        }

        if (htmlContent.isNullOrEmpty() || messageId.isNullOrEmpty() || filterId.isNullOrEmpty()) {
            Log.e("InAppDisplay", "Invalid in-app content")
            showNext()
            return
        }

        // ✅ Send ack when shown
        handleAck(messageId)

        if (code != null) {
            when {
                code.contains("roadblock", ignoreCase = true) ->
                    showRoadblock(messageId,filterId,htmlContent) {
                        handleDismiss(messageId,filterId)   // ✅ Track dismissed when closed
                        showNext()
                    }

                code.contains("banner", ignoreCase = true) ->
                    showBanner(messageId,filterId,htmlContent) {
                        handleDismiss(messageId,filterId)
                        showNext()
                    }

                code.contains("picture-in-picture", ignoreCase = true) ->
                    showPictureInPicture(messageId,filterId,htmlContent, align) {
                        handleDismiss(messageId,filterId)
                        showNext()
                    }

                code.contains("floater", ignoreCase = true) ->
                    showFloater(htmlContent, draggable) {
                        handleDismiss(messageId,filterId)
                        showNext()
                    }

                code.contains("bottomsheet", ignoreCase = true) ->
                    showBottomSheet(messageId,htmlContent,filterId) {
                        handleDismiss(messageId,filterId)
                        showNext()
                    }

                code.contains("inline", ignoreCase = true) -> {
                    if (placeholderId != null) {
                        PushAppPlaceholderManager.dispatchPlaceholderContent(
                            placeholderId,
                            code,
                            htmlContent
                        )
                    }
                    // inline doesn’t really dismiss in the same sense, so just move on
                    showNext()
                }

                else -> {
                    Log.e("InAppDisplay", "Unknown layout type: $code")
                    showNext()
                }
            }
        }
    }


    fun getAlignment(vertical: String, horizontal: String): String {
        val verticalPart = when (vertical) {
            "flex-start" -> "top"
            "center" -> "center"
            "flex-end" -> "bottom"
            else -> "bottom"
        }

        val horizontalPart = when (horizontal) {
            "flex-start" -> "left"
            "center" -> "center"
            "flex-end" -> "right"
            else -> "right"
        }

        return "$verticalPart-$horizontalPart"
    }


    private fun isImageUrl(content: String) = content.startsWith("http") &&
            (content.endsWith(".png") || content.endsWith(".jpg") || content.endsWith(".jpeg")
                    || content.endsWith(".gif") || content.endsWith(".webp"))

    @SuppressLint("AddJavascriptInterface", "SetJavaScriptEnabled")
    private fun createContentView(
        messageId: String,
        content: String,
        onMessage: (String) -> Unit
    ): FrameLayout {
        val frame = FrameLayout(activity)

        if (isImageUrl(content)) {
            val imageView = ImageView(activity)
            Glide.with(activity).load(content).into(imageView)
            frame.addView(
                imageView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        } else {
            val webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = true
                settings.allowContentAccess = true

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        Log.d("InAppDisplay", "console: ${message?.message()}")
                        return true
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        activity.runOnUiThread {
                            request?.grant(request.resources)
                        }
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(msg: String) {
                        Log.d("InAppDisplay", "CTA clicked: $msg")
                        onMessage(msg)
                    }
                }, "InAppBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Inject JS bridge + force autoplay
                        evaluateJavascript(
                            """
(function() {
    console.log(">>> Injecting InAppBridge + Autoplay patch");

    // === CTA Bridge Injection (updated for label param) ===
    window.handleClick = function(eventType, lab, val) {
        console.log(">>> Patched handleClick called", eventType, lab, val);
        var message = JSON.stringify({
          event: eventType,
          timestamp: Date.now(),
          data: { url: "", label: lab, value: val }
        });
        InAppBridge.postMessage(message);
    };


    // === Force Autoplay ===
    var videos = document.querySelectorAll('video');
    videos.forEach(function(v) {
        try {
            v.muted = true; // ensure autoplay allowed
            v.playsInline = true;
            var playPromise = v.play();
            if (playPromise !== undefined) {
                playPromise.then(_ => {
                    console.log("Video autoplay started");
                }).catch(err => {
                    console.warn("Autoplay failed:", err);
                    // Retry after small delay
                    setTimeout(() => v.play().catch(e => console.warn("Retry failed", e)), 500);
                });
            }
        } catch(e) {
            console.warn("Autoplay script error", e);
        }
    });

    // Handle iframe videos (e.g., YouTube / Vimeo)
    var iframes = document.querySelectorAll('iframe[src*="youtube"],iframe[src*="vimeo"]');
    iframes.forEach(function(f) {
        if (f.src.indexOf("autoplay=1") === -1) {
            var sep = f.src.indexOf("?") === -1 ? "?" : "&";
            f.src = f.src + sep + "autoplay=1&mute=1&playsinline=1";
        }
    });
})();
""".trimIndent(),
                            null
                        )
                    }
                }

                loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
            }

            frame.addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        return frame
    }





    private fun getNavigationBarHeight(): Int {
        val resources = activity.resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    // ---------------- Display Types with callback ----------------

    private fun showRoadblock(messageId: String,filterId : String,html: String, onClose: () -> Unit) {
        activity.runOnUiThread {
            val container = FrameLayout(activity).apply {
                setBackgroundColor(Color.parseColor("#B3000000"))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(0, 0, 0, getNavigationBarHeight())
            }

            val webView = createContentView(messageId, html) { msg ->
                try {
                    // Parse the JSON string
                    val json = JSONObject(msg)
                    val event = json.optString("event")
                    val timestamp = json.optLong("timestamp")

                    val data = json.optJSONObject("data")
                    val url = data?.optString("url")
                    val label = data?.optString("label")
                    val value = data?.optString("value")

                    Log.d("InAppDisplay", "Event: $event, Label: $label, Value: $value, URL: $url, Timestamp: $timestamp")
                    if (value != null) {
                        handleCtaClick(messageId,filterId,value)
                        (container.parent as? ViewGroup)?.removeView(container)
                        onClose()
                    }
                } catch (e: JSONException) {
                    Log.e("InAppDisplay", "Failed to parse JSON from WebView: $msg", e)
                }
            }

            container.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

            val closeButton = Button(activity).apply {
                text = "\u2715"
                setTextColor(Color.WHITE)
                textSize = 8f
                setOnClickListener {
                    (container.parent as? ViewGroup)?.removeView(container)
                    onClose()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#80000000"))
                }
            }

            val closeParams = FrameLayout.LayoutParams(dpToPx(30), dpToPx(30)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dpToPx(40)
                rightMargin = dpToPx(20)
            }

            container.addView(closeButton, closeParams)
            activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                .addView(container, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun showBanner(messageId: String,filterId : String,html: String, onClose: () -> Unit) {
        activity.runOnUiThread {
            var webView : FrameLayout? = null
            webView = createContentView(messageId, html) { msg ->
                try {
                    // Parse the JSON string
                    val json = JSONObject(msg)
                    val event = json.optString("event")
                    val timestamp = json.optLong("timestamp")

                    val data = json.optJSONObject("data")
                    val url = data?.optString("url")
                    val label = data?.optString("label")
                    val value = data?.optString("value")

                    Log.d("InAppDisplay", "Event: $event, Label: $label, Value: $value, URL: $url, Timestamp: $timestamp")
                    if (value != null) {
                        handleCtaClick(messageId,filterId,value)
                        if(webView != null){
                            (webView!!.parent as? ViewGroup)?.removeView(webView)
                            onClose()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("InAppDisplay", "Failed to parse JSON from WebView: $msg", e)
                }
            }

            val closeButton = Button(activity).apply {
                text = "\u2715"
                setTextColor(Color.WHITE)
                textSize = 8f
                setOnClickListener {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    onClose()
                }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#80000000")) }
            }
            webView.addView(closeButton, FrameLayout.LayoutParams(dpToPx(30), dpToPx(30)).apply { gravity = Gravity.END or Gravity.TOP })
            activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                .addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(100)).apply { gravity = Gravity.TOP })
        }
    }

//    @SuppressLint("SetJavaScriptEnabled")
@SuppressLint("SetJavaScriptEnabled")
private fun showPictureInPicture(
    messageId: String,
    filterId : String,
    html: String,
    align: String = "bottom-right",
    onClose: () -> Unit = {}
) {
    activity.runOnUiThread {
        // === PiP Container ===
        val pipWrapper = FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // === WebView Setup (with autoplay & permission fixes) ===
        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null) // ✅ Fix blank videos

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    Log.d("PiPWebView", "console: ${message?.message()}")
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    activity.runOnUiThread {
                        request?.grant(request.resources)
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // ✅ Inject autoplay patch with small delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        evaluateJavascript(
                            """
                            (function() {
                                console.log(">>> Injecting PiP Autoplay patch");

                                // --- Autoplay for video tags ---
                                var videos = document.querySelectorAll('video');
                                videos.forEach(function(v) {
                                    try {
                                        v.muted = true;
                                        v.playsInline = true;
                                        var playPromise = v.play();
                                        if (playPromise !== undefined) {
                                            playPromise.then(_ => {
                                                console.log("PiP video autoplay started");
                                            }).catch(err => {
                                                console.warn("PiP autoplay failed:", err);
                                                setTimeout(() => v.play().catch(e => console.warn("Retry failed", e)), 500);
                                            });
                                        }
                                    } catch(e) {
                                        console.warn("PiP video patch error", e);
                                    }
                                });

                                // --- Autoplay for iframes (YouTube/Vimeo) ---
                                var iframes = document.querySelectorAll('iframe[src*="youtube"],iframe[src*="vimeo"]');
                                iframes.forEach(function(f) {
                                    try {
                                        if (f.src.indexOf("autoplay=1") === -1) {
                                            var sep = f.src.indexOf("?") === -1 ? "?" : "&";
                                            f.src = f.src + sep + "autoplay=1&mute=1&playsinline=1";
                                            var src = f.src; f.src = ''; f.src = src;
                                        }
                                    } catch(e) {
                                        console.warn("PiP iframe patch error", e);
                                    }
                                });
                            })();
                            """.trimIndent(),
                            null
                        )
                    }, 500)
                }
            }

            loadDataWithBaseURL("https://example.com", html, "text/html", "UTF-8", null)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // === Add WebView to wrapper ===
        pipWrapper.addView(webView)

        // === Maximize button overlay ===
        val maximizeButton = ImageView(activity).apply {
            setImageResource(R.mipmap.maximize)
            setBackgroundColor(Color.parseColor("#66000000"))
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dpToPx(6)
                marginEnd = dpToPx(6)
            }

            setOnClickListener {
                val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                rootView.removeView(pipWrapper)
                showRoadblock(messageId, filterId,html) { onClose() }
            }
        }

        pipWrapper.addView(maximizeButton)

        // === Screen dimensions (1/3 PiP) ===
        val displayMetrics = activity.resources.displayMetrics
        val width = displayMetrics.widthPixels / 3
        val height = displayMetrics.heightPixels / 3

        // === Position based on align string ===
        val gravity = when (align.lowercase()) {
            "top-left" -> Gravity.TOP or Gravity.START
            "top-center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            "top-right" -> Gravity.TOP or Gravity.END
            "center-left" -> Gravity.CENTER_VERTICAL or Gravity.START
            "center-center", "center" -> Gravity.CENTER
            "center-right" -> Gravity.CENTER_VERTICAL or Gravity.END
            "bottom-left" -> Gravity.BOTTOM or Gravity.START
            "bottom-center" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            "bottom-right" -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.BOTTOM or Gravity.END
        }

        // === Attach to window ===
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(
            pipWrapper,
            FrameLayout.LayoutParams(width, height).apply {
                this.gravity = gravity
                topMargin = if (gravity and Gravity.TOP != 0) dpToPx(20) else 0
                bottomMargin = if (gravity and Gravity.BOTTOM != 0) dpToPx(20) else 0
                marginStart = if (gravity and Gravity.START != 0) dpToPx(20) else 0
                marginEnd = if (gravity and Gravity.END != 0) dpToPx(20) else 0
            }
        )
    }
}


    @SuppressLint("SetJavaScriptEnabled")
    private fun showFloater(html: String, draggable: Boolean?, onClose: () -> Unit) {
        println("Draggable: $draggable")
        activity.runOnUiThread {
            // === Draggable container ===
            val container = object : FrameLayout(activity) {
                private var dX = 0f
                private var dY = 0f
                private var dragging = false

                override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                    return draggable == true
                }

                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (draggable != true) return super.onTouchEvent(event)

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            dX = x - event.rawX
                            dY = y - event.rawY
                            dragging = true
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (dragging) {
                                val newX = event.rawX + dX
                                val newY = event.rawY + dY
                                val maxX = activity.resources.displayMetrics.widthPixels - width
                                val maxY = activity.resources.displayMetrics.heightPixels - height
                                x = newX.coerceIn(0f, maxX.toFloat())
                                y = newY.coerceIn(0f, maxY.toFloat())
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            dragging = false
                            return true
                        }
                    }
                    return super.onTouchEvent(event)
                }
            }.apply {
                setBackgroundColor(Color.TRANSPARENT)
            }

            // === WebView setup ===
            val webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null) // ✅ Use hardware accel for video

                // === WebChromeClient ===
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        Log.d("FloaterWebView", "console: ${message?.message()}")
                        return true
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        activity.runOnUiThread {
                            request?.grant(request.resources)
                        }
                    }
                }

                // === WebViewClient with delayed autoplay patch ===
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Inject autoplay patch with slight delay to ensure DOM ready
                        Handler(Looper.getMainLooper()).postDelayed({
                            evaluateJavascript(
                                """
                            (function() {
                                console.log(">>> Injecting Floater Autoplay patch (delayed)");

                                // --- Force autoplay for all <video> tags ---
                                var videos = document.querySelectorAll('video');
                                videos.forEach(function(v) {
                                    try {
                                        v.muted = true;
                                        v.playsInline = true;
                                        var playPromise = v.play();
                                        if (playPromise !== undefined) {
                                            playPromise.then(_ => {
                                                console.log("Floater video autoplay started");
                                            }).catch(err => {
                                                console.warn("Floater autoplay failed:", err);
                                                setTimeout(() => v.play().catch(e => console.warn("Retry failed", e)), 500);
                                            });
                                        }
                                    } catch(e) {
                                        console.warn("Floater autoplay error", e);
                                    }
                                });

                                // --- Handle iframe embeds (YouTube, Vimeo, etc.) ---
                                var iframes = document.querySelectorAll('iframe[src*="youtube"],iframe[src*="vimeo"]');
                                iframes.forEach(function(f) {
                                    try {
                                        if (f.src.indexOf("autoplay=1") === -1) {
                                            var sep = f.src.indexOf("?") === -1 ? "?" : "&";
                                            f.src = f.src + sep + "autoplay=1&mute=1&playsinline=1";
                                            // Force reload after param change
                                            var src = f.src; f.src = ''; f.src = src;
                                        }
                                    } catch(e) {
                                        console.warn("Iframe autoplay patch error", e);
                                    }
                                });
                            })();
                            """.trimIndent(),
                                null
                            )
                        }, 500)
                    }
                }

                // Use https://example.com as baseURL to avoid mixed-content issues
                loadDataWithBaseURL("https://example.com", html, "text/html", "UTF-8", null)

                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            container.addView(webView)

            // === Container position and size ===
            val widthPx = dpToPx(150)
            val heightPx = dpToPx(150)
            container.x = (activity.resources.displayMetrics.widthPixels - widthPx - dpToPx(16)).toFloat()
            container.y = (activity.resources.displayMetrics.heightPixels - heightPx - dpToPx(80)).toFloat()

            // === Add to root ===
            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(container, FrameLayout.LayoutParams(widthPx, heightPx))
            container.bringToFront()
        }
    }



    private fun showBottomSheet(messageId: String,filterId : String,html: String, onClose: () -> Unit) {
        activity.runOnUiThread {
            val dialog = BottomSheetDialog(activity)
            val container = FrameLayout(activity)

            // Create WebView
            val webView = createContentView(messageId, html) { msg ->
                try {
                    // Parse the JSON string
                    val json = JSONObject(msg)
                    val event = json.optString("event")
                    val timestamp = json.optLong("timestamp")

                    val data = json.optJSONObject("data")
                    val url = data?.optString("url")
                    val label = data?.optString("label")
                    val value = data?.optString("value")

                    Log.d("InAppDisplay", "Event: $event, Label: $label, Value: $value, URL: $url, Timestamp: $timestamp")
                    if (value != null) {
                        handleCtaClick(messageId,filterId,value)
                        dialog.dismiss()
                    }
                } catch (e: JSONException) {
                    Log.e("InAppDisplay", "Failed to parse JSON from WebView: $msg", e)
                }
            }


            // WebView fills the container
            val webViewParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Close button
            val closeButton = Button(activity).apply {
                text = "\u2715"
                setTextColor(Color.WHITE)
                textSize = 14f
                setOnClickListener { dialog.dismiss() }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#80000000"))
                }
            }

            val closeParams = FrameLayout.LayoutParams(
                dpToPx(36), dpToPx(36)
            ).apply {
                gravity = Gravity.END or Gravity.TOP
                topMargin = dpToPx(8)
                marginEnd = dpToPx(8)
            }

            // Add views
            container.addView(webView, webViewParams)
            container.addView(closeButton, closeParams)
            dialog.setContentView(container)

            // Force BottomSheetDialog to 60% of screen height
            dialog.setOnShowListener { dlg ->
                val d = dlg as BottomSheetDialog
                val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let { sheet ->
                    val behavior = BottomSheetBehavior.from(sheet)
                    val screenHeight = Resources.getSystem().displayMetrics.heightPixels
                    val desiredHeight = (screenHeight * 0.5).toInt()

                    sheet.layoutParams.height = desiredHeight
                    sheet.requestLayout()
                    behavior.peekHeight = desiredHeight
                    behavior.isFitToContents = true
                }
            }

            dialog.setOnDismissListener { onClose() }
            dialog.show()
        }
    }

    fun handleDismiss(messageId: String,filterId : String) {
        // Call the track API when the in-app is dismissed
        PushApp.getInstance().trackInAppEvent(
            messageId = messageId,
            filterId = filterId,
            event = "dismissed",
            ctaId = null
        )
    }

    fun handleCtaClick(messageId: String,filterId : String, ctaId: String) {
        PushApp.getInstance().trackInAppEvent(
            messageId = messageId,
            filterId = filterId,
            event = "cta",
            ctaId = ctaId
        )
    }

    fun handleAck(messageId: String) {
        // Acknowledge message delivery
        PushApp.getInstance().ackInApp(
            messageId = messageId
        )
    }

    // ⭐️ NEW: Show tooltip at registered target position
    fun showTooltipAtPosition(
        activity: Activity,
        tooltipTarget: TooltipTargetInfo,
        messageId: String,
        filterId: String,
        title: String,
        message: String,
        icon: String? = null,
        bgColor: String = "#000000",
        widthPercent: Int = 60,
        line1FontSize: Int = 14,
        line2FontSize: Int = 12,
        line1FontColor: String? = null,
        line2FontColor: String? = null
    ) {
        activity.runOnUiThread {
            try {
                val TAG = "InAppDisplay"

                val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                if (rootView == null) {
                    Log.e(TAG, "❌ Root view is null, cannot show tooltip")
                    return@runOnUiThread
                }

                findWebView(rootView)?.setBackgroundColor(Color.TRANSPARENT)

                val bgColorInt = try { Color.parseColor(bgColor) } catch (e: Exception) { Color.BLACK }
                val titleColor = line1FontColor?.let { try { Color.parseColor(it) } catch (e: Exception) { Color.WHITE } } ?: Color.WHITE
                val messageColor = line2FontColor?.let { try { Color.parseColor(it) } catch (e: Exception) { Color.WHITE } } ?: Color.WHITE

                val screenWidth = activity.resources.displayMetrics.widthPixels
                val screenHeight = activity.resources.displayMetrics.heightPixels
                val dpToPx: (Int) -> Int = { dp -> dpToPxNew(activity, dp) }
                val scale: Float = activity.resources.displayMetrics.density

                val tooltipWidth = (screenWidth * widthPercent / 100).coerceAtMost(screenWidth - dpToPx(40))

                val triangleSize = dpToPx(12)
                val offsetFromTarget = dpToPx(16)
                val clearanceBuffer = dpToPx(4)

                val firstContentView = rootView.getChildAt(0)
                val contentViewLocation = IntArray(2)
                var absoluteYOffset = 0
                if (firstContentView != null) {
                    firstContentView.getLocationOnScreen(contentViewLocation)
                    absoluteYOffset = contentViewLocation[1]
                }

                val targetX_js = tooltipTarget.x.toFloat()
                val targetY_js = tooltipTarget.y.toFloat()

                val targetX_px = (targetX_js * scale).roundToInt()
                val targetY_px = (targetY_js * scale).roundToInt()

                val targetY_fixed = targetY_px + absoluteYOffset

                val targetWidth_px = (tooltipTarget.width.toFloat() * scale).roundToInt()
                val targetHeight_px = (tooltipTarget.height.toFloat() * scale).roundToInt()

                val targetCenterX = targetX_px + targetWidth_px / 2
                val targetTop = targetY_fixed
                val targetBottom = targetY_fixed + targetHeight_px

                val closeButtonMargin = dpToPx(2)
                val closeButtonSize = dpToPx(16)
                val requiredRightPadding = dpToPx(10)

                // 1. TOOLTIP BOX (content only)
                val tooltipContainer = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(tooltipWidth, FrameLayout.LayoutParams.WRAP_CONTENT)

                    setBackgroundColor(bgColorInt)
                    setPadding(dpToPx(16), dpToPx(12), requiredRightPadding, dpToPx(12))
                    elevation = dpToPx(8).toFloat()
                }

                val textLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                }

                if (title.isNotEmpty() || icon != null) {
                    val titleView = TextView(activity).apply {
                        text = if (icon != null) "$icon $title" else title
                        textSize = line1FontSize.toFloat()
                        setTextColor(titleColor)
                        setPadding(0, 0, 0, dpToPx(4))
                    }
                    textLayout.addView(titleView)
                }

                if (message.isNotEmpty()) {
                    val messageView = TextView(activity).apply {
                        text = message
                        textSize = line2FontSize.toFloat()
                        setTextColor(messageColor)
                    }
                    textLayout.addView(messageView)
                }
                tooltipContainer.addView(textLayout)

                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(tooltipWidth, View.MeasureSpec.EXACTLY)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                tooltipContainer.measure(widthMeasureSpec, heightMeasureSpec)
                val actualTooltipHeight = tooltipContainer.measuredHeight

                // 2. POSITIONING LOGIC UPDATE: Account for triangle size
                val totalHeightRequired = actualTooltipHeight + offsetFromTarget + triangleSize + clearanceBuffer
                val spaceAbove = targetTop
                val spaceBelow = screenHeight - targetBottom

                val tooltipX: Int
                val tooltipY: Int
                val showAbove: Boolean

                if (spaceAbove >= totalHeightRequired) {
                    tooltipX = targetCenterX - tooltipWidth / 2
                    // Adjusted to start Y coordinate to account for triangle being part of the dialog's content
                    tooltipY = targetTop - actualTooltipHeight - offsetFromTarget - clearanceBuffer - triangleSize
                    showAbove = true
                } else if (spaceBelow >= totalHeightRequired) {
                    tooltipX = targetCenterX - tooltipWidth / 2
                    tooltipY = targetBottom + offsetFromTarget + clearanceBuffer
                    showAbove = false
                } else {
                    // Fallback to center, need to adjust for triangle size
                    tooltipX = targetCenterX - tooltipWidth / 2
                    tooltipY = targetTop + targetHeight_px / 2 - actualTooltipHeight / 2 - triangleSize
                    showAbove = true
                }

                val finalX = tooltipX.coerceIn(dpToPx(10), screenWidth - tooltipWidth - dpToPx(10))
                val finalY = tooltipY.coerceAtLeast(0)

                // 3. TRIANGLE VIEW
                val triangleView = object : View(activity) {
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val paint = Paint().apply { color = bgColorInt; style = Paint.Style.FILL; isAntiAlias = true }
                        val path = Path().apply {
                            if (showAbove) {
                                // Triangle points down (for content shown above target)
                                moveTo(width / 2f, height.toFloat()); lineTo(0f, 0f); lineTo(width.toFloat(), 0f); close()
                            } else {
                                // Triangle points up (for content shown below target)
                                moveTo(width / 2f, 0f); lineTo(0f, height.toFloat()); lineTo(width.toFloat(), height.toFloat()); close()
                            }
                        }
                        canvas.drawPath(path, paint)
                    }
                }.apply {
                    layoutParams = FrameLayout.LayoutParams(triangleSize, triangleSize)
                    setBackgroundColor(Color.TRANSPARENT)
                }

                val triangleCenterPosition = targetCenterX - finalX
                val triangleX = (triangleCenterPosition - triangleSize / 2).coerceIn(dpToPx(8), tooltipWidth - triangleSize - dpToPx(8))

                val triangleParams = FrameLayout.LayoutParams(triangleSize, triangleSize).apply {
                    leftMargin = triangleX
                    if (showAbove) {
                        gravity = Gravity.BOTTOM or Gravity.START // Triangle sits at the bottom of the wrapper
                    } else {
                        gravity = Gravity.TOP or Gravity.START // Triangle sits at the top of the wrapper
                    }
                }

                // 4. WRAPPER LAYOUT (New root for the Dialog, solves clipping)
                val wrapperLayout = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(tooltipWidth, actualTooltipHeight + triangleSize)
                }

                val boxParams = FrameLayout.LayoutParams(tooltipWidth, actualTooltipHeight).apply {
                    if (showAbove) {
                        // Box starts at the top of the wrapper
                        gravity = Gravity.TOP or Gravity.START
                    } else {
                        // Box starts after the triangle (at the bottom of the wrapper)
                        gravity = Gravity.BOTTOM or Gravity.START
                    }
                }

                wrapperLayout.addView(tooltipContainer, boxParams)
                wrapperLayout.addView(triangleView, triangleParams)


                // 5. CLOSE BUTTON (Must be relative to the inner tooltipContainer)
                val closeButton = TextView(activity).apply {
                    text = "✕"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    setPadding(0, 0, 0, 0)

                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#80000000"))
                    }
                }

                val closeParams = FrameLayout.LayoutParams(closeButtonSize, closeButtonSize).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = closeButtonMargin
                    rightMargin = closeButtonMargin
                }
                tooltipContainer.addView(closeButton, closeParams)


                // 6. CREATE AND SHOW DIALOG
                val dialog = Dialog(activity).apply {
                    setContentView(wrapperLayout) // Use the wrapper as content

                    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                    val windowParams = window?.attributes
                    windowParams?.gravity = Gravity.TOP or Gravity.LEFT

                    // Adjust Y position based on placement to align the box correctly
                    windowParams?.x = finalX
                    // If showing above, the dialog starts higher by triangleSize to make room for it
                    windowParams?.y = if (showAbove) finalY + triangleSize - 40 else finalY

                    // Dialog size is now Wrapper size (Box + Triangle)
                    windowParams?.width = tooltipWidth
                    windowParams?.height = actualTooltipHeight + triangleSize

                    windowParams?.flags = windowParams?.flags?.or(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                    window?.attributes = windowParams

                    closeButton.setOnClickListener {
                        handleDismiss(messageId, filterId)
                        dismiss()
                        (rootView.findViewWithTag<View>("DEBUG_BOX") as? ViewGroup)?.let { rootView.removeView(it) }
                    }

                    setCanceledOnTouchOutside(true)
                    setOnDismissListener {
                        (rootView.findViewWithTag<View>("DEBUG_BOX") as? ViewGroup)?.let { rootView.removeView(it) }
                    }
                }

                // 7. ADD VISUAL DEBUGGING OVERLAY (RED BORDER)
                val debugOverlay = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(targetWidth_px, targetHeight_px).apply {
                        setMargins(targetX_px, targetY_fixed, 0, 0)
                    }
                    background = GradientDrawable().apply {
                        setStroke(dpToPx(3), Color.RED)
                        setColor(Color.TRANSPARENT)
                    }
                    elevation = 1000f
                    isClickable = false
                    tag = "DEBUG_BOX"
                }
//                rootView.addView(debugOverlay)

                dialog.show()

            } catch (e: Exception) {
                Log.e(TAG, "Error showing tooltip: ${e.message}", e)
            }
        }
    }

    private val TAG = "InAppDisplay"

    // --- HELPER FUNCTIONS (Must be accurate in your environment) ---

    // 1. Density Pixel to Pixel conversion
    private fun dpToPx(dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).roundToInt()
    }

    private fun dpToPxNew(activity: Activity, dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).roundToInt()
    }

    // 2. Locate the WebView instance in the view hierarchy
    private fun findWebView(viewGroup: ViewGroup): WebView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is WebView) {
                return child
            } else if (child is ViewGroup) {
                val webView = findWebView(child)
                if (webView != null) return webView
            }
        }
        return null
    }
}
