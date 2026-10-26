package com.ejemplo.arcelormittaldemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.CameraUnavailableException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var detectionOverlay: DetectionOverlayView
    private lateinit var statusView: TextView
    private lateinit var flashlightButton: FloatingActionButton
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cameraExecutor: Executor = Dispatchers.Default.limitedParallelism(1).asExecutor()
    private lateinit var viewModel: InspectionViewModel
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProviderRequestPending = false
    private var permissionRequestAttempted = false

    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            viewModel.reportError(getString(R.string.camera_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[InspectionViewModel::class.java]
        previewView = findViewById(R.id.camera_preview)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        detectionOverlay = findViewById(R.id.detection_overlay)
        statusView = findViewById(R.id.inspection_status)
        flashlightButton = findViewById(R.id.fab_flashlight)

        if (!OpenCVLoader.initLocal()) {
            statusView.text = getString(R.string.camera_start_failed)
            finish()
            return
        }

        flashlightButton.setOnClickListener { toggleFlashlight() }
        statusView.setOnClickListener { retryCameraIfNeeded() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) {
            startCamera()
        } else if (!permissionRequestAttempted) {
            requestCameraPermissionIfNeeded()
        }
    }

    override fun onStop() {
        imageAnalysis?.clearAnalyzer()
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        camera = null
        imageAnalysis = null
        viewModel.cameraStopped(getString(R.string.camera_stopped))
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun requestCameraPermissionIfNeeded() {
        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionRequestAttempted = true
            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }
    }

    private fun retryCameraIfNeeded() {
        if (!hasCameraPermission()) {
            requestCameraPermissionIfNeeded()
        } else if (!viewModel.state.value.cameraReady) {
            startCamera()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val existingProvider = cameraProvider
        if (existingProvider != null) {
            bindCameraUseCases(existingProvider)
            return
        }

        if (cameraProviderRequestPending || !hasCameraPermission()) return
        cameraProviderRequestPending = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                cameraProviderRequestPending = false
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                    !hasCameraPermission()
                ) {
                    return@addListener
                }
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    bindCameraUseCases(provider)
                } catch (error: ExecutionException) {
                    viewModel.reportError(getString(R.string.camera_start_failed))
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    viewModel.reportError(getString(R.string.camera_start_failed))
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            !hasCameraPermission()
        ) {
            return
        }

        try {
            provider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(
                cameraExecutor,
                CameraFrameAnalyzer(
                    detector = AnomalyDetector(),
                    onFrame = viewModel::reportFrame,
                    onError = viewModel::reportError
                )
            )
            imageAnalysis = analysis
            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            val flashAvailable = camera?.cameraInfo?.hasFlashUnit() == true
            viewModel.cameraReady(flashAvailable)
            if (!flashAvailable) {
                viewModel.reportError(getString(R.string.torch_unavailable))
            }
        } catch (error: CameraUnavailableException) {
            viewModel.reportError(getString(R.string.camera_start_failed))
        } catch (error: IllegalArgumentException) {
            viewModel.reportError(getString(R.string.camera_start_failed))
        }
    }

    private fun toggleFlashlight() {
        val currentCamera = camera ?: return
        val requestedState = !viewModel.state.value.torchEnabled
        val torchFuture = currentCamera.cameraControl.enableTorch(requestedState)
        torchFuture.addListener(
            {
                try {
                    torchFuture.get()
                    viewModel.torchEnabled(requestedState)
                } catch (error: ExecutionException) {
                    viewModel.reportError(getString(R.string.torch_failed))
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    viewModel.reportError(getString(R.string.torch_failed))
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun render(state: InspectionUiState) {
        detectionOverlay.showFrame(state.frame)
        flashlightButton.isEnabled = state.cameraReady && state.torchAvailable
        flashlightButton.contentDescription = getString(
            if (state.torchEnabled) R.string.flashlight_disable else R.string.flashlight_enable
        )
        statusView.text = when {
            state.message.isNotBlank() -> state.message
            !state.cameraReady -> getString(R.string.status_waiting_for_camera)
            else -> resources.getQuantityString(
                R.plurals.candidate_count,
                state.frame?.candidates?.size ?: 0,
                state.frame?.candidates?.size ?: 0
            ) + "\n" + getString(R.string.status_point_at_surface)
        }
    }
}
