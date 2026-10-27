package com.ejemplo.arcelormittaldemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var frame: InspectionFrame? = null
    private var pressedCandidate: CandidateBox? = null
    private var downX = 0f
    private var downY = 0f
    var onCandidateClick: ((CandidateBox) -> Unit)? = null
    private val density = resources.displayMetrics.density
    private val tapSlop = maxOf(
        ViewConfiguration.get(context).scaledTouchSlop * TAP_SLOP_MULTIPLIER,
        MIN_TAP_SLOP_DP * density
    )
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedCandidate = findCandidateAt(event.x, event.y)
                downX = event.x
                downY = event.y
                return pressedCandidate != null
            }
            MotionEvent.ACTION_UP -> {
                val pressed = pressedCandidate
                pressedCandidate = null
                if (
                    pressed != null &&
                    CandidateTap.remainsWithinTapSlop(
                        downX,
                        downY,
                        event.x,
                        event.y,
                        tapSlop
                    )
                ) {
                    performClick()
                    onCandidateClick?.invoke(pressed)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedCandidate = null
                return true
            }
        }
        return pressedCandidate != null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentFrame = frame ?: return
        if (width == 0 || height == 0) return

        currentFrame.candidates.forEach { candidate ->
            mapCandidateBounds(currentFrame, candidate, bounds)
            canvas.drawRect(bounds, boxPaint)
            canvas.drawText(
                "CANDIDATO",
                bounds.left,
                maxOf(labelPaint.textSize, bounds.top - 6f * density),
                labelPaint
            )
        }
    }

    private fun findCandidateAt(x: Float, y: Float): CandidateBox? {
        val currentFrame = frame ?: return null
        if (width == 0 || height == 0) return null
        return FrameCoordinates.candidateAt(currentFrame, x, y, width, height)
    }

    private fun mapCandidateBounds(
        currentFrame: InspectionFrame,
        candidate: CandidateBox,
        output: RectF
    ) {
        val mapped = FrameCoordinates.mapToView(
            candidate,
            currentFrame.width,
            currentFrame.height,
            currentFrame.rotationDegrees,
            width,
            height
        )
        output.set(
            mapped.left,
            mapped.top,
            mapped.right,
            mapped.bottom
        )
    }

    private companion object {
        const val TAP_SLOP_MULTIPLIER = 2f
        const val MIN_TAP_SLOP_DP = 24f
    }
}
