package com.ejemplo.arcelormittaldemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionViewModelTest {
    @Test
    fun failedCaptureCanBeStartedAgain() {
        val viewModel = InspectionViewModel()

        viewModel.captureStarted()
        viewModel.captureFailed("La región cambió.")
        viewModel.captureStarted()

        assertTrue(viewModel.state.value.captureInProgress)
        assertEquals("", viewModel.state.value.message)
    }

    @Test
    fun clearingReportEvidenceResetsCaptureState() {
        val viewModel = InspectionViewModel()
        viewModel.captureStarted()

        viewModel.clearEvidence()

        assertFalse(viewModel.state.value.captureInProgress)
        assertEquals(null, viewModel.state.value.evidence)
    }

    @Test
    fun stoppingCameraClearsStaleEvidenceAndCaptureState() {
        val viewModel = InspectionViewModel()
        viewModel.captureStarted()

        viewModel.cameraStopped("Cámara detenida.")

        assertFalse(viewModel.state.value.captureInProgress)
        assertEquals(null, viewModel.state.value.evidence)
        assertEquals(null, viewModel.state.value.frame)
        assertFalse(viewModel.state.value.cameraReady)
    }
}
