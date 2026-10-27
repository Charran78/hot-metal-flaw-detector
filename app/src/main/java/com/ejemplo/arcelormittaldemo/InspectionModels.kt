package com.ejemplo.arcelormittaldemo

data class CandidateBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class InspectionFrame(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val candidates: List<CandidateBox>
)

data class InspectionUiState(
    val frame: InspectionFrame? = null,
    val cameraReady: Boolean = false,
    val torchAvailable: Boolean = false,
    val torchEnabled: Boolean = false,
    val captureInProgress: Boolean = false,
    val evidence: CapturedEvidence? = null,
    val message: String = ""
)

object FrameCoordinates {
    fun rotatedSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        return when (normalizeRotation(rotationDegrees)) {
            90, 270 -> height to width
            else -> width to height
        }
    }

    fun rotate(
        box: CandidateBox,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): CandidateBox {
        require(width > 0 && height > 0)
        return when (normalizeRotation(rotationDegrees)) {
            90 -> CandidateBox(
                left = height - box.bottom,
                top = box.left,
                right = height - box.top,
                bottom = box.right
            )
            180 -> CandidateBox(
                left = width - box.right,
                top = height - box.bottom,
                right = width - box.left,
                bottom = height - box.top
            )
            270 -> CandidateBox(
                left = box.top,
                top = width - box.right,
                right = box.bottom,
                bottom = width - box.left
            )
            else -> box
        }
    }

    fun mapToView(
        box: CandidateBox,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int,
        viewWidth: Int,
        viewHeight: Int
    ): CandidateBox {
        require(viewWidth > 0 && viewHeight > 0)
        val (rotatedWidth, rotatedHeight) = rotatedSize(
            frameWidth,
            frameHeight,
            rotationDegrees
        )
        val scale = maxOf(viewWidth.toFloat() / rotatedWidth, viewHeight.toFloat() / rotatedHeight)
        val horizontalOffset = (viewWidth - rotatedWidth * scale) / 2f
        val verticalOffset = (viewHeight - rotatedHeight * scale) / 2f
        val rotated = rotate(box, frameWidth, frameHeight, rotationDegrees)
        return CandidateBox(
            left = horizontalOffset + rotated.left * scale,
            top = verticalOffset + rotated.top * scale,
            right = horizontalOffset + rotated.right * scale,
            bottom = verticalOffset + rotated.bottom * scale
        )
    }

    fun candidateAt(
        frame: InspectionFrame,
        x: Float,
        y: Float,
        viewWidth: Int,
        viewHeight: Int
    ): CandidateBox? = frame.candidates.firstOrNull { candidate ->
        val mapped = mapToView(
            candidate,
            frame.width,
            frame.height,
            frame.rotationDegrees,
            viewWidth,
            viewHeight
        )
        x >= mapped.left && x <= mapped.right && y >= mapped.top && y <= mapped.bottom
    }

    private fun normalizeRotation(rotationDegrees: Int): Int {
        val normalized = (rotationDegrees % 360 + 360) % 360
        require(normalized == 0 || normalized == 90 || normalized == 180 || normalized == 270)
        return normalized
    }
}

object CandidateTap {
    fun remainsWithinTapSlop(
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        slopPixels: Float
    ): Boolean {
        require(slopPixels >= 0f)
        val deltaX = upX - downX
        val deltaY = upY - downY
        return deltaX * deltaX + deltaY * deltaY <= slopPixels * slopPixels
    }
}
