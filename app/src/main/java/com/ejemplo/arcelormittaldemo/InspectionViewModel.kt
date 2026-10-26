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
                frame = null,
                message = message
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
