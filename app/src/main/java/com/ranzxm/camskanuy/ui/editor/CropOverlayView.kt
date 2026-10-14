package com.ranzxm.camskanuy.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import com.ranzxm.camskanuy.scanner.ScanCrop
import kotlin.math.hypot
import kotlin.math.min

/** Source-coordinate overlay. Labeled external controls provide the complete TalkBack alternative. */
class CropOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val imageRect = RectF()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CropOverlayView, com.google.android.material.R.attr.colorPrimary)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3
    }
    private val contrastPaint = Paint(linePaint).apply { color = Color.BLACK; strokeWidth *= 2f }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var image: Bitmap? = null
    private var dragged = -1
    var crop: ScanCrop = ScanCrop.FULL
        set(value) { field = value; invalidate() }
    var selectedCorner: Int = 0
        set(value) { field = value; invalidate() }
    var onCropChanged: ((ScanCrop) -> Unit)? = null
    var onCornerSelected: ((Int) -> Unit)? = null

    fun setImage(bitmap: Bitmap?) { image = bitmap; requestLayout(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = image ?: return
        val inset = resources.displayMetrics.density * 12
        val scale = min((width - inset * 2) / bitmap.width, (height - inset * 2) / bitmap.height).coerceAtLeast(0f)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        imageRect.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint)
        val path = Path()
        crop.points.forEachIndexed { index, point ->
            val x = imageRect.left + point.x * imageRect.width()
            val y = imageRect.top + point.y * imageRect.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, contrastPaint)
        canvas.drawPath(path, linePaint)
        crop.points.forEachIndexed { index, point ->
            val x = imageRect.left + point.x * imageRect.width()
            val y = imageRect.top + point.y * imageRect.height()
            val radius = resources.displayMetrics.density * if (index == selectedCorner) 10 else 8
            canvas.drawCircle(x, y, radius, handlePaint)
            canvas.drawCircle(x, y, radius, contrastPaint)
            canvas.drawCircle(x, y, radius, linePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || image == null || imageRect.width() <= 0 || imageRect.height() <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragged = crop.points.indices.minByOrNull { i ->
                    val p = crop.points[i]
                    hypot(event.x - imageRect.left - p.x * imageRect.width(), event.y - imageRect.top - p.y * imageRect.height())
                } ?: return false
                val point = crop.points[dragged]
                if (hypot(event.x - imageRect.left - point.x * imageRect.width(), event.y - imageRect.top - point.y * imageRect.height()) >
                    resources.displayMetrics.density * 48) { dragged = -1; return false }
                selectedCorner = dragged
                onCornerSelected?.invoke(dragged)
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragged < 0) return false
                crop = crop.move(dragged, (event.x - imageRect.left) / imageRect.width(), (event.y - imageRect.top) / imageRect.height())
                onCropChanged?.invoke(crop)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragged < 0) return false
                dragged = -1
                parent.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
