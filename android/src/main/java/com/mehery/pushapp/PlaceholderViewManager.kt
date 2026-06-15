package com.mehery.pushapp

import android.app.Activity
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import kotlin.math.max
import kotlin.math.roundToInt

private const val INLINE_PLACEHOLDER_Z = 1f
private const val OVERLAY_Z = 20_000f

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

    private data class ClippedRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val visible: Boolean,
    )

    private fun clipToTopChrome(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        clipTop: Int?,
    ): ClippedRect {
        if (clipTop == null || clipTop <= 0) {
            return ClippedRect(x, y, width, height, width > 0 && height > 0)
        }

        val bottom = y + height
        if (bottom <= clipTop) {
            return ClippedRect(x, y, width, 0, false)
        }

        if (y < clipTop) {
            return ClippedRect(x, clipTop, width, bottom - clipTop, true)
        }

        return ClippedRect(x, y, width, height, height > 0)
    }

    fun createPlaceholderView(
        activity: Activity,
        placeholderId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        clipTop: Int? = null,
    ) {
        val clipped = clipToTopChrome(x, y, width, height, clipTop)
        val scale = activity.resources.displayMetrics.density
        val scaledX = (clipped.x.toFloat() * scale).roundToInt()
        val scaledY = (clipped.y.toFloat() * scale).roundToInt()
        val scaledWidth = max((clipped.width.toFloat() * scale).roundToInt(), (120 * scale).roundToInt())
        val scaledHeight = if (clipped.visible) {
            max((clipped.height.toFloat() * scale).roundToInt(), (120 * scale).roundToInt())
        } else {
            0
        }

        removePlaceholderView(activity, placeholderId)

        Log.d(TAG, "Creating placeholder view: $placeholderId at viewport ($x, $y) size ${width}x${height}")

        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            ?: return

        val placeholderView = PlaceholderView(activity)
        placeholderView.setPlaceholderId(placeholderId)

        val params = FrameLayout.LayoutParams(scaledWidth, scaledHeight)
        params.leftMargin = scaledX
        params.topMargin = scaledY

        placeholderView.setZ(INLINE_PLACEHOLDER_Z)
        ViewCompat.setElevation(placeholderView, INLINE_PLACEHOLDER_Z)
        placeholderView.isClickable = false
        placeholderView.isFocusable = false
        placeholderView.isFocusableInTouchMode = false
        placeholderView.layoutParams = params
        placeholderView.visibility = if (clipped.visible && scaledHeight > 0) View.VISIBLE else View.INVISIBLE

        rootView.addView(placeholderView)
        rootView.requestLayout()

        placeholderViews[placeholderId] = placeholderView
        Log.d(TAG, "Placeholder view created: $placeholderId at ($scaledX, $scaledY) size ${scaledWidth}x${scaledHeight}")
    }

    fun updatePlaceholderView(
        activity: Activity,
        placeholderId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        clipTop: Int? = null,
    ) {
        val placeholderView = placeholderViews[placeholderId] ?: return

        val clipped = clipToTopChrome(x, y, width, height, clipTop)
        val scale = activity.resources.displayMetrics.density
        val scaledX = (clipped.x.toFloat() * scale).roundToInt()
        val scaledY = (clipped.y.toFloat() * scale).roundToInt()
        val scaledWidth = max((clipped.width.toFloat() * scale).roundToInt(), 1)
        val scaledHeight = (clipped.height.toFloat() * scale).roundToInt()

        val params = placeholderView.layoutParams as? FrameLayout.LayoutParams ?: return
        params.width = scaledWidth
        params.height = scaledHeight
        params.leftMargin = scaledX
        params.topMargin = scaledY
        placeholderView.layoutParams = params

        val screenHeight = activity.resources.displayMetrics.heightPixels
        val onScreen = clipped.visible &&
            scaledHeight > 0 &&
            scaledY + scaledHeight > 0 &&
            scaledY < screenHeight
        placeholderView.visibility = if (onScreen) View.VISIBLE else View.INVISIBLE
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
        placeholderViews.keys.toList().forEach { removePlaceholderView(activity, it) }
    }

    fun registerTooltipTarget(
        activity: Activity,
        targetId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        Log.d(TAG, "Registering tooltip target: $targetId at ($x, $y) size ${width}x${height}")

        tooltipTargets[targetId] = TooltipTargetInfo(
            targetId = targetId,
            x = x,
            y = y,
            width = width,
            height = height,
        )

        Log.d(TAG, "Sending widget_open event with compare=$targetId")
        PushApp.getInstance().sendEvent("widget_open", mapOf("compare" to targetId))
        Log.d(TAG, "widget_open event sent for tooltip target: $targetId")
    }

    fun unregisterTooltipTarget(activity: Activity, targetId: String) {
        Log.d(TAG, "Unregistering tooltip target: $targetId")
        tooltipTargets.remove(targetId)
        Log.d(TAG, "Tooltip target removed: $targetId")
    }

    fun getTooltipTarget(targetId: String): TooltipTargetInfo? {
        return tooltipTargets[targetId]
    }

    fun getAllTooltipTargetIds(): Set<String> {
        return tooltipTargets.keys
    }

    fun setPlaceholdersVisible(visible: Boolean) {
        val visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
        placeholderViews.values.forEach { it.visibility = visibility }
    }
}
