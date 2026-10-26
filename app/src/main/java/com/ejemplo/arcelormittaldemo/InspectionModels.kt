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

    private fun normalizeRotation(rotationDegrees: Int): Int {
        val normalized = (rotationDegrees % 360 + 360) % 360
        require(normalized == 0 || normalized == 90 || normalized == 180 || normalized == 270)
        return normalized
    }
}
