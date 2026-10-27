package com.ejemplo.arcelormittaldemo

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCoordinatesTest {
    private val box = CandidateBox(100f, 50f, 200f, 150f)

    @Test
    fun keepsBoundsForZeroRotation() {
        assertEquals(box, FrameCoordinates.rotate(box, 640, 480, 0))
    }

    @Test
    fun rotatesBoundsClockwiseByNinetyDegrees() {
        assertEquals(
            CandidateBox(330f, 100f, 430f, 200f),
            FrameCoordinates.rotate(box, 640, 480, 90)
        )
    }

    @Test
    fun rotatesBoundsByOneHundredEightyDegrees() {
        assertEquals(
            CandidateBox(440f, 330f, 540f, 430f),
            FrameCoordinates.rotate(box, 640, 480, 180)
        )
    }

    @Test
    fun rotatesBoundsCounterClockwiseByNinetyDegrees() {
        assertEquals(
            CandidateBox(50f, 440f, 150f, 540f),
            FrameCoordinates.rotate(box, 640, 480, 270)
        )
    }

    @Test
    fun swapsFrameDimensionsForQuarterTurns() {
        assertEquals(480 to 640, FrameCoordinates.rotatedSize(640, 480, 90))
        assertEquals(480 to 640, FrameCoordinates.rotatedSize(640, 480, 270))
    }

    @Test
    fun mapsCandidateBoundsUsingFillCenterCrop() {
        val mapped = FrameCoordinates.mapToView(
            box,
            frameWidth = 640,
            frameHeight = 480,
            rotationDegrees = 0,
            viewWidth = 1000,
            viewHeight = 1000
        )

        assertEquals(41.6667f, mapped.left, 0.001f)
        assertEquals(104.1667f, mapped.top, 0.001f)
        assertEquals(250f, mapped.right, 0.001f)
        assertEquals(312.5f, mapped.bottom, 0.001f)
    }

    @Test
    fun hitTestUsesTheSameRotatedAndCroppedCandidateBounds() {
        val frame = InspectionFrame(
            width = 640,
            height = 480,
            rotationDegrees = 0,
            candidates = listOf(box)
        )

        assertEquals(box, FrameCoordinates.candidateAt(frame, 100f, 200f, 1000, 1000))
        assertEquals(null, FrameCoordinates.candidateAt(frame, 10f, 200f, 1000, 1000))
    }

    @Test
    fun candidateTapAllowsSmallFingerMovement() {
        assertEquals(
            true,
            CandidateTap.remainsWithinTapSlop(
                downX = 100f,
                downY = 100f,
                upX = 118f,
                upY = 110f,
                slopPixels = 24f
            )
        )
    }

    @Test
    fun candidateTapRejectsDraggingAwayFromSelectedBox() {
        assertEquals(
            false,
            CandidateTap.remainsWithinTapSlop(
                downX = 100f,
                downY = 100f,
                upX = 140f,
                upY = 100f,
                slopPixels = 24f
            )
        )
    }
}
