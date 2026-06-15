package com.mehery.pushapp

import android.util.Log
import android.view.View
import android.webkit.WebView

object PushAppPlaceholderManager {

    private const val TAG = "PlaceholderManager"
    private val placeholderViews = mutableMapOf<String, WebView>()
    private val tooltipAnchors = mutableMapOf<String, TooltipAnchor>()
    private val pendingHtml = mutableMapOf<String, String>()

    fun registerPlaceholder(id: String, webView: WebView) {
        Log.d(TAG, "Registering placeholder: $id")
        placeholderViews[id] = webView
        Log.d(TAG, "Placeholder registered. Total placeholders: ${placeholderViews.keys}")

        pendingHtml.remove(id)?.let { html ->
            Log.d(TAG, "Delivering cached inline HTML to placeholder: $id")
            loadHtml(webView, html)
        }

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
        pendingHtml.remove(id)
    }

    fun dispatchPlaceholderContent(placeholderId: String, messageId: String, html: String) {
        Log.d(
            TAG,
            "dispatchPlaceholderContent placeholderId=$placeholderId messageId=$messageId " +
                "registered=${placeholderViews.keys} htmlLength=${html.length}",
        )

        val webView = placeholderViews[placeholderId]
        if (webView != null) {
            Log.d(TAG, "Loading inline HTML into registered placeholder: $placeholderId")
            loadHtml(webView, html)
            return
        }

        tooltipAnchors[placeholderId]?.let { anchor ->
            Log.d(TAG, "Showing inline HTML via tooltip anchor: $placeholderId")
            anchor.showTooltip(html)
            return
        }

        Log.w(TAG, "Placeholder $placeholderId not registered yet — caching HTML for later delivery")
        pendingHtml[placeholderId] = html
    }

    private fun loadHtml(webView: WebView, html: String) {
        webView.post {
            webView.visibility = View.VISIBLE
            if (webView is PlaceholderView) {
                webView.loadPlaceholderHtml(html)
            } else {
                webView.loadDataWithBaseURL(
                    "https://pushapp.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        }
    }

    interface TooltipAnchor {
        fun showTooltip(html: String)
    }
}
