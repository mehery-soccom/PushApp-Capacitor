package com.mehery.pushapp

import android.app.Activity
import android.graphics.Rect
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import kotlin.math.roundToInt

data class TooltipTargetInfo(
    val targetId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    fun getRect(): Rect {
        return Rect(x, y, x + width, y + height)
    }
}

object PlaceholderViewManager {
    private const val TAG = "PlaceholderViewManager"
    private val placeholderViews = mutableMapOf<String, PlaceholderView>()
    private val tooltipTargets = mutableMapOf<String, TooltipTargetInfo>()

    fun createPlaceholderView(
        activity: Activity,
        placeholderId: String,
        x: Int,  // In viewport pixels from JavaScript
        y: Int,  // In viewport pixels from JavaScript
        width: Int,  // In viewport pixels from JavaScript
        height: Int  // In viewport pixels from JavaScript
    ) {
        // We will use the density scale to convert JavaScript's viewport pixels (which may be scaled DPs)
        // into physical Android pixels (px) for layout parameters.
        val scale: Float = activity.resources.displayMetrics.density

        // --- NEW LOGIC: Scale all JavaScript coordinates/dimensions to Android pixels ---
        val scaledX = (x.toFloat() * scale).roundToInt()
        val scaledY = (y.toFloat() * scale).roundToInt()
        val scaledWidth = (width.toFloat() * scale).roundToInt()
        val scaledHeight = (height.toFloat() * scale).roundToInt()
        // -----------------------------------------------------------------------------

        // Remove existing placeholder if any
        removePlaceholderView(activity, placeholderId)

        Log.d(TAG, "Creating placeholder view: $placeholderId at viewport ($x, $y) size ${width}x${height}")

        // Get the window's decor view (top-level view)
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            ?: return

        // JavaScript getBoundingClientRect() gives coordinates relative to the viewport
        // We need to account for the content view's position relative to the screen (status bar offset)
        val contentViewLocation = IntArray(2)
        rootView.getLocationOnScreen(contentViewLocation)
        val contentViewX = contentViewLocation[0]
        val contentViewY = contentViewLocation[1]

        // The scaled Y coordinate is relative to the viewport's top edge (0).
        // The content view starts at contentViewY.
        // If we use the content view as the parent, we need to shift the Y coordinate down
        // by the content view's top offset relative to the screen top (contentViewY).
        // However, since the WebView usually fills the content view, the scaledY (from JS)
        // should already be relative to the content area (assuming a standard setup).
        // We will rely on the scaled coordinates and margins relative to the rootView.
        val absoluteX = scaledX
        val absoluteY = scaledY

        Log.d(TAG, "Coordinates: viewport ($x, $y) -> Scaled PX ($scaledX, $scaledY)")
        Log.d(TAG, "ContentView location on screen: ($contentViewX, $contentViewY)")

        // Create placeholder view
        val placeholderView = PlaceholderView(activity)
        placeholderView.setPlaceholderId(placeholderId)

        // Set layout parameters using the scaled PX values
        val params = FrameLayout.LayoutParams(
            scaledWidth, // Use scaled width
            scaledHeight // Use scaled height
        )

        // Set position (absolute coordinates relative to content view)
        params.leftMargin = absoluteX
        params.topMargin = absoluteY

        // Make sure it's on top
        placeholderView.setZ(1000f)

        // Make it non-interactive so touches pass through when empty/transparent
        placeholderView.isClickable = false
        placeholderView.isFocusable = false
        placeholderView.isFocusableInTouchMode = false

        placeholderView.layoutParams = params

        // Add to root view (will overlay the WebView)
        rootView.addView(placeholderView)

        // Store reference
        placeholderViews[placeholderId] = placeholderView

        Log.d(TAG, "Placeholder view created and added: $placeholderId at ($absoluteX, $absoluteY) in PX")
    }
    
    private fun getStatusBarHeight(activity: Activity): Int {
        var result = 0
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = activity.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun removePlaceholderView(activity: Activity, placeholderId: String) {
        val placeholderView = placeholderViews[placeholderId] ?: return

        try {
            val parent = placeholderView.parent as? ViewGroup
            parent?.removeView(placeholderView)
            placeholderViews.remove(placeholderId)
            Log.d(TAG, "Placeholder view removed: $placeholderId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing placeholder view: ${e.message}", e)
        }
    }

    fun removeAllPlaceholderViews(activity: Activity) {
        val ids = placeholderViews.keys.toList()
        ids.forEach { removePlaceholderView(activity, it) }
    }

    // ⭐️ NEW: Register tooltip target
    fun registerTooltipTarget(
        activity: Activity,
        targetId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        Log.d(TAG, "Registering tooltip target: $targetId at ($x, $y) size ${width}x${height}")

        // Convert from pixels to dp if needed (coordinates come from JavaScript as pixels)
        val density = activity.resources.displayMetrics.density
        val targetInfo = TooltipTargetInfo(
            targetId = targetId,
            x = x, // Already in pixels from JS
            y = y, // Already in pixels from JS
            width = width, // Already in pixels from JS
            height = height // Already in pixels from JS
        )

        tooltipTargets[targetId] = targetInfo

        // Send widget_open event with compare=targetId
        Log.d(TAG, "Sending widget_open event with compare=$targetId")
        PushApp.getInstance().sendEvent("widget_open", mapOf("compare" to targetId))
        Log.d(TAG, "widget_open event sent for tooltip target: $targetId")
    }

    // ⭐️ NEW: Unregister tooltip target
    fun unregisterTooltipTarget(activity: Activity, targetId: String) {
        Log.d(TAG, "Unregistering tooltip target: $targetId")
        tooltipTargets.remove(targetId)
        Log.d(TAG, "Tooltip target removed: $targetId")
    }

    // ⭐️ NEW: Get tooltip target info
    fun getTooltipTarget(targetId: String): TooltipTargetInfo? {
        return tooltipTargets[targetId]
    }

    // ⭐️ NEW: Get all tooltip target IDs
    fun getAllTooltipTargetIds(): Set<String> {
        return tooltipTargets.keys
    }
}

