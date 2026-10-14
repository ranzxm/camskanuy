package com.ranzxm.camskanuy.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/** Pinch zoom/pan only; page changes remain explicit, accessible buttons. */
class DocumentZoomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragged = false
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            changeZoom(detector.scaleFactor)
            return true
        }
    })

    init { scaleType = ScaleType.MATRIX; isClickable = true }

    override fun setImageBitmap(bm: Bitmap?) { super.setImageBitmap(bm); resetZoom() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); updateMatrix() }
    fun resetZoom() { zoom = 1f; offsetX = 0f; offsetY = 0f; updateMatrix() }
    fun changeZoom(factor: Float) { zoom = (zoom * factor).coerceIn(1f, 5f); updateMatrix() }

    private fun updateMatrix() {
        val image = drawable ?: return
        if (image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0 || width == 0 || height == 0) return
        val scale = min(width.toFloat() / image.intrinsicWidth, height.toFloat() / image.intrinsicHeight) * zoom
        val iw = image.intrinsicWidth * scale
        val ih = image.intrinsicHeight * scale
        val xBound = ((iw - width) / 2).coerceAtLeast(0f)
        val yBound = ((ih - height) / 2).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-xBound, xBound)
        offsetY = offsetY.coerceIn(-yBound, yBound)
        imageMatrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - iw) / 2 + offsetX, (height - ih) / 2 + offsetY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(zoom > 1f || detector.isInProgress)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; dragged = false }
            MotionEvent.ACTION_MOVE -> {
                if (!detector.isInProgress && zoom > 1f) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (dx != 0f || dy != 0f) dragged = true
                    offsetX += dx; offsetY += dy; updateMatrix()
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> if (!dragged) performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (zoom > 1f) {
            info.isScrollable = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id -> offsetY += height / 4f
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id -> offsetY -= height / 4f
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id -> offsetX += width / 4f
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id -> offsetX -= width / 4f
            else -> return super.performAccessibilityAction(action, arguments)
        }
        updateMatrix()
        return true
    }
}
