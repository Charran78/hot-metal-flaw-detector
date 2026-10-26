package com.ejemplo.arcelormittaldemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var frame: InspectionFrame? = null
    private val density = resources.displayMetrics.density
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(224, 16, 33)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            resources.displayMetrics
        )
        setShadowLayer(3f * density, 0f, 1f * density, Color.BLACK)
    }
    private val bounds = RectF()

    fun showFrame(inspectionFrame: InspectionFrame?) {
        frame = inspectionFrame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentFrame = frame ?: return
        if (width == 0 || height == 0) return

        val (rotatedWidth, rotatedHeight) = FrameCoordinates.rotatedSize(
            currentFrame.width,
            currentFrame.height,
            currentFrame.rotationDegrees
        )
        val scale = maxOf(width.toFloat() / rotatedWidth, height.toFloat() / rotatedHeight)
        val horizontalOffset = (width - rotatedWidth * scale) / 2f
        val verticalOffset = (height - rotatedHeight * scale) / 2f

        currentFrame.candidates.forEach { candidate ->
            val rotated = FrameCoordinates.rotate(
                candidate,
                currentFrame.width,
                currentFrame.height,
                currentFrame.rotationDegrees
            )
            bounds.set(
                horizontalOffset + rotated.left * scale,
                verticalOffset + rotated.top * scale,
                horizontalOffset + rotated.right * scale,
                verticalOffset + rotated.bottom * scale
            )
            canvas.drawRect(bounds, boxPaint)
            canvas.drawText(
                "CANDIDATO",
                bounds.left,
                maxOf(labelPaint.textSize, bounds.top - 6f * density),
                labelPaint
            )
        }
    }
}
