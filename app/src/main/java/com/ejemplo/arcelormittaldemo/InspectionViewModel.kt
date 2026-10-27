package com.ejemplo.arcelormittaldemo

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InspectionViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(InspectionUiState())
    val state: StateFlow<InspectionUiState> = mutableState.asStateFlow()

    fun cameraReady(torchAvailable: Boolean) {
        mutableState.update {
            it.copy(
                cameraReady = true,
                torchAvailable = torchAvailable,
                torchEnabled = false,
                message = ""
            )
        }
    }

    fun cameraStopped(message: String) {
        mutableState.update {
            it.copy(
                cameraReady = false,
                torchEnabled = false,
                captureInProgress = false,
                frame = null,
                evidence = null,
                message = message
            )
        }
    }

    fun captureStarted() {
        mutableState.update {
            it.copy(captureInProgress = true, evidence = null, message = "")
        }
    }

    fun captureCompleted(evidence: CapturedEvidence) {
        mutableState.update {
            it.copy(captureInProgress = false, evidence = evidence, message = "")
        }
    }

    fun captureFailed(message: String) {
        mutableState.update {
            it.copy(captureInProgress = false, message = message)
        }
    }

    fun clearEvidence() {
        mutableState.update {
            it.copy(
                captureInProgress = false,
                evidence = null
            )
        }
    }

    fun reportFrame(frame: InspectionFrame) {
        mutableState.update { it.copy(frame = frame, message = "") }
    }

    fun reportError(message: String) {
        mutableState.update { it.copy(message = message) }
    }

    fun torchEnabled(enabled: Boolean) {
        mutableState.update { it.copy(torchEnabled = enabled, message = "") }
    }
}
