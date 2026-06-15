package com.mehery.pushapp

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.mehery.pushapp.R

/**
 * Custom WebView that automatically registers itself as a placeholder
 * and sends widget_open event when attached to the window.
 * 
 * Usage in XML:
 * <com.mehery.pushapp.PlaceholderView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:placeholderId="my_placeholder_id" />
 */
@SuppressLint("SetJavaScriptEnabled")
class PlaceholderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : WebView(context, attrs, defStyleAttr, defStyleRes) {

    private var placeholderId: String? = null
    private var isRegistered = false

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d("PlaceholderView", "Inline HTML loaded for placeholder: $placeholderId")
            }
        }
        setOnTouchListener { _, _ -> false }

        // Read placeholderId from XML attributes
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(
                it,
                R.styleable.MeSendPlaceholderView,
                defStyleAttr,
                defStyleRes
            )
            try {
                placeholderId = typedArray.getString(R.styleable.MeSendPlaceholderView_placeholderId)
                Log.d("PlaceholderView", "PlaceholderView created with id: $placeholderId")
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * Set placeholder ID programmatically
     */
    fun setPlaceholderId(id: String) {
        Log.d("PlaceholderView", "setPlaceholderId called with: $id, current: $placeholderId, attached: $isAttachedToWindow")
        if (placeholderId != id) {
            // Unregister old placeholder if registered
            if (isRegistered && placeholderId != null) {
                PushAppPlaceholderManager.unregisterPlaceholder(placeholderId!!)
                isRegistered = false
            }
            placeholderId = id
            // Register new placeholder if attached to window
            if (isAttachedToWindow) {
                Log.d("PlaceholderView", "View is attached, registering immediately")
                registerPlaceholder()
            } else {
                Log.d("PlaceholderView", "View not attached yet, will register in onAttachedToWindow")
            }
        }
    }

    /**
     * Get current placeholder ID
     */
    fun getPlaceholderId(): String? = placeholderId

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d("PlaceholderView", "onAttachedToWindow called, placeholderId: $placeholderId")
        registerPlaceholder()
    }

    override fun onDetachedFromWindow() {
        unregisterPlaceholder()
        super.onDetachedFromWindow()
    }

    private fun registerPlaceholder() {
        Log.d("PlaceholderView", "registerPlaceholder called. isRegistered: $isRegistered, placeholderId: $placeholderId")
        if (!isRegistered && placeholderId != null && placeholderId!!.isNotBlank()) {
            Log.d("PlaceholderView", "Registering placeholder: $placeholderId")
            PushAppPlaceholderManager.registerPlaceholder(placeholderId!!, this)
            isRegistered = true
            Log.d("PlaceholderView", "Placeholder registered successfully: $placeholderId")
        } else {
            Log.w("PlaceholderView", "Cannot register: isRegistered=$isRegistered, placeholderId=$placeholderId")
        }
    }

    private fun unregisterPlaceholder() {
        if (isRegistered && placeholderId != null) {
            PushAppPlaceholderManager.unregisterPlaceholder(placeholderId!!)
            isRegistered = false
            Log.d("PlaceholderView", "Placeholder unregistered: $placeholderId")
        }
    }

    fun loadPlaceholderHtml(html: String) {
        visibility = View.VISIBLE
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener(null)
        loadDataWithBaseURL(
            "https://pushapp.local/",
            html,
            "text/html",
            "UTF-8",
            null,
        )
    }
}

