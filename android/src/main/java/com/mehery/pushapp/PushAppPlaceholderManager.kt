package com.mehery.pushapp

import android.app.Activity
import android.util.Log
import android.webkit.WebView

object PushAppPlaceholderManager {

    private const val TAG = "PlaceholderManager"
    private val placeholderViews = mutableMapOf<String, WebView>()
    private val tooltipAnchors = mutableMapOf<String, TooltipAnchor>()

    fun registerPlaceholder(id: String, webView: WebView) {
        Log.d(TAG, "Registering placeholder: $id")
        placeholderViews[id] = webView
        Log.d(TAG, "Placeholder registered. Total placeholders: ${placeholderViews.size}")
        Log.d(TAG, "Sending widget_open event with compare=$id")
        PushApp.getInstance().sendEvent("widget_open", mapOf("compare" to id))
        Log.d(TAG, "widget_open event sent for placeholder: $id")
    }

    fun registerTooltipAnchor(id: String, anchor: TooltipAnchor) {
        tooltipAnchors[id] = anchor
        PushApp.getInstance().sendEvent("widget_open", mapOf("compare" to id))
    }

    fun unregisterPlaceholder(id: String) {
        placeholderViews.remove(id)
        tooltipAnchors.remove(id)
    }

    fun dispatchPlaceholderContent(placeholderId : String,id: String, html: String) {
        println("placeholderViews keys: ${placeholderViews.keys}")
        println("tooltipAnchors keys: ${tooltipAnchors.keys}")
        placeholderViews[placeholderId]?.post {
            placeholderViews[placeholderId]?.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } ?: tooltipAnchors[placeholderId]?.let { anchor ->
            anchor.showTooltip(html)
        } ?: println("Placeholder $placeholderId not found")
    }

    interface TooltipAnchor {
        fun showTooltip(html: String)
    }
}
