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
}
